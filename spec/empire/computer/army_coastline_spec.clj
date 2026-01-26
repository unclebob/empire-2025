(ns empire.computer.army-coastline-spec
  (:require [speclj.core :refer :all]
            [empire.computer.army :as army]
            [empire.fsm.integration :as integration]
            [empire.fsm.coastline-explorer :as explorer]
            [empire.fsm.context :as context]
            [empire.fsm.engine :as engine]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [reset-all-atoms! build-test-map]]))

(describe "Coastline Explorer FSM"

  (describe "seeking coast phase"
    (before
      (reset-all-atoms!)
      ;; Map with coast on the right side
      ;; ####~
      ;; ##X#~
      ;; ####~
      ;; ####~
      (reset! atoms/game-map (build-test-map ["####~"
                                               "##X#~"
                                               "####~"
                                               "####~"]))
      (reset! atoms/computer-map [[{:type :land} {:type :land} {:type :land} {:type :land} {:type :sea}]
                                   [{:type :land} {:type :land} {:type :city :city-status :computer} {:type :land} {:type :sea}]
                                   [{:type :land} {:type :land} {:type :land} {:type :land} {:type :sea}]
                                   [{:type :land} {:type :land} {:type :land} {:type :land} {:type :sea}]])
      ;; Place exploring army at [1 1] (not on coast) with FSM structure
      (let [explorer-data (explorer/create-explorer-data [1 1])]
        (swap! atoms/game-map assoc-in [1 1 :contents]
               (merge {:type :army :owner :computer :hits 1 :mode :explore :explore-steps 50}
                      explorer-data)))
      (integration/initialize-general)
      (integration/process-general-turn))

    (it "sets explore-direction when starting exploration"
      (reset! atoms/game-map (build-test-map ["####~"
                                               "##X#~"
                                               "####~"
                                               "####~"]))
      (swap! atoms/game-map assoc-in [1 1 :contents]
             {:type :army :owner :computer :hits 1 :mode :awake})
      (army/process-army [1 1])
      (let [unit (get-in @atoms/game-map [1 1 :contents])]
        (should-not-be-nil (get-in unit [:fsm-data :explore-direction]))
        (should= false (get-in unit [:fsm-data :found-coast]))))

    (it "starts in seeking-coast state when not on coast"
      (let [unit (get-in @atoms/game-map [1 1 :contents])]
        (should= :seeking-coast (:fsm-state unit))))

    (it "sets found-coast true when reaching coastal cell"
      ;; Place army adjacent to sea at [1 3] in seeking state
      (swap! atoms/game-map assoc-in [1 1 :contents] nil)
      (let [explorer-data (assoc (explorer/create-explorer-data [1 3])
                                 :fsm-state :seeking-coast)]
        (swap! atoms/game-map assoc-in [1 3 :contents]
               (merge {:type :army :owner :computer :hits 1 :mode :explore :explore-steps 40}
                      explorer-data
                      {:fsm-data (assoc (:fsm-data explorer-data) :found-coast false)})))
      (army/process-army [1 3])
      ;; Army should have moved and transitioned to following-coast
      (let [positions (for [i (range 4) j (range 5)
                            :let [unit (get-in @atoms/game-map [i j :contents])]
                            :when (and unit (= :army (:type unit)))]
                        [i j (get-in @atoms/game-map [i j :contents])])]
        (should= 1 (count positions))
        (let [[_ _ unit] (first positions)]
          (should= :following-coast (:fsm-state unit))))))

  (describe "following coast phase"
    (before
      (reset-all-atoms!)
      ;; Map with coast to follow
      ;; ~####
      ;; ~####
      ;; ~~###
      ;; ~~~##
      (reset! atoms/game-map (build-test-map ["~####"
                                               "~####"
                                               "~~###"
                                               "~~~##"]))
      (reset! atoms/computer-map [[{:type :sea} {:type :land} {:type :land} {:type :land} {:type :land}]
                                   [{:type :sea} {:type :land} {:type :land} {:type :land} {:type :land}]
                                   [{:type :sea} {:type :sea} {:type :land} {:type :land} {:type :land}]
                                   [{:type :sea} {:type :sea} {:type :sea} {:type :land} {:type :land}]])
      ;; Place exploring army at [1 1] on coast, already in following state
      (swap! atoms/game-map assoc-in [1 1 :contents]
             {:type :army :owner :computer :hits 1 :mode :explore
              :explore-steps 40
              :fsm explorer/coastline-explorer-fsm
              :fsm-state :following-coast
              :fsm-data {:position [1 1]
                         :recent-moves [[1 1]]
                         :found-coast true
                         :explore-direction [1 0]}})
      (integration/initialize-general)
      (integration/process-general-turn))

    (it "moves to coastal cell when following coast"
      (army/process-army [1 1])
      ;; Find where army moved
      (let [positions (for [i (range 4) j (range 5)
                            :let [unit (get-in @atoms/game-map [i j :contents])]
                            :when (and unit (= :army (:type unit)))]
                        [i j])]
        (should= 1 (count positions))
        (let [[row col] (first positions)
              on-coast? (some (fn [[dr dc]]
                                (let [nr (+ row dr) nc (+ col dc)
                                      cell (get-in @atoms/game-map [nr nc])]
                                  (= :sea (:type cell))))
                              [[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]])]
          (should on-coast?)))))

  (describe "backtrack prevention"
    (before
      (reset-all-atoms!)
      ;; Narrow corridor along coast
      ;; ~#
      ;; ~#
      ;; ~#
      ;; ~#
      (reset! atoms/game-map (build-test-map ["~#"
                                               "~#"
                                               "~#"
                                               "~#"]))
      (reset! atoms/computer-map [[{:type :sea} {:type :land}]
                                   [{:type :sea} {:type :land}]
                                   [{:type :sea} {:type :land}]
                                   [{:type :sea} {:type :land}]])
      (integration/initialize-general)
      (integration/process-general-turn))

    (it "keeps only last 10 moves in recent-moves"
      (swap! atoms/game-map assoc-in [0 1 :contents]
             {:type :army :owner :computer :hits 1 :mode :explore
              :explore-steps 40
              :fsm explorer/coastline-explorer-fsm
              :fsm-state :following-coast
              :fsm-data {:position [0 1]
                         :recent-moves [[0 0] [0 1] [1 0] [1 1] [2 0] [2 1] [3 0] [3 1] [4 0] [4 1] [5 0]]
                         :found-coast true}})
      (army/process-army [0 1])
      (let [positions (for [i (range 4) j (range 2)
                            :let [unit (get-in @atoms/game-map [i j :contents])]
                            :when (and unit (= :army (:type unit)))]
                        (get-in @atoms/game-map [i j :contents]))]
        (when (seq positions)
          (let [unit (first positions)
                recent (get-in unit [:fsm-data :recent-moves])]
            (should (<= (count recent) 10)))))))

  (describe "prefers non-backtrack moves"
    (before
      (reset-all-atoms!)
      ;; T-junction on coast
      ;; ~~#~~
      ;; ~~#~~
      ;; #####
      (reset! atoms/game-map (build-test-map ["~~#~~"
                                               "~~#~~"
                                               "#####"]))
      (reset! atoms/computer-map [[{:type :sea} {:type :sea} {:type :land} {:type :sea} {:type :sea}]
                                   [{:type :sea} {:type :sea} {:type :land} {:type :sea} {:type :sea}]
                                   [{:type :land} {:type :land} {:type :land} {:type :land} {:type :land}]])
      (integration/initialize-general)
      (integration/process-general-turn))

    (it "avoids recent moves when possible"
      ;; Army at [1 2] with recent move at [0 2], should not go back to [0 2]
      (swap! atoms/game-map assoc-in [1 2 :contents]
             {:type :army :owner :computer :hits 1 :mode :explore
              :explore-steps 40
              :fsm explorer/coastline-explorer-fsm
              :fsm-state :following-coast
              :fsm-data {:position [1 2]
                         :recent-moves [[0 2] [1 2]]
                         :found-coast true}})
      (army/process-army [1 2])
      ;; Army should have moved somewhere other than [0 2] (backtrack) or stayed at [1 2]
      (let [unit-at-backtrack (get-in @atoms/game-map [0 2 :contents])
            positions (for [i (range 3) j (range 5)
                            :let [unit (get-in @atoms/game-map [i j :contents])]
                            :when (and unit (= :army (:type unit)))]
                        [i j])]
        ;; Should not have moved to backtrack position [0 2]
        (should-be-nil unit-at-backtrack)
        ;; Should have moved to row 2 (the only non-backtrack options)
        (should= 1 (count positions))
        (should= 2 (first (first positions))))))

  (describe "FSM actions return :move-to"

    (before
      (reset-all-atoms!)
      ;; Map with coast on the right side
      ;; ####~
      ;; ##A#~
      ;; ####~
      ;; ####~
      (reset! atoms/game-map (build-test-map ["####~"
                                               "####~"
                                               "####~"
                                               "####~"]))
      (reset! atoms/computer-map [[{:type :land} {:type :land} {:type :land} {:type :land} {:type :sea}]
                                   [{:type :land} {:type :land} {:type :land} {:type :land} {:type :sea}]
                                   [{:type :land} {:type :land} {:type :land} {:type :land} {:type :sea}]
                                   [{:type :land} {:type :land} {:type :land} {:type :land} {:type :sea}]]))

    (it "seek-coast action returns :move-to in fsm-data"
      ;; Unit in seeking-coast state at [1 1] (not on coast)
      (let [explorer-data (explorer/create-explorer-data [1 1])
            unit (merge {:type :army :owner :computer :hits 1 :mode :explore :explore-steps 50}
                        explorer-data
                        {:fsm-state :seeking-coast})
            ctx (context/build-context unit)
            stepped (engine/step unit ctx)]
        (should-not-be-nil (get-in stepped [:fsm-data :move-to]))
        (should (vector? (get-in stepped [:fsm-data :move-to])))))

    (it "follow-coast action returns :move-to in fsm-data"
      ;; Unit in following-coast state at [1 3] (on coast)
      (let [unit {:type :army :owner :computer :hits 1 :mode :explore :explore-steps 40
                  :fsm explorer/coastline-explorer-fsm
                  :fsm-state :following-coast
                  :fsm-data {:position [1 3]
                             :recent-moves [[1 3]]
                             :found-coast true}}
            ctx (context/build-context unit)
            stepped (engine/step unit ctx)]
        (should-not-be-nil (get-in stepped [:fsm-data :move-to]))
        (should (vector? (get-in stepped [:fsm-data :move-to])))))

    (it "seek-coast action updates recent-moves"
      (let [explorer-data (explorer/create-explorer-data [1 1])
            unit (merge {:type :army :owner :computer :hits 1 :mode :explore :explore-steps 50}
                        explorer-data
                        {:fsm-state :seeking-coast})
            ctx (context/build-context unit)
            stepped (engine/step unit ctx)
            new-recent (get-in stepped [:fsm-data :recent-moves])
            move-to (get-in stepped [:fsm-data :move-to])]
        ;; recent-moves should include the new position
        (should (some #{move-to} new-recent))))

    (it "follow-coast action prefers coastal moves"
      (let [unit {:type :army :owner :computer :hits 1 :mode :explore :explore-steps 40
                  :fsm explorer/coastline-explorer-fsm
                  :fsm-state :following-coast
                  :fsm-data {:position [1 3]
                             :recent-moves [[1 3]]
                             :found-coast true}}
            ctx (context/build-context unit)
            stepped (engine/step unit ctx)
            move-to (get-in stepped [:fsm-data :move-to])
            ;; Check that move-to is adjacent to sea
            on-coast? (some (fn [[dr dc]]
                              (let [nr (+ (first move-to) dr)
                                    nc (+ (second move-to) dc)
                                    cell (get-in @atoms/game-map [nr nc])]
                                (= :sea (:type cell))))
                            [[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]])]
        (should on-coast?))))

  (describe "prefers moves exposing unexplored territory"

    (it "chooses coastal move with more unexplored neighbors"
      (reset-all-atoms!)
      ;; Map: L-shaped coast with unit at corner
      ;; ~~~##
      ;; ~~~##
      ;; #####
      ;; #####
      ;; Unit at [2 2] can move to [1 2], [1 3], [2 1], [2 3], [3 1], [3 2], [3 3]
      ;; Coastal moves: [1 2], [1 3], [2 1], [2 3]
      ;; [1 3] and [2 3] expose unexplored columns 3-4
      ;; [1 2] and [2 1] are near explored territory
      (reset! atoms/game-map (build-test-map ["~~~##"
                                               "~~~##"
                                               "#####"
                                               "#####"]))
      ;; Computer has explored left/bottom, right/top is unexplored
      (reset! atoms/computer-map (build-test-map ["~~~  "
                                                   "~~~  "
                                                   "###  "
                                                   "#####"]))
      ;; Unit at [2 2] following coast
      (let [unit {:type :army :owner :computer :hits 1 :mode :explore :explore-steps 40
                  :fsm explorer/coastline-explorer-fsm
                  :fsm-state :following-coast
                  :fsm-data {:position [2 2]
                             :recent-moves [[2 2]]
                             :found-coast true}}
            ctx (context/build-context unit)
            ;; Run multiple times to verify preference (not random)
            moves (repeatedly 20 #(get-in (engine/step unit ctx) [:fsm-data :move-to]))]
        ;; Coastal moves with most unexplored exposure: [1 3], [2 3]
        ;; These have 3+ unexplored neighbors vs [1 2], [2 1] which have fewer
        ;; Should consistently pick moves toward unexplored
        (should (every? #(or (= [1 3] %) (= [2 3] %)) moves))))

    (it "still avoids backtracking even when scoring unexplored"
      (reset-all-atoms!)
      (reset! atoms/game-map (build-test-map ["####~"
                                               "####~"
                                               "####~"
                                               "####~"]))
      ;; All unexplored except current position
      (reset! atoms/computer-map (build-test-map ["    ~"
                                                   "  # ~"
                                                   "    ~"
                                                   "    ~"]))
      ;; Unit at [1 2] with [1 3] in recent moves (backtrack)
      (let [unit {:type :army :owner :computer :hits 1 :mode :explore :explore-steps 40
                  :fsm explorer/coastline-explorer-fsm
                  :fsm-state :following-coast
                  :fsm-data {:position [1 2]
                             :recent-moves [[1 3] [1 2]]
                             :found-coast true}}
            ctx (context/build-context unit)
            moves (repeatedly 10 #(get-in (engine/step unit ctx) [:fsm-data :move-to]))]
        ;; Should not go back to [1 3]
        (should (not-any? #(= [1 3] %) moves))))))

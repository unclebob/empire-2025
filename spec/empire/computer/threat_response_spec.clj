(ns empire.computer.threat-response-spec
  (:require [empire.test-utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.core :as core]
            [empire.application.state-access :as sa]
            [empire.computer.threat-response :as threat-response]
            [empire.config :as config]
            [empire.test-utils :refer [build-test-map reset-all-atoms! set-test-player-map! set-test-computer-map! set-test-world! update-test-world!]]))

(defn computer-units
  [pred]
  (for [i (range (count (test-utils/read-test-state :game-map)))
        j (range (count (first (test-utils/read-test-state :game-map))))
        :let [u (get-in (test-utils/read-test-state :game-map) [i j :contents])]
        :when (and u (= :computer (:owner u)) (pred u))]
    [[i j] u]))

(describe "threat-response"
  (before (reset-all-atoms!))

  (it "assigns up to 4 closest fighters when enemy fighter detected"
    (let [gm (build-test-map ["f~~"
                              "~~~"
                              "f~~"
                              "~~~"
                              "f~~"
                              "~~~"
                              "f~~"
                              "~~F"
                              "f~~"])]
      (set-test-world! gm)
      (set-test-computer-map! gm)
      (let [enemy-cell (get-in (test-utils/read-test-state :game-map) [2 7])]
        (threat-response/handle-detection! [2 7] enemy-cell))
      (let [assigned (computer-units #(= :fighter-sweep (:threat-mission %)))
            assigned-positions (set (map first assigned))]
        (should= 4 (count assigned))
        (should (not (contains? assigned-positions [0 0])))
        (doseq [[_ unit] assigned]
          (should= [2 7] (:threat-center unit))))))

  (it "assigns 2 patrol boats and 2 battleships when enemy ship detected"
    (let [gm (build-test-map ["p~~~"
                              "~b~~"
                              "p~~~"
                              "~b~~"
                              "p~~~"
                              "~b~~"
                              "~~~~"
                              "~~~~"
                              "~~~D"])]
      (set-test-world! gm)
      (set-test-computer-map! gm)
      (threat-response/handle-detection! [3 8] (get-in (test-utils/read-test-state :game-map) [3 8]))
      (let [assigned (computer-units #(= :sea-scout (:threat-mission %)))
            patrols (count (filter #(= :patrol-boat (:type (second %))) assigned))
            battleships (count (filter #(= :battleship (:type (second %))) assigned))]
        (should= 4 (count assigned))
        (should= 2 patrols)
        (should= 2 battleships))))

  (it "activates major invasion and assigns loaded transport to invading mission"
    (let [gm (build-test-map ["t~~~"
                              "~~~~"
                              "##O#"
                              "~~~~"])]
      (set-test-world! gm)
      (set-test-computer-map! gm)
      (update-test-world! assoc-in [0 0 :contents :army-count] 4)
      (update-test-world! assoc-in [0 0 :contents :transport-mission] :sailing)
      (threat-response/handle-detection! [2 2] (get-in (test-utils/read-test-state :game-map) [2 2]))
      (threat-response/refresh-major-invasion-assignments!)
      (let [transport (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
        (should (:active? (test-utils/read-test-state :major-invasion-state)))
        (should-contain [2 2] (:detection-points (test-utils/read-test-state :major-invasion-state)))
        (should= :invading (:transport-mission transport))
        (should= [2 2] (:invasion-target transport))
        (should (seq (:invasion-path transport))))
      (should (threat-response/major-invasion-target-land? [1 2]))))

  (it "selects a radius-2 invasion unload target when it yields a shorter approach"
    (let [gm (build-test-map ["~~~~~"
                              "~###~"
                              "~#O#~"
                              "t~~~~"
                              "~~~~~"])]
      (set-test-world! gm)
      (set-test-computer-map! gm)
      (update-test-world! assoc-in [0 3 :contents :army-count] 4)
      (update-test-world! assoc-in [0 3 :contents :transport-mission] :sailing)
      (threat-response/handle-detection! [2 2] (get-in (test-utils/read-test-state :game-map) [2 2]))
      (threat-response/refresh-major-invasion-assignments!)
      (let [transport (get-in (test-utils/read-test-state :game-map) [0 3 :contents])]
        (should (#{:invading :unloading} (:transport-mission transport)))
        (should (<= (core/chebyshev-distance (:invasion-target transport) [2 2]) 2)))))

  (it "ignores non-threat detections"
    (let [gm (build-test-map ["~~~"])]
      (set-test-world! gm)
      (set-test-computer-map! gm)
      (should-be-nil
       (threat-response/handle-detection! [0 0] (get-in (test-utils/read-test-state :game-map) [0 0])))
      (should-not (:active? (test-utils/read-test-state :major-invasion-state)))))

  (it "coalesces nearby major invasion detections to avoid repeated recompute"
    (let [recompute-count (atom 0)
          city-cell {:type :city :city-status :player}]
      (with-redefs [empire.computer.threat-response/recompute-major-invasion-target-land!
                    (fn [] (swap! recompute-count inc))]
        (threat-response/handle-detection! [10 10] city-cell)
        (threat-response/handle-detection! [11 10] city-cell)
        (threat-response/handle-detection! [20 20] city-cell))
      (should= 2 @recompute-count)
      (should= 2 (count (:detection-points (test-utils/read-test-state :major-invasion-state))))))

  (it "assigns naval invasion pathing toward coastal staging when city is not directly sea-reachable"
    (let [gm (build-test-map ["t~~~~~~"
                              "#######"
                              "#~~~~~#"
                              "#~#O#~#"
                              "#~~~~~#"
                              "#######"
                              "~~~~~~~"])
          bfs-calls (atom 0)]
      (set-test-world! gm)
      (set-test-computer-map! gm)
      (update-test-world! assoc-in [0 0 :contents :army-count] 4)
      (update-test-world! assoc-in [0 0 :contents :transport-mission] :sailing)
      (test-utils/set-test-state! :major-invasion-state {:active? true
                                          :detection-points [[3 3]]
                                          :target-land-set #{[3 3]}
                                          :sea-reachable-detection-points #{}
                                          :target-land-revision 1})
      (with-redefs [empire.movement.pathfinding-bfs/bfs-to-land-ho-target
                    (fn [& _]
                      (swap! bfs-calls inc)
                      nil)]
        (threat-response/refresh-major-invasion-assignments!))
      (should (< 0 @bfs-calls))
      (should= :sailing (get-in (test-utils/read-test-state :game-map) [0 0 :contents :transport-mission])))))

(describe "fighter-step-threat"
  (before (reset-all-atoms!))

  (it "attacks adjacent enemy and consumes one fuel step"
    (with-redefs [empire.computer.fighter-movement/find-adjacent-enemy (fn [_] [1 2])
                  empire.computer.fighter-movement/attack-enemy (fn [_ _] [1 1])
                  empire.computer.fighter-movement/consume-fighter-fuel (fn [_] true)]
      (should= {:pos [1 1] :steps-used 1}
               (@#'threat-response/fighter-step-threat [1 0] {:threat-center [1 0]
                                                               :threat-radius 5}))))

  (it "lands at city when refuel is needed and city is adjacent"
    (set-test-world! (build-test-map ["#O#"]))
    (with-redefs [empire.computer.fighter-movement/find-adjacent-enemy (constantly nil)
                  empire.computer.fighter-movement/should-return-to-refuel? (fn [_ _] true)
                  empire.computer.fighter-movement/find-nearest-refueling-site (fn [_] [1 0])
                  empire.computer.fighter-movement/distance-to (fn [_ _] 1)
                  empire.computer.fighter-movement/land-at-city (fn [_ _] :landed)]
      (should-be-nil
       (@#'threat-response/fighter-step-threat [0 0] {:fuel 1 :threat-center [0 0]}))))

  (it "refuels in place when adjacent refuel site is not a city"
    (set-test-world! (build-test-map ["~~~"]))
    (update-test-world! assoc-in [0 0 :contents] {:type :fighter :owner :computer :fuel 1})
    (with-redefs [empire.computer.fighter-movement/find-adjacent-enemy (constantly nil)
                  empire.computer.fighter-movement/should-return-to-refuel? (fn [_ _] true)
                  empire.computer.fighter-movement/find-nearest-refueling-site (fn [_] [1 0])
                  empire.computer.fighter-movement/distance-to (fn [_ _] 1)]
      (should= {:pos [0 0] :steps-used 1}
               (@#'threat-response/fighter-step-threat [0 0] {:fuel 1 :threat-center [0 0]}))
      (should= config/fighter-fuel
               (get-in (test-utils/read-test-state :game-map) [0 0 :contents :fuel]))))

  (it "moves back toward threat center when outside radius"
    (with-redefs [empire.computer.fighter-movement/find-adjacent-enemy (constantly nil)
                  empire.computer.fighter-movement/should-return-to-refuel? (fn [_ _] false)
                  empire.computer.fighter-movement/find-nearest-refueling-site (fn [_] [0 0])
                  empire.computer.fighter-movement/distance-to (fn [_ _] 1)
                  empire.computer.fighter-movement/hop-over-friendly (fn [_ _] {:dest [2 2] :hops 2})
                  empire.computer.fighter-movement/execute-hop (fn [_ _] {:pos [2 2] :hops 2})
                  empire.computer.fighter-movement/consume-fighter-fuel (fn [_] true)]
      (should= {:pos [2 2] :steps-used 2}
               (@#'threat-response/fighter-step-threat [0 0] {:threat-center [4 4]
                                                               :threat-radius 1}))))

  (it "patrols when in-radius and no higher-priority action applies"
    (with-redefs [empire.computer.fighter-movement/find-adjacent-enemy (constantly nil)
                  empire.computer.fighter-movement/should-return-to-refuel? (fn [_ _] false)
                  empire.computer.fighter-movement/find-nearest-refueling-site (fn [_] [0 0])
                  empire.computer.fighter-movement/distance-to (fn [_ _] 1)
                  empire.computer.fighter-movement/do-patrol (fn [_] {:pos [0 1] :hops 1})
                  empire.computer.fighter-movement/consume-fighter-fuel (fn [_] true)]
      (should= {:pos [0 1] :steps-used 1}
               (@#'threat-response/fighter-step-threat [0 0] {:fuel 10
                                                               :threat-center [0 0]
                                                               :threat-radius 5})))))

(describe "process-ship-threat"
  (before (reset-all-atoms!))

  (it "returns false when unit has no threat mission"
    (should-not
     (threat-response/process-ship-threat [0 0] :patrol-boat {})))

  (it "handles sea-scout by attacking adjacent enemy first"
    (with-redefs [empire.computer.ship-core/find-adjacent-enemy-ship (fn [_] [1 0])
                  empire.computer.ship-core/attack-enemy (fn [_ _] :attacked)
                  empire.computer.ship-core/move-toward (fn [_ _] (throw (ex-info "should not move" {})))
                  empire.computer.ship-core/explore-sea (fn [_ _] (throw (ex-info "should not explore" {})))]
      (should
       (threat-response/process-ship-threat
        [0 0] :patrol-boat {:threat-mission :sea-scout :threat-center [5 5] :threat-radius 1}))))

  (it "handles sea-scout by moving toward center when out of radius"
    (let [moved-target (atom nil)]
      (with-redefs [empire.computer.ship-core/find-adjacent-enemy-ship (constantly nil)
                    empire.computer.ship-core/move-toward (fn [_ target] (reset! moved-target target) :moved)
                    empire.computer.ship-core/explore-sea (fn [_ _] :explored)]
        (should
         (threat-response/process-ship-threat
          [0 0] :patrol-boat {:threat-mission :sea-scout :threat-center [4 4] :threat-radius 1}))
        (should= [4 4] @moved-target))))

  (it "handles sea-scout by exploring when in radius and no enemy"
    (let [explored? (atom false)]
      (with-redefs [empire.computer.ship-core/find-adjacent-enemy-ship (constantly nil)
                    empire.computer.ship-core/move-toward (fn [_ _] (throw (ex-info "should not move" {})))
                    empire.computer.ship-core/explore-sea (fn [_ _] (reset! explored? true) :explored)]
        (should
         (threat-response/process-ship-threat
          [0 0] :patrol-boat {:threat-mission :sea-scout :threat-center [0 0] :threat-radius 5}))
        (should @explored?))))

  (it "handles major invasion by moving toward nearest detection point"
    (test-utils/set-test-state! :major-invasion-state {:active? true :detection-points [[3 3]] :target-land-set #{} :started-round 1})
    (let [moved-target (atom nil)]
      (with-redefs [empire.computer.ship-core/find-adjacent-enemy-ship (constantly nil)
                    empire.computer.ship-core/move-toward (fn [_ target] (reset! moved-target target) :moved)
                    empire.computer.ship-core/explore-sea (fn [_ _] :explored)]
        (should
         (threat-response/process-ship-threat
          [0 0] :destroyer {:major-invasion true}))
        (should= [3 3] @moved-target))))

  (it "moves major-invasion patrol boat at patrol speed (4 steps)"
    (set-test-world! (build-test-map ["p~~~~~~~~~"]))
    (with-redefs [empire.computer.ship-core/find-adjacent-enemy-ship (constantly nil)
                  rand-nth (fn [xs] (first xs))]
      (should
       (threat-response/process-ship-threat
        [0 0] :patrol-boat {:major-invasion true :major-invasion-target [9 0]}))
      (should= :patrol-boat (get-in (test-utils/read-test-state :game-map) [4 0 :contents :type]))))

  (it "handles major invasion by exploring when no target exists"
    (test-utils/set-test-state! :major-invasion-state {:active? true :detection-points [] :target-land-set #{} :started-round 1})
    (let [explored? (atom false)]
      (with-redefs [empire.computer.ship-core/find-adjacent-enemy-ship (constantly nil)
                    empire.computer.ship-core/move-toward (fn [_ _] nil)
                    empire.computer.ship-core/explore-sea (fn [_ _] (reset! explored? true) :explored)]
        (should
         (threat-response/process-ship-threat
          [0 0] :destroyer {:major-invasion true}))
        (should @explored?))))

  (it "moves major-invasion ship toward detection point even when direct sea access is unavailable"
    (test-utils/set-test-state! :major-invasion-state {:active? true
             :detection-points [[3 3]]
             :sea-reachable-detection-points #{}
             :target-land-set #{}
             :started-round 1})
    (let [moved? (atom false)
          explored? (atom false)]
      (with-redefs [empire.computer.ship-core/find-adjacent-enemy-ship (constantly nil)
                    empire.computer.ship-core/move-toward (fn [_ _] (reset! moved? true) :moved)
                    empire.computer.ship-core/explore-sea (fn [_ _] (reset! explored? true) :explored)]
        (should
         (threat-response/process-ship-threat
          [0 0] :destroyer {:major-invasion true :major-invasion-target [3 3]}))
        (should @moved?)
        (should-not @explored?))))

  (it "makes patrol boats yield away from nearby invading transports"
    (set-test-world! (build-test-map ["~~~"
                                      "tp~"
                                      "~~~"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (update-test-world! assoc-in [0 1 :contents :major-invasion] true)
    (update-test-world! assoc-in [0 1 :contents :transport-mission] :invading)
    (with-redefs [empire.computer.ship-core/find-adjacent-enemy-ship (constantly nil)
                  rand-nth (fn [xs] (first xs))]
      (should
       (threat-response/process-ship-threat
        [1 1] :patrol-boat {:major-invasion true :major-invasion-target [1 1]})))
    (let [patrol-pos (first (for [x (range 3)
                                  y (range 3)
                                  :when (= :patrol-boat (get-in (test-utils/read-test-state :game-map) [x y :contents :type]))]
                              [x y]))]
      (should-not= [1 1] patrol-pos)
      (should (> (core/distance patrol-pos [0 1]) 1))))

  (it "keeps invading patrol boats within 10 cells of invasion point"
    (set-test-world! (build-test-map ["~~~~~~~p~~~~~~~"]))
    (with-redefs [empire.computer.ship-core/find-adjacent-enemy-ship (constantly nil)
                  rand-nth (fn [xs] (first xs))]
      (should
       (threat-response/process-ship-threat
        [7 0] :patrol-boat {:major-invasion true :major-invasion-target [0 0]}))
      (let [patrol-pos (first (for [x (range 15)
                                    :when (= :patrol-boat (get-in (test-utils/read-test-state :game-map) [x 0 :contents :type]))]
                                [x 0]))]
        (should (<= (core/distance patrol-pos [0 0]) 10)))))

  (it "does not run explore BFS for major-invasion patrol boats when move fails"
    (let [explored? (atom false)]
      (with-redefs [empire.computer.ship-core/find-adjacent-enemy-ship (constantly nil)
                    empire.computer.ship-core/move-toward (fn [_ _] nil)
                    empire.computer.ship-core/explore-sea (fn [& _] (reset! explored? true) :explored)]
        (should
         (threat-response/process-ship-threat
          [0 0] :patrol-boat {:major-invasion true :major-invasion-target [0 0]}))
        (should-not @explored?))))
  )

(describe "process-fighter-threat"
  (before (reset-all-atoms!))

  (it "returns nil when unit is not on fighter-sweep or major-invasion mission"
    (should-be-nil (threat-response/process-fighter-threat [0 0] {:threat-mission :none})))

  (it "returns true and iterates while fighter-step-threat yields moves"
    (set-test-world! (build-test-map ["f"]))
    (let [calls (atom 0)]
      (with-redefs [empire.computer.threat-response/fighter-step-threat
                    (fn [_ _]
                      (swap! calls inc)
                      (if (= 1 @calls)
                        {:pos [0 0] :steps-used 2}
                        nil))]
        (should (threat-response/process-fighter-threat [0 0] {:threat-mission :fighter-sweep}))
        (should= 2 @calls))))

  (it "returns true for major-invasion fighters"
    (set-test-world! (build-test-map ["f"]))
    (let [calls (atom 0)]
      (with-redefs [empire.computer.threat-response/fighter-step-threat
                    (fn [_ _]
                      (swap! calls inc)
                      nil)]
        (should (threat-response/process-fighter-threat
                 [0 0] {:major-invasion true :major-invasion-target [3 3]}))
        (should= 1 @calls)))))

(describe "prepare-transport!"
  (before (reset-all-atoms!))

  (it "returns nil when major invasion is not active"
    (test-utils/set-test-state! :major-invasion-state {:active? false :detection-points [] :target-land-set #{}})
    (should-be-nil (threat-response/prepare-transport! [0 0])))

  (it "returns nil when active but cell is not a transport"
    (test-utils/set-test-state! :major-invasion-state {:active? true :detection-points [] :target-land-set #{}})
    (set-test-world! (build-test-map ["a"]))
    (should-be-nil (threat-response/prepare-transport! [0 0])))

  (it "delegates to transport invasion prep and returns true"
    (test-utils/set-test-state! :major-invasion-state {:active? true :detection-points [[1 1]] :target-land-set #{}})
    (set-test-world! (build-test-map ["t"]))
    (let [called (atom nil)]
      (with-redefs [empire.computer.threat-response/prepare-transport-major-invasion!
                    (fn [pos unit] (reset! called [pos (:type unit)]))]
        (should (threat-response/prepare-transport! [0 0]))
        (should= [[0 0] :transport] @called)))))

(describe "prepare-transport-major-invasion!"
  (before (reset-all-atoms!))

  (it "best-invasion-target-and-path prefers closer landing over shorter path"
    (with-redefs [empire.computer.threat-response/load-major-invasion-state
                  (fn [] {:active? true :target-land-set #{[0 0]}})
                  empire.application.state-access/read-state
                  (fn [k] (case k :computer-map {} nil))
                  empire.computer.threat-response/connected-coastal-candidates
                  (fn [_ _ _] [[10 0] [2 0]])
                  empire.movement.pathfinding-bfs/bfs-to-land-ho-target
                  (fn [_ candidate _]
                    (case candidate
                      [10 0] [[10 0]]
                      [2 0] [[1 0] [2 0]]
                      nil))]
      (let [result (@#'threat-response/best-invasion-target-and-path [0 0] [0 0])]
        (should= [2 0] (:target result))
        (should= [[1 0] [2 0]] (:path result)))))

  (it "reuses existing invasion plan when position and target revision are unchanged"
    (set-test-world! (build-test-map ["t~"
                                      "~O"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (test-utils/set-test-state! :major-invasion-state {:active? true
                                        :detection-points [[1 1]]
                                        :target-land-set #{[1 1]}
                                        :sea-reachable-detection-points #{[1 1]}
                                        :target-land-revision 7})
    (update-test-world! assoc-in [0 0 :contents]
                        {:type :transport :owner :computer :army-count 4
                         :transport-mission :invading
                         :invasion-target [1 1]
                         :invasion-path [[0 1]]
                         :invasion-path-origin [0 0]
                         :invasion-plan-revision 7})
    (let [called? (atom false)
          unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
      (with-redefs [empire.computer.threat-response/best-invasion-target-and-path
                    (fn [& _] (reset! called? true) {:target [1 1] :path [[0 1]]})]
        (@#'threat-response/prepare-transport-major-invasion! [0 0] unit))
      (should-not @called?)
      (should= [[0 1]] (get-in (test-utils/read-test-state :game-map) [0 0 :contents :invasion-path]))))

  (it "recomputes invasion plan when target revision changes"
    (set-test-world! (build-test-map ["t~"
                                      "~O"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (test-utils/set-test-state! :major-invasion-state {:active? true
                                        :detection-points [[1 1]]
                                        :target-land-set #{[1 1]}
                                        :sea-reachable-detection-points #{[1 1]}
                                        :target-land-revision 9})
    (update-test-world! assoc-in [0 0 :contents]
                        {:type :transport :owner :computer :army-count 4
                         :transport-mission :invading
                         :invasion-target [1 1]
                         :invasion-path [[0 1]]
                         :invasion-path-origin [0 0]
                         :invasion-plan-revision 8})
    (let [calls (atom 0)
          unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
      (with-redefs [empire.computer.threat-response/nearest-major-sea-target (fn [_] [1 1])
                    empire.computer.threat-response/best-invasion-target-and-path
                    (fn [& _]
                      (swap! calls inc)
                      {:target [1 1] :path [[1 0] [1 1]]})]
        (@#'threat-response/prepare-transport-major-invasion! [0 0] unit))
      (should= 1 @calls)
      (should= 9 (get-in (test-utils/read-test-state :game-map) [0 0 :contents :invasion-plan-revision]))
      (should= [0 0] (get-in (test-utils/read-test-state :game-map) [0 0 :contents :invasion-path-origin]))))

  (it "prefers sea-reachable major target for transport planning"
    (set-test-world! (build-test-map ["t~"
                                      "~O"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (test-utils/set-test-state! :major-invasion-state {:active? true
                                        :detection-points [[1 1] [9 9]]
                                        :target-land-set #{[1 1] [9 9]}
                                        :sea-reachable-detection-points #{[9 9]}
                                        :target-land-revision 2})
    (update-test-world! assoc-in [0 0 :contents]
                        {:type :transport :owner :computer :army-count 3
                         :transport-mission :sailing})
    (let [seen-target (atom nil)
          unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
      (with-redefs [empire.computer.threat-response/best-invasion-target-and-path
                    (fn [_ target]
                      (reset! seen-target target)
                      {:target target :path [[0 1]]})]
        (@#'threat-response/prepare-transport-major-invasion! [0 0] unit))
      (should= [9 9] @seen-target)
      (should= [9 9] (get-in (test-utils/read-test-state :game-map) [0 0 :contents :major-invasion-target]))))

  (it "sets empty invasion transport mission to find-armies-for-invasion"
    (set-test-world! (build-test-map ["t~"
                                      "~O"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (test-utils/set-test-state! :major-invasion-state {:active? true
                                        :detection-points [[1 1]]
                                        :target-land-set #{[1 1]}
                                        :sea-reachable-detection-points #{[1 1]}
                                        :target-land-revision 1})
    (update-test-world! assoc-in [0 0 :contents :army-count] 0)
    (update-test-world! assoc-in [0 0 :contents :transport-mission] :sailing)
    (@#'threat-response/prepare-transport-major-invasion! [0 0] (get-in (test-utils/read-test-state :game-map) [0 0 :contents]))
    (should= :find-armies-for-invasion (get-in (test-utils/read-test-state :game-map) [0 0 :contents :transport-mission]))))

(describe "refresh-major-invasion-assignments!"
  (before (reset-all-atoms!))

  (it "does nothing when major invasion is inactive"
    (test-utils/set-test-state! :major-invasion-state {:active? false :detection-points [] :target-land-set #{}})
    (let [called? (atom false)]
      (with-redefs [empire.computer.threat-response/prepare-transport-major-invasion!
                    (fn [_ _] (reset! called? true))]
        (threat-response/refresh-major-invasion-assignments!)
        (should-not @called?))))

  (it "marks fighters and major ship types; delegates transports"
    (set-test-world! (build-test-map ["fpta"]))
    (test-utils/set-test-state! :major-invasion-state {:active? true
                                        :detection-points [[3 3]]
                                        :sea-reachable-detection-points #{[3 3]}
                                        :target-land-set #{}})
    (let [transport-calls (atom [])]
      (with-redefs [empire.computer.threat-response/nearest-major-sea-target (fn [_] [3 3])
                    empire.computer.threat-response/prepare-transport-major-invasion!
                    (fn [pos unit] (swap! transport-calls conj [pos (:type unit)]))]
        (threat-response/refresh-major-invasion-assignments!)
        (should= true (get-in (test-utils/read-test-state :game-map) [0 0 :contents :major-invasion]))
        (should= [3 3] (get-in (test-utils/read-test-state :game-map) [0 0 :contents :major-invasion-target]))
        (should= true (get-in (test-utils/read-test-state :game-map) [1 0 :contents :major-invasion]))
        (should= [3 3] (get-in (test-utils/read-test-state :game-map) [1 0 :contents :major-invasion-target]))
        (should= [[[2 0] :transport]] @transport-calls)
        (should-not-contain :major-invasion (get-in (test-utils/read-test-state :game-map) [3 0 :contents])))))

  (it "assigns inland armies to move-to-coast-for-invasion with a coast target"
    (set-test-world! (build-test-map ["fO~~~"
                                      "#####"
                                      "##a##"
                                      "#####"
                                      "~~~~~"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (test-utils/set-test-state! :major-invasion-state {:active? true
                                        :detection-points [[1 0]]
                                        :target-land-set #{[1 0]}
                                        :sea-reachable-detection-points #{}
                                        :target-land-revision 1})
    (threat-response/refresh-major-invasion-assignments!)
    (should= :move-to-coast-for-invasion (get-in (test-utils/read-test-state :game-map) [2 2 :contents :mode]))
    (should (vector? (get-in (test-utils/read-test-state :game-map) [2 2 :contents :coast-target])))))

(describe "dec-threat-rounds"
  (it "returns unit unchanged when no threat timer exists"
    (let [u {:type :fighter :mode :awake}]
      (should= u (@#'threat-response/dec-threat-rounds u))))

  (it "decrements positive timer"
    (should= 1 (:threat-rounds-left
                (@#'threat-response/dec-threat-rounds {:threat-rounds-left 2 :threat-mission :fighter-sweep}))))

  (it "clears threat mission fields when timer expires"
    (let [out (@#'threat-response/dec-threat-rounds {:threat-rounds-left 1
                                                     :threat-mission :fighter-sweep
                                                     :threat-center [1 1]
                                                     :threat-radius 5})]
      (should-be-nil (:threat-rounds-left out))
      (should-be-nil (:threat-mission out))
      (should-be-nil (:threat-center out))
      (should-be-nil (:threat-radius out)))))

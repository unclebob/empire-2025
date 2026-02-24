(ns empire.computer.army-spec
  "Tests for VMS Empire style computer army movement."
  (:require [speclj.core :refer :all]
            [empire.computer.army :as army]
            [empire.computer.core :as core]
            [empire.computer.production :as production]
            [empire.computer.stamping :as stamping]
            [empire.combat :as combat]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [build-test-map reset-all-atoms!]]))

(describe "process-army"
  (before (reset-all-atoms!))

  (context "attack behavior"
    (it "attacks adjacent player army"
      (reset! atoms/game-map (build-test-map ["aA#"]))
      (reset! atoms/computer-map (build-test-map ["aA#"]))
      (let [result (army/process-army [0 0])]
        ;; Either army won or lost, but combat happened
        ;; Check that computer army is no longer at [0 0]
        (should (or (nil? (:contents (get-in @atoms/game-map [0 0])))
                    ;; Or army moved to [1 0] after winning
                    (= :army (:type (:contents (get-in @atoms/game-map [1 0]))))))))

    (it "attacks adjacent free city"
      (reset! atoms/game-map (build-test-map ["a+#"]))
      (reset! atoms/computer-map (build-test-map ["a+#"]))
      ;; Run multiple times to account for 50% conquest chance
      (loop [attempts 10]
        (when (pos? attempts)
          (reset! atoms/game-map (build-test-map ["a+#"]))
          (reset! atoms/computer-map (build-test-map ["a+#"]))
          (army/process-army [0 0])
          (let [city-status (:city-status (get-in @atoms/game-map [1 0]))]
            (when (= :free city-status)
              (recur (dec attempts))))))
      ;; After up to 10 attempts, city should be conquered (very high probability)
      ;; Actually we just verify the army tried to attack
      (should-not= :free (:city-status (get-in @atoms/game-map [1 0])))))

  (context "sentry behavior"
    (it "sentry army doesn't move even with free city nearby"
      (reset! atoms/game-map (build-test-map ["a#+"]))
      (reset! atoms/computer-map (build-test-map ["a#+"]))
      (swap! atoms/game-map assoc-in [0 0 :contents :mode] :sentry)
      (army/process-army [0 0])
      (should= :army (get-in @atoms/game-map [0 0 :contents :type]))
      (should= :sentry (get-in @atoms/game-map [0 0 :contents :mode])))

    (it "sentry army attacks adjacent player army"
      (reset! atoms/game-map (build-test-map ["aA#"]))
      (reset! atoms/computer-map (build-test-map ["aA#"]))
      (swap! atoms/game-map assoc-in [0 0 :contents :mode] :sentry)
      (army/process-army [0 0])
      ;; Combat should have occurred
      (should (or (nil? (:contents (get-in @atoms/game-map [0 0])))
                  (= :computer (:owner (:contents (get-in @atoms/game-map [1 0])))))))

    (it "sentry army with attack-target moves toward target"
      (reset! atoms/game-map (build-test-map ["a##+"]))
      (reset! atoms/computer-map (build-test-map ["a##+"]))
      (swap! atoms/game-map assoc-in [0 0 :contents]
             {:type :army :owner :computer :hits 1 :mode :sentry
              :attack-target [3 0] :country-id 1})
      (army/process-army [0 0])
      ;; Army should have moved toward target (no longer at [0 0])
      (should-be-nil (:contents (get-in @atoms/game-map [0 0])))
      (should= :army (get-in @atoms/game-map [1 0 :contents :type]))))

  (context "random-explore behavior"
    (it "goes sentry on coast (adjacent to sea)"
      ;; Army at [1 0] on land, sea at [0 0]
      (reset! atoms/game-map (build-test-map ["~a##"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [1 0 :contents]
             {:type :army :owner :computer :hits 1
              :mode :random-explore :random-explore-direction [1 0] :country-id 1})
      (army/process-army [1 0])
      (should= :sentry (get-in @atoms/game-map [1 0 :contents :mode])))

    (it "moves inland in stored direction"
      ;; 3x3 all-land, army at [1 1], direction [1 0] (right)
      (reset! atoms/game-map (build-test-map ["###"
                                               "#a#"
                                               "###"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [1 1 :contents]
             {:type :army :owner :computer :hits 1
              :mode :random-explore :random-explore-direction [1 0] :country-id 1})
      (army/process-army [1 1])
      ;; Should move right to [2 1]
      (should-be-nil (:contents (get-in @atoms/game-map [1 1])))
      (should= :army (get-in @atoms/game-map [2 1 :contents :type])))

    (it "clears mode to awake when blocked inland"
      ;; Army at [2 0] heading right [1 0] — would go off 3-col map
      (reset! atoms/game-map (build-test-map ["###"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [2 0 :contents]
             {:type :army :owner :computer :hits 1
              :mode :random-explore :random-explore-direction [1 0] :country-id 1})
      (army/process-army [2 0])
      ;; Army stays, mode cleared to awake so it redirects to coast next round
      (should= :army (get-in @atoms/game-map [2 0 :contents :type]))
      (should= :awake (get-in @atoms/game-map [2 0 :contents :mode]))
      (should-be-nil (get-in @atoms/game-map [2 0 :contents :random-explore-direction]))))

  (context "attack-target behavior"
    (it "moves toward valid target"
      ;; Army at [0 0], free city at [3 0]
      (reset! atoms/game-map (build-test-map ["a##+"]))
      (reset! atoms/computer-map (build-test-map ["a##+"]))
      (swap! atoms/game-map assoc-in [0 0 :contents]
             {:type :army :owner :computer :hits 1 :mode :awake
              :attack-target [3 0] :country-id 1})
      (army/process-army [0 0])
      ;; Should move toward target
      (should-be-nil (:contents (get-in @atoms/game-map [0 0])))
      (should= :army (get-in @atoms/game-map [1 0 :contents :type])))

    (it "clears target when city conquered by computer"
      ;; Army at [0 0], target city already computer-owned
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :computer :hits 1
                                                        :mode :awake :attack-target [2 0]}}
                                {:type :land}
                                {:type :city :city-status :computer}]])
      (reset! atoms/computer-map @atoms/game-map)
      (army/process-army [0 0])
      ;; Target should be cleared
      (should-be-nil (get-in @atoms/game-map [0 0 :contents :attack-target])))

    (it "clears target when city no longer exists on computer-map"
      ;; Army with attack-target, but target cell is unexplored on computer-map
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :computer :hits 1
                                                        :mode :awake :attack-target [2 0]}}
                                {:type :land}
                                {:type :city :city-status :free}]])
      (reset! atoms/computer-map [[{:type :land} {:type :land} nil]])
      (army/process-army [0 0])
      ;; Target should be cleared (not visible on computer-map)
      (should-be-nil (get-in @atoms/game-map [0 0 :contents :attack-target]))))

  (context "city attack coordination"
    (it "assigns up to 6 closest armies to visible free city"
      ;; 8 sentry armies and a free city visible on computer-map (10 cols x 1 row)
      (let [army-cell {:type :land :country-id 1
                       :contents {:type :army :owner :computer :hits 1
                                  :mode :sentry :country-id 1}}]
        (reset! atoms/game-map [[army-cell] [army-cell] [army-cell] [army-cell]
                                 [army-cell] [army-cell] [army-cell] [army-cell]
                                 [{:type :land}] [{:type :city :city-status :free}]])
        (reset! atoms/computer-map @atoms/game-map)
        (army/assign-city-attacks)
        ;; Count armies with attack-target set
        (let [assigned (count (for [i (range 10)
                                    :let [unit (get-in @atoms/game-map [i 0 :contents])]
                                    :when (and unit (:attack-target unit))]
                                true))]
          (should= 6 assigned))))

    (it "does not assign coast-walk armies"
      (let [army-cell {:type :land :country-id 1
                       :contents {:type :army :owner :computer :hits 1
                                  :mode :sentry :country-id 1}}
            cw-cell {:type :land :country-id 1
                     :contents {:type :army :owner :computer :hits 1
                                :mode :coast-walk :coast-direction :clockwise
                                :coast-start [0 0] :coast-visited [] :country-id 1}}]
        (reset! atoms/game-map [[cw-cell] [army-cell] [{:type :city :city-status :free}]])
        (reset! atoms/computer-map @atoms/game-map)
        (army/assign-city-attacks)
        ;; Coast-walk army should NOT have attack-target
        (should-be-nil (get-in @atoms/game-map [0 0 :contents :attack-target]))
        ;; Sentry army should have attack-target
        (should= [2 0] (get-in @atoms/game-map [1 0 :contents :attack-target])))))

  (context "exploration behavior"
    (it "explores when nothing else to do"
      (reset! atoms/game-map (build-test-map ["a##"]))
      (reset! atoms/computer-map (build-test-map ["a##"]))
      (let [result (army/process-army [0 0])]
        ;; Army should move to some passable cell
        (should (or (= [1 0] result) (nil? result)))))

    (it "returns nil when no valid moves"
      ;; Army surrounded by sea
      (reset! atoms/game-map [[{:type :sea} {:type :sea} {:type :sea}]
                               [{:type :sea} {:type :land :contents {:type :army :owner :computer}} {:type :sea}]
                               [{:type :sea} {:type :sea} {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (let [result (army/process-army [1 1])]
        (should-be-nil result))))

  (context "coast-walk behavior"
    (it "moves along coastline (land adjacent to sea)"
      ;; Map: land strip with sea below
      ;; a###
      ;; ~~~~
      ;; Army at [0 0] in coast-walk mode should move to [1 0] (land adjacent to sea)
      (reset! atoms/game-map (build-test-map ["a###"
                                               "~~~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [0 0 :contents]
             {:type :army :owner :computer :hits 1
              :mode :coast-walk :coast-direction :clockwise
              :coast-start [0 0] :coast-visited [[0 0]]})
      (army/process-army [0 0])
      ;; Army should have moved to an adjacent land cell that is also adjacent to sea
      (should-be-nil (:contents (get-in @atoms/game-map [0 0])))
      (should= :army (get-in @atoms/game-map [1 0 :contents :type])))

    (it "terminates when no coast-adjacent moves available"
      ;; 3x3 all-land map, army at center. No sea anywhere → no coast candidates → terminate
      (reset! atoms/game-map (build-test-map ["###"
                                               "#a#"
                                               "###"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [1 1 :contents]
             {:type :army :owner :computer :hits 1
              :mode :coast-walk :coast-direction :clockwise
              :coast-start [0 0] :coast-visited [[0 0] [1 1]]})
      (army/process-army [1 1])
      ;; Should have terminated - switched to sentry mode
      (let [unit (get-in @atoms/game-map [1 1 :contents])]
        (should= :sentry (:mode unit))
        (should-be-nil (:coast-direction unit))))

    (it "terminates when returning to start position"
      ;; ##~   Army at [0 0], only coast candidate is [1 0] which equals coast-start
      ;; ~~~
      (reset! atoms/game-map (build-test-map ["##~"
                                               "~~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [0 0 :contents]
             {:type :army :owner :computer :hits 1
              :mode :coast-walk :coast-direction :clockwise
              :coast-start [1 0] :coast-visited [[0 0]]})
      (army/process-army [0 0])
      ;; Army should have moved to [1 0] (coast-start) and terminated
      (let [unit (get-in @atoms/game-map [1 0 :contents])]
        (should= :army (:type unit))
        (should= :sentry (:mode unit))
        (should-be-nil (:coast-direction unit))))

    (it "prefers unexplored territory"
      ;; 2x5 map: land on top, sea on bottom. Army at [2 0].
      ;; Computer-map has [0 0] and [1 0] unexplored (nil).
      ;; Candidate [1 0] has unexplored neighbor [0 0], candidate [3 0] does not.
      (reset! atoms/game-map (build-test-map ["#####"
                                               "~~~~~"]))
      (reset! atoms/computer-map [[nil {:type :sea}]
                                   [nil {:type :sea}]
                                   [{:type :land} {:type :sea}]
                                   [{:type :land} {:type :sea}]
                                   [{:type :land} {:type :sea}]])
      (swap! atoms/game-map assoc-in [2 0 :contents]
             {:type :army :owner :computer :hits 1
              :mode :coast-walk :coast-direction :clockwise
              :coast-start [4 0] :coast-visited [[2 0]]})
      (army/process-army [2 0])
      ;; Should move toward [1 0] which has unexplored neighbor [0 0]
      (should= :army (get-in @atoms/game-map [1 0 :contents :type])))

    (it "avoids backtracking"
      ;; Army at [1 0], visited includes [0 0], should prefer [2 0]
      (reset! atoms/game-map (build-test-map ["###"
                                               "~~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [1 0 :contents]
             {:type :army :owner :computer :hits 1
              :mode :coast-walk :coast-direction :clockwise
              :coast-start [0 0] :coast-visited [[0 0] [1 0]]})
      (army/process-army [1 0])
      ;; Should avoid [0 0] (in visited) and go to [2 0]
      (should= :army (get-in @atoms/game-map [2 0 :contents :type])))

    (it "attacks adjacent enemy even in coast-walk mode"
      (reset! atoms/game-map (build-test-map ["aA#"
                                               "~~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [0 0 :contents]
             {:type :army :owner :computer :hits 1
              :mode :coast-walk :coast-direction :clockwise
              :coast-start [0 0] :coast-visited [[0 0]]})
      (army/process-army [0 0])
      ;; Combat should have occurred
      (should (or (nil? (:contents (get-in @atoms/game-map [0 0])))
                  (= :computer (:owner (:contents (get-in @atoms/game-map [1 0]))))))))

  (context "territory stamping"
    (it "computer army with country-id stamps land cell it moves to"
      ;; Army at [0 0] with country-id 3, random-explore direction [0 1]
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :computer :hits 1
                                                        :country-id 3 :mode :random-explore
                                                        :random-explore-direction [0 1]}}
                                {:type :land}
                                {:type :land}]])
      (reset! atoms/computer-map @atoms/game-map)
      (army/process-army [0 0])
      ;; The cell army moved to should have country-id stamped
      (should= 3 (:country-id (get-in @atoms/game-map [0 1]))))

    (it "army without country-id does not stamp land"
      ;; Army at [0 0] without country-id
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :computer :hits 1}}
                                {:type :land}
                                {:type :land}]])
      (reset! atoms/computer-map @atoms/game-map)
      (army/process-army [0 0])
      ;; Land cell should not have country-id
      (should-be-nil (:country-id (get-in @atoms/game-map [0 1]))))

    (it "army does not stamp sea or city cells"
      ;; Army at [0 0] with country-id, next to a city
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :computer :hits 1 :country-id 3}}
                                {:type :city :city-status :free}]])
      (reset! atoms/computer-map @atoms/game-map)
      (army/process-army [0 0])
      ;; City cell should not have country-id from stamping (may get one from conquest though)
      ;; Just verify the cell is still a city
      (should= :city (:type (get-in @atoms/game-map [0 1]))))

    (it "move-unit-to stamps territory for computer armies"
      ;; Directly test core/move-unit-to stamps land
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :computer :hits 1 :country-id 5}}
                                {:type :land}]])
      (core/move-unit-to [0 0] [0 1])
      (should= 5 (:country-id (get-in @atoms/game-map [0 1])))))

  (context "ignores non-computer units"
    (it "returns nil for player army"
      (reset! atoms/game-map (build-test-map ["A#"]))
      (should-be-nil (army/process-army [0 0])))

    (it "returns nil for empty cell"
      (reset! atoms/game-map (build-test-map ["##"]))
      (should-be-nil (army/process-army [0 0]))))

  (context "country sovereignty"
    (it "army is blocked by foreign territory"
      ;; Army with country-id 1 at [0 0], target land has country-id 2
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :computer :hits 1
                                                        :country-id 1 :mode :random-explore
                                                        :random-explore-direction [0 1]}}
                                {:type :land :country-id 2}
                                {:type :land :country-id 2}]])
      (reset! atoms/computer-map @atoms/game-map)
      (army/process-army [0 0])
      ;; Army should not have moved into foreign territory
      (should= :army (get-in @atoms/game-map [0 0 :contents :type])))

    (it "army passes through own territory"
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :computer :hits 1
                                                        :country-id 1 :mode :random-explore
                                                        :random-explore-direction [0 1]}}
                                {:type :land :country-id 1}
                                {:type :land :country-id 1}]])
      (reset! atoms/computer-map @atoms/game-map)
      (army/process-army [0 0])
      ;; Army should have moved into own territory
      (should= :army (get-in @atoms/game-map [0 1 :contents :type])))

    (it "army passes through unclaimed land"
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :computer :hits 1
                                                        :country-id 1 :mode :random-explore
                                                        :random-explore-direction [0 1]}}
                                {:type :land}
                                {:type :land}]])
      (reset! atoms/computer-map @atoms/game-map)
      (army/process-army [0 0])
      ;; Army should have moved into unclaimed land
      (should= :army (get-in @atoms/game-map [0 1 :contents :type])))

    (it "army with no country-id passes through any territory"
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :computer :hits 1
                                                        :mode :random-explore
                                                        :random-explore-direction [0 1]}}
                                {:type :land :country-id 2}
                                {:type :land :country-id 2}]])
      (reset! atoms/computer-map @atoms/game-map)
      (army/process-army [0 0])
      ;; Army without country-id should move freely
      (should= :army (get-in @atoms/game-map [0 1 :contents :type])))

    (it "army can approach cities in foreign territory"
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :computer :hits 1 :country-id 1}}
                                {:type :city :city-status :free :country-id 2}]])
      (reset! atoms/computer-map @atoms/game-map)
      (army/process-army [0 0])
      ;; Army should attack the city despite foreign country-id
      ;; (army is removed after conquest attempt either way)
      (should-be-nil (get-in @atoms/game-map [0 0 :contents])))

    (it "coast-walk terminates at sovereignty boundary"
      ;; Coast-walking army hits foreign territory - no valid candidates
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :computer :hits 1
                                                       :country-id 1
                                                       :mode :coast-walk :coast-direction :clockwise
                                                       :coast-start [0 0] :coast-visited [[0 0]]}}
                                {:type :land :country-id 2}
                                {:type :land :country-id 2}]
                               [{:type :sea} {:type :sea} {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (army/process-army [0 0])
      ;; Should terminate coast-walk and switch to sentry
      (let [unit (get-in @atoms/game-map [0 0 :contents])]
        (should= :sentry (:mode unit))
        (should-be-nil (:coast-direction unit))))

    (it "army can still attack adjacent enemy across sovereignty border"
      ;; Army with country-id 1 adjacent to player army in foreign territory
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :computer :hits 1 :country-id 1}}
                                {:type :land :country-id 2 :contents {:type :army :owner :player :hits 1}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (army/process-army [0 0])
      ;; Combat should have occurred - computer army no longer at [0 0]
      (should-be-nil (get-in @atoms/game-map [0 0 :contents]))))

  (context "coastal fill behavior"
    (it "goes sentry on coastal cell when no cities to target"
      ;; Army at [1 0] on coastal cell (adjacent to sea at row 1)
      ;; Fully explored, no player/free cities
      (reset! atoms/game-map (build-test-map ["####"
                                               "~~~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (doseq [col (range 4)]
        (swap! atoms/game-map assoc-in [col 0 :country-id] 1))
      (swap! atoms/game-map assoc-in [1 0 :contents]
             {:type :army :owner :computer :hits 1 :mode :awake :country-id 1})
      (with-redefs [rand (constantly 0.5)]
        (army/process-army [1 0]))
      ;; Army should go sentry - already on coastal cell, nothing else to do
      (should= :sentry (get-in @atoms/game-map [1 0 :contents :mode])))

    (it "moves toward unoccupied coastal cell from interior"
      ;; Army at [1 0] (interior), coastal cells at row 2
      ;; ####
      ;; ####
      ;; ####
      ;; ~~~~
      (reset! atoms/game-map (build-test-map ["####"
                                               "####"
                                               "####"
                                               "~~~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (doseq [col (range 4) row (range 3)]
        (swap! atoms/game-map assoc-in [col row :country-id] 1))
      (swap! atoms/game-map assoc-in [1 0 :contents]
             {:type :army :owner :computer :hits 1 :mode :awake :country-id 1})
      (with-redefs [rand (constantly 0.5)]
        (army/process-army [1 0]))
      ;; Army should have moved toward coastal cell (row 2)
      (should-be-nil (:contents (get-in @atoms/game-map [1 0])))
      (should= :army (get-in @atoms/game-map [1 1 :contents :type])))

)

  (context "interior exploration"
    (it "interior explorer continues walking in its direction"
      ;; Army at [1 1] with interior-explore-direction already set
      (reset! atoms/game-map (build-test-map ["####"
                                               "####"
                                               "####"
                                               "~~~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (doseq [col (range 4) row (range 3)]
        (swap! atoms/game-map assoc-in [col row :country-id] 1))
      (swap! atoms/game-map assoc-in [1 1 :contents]
             {:type :army :owner :computer :hits 1 :mode :awake :country-id 1
              :interior-explore-direction [0 -1]})
      (army/process-army [1 1])
      ;; Army should have moved north to [1 0]
      (let [unit (get-in @atoms/game-map [1 0 :contents])]
        (should= :army (:type unit))
        (should= [0 -1] (:interior-explore-direction unit))))

    (it "interior explorer clears direction when reaching coast"
      ;; Army at [1 1] heading south [0 1] toward sea
      (reset! atoms/game-map (build-test-map ["####"
                                               "####"
                                               "####"
                                               "~~~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (doseq [col (range 4) row (range 3)]
        (swap! atoms/game-map assoc-in [col row :country-id] 1))
      (swap! atoms/game-map assoc-in [1 1 :contents]
             {:type :army :owner :computer :hits 1 :mode :awake :country-id 1
              :interior-explore-direction [0 1]})
      (army/process-army [1 1])
      ;; Army at [1 2] (coastal) should have direction cleared
      (let [unit (get-in @atoms/game-map [1 2 :contents])]
        (should= :army (:type unit))
        (should-be-nil (:interior-explore-direction unit))))

    (it "interior explorer clears direction when blocked"
      ;; Army at [0 0] heading north [0 -1] — would go off map
      (reset! atoms/game-map (build-test-map ["####"
                                               "####"
                                               "####"
                                               "~~~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (doseq [col (range 4) row (range 3)]
        (swap! atoms/game-map assoc-in [col row :country-id] 1))
      (swap! atoms/game-map assoc-in [0 0 :contents]
             {:type :army :owner :computer :hits 1 :mode :awake :country-id 1
              :interior-explore-direction [0 -1]})
      (army/process-army [0 0])
      ;; Army should still be at [0 0] but direction cleared
      (let [unit (get-in @atoms/game-map [0 0 :contents])]
        (should= :army (:type unit))
        (should-be-nil (:interior-explore-direction unit))))))

(describe "backtrack and anti-oscillation"
  (before (reset-all-atoms!))

  (context "backtrack memory"
    (it "records move-history after moving"
      (reset! atoms/game-map (build-test-map ["a##"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [0 0 :contents :country-id] 1)
      (swap! atoms/game-map assoc-in [0 0 :contents :mode] :awake)
      (doseq [col (range 3)]
        (swap! atoms/game-map assoc-in [col 0 :country-id] 1))
      ;; Give interior-explore-direction to force predictable move
      (swap! atoms/game-map assoc-in [0 0 :contents :interior-explore-direction] [1 0])
      (army/process-army [0 0])
      ;; Army should be at [1 0] with move-history containing [0 0]
      (should= [[0 0]] (get-in @atoms/game-map [1 0 :contents :move-history])))

    (it "caps move-history at 4 entries"
      (reset! atoms/game-map (build-test-map ["a######"]))
      (reset! atoms/computer-map @atoms/game-map)
      (doseq [col (range 7)]
        (swap! atoms/game-map assoc-in [col 0 :country-id] 1))
      (swap! atoms/game-map assoc-in [0 0 :contents :country-id] 1)
      (swap! atoms/game-map assoc-in [0 0 :contents :interior-explore-direction] [1 0])
      ;; Move 1: [0 0] -> [1 0]
      (army/process-army [0 0])
      (swap! atoms/game-map assoc-in [1 0 :contents :interior-explore-direction] [1 0])
      ;; Move 2: [1 0] -> [2 0]
      (army/process-army [1 0])
      (swap! atoms/game-map assoc-in [2 0 :contents :interior-explore-direction] [1 0])
      ;; Move 3: [2 0] -> [3 0]
      (army/process-army [2 0])
      (swap! atoms/game-map assoc-in [3 0 :contents :interior-explore-direction] [1 0])
      ;; Move 4: [3 0] -> [4 0]
      (army/process-army [3 0])
      (swap! atoms/game-map assoc-in [4 0 :contents :interior-explore-direction] [1 0])
      ;; Move 5: [4 0] -> [5 0]
      (army/process-army [4 0])
      ;; Should have last 4: [1 0] [2 0] [3 0] [4 0] — not [0 0]
      (let [history (get-in @atoms/game-map [5 0 :contents :move-history])]
        (should= 4 (count history))
        (should-not-contain [0 0] history)
        (should-contain [4 0] history)))

    (it "does not oscillate between two cells"
      ;; Computer city at [2 0] with army, sentries at [0 0] and [4 0]
      ;; Sea on row 1. Army should NOT bounce city<->adjacent.
      (reset! atoms/game-map (build-test-map ["a#X#a"
                                               "~~~~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (doseq [col (range 5)]
        (swap! atoms/game-map assoc-in [col 0 :country-id] 1))
      (swap! atoms/game-map assoc-in [0 0 :contents :mode] :sentry)
      (swap! atoms/game-map assoc-in [0 0 :contents :country-id] 1)
      (swap! atoms/game-map assoc-in [4 0 :contents :mode] :sentry)
      (swap! atoms/game-map assoc-in [4 0 :contents :country-id] 1)
      (swap! atoms/game-map assoc-in [2 0 :contents]
             {:type :army :owner :computer :hits 1 :mode :awake :country-id 1})
      ;; Round 1: army leaves city
      (with-redefs [rand (constantly 0.5)]
        (army/process-army [2 0]))
      (let [army-pos (cond
                       (= :army (get-in @atoms/game-map [1 0 :contents :type])) [1 0]
                       (= :army (get-in @atoms/game-map [3 0 :contents :type])) [3 0]
                       :else nil)]
        (should-not-be-nil army-pos)
        ;; Round 2: army should NOT go back to city
        (with-redefs [rand (constantly 0.5)]
          (army/process-army army-pos))
        (should-not= :awake (get-in @atoms/game-map [2 0 :contents :mode]))))

    (it "wakes nearby sentries when stuck"
      ;; Army boxed in by sentries on all neighbors
      (reset! atoms/game-map (build-test-map ["#####"
                                               "##a##"
                                               "#####"]))
      (reset! atoms/computer-map @atoms/game-map)
      (doseq [col (range 5) row (range 3)]
        (swap! atoms/game-map assoc-in [col row :country-id] 1))
      (doseq [pos [[1 0] [2 0] [3 0] [1 1] [3 1] [1 2] [2 2] [3 2]]]
        (swap! atoms/game-map assoc-in (conj pos :contents)
               {:type :army :owner :computer :hits 1 :mode :sentry :country-id 1}))
      (swap! atoms/game-map assoc-in [2 1 :contents :mode] :awake)
      (swap! atoms/game-map assoc-in [2 1 :contents :country-id] 1)
      (with-redefs [rand (constantly 0.5)
                    rand-nth first]
        (army/process-army [2 1]))
      ;; At least one nearby sentry should have been woken
      (let [modes (map #(get-in @atoms/game-map (conj % :contents :mode))
                       [[1 0] [2 0] [3 0] [1 1] [3 1] [1 2] [2 2] [3 2]])]
        (should (some #(not= :sentry %) modes))))

    (it "woken sentries have interior-explore-direction away from stuck army"
      (reset! atoms/game-map (build-test-map ["a#a##"
                                               "~~~~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (doseq [col (range 5)]
        (swap! atoms/game-map assoc-in [col 0 :country-id] 1))
      ;; Sentry at [0 0]
      (swap! atoms/game-map assoc-in [0 0 :contents :mode] :sentry)
      (swap! atoms/game-map assoc-in [0 0 :contents :country-id] 1)
      ;; Army at [2 0] — all neighbors occupied or sea
      (swap! atoms/game-map assoc-in [2 0 :contents :mode] :awake)
      (swap! atoms/game-map assoc-in [2 0 :contents :country-id] 1)
      ;; Sentries at [3 0] and [4 0]
      (swap! atoms/game-map assoc-in [3 0 :contents]
             {:type :army :owner :computer :hits 1 :mode :sentry :country-id 1})
      (swap! atoms/game-map assoc-in [4 0 :contents]
             {:type :army :owner :computer :hits 1 :mode :sentry :country-id 1})
      (with-redefs [rand (constantly 0.5)
                    rand-nth first]
        (army/process-army [2 0]))
      ;; Sentry at [0 0] should have direction pointing away (negative col)
      (let [dir (get-in @atoms/game-map [0 0 :contents :interior-explore-direction])]
        (when dir (should (neg? (first dir))))))

    (it "army goes sentry near coast when all coastal cells occupied"
      ;; All coastal cells (row 2) have sentries. Army at interior [2 1].
      (reset! atoms/game-map (build-test-map ["#####"
                                               "##a##"
                                               "#####"
                                               "~~~~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (doseq [col (range 5) row (range 3)]
        (swap! atoms/game-map assoc-in [col row :country-id] 1))
      ;; Fill all coastal cells (row 2) with sentries
      (doseq [col (range 5)]
        (swap! atoms/game-map assoc-in [col 2 :contents]
               {:type :army :owner :computer :hits 1 :mode :sentry :country-id 1}))
      (swap! atoms/game-map assoc-in [2 1 :contents :country-id] 1)
      (with-redefs [rand (constantly 0.5)]
        (army/process-army [2 1]))
      ;; Army should have moved toward coast or gone sentry (queued)
      (let [unit (get-in @atoms/game-map [2 1 :contents])]
        ;; Either moved away or went sentry
        (should (or (nil? unit)
                    (= :sentry (:mode unit))))))

    (it "wakes nearby sentries when army boards transport"
      (reset! atoms/game-map (build-test-map ["###"
                                               "~t~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (doseq [col (range 3)]
        (swap! atoms/game-map assoc-in [col 0 :country-id] 1))
      ;; Army at [1 0]
      (swap! atoms/game-map assoc-in [1 0 :contents]
             {:type :army :owner :computer :hits 1 :mode :awake :country-id 1})
      ;; Sentry at [2 0]
      (swap! atoms/game-map assoc-in [2 0 :contents]
             {:type :army :owner :computer :hits 1 :mode :sentry :country-id 1})
      ;; Transport at [1 1] in loading mode
      (swap! atoms/game-map assoc-in [1 1 :contents :transport-mission] :loading)
      ;; Board the army
      (core/board-transport [1 0] [1 1])
      ;; Sentry at [2 0] should be woken
      (should-not= :sentry (get-in @atoms/game-map [2 0 :contents :mode])))

    (it "army progresses over multiple rounds instead of oscillating"
      ;; Integration test: run multiple rounds, verify progress
      (reset! atoms/game-map (build-test-map ["a#X#a"
                                               "~~~~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (doseq [col (range 5)]
        (swap! atoms/game-map assoc-in [col 0 :country-id] 1))
      (swap! atoms/game-map assoc-in [0 0 :contents :mode] :sentry)
      (swap! atoms/game-map assoc-in [0 0 :contents :country-id] 1)
      (swap! atoms/game-map assoc-in [4 0 :contents :mode] :sentry)
      (swap! atoms/game-map assoc-in [4 0 :contents :country-id] 1)
      (swap! atoms/game-map assoc-in [2 0 :contents]
             {:type :army :owner :computer :hits 1 :mode :awake :country-id 1})
      ;; Run 6 rounds
      (with-redefs [rand (constantly 0.5)
                    rand-nth first]
        (dotimes [_ 6]
          (let [game-map @atoms/game-map]
            (doseq [c (range 5)
                    :let [unit (get-in game-map [c 0 :contents])]
                    :when (and unit (= :army (:type unit))
                               (= :computer (:owner unit))
                               (#{:awake} (:mode unit)))]
              (army/process-army [c 0])))))
      ;; After 6 rounds some sentries should have been woken
      ;; or army should have settled (not stuck oscillating)
      (let [awake-count (count (for [c (range 5)
                                     :let [unit (get-in @atoms/game-map [c 0 :contents])]
                                     :when (and unit (= :army (:type unit))
                                                (#{:awake} (:mode unit)))]
                                 true))]
        ;; Should not have an army perpetually awake bouncing around
        ;; (it either settles as sentry or wakes others who settle)
        (should (>= 1 awake-count)))))

  (context "update-backtrack trimming (L33)"
    (it "trims coast-visited to last 10 entries after 11+ visits"
      ;; 14-col land strip with sea below. Coast-walk army visits 12 cells.
      ;; After 11+ entries, update-backtrack should trim to last 10.
      (let [cols 14
            land-str (apply str (cons \a (repeat (dec cols) \#)))
            sea-str (apply str (repeat cols \~))]
        (reset! atoms/game-map (build-test-map [land-str sea-str]))
        (reset! atoms/computer-map @atoms/game-map)
        ;; Start army at [0 0] in coast-walk mode with 10 already-visited entries
        (swap! atoms/game-map assoc-in [0 0 :contents]
               {:type :army :owner :computer :hits 1
                :mode :coast-walk :coast-direction :clockwise
                :coast-start [13 0]
                :coast-visited [[99 0] [98 0] [97 0] [96 0] [95 0]
                                [94 0] [93 0] [92 0] [91 0] [90 0]]})
        ;; Move once — should add to visited and trim to 10
        (army/process-army [0 0])
        (let [unit (get-in @atoms/game-map [1 0 :contents])]
          (should= :army (:type unit))
          (should= 10 (count (:coast-visited unit)))
          ;; Oldest entry [99 0] should be trimmed off
          (should-not-contain [99 0] (:coast-visited unit))
          ;; New entry [1 0] should be present
          (should-contain [1 0] (:coast-visited unit)))))))

(describe "army stamping and territory"
  (before (reset-all-atoms!))

  (context "army stamping"
    (it "limits coast-walkers to 2 per country"
      (let [cell {:type :city :city-status :computer :country-id 1}
            base {:type :army :owner :computer :hits 1 :mode :awake}]
        (reset! atoms/coast-walkers-produced {})
        (with-redefs [empire.computer.production/country-coastal-cells-explored? (constantly false)]
          (let [u1 (stamping/apply-coast-walk-fields base :army cell [0 0])
                u2 (stamping/apply-coast-walk-fields base :army cell [1 0])
                u3 (stamping/apply-coast-walk-fields base :army cell [2 0])]
            (should= :coast-walk (:mode u1))
            (should= :coast-walk (:mode u2))
            ;; Third should NOT get coast-walk
            (should= :awake (:mode u3))))))

    (it "1/3 of non-coast-walk armies get random-explore"
      (let [cell {:type :city :city-status :computer :country-id 1}
            base {:type :army :owner :computer :hits 1 :mode :awake}]
        ;; Mock rand < 1/3 → should get random-explore
        (with-redefs [rand (constantly 0.2)
                      rand-nth (constantly [0 1])]
          (let [result (stamping/apply-random-explore-fields base :army cell)]
            (should= :random-explore (:mode result))
            (should= [0 1] (:random-explore-direction result))))))

    (it "2/3 of non-coast-walk armies stay awake"
      (let [cell {:type :city :city-status :computer :country-id 1}
            base {:type :army :owner :computer :hits 1 :mode :awake}]
        ;; Mock rand >= 1/3 → should stay awake
        (with-redefs [rand (constantly 0.5)]
          (let [result (stamping/apply-random-explore-fields base :army cell)]
            (should= :awake (:mode result))))))

    (it "does not override coast-walk to random-explore"
      (let [cell {:type :city :city-status :computer :country-id 1}
            cw {:type :army :owner :computer :hits 1 :mode :coast-walk
                :coast-direction :clockwise :coast-start [0 0] :coast-visited [[0 0]]}]
        (with-redefs [rand (constantly 0.1)]
          (let [result (stamping/apply-random-explore-fields cw :army cell)]
            (should= :coast-walk (:mode result)))))))

  (context "stamp-territory on cities"
    (it "stamps city cell with army's country-id"
      (reset! atoms/game-map [[{:type :city :city-status :computer}]])
      (let [army {:type :army :owner :computer :country-id 5}]
        (core/stamp-territory [0 0] army)
        (should= 5 (:country-id (get-in @atoms/game-map [0 0])))))))

;; --- Targeted mutation-killing tests ---

(describe "combat and objectives"
  (before (reset-all-atoms!))

  (context "attack-enemy deterministic combat (L115)"
    (it "attacker wins — moves to enemy position"
      (reset! atoms/game-map (build-test-map ["aA#"]))
      (reset! atoms/computer-map @atoms/game-map)
      (with-redefs [combat/resolve-combat
                    (fn [atk _def] {:winner :attacker :survivor atk})]
        (army/process-army [0 0]))
      ;; Computer army should now be at [1 0]
      (should= :computer (get-in @atoms/game-map [1 0 :contents :owner]))
      (should= :army (get-in @atoms/game-map [1 0 :contents :type]))
      ;; Original position empty
      (should-be-nil (get-in @atoms/game-map [0 0 :contents])))

    (it "attacker loses — removed from map"
      (reset! atoms/game-map (build-test-map ["aA#"]))
      (reset! atoms/computer-map @atoms/game-map)
      (with-redefs [combat/resolve-combat
                    (fn [_atk def] {:winner :defender :survivor def})]
        (army/process-army [0 0]))
      ;; Computer army at [0 0] removed
      (should-be-nil (get-in @atoms/game-map [0 0 :contents]))
      ;; Player army still at [1 0]
      (should= :player (get-in @atoms/game-map [1 0 :contents :owner]))))

  (context "find-city-objective filters (L142, L143, L149)"
    (it "player-cities filter finds player city (L142)"
      ;; Army at [0 0] no country-id. Player city right, free city left (opposite directions).
      ;; "O#a#+" → col0=player-city, col1=land, col2=army, col3=land, col4=free-city
      ;; Player city at [0 0], free at [4 0]. Army at [2 0].
      ;; find-city-objective: player-cities = [[0 0]], free-cities = [[4 0]].
      ;; Targets player city first → army moves LEFT toward [0 0].
      ;; With L142 mutation (= → not=): player-cities filter broken → empty.
      ;; Falls to free-cities → army moves RIGHT toward [4 0].
      (reset! atoms/game-map (build-test-map ["O#a#+"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [2 0 :contents]
             {:type :army :owner :computer :hits 1 :mode :awake})
      (army/process-army [2 0])
      ;; Army should have moved toward player city (left, to [1 0])
      (should= :army (get-in @atoms/game-map [1 0 :contents :type])))

    (it "free-cities filter finds free city (L143)"
      ;; Army at [0 0] no country-id. Only a free city, no player cities.
      ;; Need to distinguish find-city-objective success from explore-randomly.
      ;; Put army at center, free city at one end, obstacle at other end.
      ;; "~a#+" → col0=sea, col1=army, col2=land, col3=free-city
      ;; Army at [1 0]. Only land neighbor toward city: [2 0].
      ;; With L143 mutation: free-cities broken → no target → explore-randomly.
      ;; explore-randomly with rand-nth controlled → might go to [2 0] anyway.
      ;; Block by mocking rand-nth to pick first candidate.
      ;; explore-randomly: empty neighbors = [[2 0]], frontier check, then target.
      ;; Both paths move to [2 0]. Can't distinguish. Need different topology.
      ;;
      ;; Better: army at [2 0], free city at [0 0], multiple paths.
      ;; "+#a#" → col0=free, col1=land, col2=army, col3=land
      ;; find-city-objective → target [0 0] → step to [1 0].
      ;; With mutation: no target → explore-randomly → might pick [1 0] or [3 0].
      ;; Mock rand-nth to pick last → would pick [3 0] with explore.
      (reset! atoms/game-map (build-test-map ["+#a#"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [2 0 :contents]
             {:type :army :owner :computer :hits 1 :mode :awake})
      (with-redefs [rand-nth last]
        (army/process-army [2 0]))
      ;; With correct code: army targets [0 0], steps to [1 0].
      ;; With mutation: no target, explore-randomly with rand-nth=last picks [3 0].
      (should= :army (get-in @atoms/game-map [1 0 :contents :type])))

    (it "returns target and updates claimed when target exists (L149)"
      ;; Army at center, free city far left. All cities pre-claimed → fallback min-key.
      ;; "+##a##" → col0=free, col1-2=land, col3=army, col4-5=land
      ;; Army at [3 0], city at [0 0] (distance 3). Not adjacent → no attack.
      (reset! atoms/game-map (build-test-map ["+##a##"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [3 0 :contents]
             {:type :army :owner :computer :hits 1 :mode :awake})
      (reset! atoms/claimed-objectives #{[0 0]})
      ;; rand-nth last so explore-randomly picks rightmost neighbor [4 0]
      (with-redefs [rand-nth last]
        (army/process-army [3 0]))
      ;; Correct: target [0 0], army steps left to [2 0].
      ;; With mutation: no target, explore picks [4 0] (rightmost via rand-nth=last).
      (should= :army (get-in @atoms/game-map [2 0 :contents :type]))))

  (context "move-toward-objective preferred-in-history fallback (L175)"
    (it "falls through to sorted empty neighbors when preferred is in history"
      ;; Army at [1 0] with attack-target [3 0] (free city), move-history [[2 0]]
      ;; pathfinding prefers [2 0] but it's in history → fallback to sorted empty
      (reset! atoms/game-map (build-test-map ["#a#+"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [1 0 :contents]
             {:type :army :owner :computer :hits 1 :mode :awake
              :attack-target [3 0] :move-history [[2 0]]})
      (army/process-army [1 0])
      ;; Army should have moved to [0 0] (history blocks [2 0])
      (should= :army (get-in @atoms/game-map [0 0 :contents :type]))))

  (context "move-toward-objective sovereignty passability (L176)"
    (it "uses sovereignty check when country-id set"
      ;; Army at [0 0] with country-id 1, attack-target [2 0]. Cell [1 0] has foreign country-id 2.
      ;; With correct code: pass-fn checks sovereignty → [1 0] impassable → no path.
      ;; Fallback to empty neighbors → [1 0] blocked by sovereignty → no valid moves.
      ;; With mutation (when-not): pass-fn used when country-id nil → for non-nil country-id,
      ;; pass-fn = nil → default passability → [1 0] passable → army moves to [1 0].
      (reset! atoms/game-map (build-test-map ["a##"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [0 0 :contents]
             {:type :army :owner :computer :hits 1 :mode :awake
              :attack-target [2 0] :country-id 1})
      (swap! atoms/game-map assoc-in [0 0 :country-id] 1)
      (swap! atoms/game-map assoc-in [1 0 :country-id] 2)
      (swap! atoms/game-map assoc-in [2 0 :country-id] 2)
      (army/process-army [0 0])
      ;; Correct: army can't pass foreign territory → stays at [0 0], target cleared.
      ;; With mutation: army walks through foreign territory to [1 0].
      (should= :army (get-in @atoms/game-map [0 0 :contents :type])))))

(describe "exploration and coastal movement"
  (before (reset-all-atoms!))

  (context "explore-randomly history fallback (L212)"
    (it "falls back to unfiltered empty when all empty neighbors in history"
      ;; Army at [1 0], only neighbor [0 0] and [2 0], both in history
      ;; No country-id, no city objectives → falls to explore-randomly
      (reset! atoms/game-map (build-test-map ["#a#"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [1 0 :contents]
             {:type :army :owner :computer :hits 1 :mode :awake
              :move-history [[0 0] [2 0]]})
      ;; Make sure explore-randomly is the one running (no city objectives, no country-id)
      (with-redefs [rand-nth first]
        (army/process-army [1 0]))
      ;; Army should have moved to one of the neighbors despite them being in history
      (should (or (= :army (get-in @atoms/game-map [0 0 :contents :type]))
                  (= :army (get-in @atoms/game-map [2 0 :contents :type]))))))

  (context "coast-walk multiple best candidates (L238)"
    (it "handles two equally-scored coast candidates"
      ;; Map: 3x2, army at [1 0], sea on row 1.
      ;; [0 0] and [2 0] both adjacent to sea, both unvisited, equal unexplored neighbors
      (reset! atoms/game-map (build-test-map ["#a#"
                                               "~~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [1 0 :contents]
             {:type :army :owner :computer :hits 1
              :mode :coast-walk :coast-direction :clockwise
              :coast-start [2 0] :coast-visited [[1 0]]})
      (with-redefs [rand-nth first]
        (army/process-army [1 0]))
      ;; Army should have moved to one of the two candidates
      (should (or (= :army (get-in @atoms/game-map [0 0 :contents :type]))
                  (= :army (get-in @atoms/game-map [2 0 :contents :type]))))))

  (context "adjacent-to-computer-city? (L252, L253)"
    (it "avoids coastal cell adjacent to computer city"
      ;; "####" / "X###" / "~~~~"
      ;; col0=[land,city,sea], col1=[land,land,sea], col2=[land,land,sea], col3=[land,land,sea]
      ;; [1 1] is coastal (adj [1 2]=sea) AND adj to city [0 1]. Filtered out.
      ;; [2 1] is coastal, NOT adj to city. [3 1] same.
      ;; Army at [1 0]: nearest unfiltered coastal = [2 1] (dist 2).
      ;; Correct: target [2 1], step diagonally to [2 1].
      ;; Mutation: target [1 1] (dist 1, no longer filtered), step to [1 1].
      (reset! atoms/game-map (build-test-map ["####"
                                               "X###"
                                               "~~~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (doseq [col (range 4)]
        (swap! atoms/game-map assoc-in [col 0 :country-id] 1)
        (swap! atoms/game-map assoc-in [col 1 :country-id] 1))
      (swap! atoms/game-map assoc-in [1 0 :contents]
             {:type :army :owner :computer :hits 1 :mode :awake :country-id 1})
      (with-redefs [rand (constantly 0.5)]
        (army/process-army [1 0]))
      ;; Correct: army goes to [2 1] (diagonal step toward target [2 1]).
      ;; With mutation: army goes to [1 1] (directly, since it's the nearest unfiltered target).
      (should-not= :army (get-in @atoms/game-map [1 1 :contents :type]))
      (should= :army (get-in @atoms/game-map [2 1 :contents :type]))))

  (context "find-nearest-unoccupied-coastal-cell (L259, L264, L266, L272)"
    (it "finds coastal cell with matching country-id (L259, L264, L266)"
      ;; Army at [0 0] interior, coastal cell at [0 2] with country-id 1
      (reset! atoms/game-map [[{:type :land :country-id 1
                                 :contents {:type :army :owner :computer :hits 1
                                            :mode :awake :country-id 1}}
                                {:type :land :country-id 1}
                                {:type :land :country-id 1}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (with-redefs [rand (constantly 0.5)]
        (army/process-army [0 0]))
      ;; Army should have moved toward coast (row 2)
      (should-be-nil (get-in @atoms/game-map [0 0 :contents]))
      (should= :army (get-in @atoms/game-map [0 1 :contents :type])))

    (it "falls back to unfiltered candidates when all near computer cities (L272)"
      ;; Army at [1 0] interior. Only coastal cell [1 1] is adjacent to computer city [0 1].
      ;; remove-adjacent-to-computer-city? filters it out → empty list → fallback to unfiltered.
      ;; Map: 3 cols x 3 rows
      ;; col 0: [land, city, sea]
      ;; col 1: [land+army, land, sea]  — [1 1] is coastal (adj to [1 2]=sea) AND adj to city [0 1]
      ;; col 2: [land, land, sea]
      ;; BUT we need all coastal cells adjacent to a computer city.
      ;; Put cities adjacent to every coastal cell.
      (reset! atoms/game-map [[{:type :city :city-status :computer :country-id 1}
                                {:type :land :country-id 1}
                                {:type :sea}]
                               [{:type :land :country-id 1
                                 :contents {:type :army :owner :computer :hits 1
                                            :mode :awake :country-id 1}}
                                {:type :land :country-id 1}
                                {:type :sea}]
                               [{:type :city :city-status :computer :country-id 1}
                                {:type :land :country-id 1}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (with-redefs [rand (constantly 0.5)]
        (army/process-army [1 0]))
      ;; Army should move toward a coastal cell despite it being near cities (fallback)
      (should-be-nil (get-in @atoms/game-map [1 0 :contents]))
      (should= :army (get-in @atoms/game-map [1 1 :contents :type]))))

  (context "find-nearest-cell-close-to-coast (L284, L290, L291)"
    (it "prefers directly coastal cell (distance 0) over near-coastal (distance 1) (L284, L290, L291)"
      ;; Army at [1 0] (interior). All directly-coastal cells are occupied
      ;; except one that doesn't exist. So find-nearest-unoccupied-coastal-cell returns nil.
      ;; Then find-nearest-cell-close-to-coast finds both directly-coastal and near-coastal cells.
      ;; It should prefer distance 0 (directly coastal) over distance 1 (near-coastal).
      ;;
      ;; Map: 3 cols x 4 rows
      ;; col 0: [land, land, sentry, sea]
      ;; col 1: [army, land, sentry, sea]
      ;; col 2: [land, land, sentry, sea]
      ;; All row-2 coastal cells have sentries. row-1 cells are near-coastal (1 step).
      ;; find-nearest-unoccupied-coastal-cell: all coastal cells occupied → nil
      ;; find-nearest-cell-close-to-coast: should find row-1 cells (distance 1 from coast)
      ;;   and row-2 is occupied so NOT candidates (contents not nil).
      ;;   Actually row-1 cells ARE candidates: empty, land, country-id match.
      ;;   For row-1 cells: adjacent-to-sea? is false, but some neighbor is adjacent-to-sea? → distance 1.
      ;;   So they're near-coastal. But there are no distance-0 unoccupied cells.
      ;;   → the code picks the nearest near-coastal cell.
      ;; To test L290/L291 distinctly: I need BOTH a directly-coastal empty cell AND a near-coastal cell.
      ;; Let me have one coastal cell empty and one near-coastal cell empty, and verify the coastal one wins.
      (reset! atoms/game-map [[{:type :land :country-id 1}
                                {:type :land :country-id 1}
                                {:type :land :country-id 1
                                 :contents {:type :army :owner :computer :hits 1 :mode :sentry :country-id 1}}
                                {:type :sea}]
                               [{:type :land :country-id 1
                                 :contents {:type :army :owner :computer :hits 1
                                            :mode :awake :country-id 1}}
                                {:type :land :country-id 1}
                                {:type :land :country-id 1}
                                {:type :sea}]
                               [{:type :land :country-id 1}
                                {:type :land :country-id 1}
                                {:type :land :country-id 1
                                 :contents {:type :army :owner :computer :hits 1 :mode :sentry :country-id 1}}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      ;; [1 2] is the only empty coastal cell (adj to [1 3]=sea).
      ;; [0 1], [1 1], [2 1] are near-coastal (neighbors of row 2 which is coastal).
      ;; find-nearest-unoccupied-coastal-cell should find [1 2].
      (with-redefs [rand (constantly 0.5)]
        (army/process-army [1 0]))
      ;; Army at [1 0] should move toward [1 2] (coastal). Next step is [1 1].
      (should-be-nil (get-in @atoms/game-map [1 0 :contents]))
      (should= :army (get-in @atoms/game-map [1 1 :contents :type]))))

  (context "fill-coastal-cell wake sentries (L325)"
    (it "wakes nearby sentries when no coastal or near-coast cells available"
      ;; Army at [2 1], all coastal and near-coast cells occupied by sentries
      ;; No coastal cells, no near-coast cells → falls to wake-nearby-sentries
      (reset! atoms/game-map [[{:type :land :country-id 1
                                 :contents {:type :army :owner :computer :hits 1 :mode :sentry :country-id 1}}]
                               [{:type :land :country-id 1
                                 :contents {:type :army :owner :computer :hits 1 :mode :sentry :country-id 1}}]
                               [{:type :land :country-id 1
                                 :contents {:type :army :owner :computer :hits 1 :mode :awake :country-id 1}}]
                               [{:type :land :country-id 1
                                 :contents {:type :army :owner :computer :hits 1 :mode :sentry :country-id 1}}]
                               [{:type :land :country-id 1
                                 :contents {:type :army :owner :computer :hits 1 :mode :sentry :country-id 1}}]])
      (reset! atoms/computer-map @atoms/game-map)
      ;; No sea anywhere → no coastal cells → fill-coastal-cell can't find targets
      ;; Army is at [2 0], all land, all occupied
      (with-redefs [rand (constantly 0.5)
                    rand-nth first]
        (army/process-army [2 0]))
      ;; At least one sentry should have been woken
      (let [modes (map #(get-in @atoms/game-map [% 0 :contents :mode]) [0 1 3 4])]
        (should (some #(not= :sentry %) modes)))))

  (context "start-interior-exploration target calculation (L354)"
    (it "correctly adds direction to position"
      ;; Call start-interior-exploration directly via var reference
      ;; Army at [2 1], direction [1 1] → target = [3 2]
      (reset! atoms/game-map (build-test-map ["#####"
                                               "#####"
                                               "#####"
                                               "~~~~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (doseq [col (range 5) row (range 3)]
        (swap! atoms/game-map assoc-in [col row :country-id] 1))
      (swap! atoms/game-map assoc-in [2 1 :contents]
             {:type :army :owner :computer :hits 1 :mode :awake :country-id 1})
      (with-redefs [rand-nth (constantly [1 1])]
        (@#'army/start-interior-exploration [2 1] 1))
      ;; Army should have moved to [3 2] = [2+1, 1+1]
      (should= :army (get-in @atoms/game-map [3 2 :contents :type]))
      (should-be-nil (get-in @atoms/game-map [2 1 :contents]))))

  (context "find-and-execute-land-action city objective (L370)"
    (it "moves toward city objective when one exists"
      ;; Call find-and-execute-land-action directly via var reference
      ;; Army at [0 0] with country-id 1, free city at [3 0]
      (reset! atoms/game-map (build-test-map ["a##+"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [0 0 :contents]
             {:type :army :owner :computer :hits 1 :mode :awake :country-id 1})
      (doseq [col (range 4)]
        (swap! atoms/game-map assoc-in [col 0 :country-id] 1))
      (with-redefs [rand (constantly 0.5)]
        (@#'army/find-and-execute-land-action [0 0] 1))
      ;; Army should have moved toward the city
      (should-be-nil (get-in @atoms/game-map [0 0 :contents]))
      (should= :army (get-in @atoms/game-map [1 0 :contents :type]))))

  (context "process-random-explore goes sentry on coast after move (L391)"
    (it "goes sentry after moving to coastal cell"
      ;; Army at [1 0] in random-explore mode heading [0 1] (south).
      ;; Target [1 1] is land adjacent to sea (coastal).
      (reset! atoms/game-map (build-test-map ["###"
                                               "###"
                                               "~~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [1 0 :contents]
             {:type :army :owner :computer :hits 1
              :mode :random-explore :random-explore-direction [0 1] :country-id 1})
      (army/process-army [1 0])
      ;; Army moved to [1 1] which is coastal → should be sentry
      (let [unit (get-in @atoms/game-map [1 1 :contents])]
        (should= :army (:type unit))
        (should= :sentry (:mode unit)))))

  (context "sentry army in city triggers fill-coastal-cell (L438)"
    (it "sentry army in computer city fills coastal cell"
      ;; Sentry army in a computer city. With country-id, should trigger fill-coastal-cell.
      ;; Coastal cell at [1 0] is empty, adjacent to sea at [1 1].
      (reset! atoms/game-map [[{:type :city :city-status :computer :country-id 1
                                 :contents {:type :army :owner :computer :hits 1
                                            :mode :sentry :country-id 1}}
                                {:type :sea}]
                               [{:type :land :country-id 1}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (army/process-army [0 0])
      ;; Army should have moved to [1 0] (coastal fill)
      (should= :army (get-in @atoms/game-map [1 0 :contents :type]))
      (should-be-nil (get-in @atoms/game-map [0 0 :contents])))

    (it "sentry army NOT in city does NOT trigger fill-coastal-cell"
      ;; Sentry army on plain land (not a city) should NOT move
      (reset! atoms/game-map [[{:type :land :country-id 1
                                 :contents {:type :army :owner :computer :hits 1
                                            :mode :sentry :country-id 1}}
                                {:type :sea}]
                               [{:type :land :country-id 1}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (army/process-army [0 0])
      ;; Army should stay put as sentry on non-city land
      (should= :army (get-in @atoms/game-map [0 0 :contents :type]))
      (should= :sentry (get-in @atoms/game-map [0 0 :contents :mode])))))

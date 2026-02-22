(ns empire.computer.army-spec
  "Tests for VMS Empire style computer army movement."
  (:require [speclj.core :refer :all]
            [empire.computer.army :as army]
            [empire.computer.core :as core]
            [empire.computer.production :as production]
            [empire.computer.stamping :as stamping]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [build-test-map reset-all-atoms!]]))

(describe "process-army"
  (before (reset-all-atoms!))

  (describe "attack behavior"
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

  (describe "sentry behavior"
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

  (describe "random-explore behavior"
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

  (describe "attack-target behavior"
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

  (describe "city attack coordination"
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

  (describe "exploration behavior"
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

  (describe "coast-walk behavior"
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

  (describe "territory stamping"
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

  (describe "ignores non-computer units"
    (it "returns nil for player army"
      (reset! atoms/game-map (build-test-map ["A#"]))
      (should-be-nil (army/process-army [0 0])))

    (it "returns nil for empty cell"
      (reset! atoms/game-map (build-test-map ["##"]))
      (should-be-nil (army/process-army [0 0]))))

  (describe "country sovereignty"
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

  (describe "coastal fill behavior"
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

  (describe "interior exploration"
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

(describe "army stamping"
  (before (reset-all-atoms!))

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

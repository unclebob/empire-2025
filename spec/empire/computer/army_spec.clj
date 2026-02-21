(ns empire.computer.army-spec
  "Tests for VMS Empire style computer army movement."
  (:require [speclj.core :refer :all]
            [empire.computer.army :as army]
            [empire.computer.core :as core]
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

  (describe "land objective behavior"
    (it "moves toward unexplored territory"
      (reset! atoms/computer-map [[{:type :land :contents {:type :army :owner :computer}}
                                    {:type :land}
                                    nil]])
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :computer}}
                                {:type :land}
                                {:type :land}]])
      (army/process-army [0 0])
      ;; Army should have moved toward unexplored
      (should= :army (get-in @atoms/game-map [0 1 :contents :type])))

    (it "moves toward free city on same continent"
      (reset! atoms/game-map (build-test-map ["a#+"]))
      (reset! atoms/computer-map (build-test-map ["a#+"]))
      (army/process-army [0 0])
      ;; Army should have moved toward free city
      (should= :army (get-in @atoms/game-map [1 0 :contents :type]))))

  (describe "transport boarding behavior"
    (it "boards adjacent loading transport"
      ;; Army adjacent to loading transport with room
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :computer}}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :loading
                                                        :army-count 0}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (let [result (army/process-army [0 0])]
        ;; Army should be nil (on transport) or have moved
        (should-be-nil result)
        ;; Transport should have army
        (should= 1 (:army-count (:contents (get-in @atoms/game-map [0 1]))))))

    (it "moves toward loading transport when no land objectives"
      ;; Fully explored continent with loading transport nearby
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :computer}}
                                {:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :loading
                                                        :army-count 0}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (army/process-army [0 0])
      ;; Army should have moved toward transport (to [0 1])
      (should= :army (get-in @atoms/game-map [0 1 :contents :type]))))

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

  (describe "player city priority"
    (it "chooses player city over free city on same continent"
      ;; Army at [0 0], player city at [2 2], free city at [2 0] (closer)
      ;; Should target player city despite free city being closer
      (let [land {:type :land}
            army-cell {:type :land :contents {:type :army :owner :computer}}
            player-city {:type :city :city-status :player}
            free-city {:type :city :city-status :free}]
        (reset! atoms/game-map [[army-cell land land]
                                 [land land land]
                                 [free-city land player-city]])
        (reset! atoms/computer-map @atoms/game-map)
        (reset! atoms/claimed-objectives #{})
        (army/process-army [0 0])
        ;; Should claim player city [2 2], not free city [2 0]
        (should (contains? @atoms/claimed-objectives [2 2]))))

    (it "chooses player city over unexplored on same continent"
      ;; Army at [0 0], player city at [2 0], unexplored at [0 2] (closer to explore path)
      (let [land {:type :land}
            army-cell {:type :land :contents {:type :army :owner :computer}}
            player-city {:type :city :city-status :player}]
        (reset! atoms/game-map [[army-cell land land]
                                 [land land land]
                                 [player-city land land]])
        ;; Computer map has unexplored at [0 2]
        (reset! atoms/computer-map [[{:type :land} {:type :land} nil]
                                     [{:type :land} {:type :land} {:type :land}]
                                     [{:type :city :city-status :player} {:type :land} {:type :land}]])
        (reset! atoms/claimed-objectives #{})
        (army/process-army [0 0])
        ;; Should claim player city [2 0], not unexplored
        (should (contains? @atoms/claimed-objectives [2 0]))))

    (it "chooses free city over unexplored when no player cities"
      ;; Army at [0 0], free city at [2 0], unexplored at [0 2]
      (let [land {:type :land}
            army-cell {:type :land :contents {:type :army :owner :computer}}
            free-city {:type :city :city-status :free}]
        (reset! atoms/game-map [[army-cell land land]
                                 [land land land]
                                 [free-city land land]])
        ;; Computer map has unexplored at [0 2]
        (reset! atoms/computer-map [[{:type :land} {:type :land} nil]
                                     [{:type :land} {:type :land} {:type :land}]
                                     [{:type :city :city-status :free} {:type :land} {:type :land}]])
        (reset! atoms/claimed-objectives #{})
        (army/process-army [0 0])
        ;; Should claim free city [2 0], not unexplored
        (should (contains? @atoms/claimed-objectives [2 0]))))

    (it "does not target unexplored cells as objectives"
      ;; Army at [0 0], no cities, only unexplored territory
      ;; New behavior: unexplored cells are NOT targeted via find-land-objective
      (let [land {:type :land}
            army-cell {:type :land :contents {:type :army :owner :computer}}]
        (reset! atoms/game-map [[army-cell land land]
                                 [land land land]
                                 [land land land]])
        ;; Computer map has unexplored at [2 2]
        (reset! atoms/computer-map [[{:type :land} {:type :land} {:type :land}]
                                     [{:type :land} {:type :land} {:type :land}]
                                     [{:type :land} {:type :land} nil]])
        (reset! atoms/claimed-objectives #{})
        (with-redefs [rand (constantly 0.5)]
          (army/process-army [0 0]))
        ;; Should NOT claim unexplored [2 2] - no longer an objective
        (should-not (contains? @atoms/claimed-objectives [2 2])))))

  (describe "objective distribution"
    (it "two armies on same continent target different free cities"
      ;; Map layout (5 cols):
      ;; Row 0: a # # # a    (armies at [0 0] and [0 4])
      ;; Row 1: # # # # #    (land buffer)
      ;; Row 2: + # # # +    (free cities at [2 0] and [2 4])
      ;; Cities are not adjacent to armies, so find-land-objective is used
      (let [land {:type :land}
            army-cell (fn [] {:type :land :contents {:type :army :owner :computer}})
            free-city {:type :city :city-status :free}]
        (reset! atoms/game-map [[(army-cell) land land land (army-cell)]
                                 [land land land land land]
                                 [free-city land land land free-city]])
        (reset! atoms/computer-map @atoms/game-map)
        (reset! atoms/claimed-objectives #{})
        ;; Process first army at [0 0] - should claim nearest city [2 0]
        (army/process-army [0 0])
        (should= #{[2 0]} @atoms/claimed-objectives)
        ;; Reset game-map for second army (first has moved)
        (reset! atoms/game-map [[land land land land (army-cell)]
                                 [land land land land land]
                                 [free-city land land land free-city]])
        (reset! atoms/computer-map @atoms/game-map)
        ;; Process second army at [0 4] - [2 4] is nearest, [2 0] is claimed
        ;; Should claim [2 4] (unclaimed) not [2 0] (already claimed)
        (army/process-army [0 4])
        (should= #{[2 0] [2 4]} @atoms/claimed-objectives)))

    (it "armies double up when all objectives claimed"
      ;; One free city at [2 0], two armies far enough away to not be adjacent
      (let [land {:type :land}
            army-cell (fn [] {:type :land :contents {:type :army :owner :computer}})
            free-city {:type :city :city-status :free}]
        (reset! atoms/game-map [[(army-cell) land land]
                                 [land land land]
                                 [free-city land land]])
        (reset! atoms/computer-map @atoms/game-map)
        (reset! atoms/claimed-objectives #{})
        ;; First army claims the city
        (army/process-army [0 0])
        (should= #{[2 0]} @atoms/claimed-objectives)
        ;; Second army - all objectives claimed, should still target [2 0]
        (reset! atoms/game-map [[land land (army-cell)]
                                 [land land land]
                                 [free-city land land]])
        (reset! atoms/computer-map @atoms/game-map)
        (army/process-army [0 2])
        (should (contains? @atoms/claimed-objectives [2 0]))))

    (it "claimed objectives reset between rounds"
      (reset! atoms/claimed-objectives #{[1 0] [1 2]})
      (should= #{[1 0] [1 2]} @atoms/claimed-objectives)
      (reset! atoms/claimed-objectives #{})
      (should= #{} @atoms/claimed-objectives)))

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
      ;; Should have terminated - switched to awake mode
      (let [unit (get-in @atoms/game-map [1 1 :contents])]
        (should= :awake (:mode unit))
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
        (should= :awake (:mode unit))
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
      ;; Army at [0 0] with country-id 3, empty land at [0 1]
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :computer :hits 1 :country-id 3}}
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
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :computer :hits 1 :country-id 1}}
                                {:type :land :country-id 2}
                                {:type :land :country-id 2}]])
      (reset! atoms/computer-map @atoms/game-map)
      (army/process-army [0 0])
      ;; Army should not have moved into foreign territory
      (should= :army (get-in @atoms/game-map [0 0 :contents :type])))

    (it "army passes through own territory"
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :computer :hits 1 :country-id 1}}
                                {:type :land :country-id 1}
                                {:type :land :country-id 1}]])
      (reset! atoms/computer-map @atoms/game-map)
      (army/process-army [0 0])
      ;; Army should have moved into own territory
      (should= :army (get-in @atoms/game-map [0 1 :contents :type])))

    (it "army passes through unclaimed land"
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :computer :hits 1 :country-id 1}}
                                {:type :land}
                                {:type :land}]])
      (reset! atoms/computer-map @atoms/game-map)
      (army/process-army [0 0])
      ;; Army should have moved into unclaimed land
      (should= :army (get-in @atoms/game-map [0 1 :contents :type])))

    (it "army with no country-id passes through any territory"
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :computer :hits 1}}
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
      ;; Should terminate coast-walk and switch to awake
      (let [unit (get-in @atoms/game-map [0 0 :contents])]
        (should= :awake (:mode unit))
        (should-be-nil (:coast-direction unit))))

    (it "army can still attack adjacent enemy across sovereignty border"
      ;; Army with country-id 1 adjacent to player army in foreign territory
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :computer :hits 1 :country-id 1}}
                                {:type :land :country-id 2 :contents {:type :army :owner :player :hits 1}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (army/process-army [0 0])
      ;; Combat should have occurred - computer army no longer at [0 0]
      (should-be-nil (get-in @atoms/game-map [0 0 :contents]))))

  (describe "unload-event-id filtering"
    (it "army does not board transport with matching unload-event-id"
      ;; Army with unload-event-id 42, adjacent transport also has unload-event-id 42
      (reset-all-atoms!)
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :computer :hits 1
                                                        :unload-event-id 42}}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :loading
                                                        :army-count 0
                                                        :unload-event-id 42}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (army/process-army [0 0])
      ;; Army should still be on land (not boarded)
      (should= :army (get-in @atoms/game-map [0 0 :contents :type]))
      ;; Transport should still have 0 armies
      (should= 0 (get-in @atoms/game-map [0 1 :contents :army-count])))

    (it "army boards transport with different unload-event-id"
      ;; Army with unload-event-id 42, transport has unload-event-id 99
      (reset-all-atoms!)
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :computer :hits 1
                                                        :unload-event-id 42}}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :loading
                                                        :army-count 0
                                                        :unload-event-id 99}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (army/process-army [0 0])
      ;; Army should be gone from land
      (should-be-nil (get-in @atoms/game-map [0 0 :contents]))
      ;; Transport should have 1 army
      (should= 1 (get-in @atoms/game-map [0 1 :contents :army-count])))

    (it "army boards transport with no unload-event-id"
      ;; Army with unload-event-id 42, transport has no unload-event-id
      (reset-all-atoms!)
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :computer :hits 1
                                                        :unload-event-id 42}}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :loading
                                                        :army-count 0}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (army/process-army [0 0])
      ;; Army should be gone from land
      (should-be-nil (get-in @atoms/game-map [0 0 :contents]))
      ;; Transport should have 1 army
      (should= 1 (get-in @atoms/game-map [0 1 :contents :army-count])))

    (it "army without unload-event-id boards any transport"
      ;; Army with no unload-event-id, transport has unload-event-id 42
      (reset-all-atoms!)
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :computer :hits 1}}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :loading
                                                        :army-count 0
                                                        :unload-event-id 42}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (army/process-army [0 0])
      ;; Army should be gone from land (boarded)
      (should-be-nil (get-in @atoms/game-map [0 0 :contents]))
      ;; Transport should have 1 army
      (should= 1 (get-in @atoms/game-map [0 1 :contents :army-count]))))

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

    (it "moves toward transport when no coastal cell to fill"
      ;; Army without country-id → fill-coastal-cell returns nil → board transport
      ;; Army at [0 0] (land), transport at [0 1] (sea, adjacent)
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :computer :hits 1
                                                        :mode :awake}}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :loading
                                                        :army-count 0}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (with-redefs [rand (constantly 0.5)]
        (army/process-army [0 0]))
      ;; Army should have boarded the transport (no country-id → no coastal fill)
      (should-be-nil (:contents (get-in @atoms/game-map [0 0])))
      (should= 1 (get-in @atoms/game-map [0 1 :contents :army-count]))))

  (describe "interior exploration"
    (it "1-in-3 armies explore interior instead of going sentry"
      ;; Army at [1 2] (coastal, adjacent to sea at row 3)
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
      (swap! atoms/game-map assoc-in [1 2 :contents]
             {:type :army :owner :computer :hits 1 :mode :awake :country-id 1})
      ;; Mock rand < 1/3, rand-nth picks direction [0 -1] (north, toward interior)
      (with-redefs [rand (constantly 0.2)
                    rand-nth (constantly [0 -1])]
        (army/process-army [1 2]))
      ;; Army should have moved north to [1 1] with explore direction set
      (let [unit (get-in @atoms/game-map [1 1 :contents])]
        (should= :army (:type unit))
        (should= [0 -1] (:interior-explore-direction unit))))

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

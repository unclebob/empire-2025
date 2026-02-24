(ns empire.computer.ship-spec
  "Tests for VMS Empire style computer ship movement."
  (:require [speclj.core :refer :all]
            [empire.computer.ship :as ship]
            [empire.computer.core :as core]
            [empire.config :as config]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [build-test-map reset-all-atoms! set-test-unit]]
            [empire.containers.helpers :as uc]
            [empire.combat :as combat]
            [empire.computer.threat :as threat]))

(describe "process-ship"
  (before (reset-all-atoms!))

  (context "dock behavior"
    (it "damaged computer ship docks at adjacent friendly city"
      (reset! atoms/game-map (build-test-map ["BdX"]))
      (reset! atoms/computer-map @atoms/game-map)
      (set-test-unit atoms/game-map "d" :hits 2)
      (ship/process-ship [1 0] :destroyer)
      ;; Ship should be removed from map
      (should-be-nil (get-in @atoms/game-map [1 0 :contents]))
      ;; Ship should be in city X's shipyard
      (let [city (get-in @atoms/game-map [2 0])
            shipyard (uc/get-shipyard-ships city)]
        (should= 1 (count shipyard))
        (should= :destroyer (:type (first shipyard)))
        (should= 2 (:hits (first shipyard))))))

  (context "attack behavior"
    (it "attacks adjacent player ship"
      (reset! atoms/game-map [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3}}
                                {:type :sea :contents {:type :patrol-boat :owner :player :hits 1}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (let [_result (ship/process-ship [0 0] :destroyer)]
        ;; Combat should have occurred
        (let [cell0 (get-in @atoms/game-map [0 0])
              cell1 (get-in @atoms/game-map [0 1])]
          (should (or (nil? (:contents cell0))
                      (nil? (:contents cell1))
                      (= :computer (:owner (:contents cell1)))))))))

  (context "escort behavior"
    (it "destroyer moves toward transport"
      (reset! atoms/game-map [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3}}
                                {:type :sea}
                                {:type :sea}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :army-count 3}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (ship/process-ship [0 0] :destroyer)
      ;; Destroyer should have moved toward transport
      (should= :destroyer (get-in @atoms/game-map [0 1 :contents :type]))))

  (context "exploration behavior"
    (it "explores toward unexplored sea"
      (reset! atoms/computer-map [[{:type :sea :contents {:type :patrol-boat :owner :computer :hits 1}}
                                    {:type :sea}
                                    nil]])
      (reset! atoms/game-map [[{:type :sea :contents {:type :patrol-boat :owner :computer :hits 1}}
                                {:type :sea}
                                {:type :sea}]])
      (ship/process-ship [0 0] :patrol-boat)
      ;; Ship should have moved toward unexplored
      (should= :patrol-boat (get-in @atoms/game-map [0 1 :contents :type])))

    (it "stays put when all sea is explored"
      (reset! atoms/game-map [[{:type :sea :contents {:type :submarine :owner :computer :hits 2}}
                                {:type :sea}
                                {:type :land}]])
      (reset! atoms/computer-map @atoms/game-map)
      (ship/process-ship [0 0] :submarine)
      ;; Ship stays put - no unexplored territory
      (should= :submarine (get-in @atoms/game-map [0 0 :contents :type])))

    (it "explores toward unexplored sea without NW bias"
      ;; 5x5 all-sea map. Ship at center, unexplored in SE corner.
      (let [game-map (build-test-map ["~~~~~"
                                      "~~~~~"
                                      "~~~~~"
                                      "~~~~~"
                                      "~~~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map (build-test-map ["~~~~~"
                                                    "~~~~~"
                                                    "~~~~~"
                                                    "~~~~~"
                                                    "~~~~-"]))
        (swap! atoms/game-map assoc-in [2 2 :contents]
               {:type :destroyer :owner :computer :hits 3})
        (ship/process-ship [2 2] :destroyer)
        ;; Should have moved
        (should-be-nil (:contents (get-in @atoms/game-map [2 2])))
        ;; Find where ship moved
        (let [new-pos (first (for [r (range 5) c (range 5)
                                   :when (= :destroyer (get-in @atoms/game-map [r c :contents :type]))]
                               [r c]))]
          ;; Should move toward SE, not NW
          (should-not= [1 1] new-pos)
          (should (or (> (first new-pos) 2)
                      (> (second new-pos) 2)))))))

  (context "hunting behavior"
    (it "moves toward visible player ship"
      (reset! atoms/game-map [[{:type :sea :contents {:type :battleship :owner :computer :hits 8}}
                                {:type :sea}
                                {:type :sea}
                                {:type :sea :contents {:type :destroyer :owner :player :hits 3}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (ship/process-ship [0 0] :battleship)
      ;; Battleship should have moved toward player ship
      (should= :battleship (get-in @atoms/game-map [0 1 :contents :type]))))

  (context "ignores non-computer ships"
    (it "returns nil for player ship"
      (reset! atoms/game-map [[{:type :sea :contents {:type :destroyer :owner :player :hits 3}}]])
      (should-be-nil (ship/process-ship [0 0] :destroyer)))

    (it "returns nil for wrong ship type"
      (reset! atoms/game-map [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3}}]])
      (should-be-nil (ship/process-ship [0 0] :patrol-boat))))

  (context "patrol boat behavior"
    (it "patrol boat moves along coastline"
      ;; 3x3 map: land in center, sea around it. Patrol boat at [1 0] (sea, adjacent to land).
      ;; It should move to another sea cell that is also adjacent to land.
      (reset! atoms/game-map (build-test-map ["~~~"
                                               "~#~"
                                               "~~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [1 0 :contents]
             {:type :patrol-boat :owner :computer :hits 1
              :patrol-country-id 1 :patrol-direction :clockwise :patrol-mode :patrolling})
      (ship/process-ship [1 0] :patrol-boat)
      ;; Patrol boat should have moved
      (should-be-nil (:contents (get-in @atoms/game-map [1 0])))
      ;; Find where it moved
      (let [new-pos (first (for [r (range 3) c (range 3)
                                 :when (= :patrol-boat (get-in @atoms/game-map [r c :contents :type]))]
                             [r c]))
            ;; The new position should be sea and adjacent to land [1 1]
            adj-to-land? (some (fn [[dr dc]]
                                 (let [nr (+ (first new-pos) dr)
                                       nc (+ (second new-pos) dc)]
                                   (= :land (:type (get-in @atoms/game-map [nr nc])))))
                               [[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]])]
        (should-not-be-nil new-pos)
        (should adj-to-land?)))

    (it "patrol boat attacks adjacent transport"
      ;; Patrol boat next to a player transport - should attack it
      (reset! atoms/game-map [[{:type :sea :contents {:type :patrol-boat :owner :computer :hits 1
                                                       :patrol-country-id 1 :patrol-direction :clockwise
                                                       :patrol-mode :patrolling}}
                                {:type :sea :contents {:type :transport :owner :player :hits 3}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (ship/process-ship [0 0] :patrol-boat)
      ;; Combat should have occurred - either patrol boat moved to [0 1] or died
      (let [cell0 (get-in @atoms/game-map [0 0])
            cell1 (get-in @atoms/game-map [0 1])]
        (should (or (nil? (:contents cell0))
                    (= :computer (:owner (:contents cell1)))))))

    (it "patrol boat avoids recent positions when coast-patrolling"
      ;; 3x5 map: land row at top, sea rows below. Patrol boat at [2,1] with history [[1,1]].
      ;; Coastal cells adjacent to land row 0: [0,1],[1,1],[2,1],[3,1],[4,1]
      ;; Patrol boat should move to a coastal cell NOT in history ([1,1]).
      (reset! atoms/game-map (build-test-map ["#####"
                                               "~~~~~"
                                               "~~~~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [2 1 :contents]
             {:type :patrol-boat :owner :computer :hits 1
              :patrol-country-id 1 :patrol-direction :clockwise :patrol-mode :patrolling
              :patrol-history [[1 1]]})
      ;; Run multiple times to confirm it never picks [1,1]
      (dotimes [_ 10]
        (reset! atoms/game-map (build-test-map ["#####"
                                                 "~~~~~"
                                                 "~~~~~"]))
        (swap! atoms/game-map assoc-in [2 1 :contents]
               {:type :patrol-boat :owner :computer :hits 1
                :patrol-country-id 1 :patrol-direction :clockwise :patrol-mode :patrolling
                :patrol-history [[1 1]]})
        (reset! atoms/computer-map @atoms/game-map)
        (ship/process-ship [2 1] :patrol-boat)
        ;; Find where patrol boat moved
        (let [new-pos (first (for [r (range 5) c (range 3)
                                   :when (= :patrol-boat (get-in @atoms/game-map [r c :contents :type]))]
                               [r c]))]
          (should-not= [1 1] new-pos))))

    (it "patrol boat falls back to any coastal cell when all filtered out"
      ;; Patrol boat at [0,0]. Only coastal neighbor [0,1] is in history.
      ;; Should still move there as fallback.
      (reset! atoms/game-map [[{:type :sea} {:type :sea}]
                                [{:type :land} {:type :land}]])
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [0 0 :contents]
             {:type :patrol-boat :owner :computer :hits 1
              :patrol-country-id 1 :patrol-direction :clockwise :patrol-mode :patrolling
              :patrol-history [[0 1]]})
      (ship/process-ship [0 0] :patrol-boat)
      ;; Only empty coastal neighbor is [0,1] which is in history.
      ;; Should still move there as fallback.
      (should= :patrol-boat (get-in @atoms/game-map [0 1 :contents :type])))

    (it "patrol boat updates patrol-history after moving"
      (reset! atoms/game-map (build-test-map ["#####"
                                               "~~~~~"
                                               "~~~~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [2 1 :contents]
             {:type :patrol-boat :owner :computer :hits 1
              :patrol-country-id 1 :patrol-direction :clockwise :patrol-mode :patrolling
              :patrol-history [[3 1]]})
      (ship/process-ship [2 1] :patrol-boat)
      ;; Find where patrol boat moved
      (let [new-pos (first (for [r (range 5) c (range 3)
                                 :when (= :patrol-boat (get-in @atoms/game-map [r c :contents :type]))]
                             [r c]))
            unit (get-in @atoms/game-map (conj new-pos :contents))]
        ;; History should now contain [2,1] (the position it just left)
        (should (some #{[2 1]} (:patrol-history unit)))))

    (it "patrol boat flees from non-transport enemy"
      ;; Note: This test must come before the destroyer escort tests
      ;; Patrol boat at [0 1], destroyer at [0 2] -- should move away to [0 0]
      (reset! atoms/game-map [[{:type :sea}
                                {:type :sea :contents {:type :patrol-boat :owner :computer :hits 1
                                                       :patrol-country-id 1 :patrol-direction :clockwise
                                                       :patrol-mode :patrolling}}
                                {:type :sea :contents {:type :destroyer :owner :player :hits 3}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (ship/process-ship [0 1] :patrol-boat)
      ;; Patrol boat should have fled to [0 0] (away from destroyer at [0 2])
      (should= :patrol-boat (get-in @atoms/game-map [0 0 :contents :type])))

    (it "2nd patrol boat sails in random heading instead of coastline patrol"
      ;; Patrol boat with patrol-number 2 should sail heading-based, not coastline
      (let [game-map (build-test-map ["~~~~~"
                                      "~~~~~"
                                      "~~~~~"
                                      "~~~~~"
                                      "~~~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [2 2 :contents]
               {:type :patrol-boat :owner :computer :hits 1
                :patrol-country-id 1 :patrol-number 2})
        (with-redefs [rand-int (constantly 180)]  ; heading south
          (ship/process-ship [2 2] :patrol-boat))
        ;; Should have moved south via heading-based sailing
        (let [unit (:contents (get-in @atoms/game-map [2 3]))]
          (should= :patrol-boat (:type unit))
          (should= 180 (:patrol-heading unit)))))

    (it "sailing patrol boat switches to coastline exploration at unexplored coast"
      ;; Patrol boat at [2 2] heading east. Land at col 4 is unexplored.
      ;; [3 2] is sea adjacent to unexplored land -> should switch to coastline patrol.
      (let [game-map (build-test-map ["~~~~#"
                                      "~~~~#"
                                      "~~~~#"
                                      "~~~~#"
                                      "~~~~#"])]
        (reset! atoms/game-map game-map)
        ;; Sea explored, land at col 4 unexplored
        (reset! atoms/computer-map [(vec (repeat 5 {:type :sea}))
                                    (vec (repeat 5 {:type :sea}))
                                    (vec (repeat 5 {:type :sea}))
                                    (vec (repeat 5 {:type :sea}))
                                    (vec (repeat 5 nil))])
        (swap! atoms/game-map assoc-in [2 2 :contents]
               {:type :patrol-boat :owner :computer :hits 1
                :patrol-country-id 1 :patrol-number 2 :patrol-heading 90})
        (with-redefs [rand-int (constantly 10)]
          (ship/process-ship [2 2] :patrol-boat))
        ;; Should have moved to [3 2] and switched to coastline exploration
        (let [unit (:contents (get-in @atoms/game-map [3 2]))]
          (should= :patrol-boat (:type unit))
          (should= :coastline-exploring (:patrol-mode unit)))))

    (it "patrol boat in coastline-exploring mode does coastline patrol"
      ;; Patrol boat at [1 2] heading east (away from coast).
      ;; Land at col 0. If heading sailing, would move to [2 2].
      ;; In coastline-exploring mode, should move along coast (stay adjacent to land).
      (reset! atoms/game-map (build-test-map ["#~~~~"
                                               "#~~~~"
                                               "#~~~~"
                                               "#~~~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [1 2 :contents]
             {:type :patrol-boat :owner :computer :hits 1
              :patrol-country-id 1 :patrol-number 2
              :patrol-mode :coastline-exploring :patrol-heading 90})
      (ship/process-ship [1 2] :patrol-boat)
      ;; Should move to a coastal cell (adjacent to land at col 0), NOT [2 2]
      (let [new-pos (first (for [c (range 5) r (range 4)
                                  :when (= :patrol-boat (get-in @atoms/game-map [c r :contents :type]))]
                              [c r]))]
        (should-not-be-nil new-pos)
        ;; Should still be adjacent to land (col 0 or col 1)
        (should (<= (first new-pos) 1))))

    (it "sailing patrol boat reflects off map border"
      ;; Patrol boat at top edge heading north -> should reflect
      (let [game-map (build-test-map ["~~~"
                                      "~~~"
                                      "~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [1 0 :contents]
               {:type :patrol-boat :owner :computer :hits 1
                :patrol-country-id 1 :patrol-number 3 :patrol-heading 0})
        (with-redefs [rand-int (constantly 10)]
          (ship/process-ship [1 0] :patrol-boat))
        ;; Should stay put with reflected heading
        (let [unit (:contents (get-in @atoms/game-map [1 0]))]
          (should= :patrol-boat (:type unit))
          (should-not= 0 (:patrol-heading unit)))))

    (it "1st patrol boat does coastline patrol as before"
      ;; Patrol boat with patrol-number 1 still does coastline patrol
      (reset! atoms/game-map (build-test-map ["~~~"
                                               "~#~"
                                               "~~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [1 0 :contents]
             {:type :patrol-boat :owner :computer :hits 1
              :patrol-country-id 1 :patrol-number 1
              :patrol-direction :clockwise :patrol-mode :patrolling})
      (ship/process-ship [1 0] :patrol-boat)
      ;; Should have done coastline move (adjacent to land)
      (should-be-nil (:contents (get-in @atoms/game-map [1 0])))
      (let [new-pos (first (for [c (range 3) r (range 3)
                                  :when (= :patrol-boat (get-in @atoms/game-map [c r :contents :type]))]
                              [c r]))]
        (should-not-be-nil new-pos))))

  (context "destroyer escort behavior"
    (it "seeking destroyer adopts unadopted transport"
      (reset! atoms/game-map [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3
                                                       :destroyer-id 1 :escort-mode :seeking}}
                                {:type :sea}
                                {:type :sea}
                                {:type :sea :contents {:type :transport :owner :computer :hits 3
                                                       :transport-id 1 :transport-mission :loading
                                                       :army-count 0}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (ship/process-ship [0 0] :destroyer)
      ;; Destroyer should have adopted the transport and moved toward it
      (let [destroyer (first (for [c (range 4)
                                   :let [unit (get-in @atoms/game-map [0 c :contents])]
                                   :when (= :destroyer (:type unit))]
                               unit))
            transport (get-in @atoms/game-map [0 3 :contents])]
        (should= :intercepting (:escort-mode destroyer))
        (should= 1 (:escort-transport-id destroyer))
        (should= 1 (:escort-destroyer-id transport))))

    (it "intercepting destroyer transitions to escorting when adjacent"
      (reset! atoms/game-map [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3
                                                       :destroyer-id 1 :escort-mode :intercepting
                                                       :escort-transport-id 1}}
                                {:type :sea :contents {:type :transport :owner :computer :hits 3
                                                       :transport-id 1 :escort-destroyer-id 1
                                                       :transport-mission :loading :army-count 0}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (ship/process-ship [0 0] :destroyer)
      ;; Should transition to escorting (already adjacent)
      (let [destroyer (get-in @atoms/game-map [0 0 :contents])]
        (should= :escorting (:escort-mode destroyer))))

    (it "escorting destroyer follows transport"
      ;; Destroyer at [0 0] escorting, transport at [0 2] (not adjacent)
      ;; Destroyer should move toward transport
      (reset! atoms/game-map [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3
                                                       :destroyer-id 1 :escort-mode :escorting
                                                       :escort-transport-id 1}}
                                {:type :sea}
                                {:type :sea :contents {:type :transport :owner :computer :hits 3
                                                       :transport-id 1 :escort-destroyer-id 1
                                                       :transport-mission :loading :army-count 0}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (ship/process-ship [0 0] :destroyer)
      ;; Should have moved toward transport
      (should= :destroyer (get-in @atoms/game-map [0 1 :contents :type])))

    (it "destroyer reverts to seeking when transport is destroyed"
      ;; Destroyer escorting a transport that no longer exists
      (reset! atoms/game-map [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3
                                                       :destroyer-id 1 :escort-mode :escorting
                                                       :escort-transport-id 99}}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (ship/process-ship [0 0] :destroyer)
      ;; Should revert to seeking
      (let [destroyer (get-in @atoms/game-map [0 0 :contents])]
        (should= :seeking (:escort-mode destroyer))
        (should-be-nil (:escort-transport-id destroyer)))))

  (context "pursue-and-kill behavior"
    (it "escorting destroyer pursues when transport spots enemy"
      ;; Destroyer at [0 0] escorting transport at [0 2].
      ;; Player sub at [0 3] — adjacent to transport, not to destroyer.
      ;; Destroyer should detect sighting via transport and pursue.
      (reset! atoms/game-map [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3
                                                       :destroyer-id 1 :escort-mode :escorting
                                                       :escort-transport-id 1}}
                                {:type :sea}
                                {:type :sea :contents {:type :transport :owner :computer :hits 3
                                                       :transport-id 1 :escort-destroyer-id 1
                                                       :transport-mission :loading :army-count 0}}
                                {:type :sea :contents {:type :submarine :owner :player :hits 2}}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (ship/process-ship [0 0] :destroyer)
      ;; Destroyer should move toward enemy and enter pursuing mode
      (let [destroyer (get-in @atoms/game-map [0 1 :contents])]
        (should= :destroyer (:type destroyer))
        (should= :pursuing (:escort-mode destroyer))
        (should= [0 3] (:pursuit-target destroyer))
        (should= 5 (:pursuit-steps-remaining destroyer))))

    (it "pursuing destroyer moves toward cell enemy could have gone to"
      ;; Destroyer at [2 2] pursuing, target was [3 2]. Transport at [1 2].
      ;; Enemy has moved. Destroyer should pick a neighbor of [3 2] that is
      ;; NOT visible to transport [1 2] or destroyer [2 2], and move toward it.
      ;; 5x5 all-sea map.
      (let [game-map (build-test-map ["~~~~~"
                                      "~~~~~"
                                      "~~~~~"
                                      "~~~~~"
                                      "~~~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [1 2 :contents]
               {:type :transport :owner :computer :hits 3
                :transport-id 1 :transport-mission :loading :army-count 0})
        (swap! atoms/game-map assoc-in [2 2 :contents]
               {:type :destroyer :owner :computer :hits 3
                :destroyer-id 1 :escort-mode :pursuing
                :escort-transport-id 1
                :pursuit-target [3 2] :pursuit-steps-remaining 5})
        (ship/process-ship [2 2] :destroyer)
        ;; Destroyer should have moved and decremented steps
        (should-be-nil (:contents (get-in @atoms/game-map [2 2])))
        (let [new-pos (first (for [c (range 5) r (range 5)
                                   :when (= :destroyer (get-in @atoms/game-map [c r :contents :type]))]
                               [c r]))
              destroyer (get-in @atoms/game-map (conj new-pos :contents))]
          (should-not-be-nil new-pos)
          (should= :pursuing (:escort-mode destroyer))
          (should= 4 (:pursuit-steps-remaining destroyer)))))

    (it "pursuing destroyer returns to escorting when steps exhausted"
      ;; Destroyer with 1 step remaining should revert to escorting
      (reset! atoms/game-map [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3
                                                       :destroyer-id 1 :escort-mode :pursuing
                                                       :escort-transport-id 1
                                                       :pursuit-target [0 3]
                                                       :pursuit-steps-remaining 1}}
                                {:type :sea}
                                {:type :sea :contents {:type :transport :owner :computer :hits 3
                                                       :transport-id 1 :transport-mission :loading
                                                       :army-count 0}}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (ship/process-ship [0 0] :destroyer)
      (let [destroyer (get-in @atoms/game-map [0 0 :contents])]
        (should= :escorting (:escort-mode destroyer))
        (should-be-nil (:pursuit-target destroyer))
        (should-be-nil (:pursuit-steps-remaining destroyer))))

    (it "pursuing destroyer ends pursuit when all candidate cells visible"
      ;; Destroyer at [1 0] pursuing target [1 1]. Transport at [1 2].
      ;; 3x3 map. All neighbors of [1 1] are visible to destroyer or transport.
      ;; No hidden cells for enemy — pursuit should end.
      (reset! atoms/game-map (build-test-map ["~~~"
                                              "~~~"
                                              "~~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [1 0 :contents]
             {:type :destroyer :owner :computer :hits 3
              :destroyer-id 1 :escort-mode :pursuing
              :escort-transport-id 1
              :pursuit-target [1 1] :pursuit-steps-remaining 4})
      (swap! atoms/game-map assoc-in [1 2 :contents]
             {:type :transport :owner :computer :hits 3
              :transport-id 1 :transport-mission :loading :army-count 0})
      (ship/process-ship [1 0] :destroyer)
      ;; All neighbors of [1 1] visible to group — pursuit ends
      (let [destroyer (first (for [c (range 3) r (range 3)
                                   :let [unit (get-in @atoms/game-map [c r :contents])]
                                   :when (= :destroyer (:type unit))]
                               unit))]
        (should= :escorting (:escort-mode destroyer))
        (should-be-nil (:pursuit-target destroyer))))

    (it "pursuing destroyer attacks adjacent enemy via priority 1"
      ;; Destroyer pursuing, player sub adjacent. Priority 1 should attack.
      (reset! atoms/game-map [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3
                                                       :destroyer-id 1 :escort-mode :pursuing
                                                       :escort-transport-id 1
                                                       :pursuit-target [0 3]
                                                       :pursuit-steps-remaining 3}}
                                {:type :sea :contents {:type :submarine :owner :player :hits 2}}
                                {:type :sea}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (ship/process-ship [0 0] :destroyer)
      ;; Combat should have occurred (priority 1 attack)
      (let [cell0 (get-in @atoms/game-map [0 0])
            cell1 (get-in @atoms/game-map [0 1])]
        (should (or (nil? (:contents cell0))
                    (= :computer (:owner (:contents cell1))))))))

  (context "carrier positioning behavior"
    (before (reset-all-atoms!))

    (it "carrier in positioning mode moves toward target"
      ;; Two distant cities (60 apart), carrier at [0 5] with target [0 30]
      (let [cells (vec (for [j (range 60)]
                         (cond
                           (= j 0) {:type :city :city-status :computer}
                           (= j 5) {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                           :carrier-mode :positioning :carrier-target [0 30]
                                                           :carrier-pair #{[0 0] [0 59]}}}
                           (= j 59) {:type :city :city-status :computer}
                           :else {:type :sea})))]
        (reset! atoms/game-map [cells])
        (reset! atoms/computer-map [cells])
        (ship/update-distant-city-pairs!)
        (ship/process-ship [0 5] :carrier)
        ;; Carrier should have moved from [0,5] to [0,6]
        (should= :carrier (get-in @atoms/game-map [0 6 :contents :type]))))

    (it "carrier transitions to holding when at target"
      ;; Two distant cities, carrier already at target position
      (let [cells (vec (for [j (range 60)]
                         (cond
                           (= j 0) {:type :city :city-status :computer}
                           (= j 30) {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                            :carrier-mode :positioning :carrier-target [0 30]
                                                            :carrier-pair #{[0 0] [0 59]}}}
                           (= j 59) {:type :city :city-status :computer}
                           :else {:type :sea})))]
        (reset! atoms/game-map [cells])
        (reset! atoms/computer-map [cells])
        (ship/update-distant-city-pairs!)
        (ship/process-ship [0 30] :carrier)
        (should= :holding (get-in @atoms/game-map [0 30 :contents :carrier-mode]))
        (should-be-nil (get-in @atoms/game-map [0 30 :contents :carrier-target]))))

    (it "carrier in holding mode stays put"
      (reset! atoms/game-map [[{:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                       :carrier-mode :holding}}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (ship/process-ship [0 0] :carrier)
      (should= :carrier (get-in @atoms/game-map [0 0 :contents :type]))
      (should= :holding (get-in @atoms/game-map [0 0 :contents :carrier-mode])))

    (it "positioning carrier without target finds position when distant cities exist"
      ;; Two distant cities (60 apart), carrier without target
      (let [cells (vec (for [j (range 60)]
                         (cond
                           (= j 0) {:type :city :city-status :computer}
                           (= j 5) {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                           :carrier-mode :positioning}}
                           (= j 59) {:type :city :city-status :computer}
                           :else {:type :sea})))]
        (reset! atoms/game-map [cells])
        (reset! atoms/computer-map [cells])
        (ship/update-distant-city-pairs!)
        (ship/process-ship [0 5] :carrier)
        ;; Carrier should have moved and gotten a target
        (let [carrier-pos (first (for [c (range 60)
                                       :when (= :carrier (get-in @atoms/game-map [0 c :contents :type]))]
                                   [0 c]))
              unit (get-in @atoms/game-map (conj carrier-pos :contents))]
          (should-not-be-nil carrier-pos)
          (should-not= [0 5] carrier-pos)  ; Should have moved
          (should-not-be-nil (:carrier-target unit))
          (should-not-be-nil (:carrier-pair unit)))))

    (it "positioning carrier without target goes to holding when no distant pairs"
      ;; Single city, no distant pairs
      (let [cells (vec (concat [{:type :city :city-status :computer}
                                 {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                         :carrier-mode :positioning}}]
                                (repeat 20 {:type :sea})))]
        (reset! atoms/game-map [cells])
        (reset! atoms/computer-map [cells])
        (ship/update-distant-city-pairs!)
        (ship/process-ship [0 1] :carrier)
        ;; Carrier should have switched to holding (no distant pairs)
        (should= :holding (get-in @atoms/game-map [0 1 :contents :carrier-mode]))))

    (it "carrier navigates around land using pathfinding"
      ;; Two distant cities, carrier at [0,10] with target [0,30], land at [0,11]
      (let [cells (vec (for [j (range 60)]
                         (cond
                           (= j 0) {:type :city :city-status :computer}
                           (= j 10) {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                            :carrier-mode :positioning :carrier-target [0 30]
                                                            :carrier-pair #{[0 0] [0 59]}}}
                           (= j 11) {:type :land}
                           (= j 59) {:type :city :city-status :computer}
                           :else {:type :sea})))]
        (reset! atoms/game-map [(vec cells) (vec (repeat 60 {:type :sea}))])
        (reset! atoms/computer-map @atoms/game-map)
        (ship/update-distant-city-pairs!)
        (ship/process-ship [0 10] :carrier)
        ;; Carrier should have moved from [0,10] (navigating around land)
        (should-be-nil (:contents (get-in @atoms/game-map [0 10])))
        (let [new-pos (first (for [r (range 2) c (range 60)
                                   :when (= :carrier (get-in @atoms/game-map [r c :contents :type]))]
                               [r c]))]
          (should-not-be-nil new-pos))))

    (it "carrier clears stale target when target becomes occupied"
      ;; Two distant cities, carrier targeting [0,30] but submarine is there
      (let [cells (vec (for [j (range 60)]
                         (cond
                           (= j 0) {:type :city :city-status :computer}
                           (= j 5) {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                           :carrier-mode :positioning :carrier-target [0 30]
                                                           :carrier-pair #{[0 0] [0 59]}}}
                           (= j 30) {:type :sea :contents {:type :submarine :owner :computer :hits 2}}
                           (= j 59) {:type :city :city-status :computer}
                           :else {:type :sea})))]
        (reset! atoms/game-map [cells])
        (reset! atoms/computer-map [cells])
        (ship/update-distant-city-pairs!)
        (ship/process-ship [0 5] :carrier)
        ;; Target [0,30] was invalid (occupied), carrier should go to holding
        ;; (no other unreserved pairs since this carrier's pair is still assigned)
        (let [carrier-pos (first (for [c (range 60)
                                       :when (= :carrier (get-in @atoms/game-map [0 c :contents :type]))]
                                   [0 c]))
              unit (get-in @atoms/game-map (conj carrier-pos :contents))]
          (should= :holding (:carrier-mode unit)))))

    (it "holding carrier repositions when pair city is lost"
      ;; Carrier holding with pair #{[0 0] [0 59]}, but city at [0 0] is now player's
      (let [cells (vec (for [j (range 60)]
                         (cond
                           (= j 0) {:type :city :city-status :player}  ; City lost to player
                           (= j 30) {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                            :carrier-mode :holding
                                                            :carrier-pair #{[0 0] [0 59]}}}
                           (= j 59) {:type :city :city-status :computer}
                           :else {:type :sea})))]
        (reset! atoms/game-map [cells])
        (reset! atoms/computer-map [cells])
        (ship/update-distant-city-pairs!)
        (ship/process-ship [0 30] :carrier)
        ;; Carrier's pair is invalid (city [0 0] is now player's), should reposition
        (let [unit (get-in @atoms/game-map [0 30 :contents])]
          (should= :repositioning (:carrier-mode unit))
          (should-be-nil (:carrier-pair unit))))))

  (context "find-carrier-position"
    (before (reset-all-atoms!))

    (it "returns nil when only one computer city (no pairs)"
      (let [cells (vec (concat [{:type :city :city-status :computer}]
                                (repeat 39 {:type :sea})))]
        (reset! atoms/game-map [cells])
        (ship/update-distant-city-pairs!)
        (should-be-nil (ship/find-carrier-position))))

    (it "returns nil when cities are close (no distant pairs)"
      ;; Two cities 20 apart (< 32)
      (reset! atoms/game-map (build-test-map ["X~~~~~~~~~~~~~~~~~~X" "####################"]))
      (ship/update-distant-city-pairs!)
      (should-be-nil (ship/find-carrier-position)))

    (it "returns map with position and pair when distant cities exist"
      ;; Two cities 59 cells apart (> 32), needs carrier
      (let [cells (vec (for [j (range 60)]
                         (cond
                           (= j 0) {:type :city :city-status :computer}
                           (= j 59) {:type :city :city-status :computer}
                           :else {:type :sea})))]
        (reset! atoms/game-map [cells])
        (ship/update-distant-city-pairs!)
        (let [result (ship/find-carrier-position)]
          (should-not-be-nil result)
          (should (map? result))
          (should= #{[0 0] [0 59]} (:pair result))
          ;; Position should be within fuel range of both cities
          (should (<= (core/distance (:position result) [0 0]) config/fighter-fuel))
          (should (<= (core/distance (:position result) [0 59]) config/fighter-fuel)))))

    (it "returns nil when all distant pairs are reserved"
      ;; Distant pair exists but carrier already assigned
      (reset! atoms/game-map (build-test-map ["X~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~Xc"
                                              "######################################"]))
      (set-test-unit atoms/game-map "c" :carrier-mode :holding
                     :carrier-pair #{[0 0] [36 0]})
      (ship/update-distant-city-pairs!)
      (should-be-nil (ship/find-carrier-position))))

  (context "carrier group escort behavior"
    (it "seeking battleship adopts carrier with open slot"
      ;; Battleship at [0,0] seeking, carrier at [0,3] holding with no BB
      (reset! atoms/game-map [[{:type :sea :contents {:type :battleship :owner :computer :hits 8
                                                       :escort-id 1 :escort-mode :seeking}}
                                {:type :sea}
                                {:type :sea}
                                {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                       :carrier-id 1 :carrier-mode :holding
                                                       :group-battleship-id nil
                                                       :group-submarine-ids []}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (ship/process-ship [0 0] :battleship)
      ;; Battleship should have adopted carrier and moved toward it
      (let [bb (get-in @atoms/game-map [0 1 :contents])
            carrier (get-in @atoms/game-map [0 3 :contents])]
        (should= :battleship (:type bb))
        (should= :intercepting (:escort-mode bb))
        (should= 1 (:escort-carrier-id bb))
        (should= 1 (:group-battleship-id carrier))))

    (it "intercepting escort transitions to orbiting when at radius 2"
      ;; Battleship at [0,0], carrier at [0,2] (Chebyshev distance 2)
      (reset! atoms/game-map [[{:type :sea :contents {:type :battleship :owner :computer :hits 8
                                                       :escort-id 1 :escort-mode :intercepting
                                                       :escort-carrier-id 1 :orbit-angle 0}}
                                {:type :sea}
                                {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                       :carrier-id 1 :carrier-mode :holding
                                                       :group-battleship-id 1
                                                       :group-submarine-ids []}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (ship/process-ship [0 0] :battleship)
      ;; Should transition to orbiting
      (let [bb (get-in @atoms/game-map [0 0 :contents])]
        (should= :orbiting (:escort-mode bb))))

    (it "orbiting escort advances along ring"
      ;; 5x5 all-sea map. Carrier at [2,2], battleship at [0,0] (orbit angle 0 = [-2,-2])
      (let [game-map (build-test-map ["~~~~~"
                                       "~~~~~"
                                       "~~~~~"
                                       "~~~~~"
                                       "~~~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [2 2 :contents]
               {:type :carrier :owner :computer :hits 8
                :carrier-id 1 :carrier-mode :holding
                :group-battleship-id 1 :group-submarine-ids []})
        (swap! atoms/game-map assoc-in [0 0 :contents]
               {:type :battleship :owner :computer :hits 8
                :escort-id 1 :escort-mode :orbiting
                :escort-carrier-id 1 :orbit-angle 0})
        (ship/process-ship [0 0] :battleship)
        ;; Should have moved from [0,0] to next orbit position
        ;; Orbit angle 0 = [-2,-2] = [0,0] relative to carrier at [2,2]
        ;; Next valid angle 1 = [-2,-1] = [0,1]
        (should-be-nil (:contents (get-in @atoms/game-map [0 0])))
        (should= :battleship (get-in @atoms/game-map [0 1 :contents :type]))
        (should= 1 (get-in @atoms/game-map [0 1 :contents :orbit-angle]))))

    (it "escort reverts to seeking when carrier is destroyed"
      ;; Battleship orbiting, no carrier on map
      (reset! atoms/game-map [[{:type :sea :contents {:type :battleship :owner :computer :hits 8
                                                       :escort-id 1 :escort-mode :orbiting
                                                       :escort-carrier-id 99 :orbit-angle 3}}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (ship/process-ship [0 0] :battleship)
      (let [bb (get-in @atoms/game-map [0 0 :contents])]
        (should= :seeking (:escort-mode bb))
        (should-be-nil (:escort-carrier-id bb))))

    (it "seeking submarine adopts carrier with open submarine slot"
      ;; Submarine at [0,0], carrier at [0,3] with 0 subs
      (reset! atoms/game-map [[{:type :sea :contents {:type :submarine :owner :computer :hits 2
                                                       :escort-id 2 :escort-mode :seeking}}
                                {:type :sea}
                                {:type :sea}
                                {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                       :carrier-id 1 :carrier-mode :holding
                                                       :group-battleship-id nil
                                                       :group-submarine-ids []}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (ship/process-ship [0 0] :submarine)
      (let [sub (get-in @atoms/game-map [0 1 :contents])
            carrier (get-in @atoms/game-map [0 3 :contents])]
        (should= :submarine (:type sub))
        (should= :intercepting (:escort-mode sub))
        (should= 1 (:escort-carrier-id sub))
        (should= [2] (:group-submarine-ids carrier))))

    (it "orbiting battleship pursues when carrier spots enemy"
      ;; 7x7 all-sea. Carrier at [3 3], battleship at [1 1] orbiting.
      ;; Player transport at [3 4] — adjacent to carrier, not to battleship.
      ;; Battleship should detect sighting via carrier and pursue.
      (let [game-map (build-test-map ["~~~~~~~"
                                      "~~~~~~~"
                                      "~~~~~~~"
                                      "~~~~~~~"
                                      "~~~~~~~"
                                      "~~~~~~~"
                                      "~~~~~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [3 3 :contents]
               {:type :carrier :owner :computer :hits 8
                :carrier-id 1 :carrier-mode :holding
                :group-battleship-id 1 :group-submarine-ids []})
        (swap! atoms/game-map assoc-in [1 1 :contents]
               {:type :battleship :owner :computer :hits 8
                :escort-id 1 :escort-mode :orbiting
                :escort-carrier-id 1 :orbit-angle 0})
        (swap! atoms/game-map assoc-in [3 4 :contents]
               {:type :transport :owner :player :hits 3})
        (ship/process-ship [1 1] :battleship)
        ;; Battleship should enter pursuit mode
        (let [bb (first (for [c (range 7) r (range 7)
                              :let [unit (get-in @atoms/game-map [c r :contents])]
                              :when (= :battleship (:type unit))]
                          unit))]
          (should= :pursuing (:escort-mode bb))
          (should= [3 4] (:pursuit-target bb))
          (should= 5 (:pursuit-steps-remaining bb)))))

    (it "pursuing battleship returns to orbiting after steps exhausted"
      ;; Battleship pursuing with 1 step remaining, carrier still on map.
      ;; Should revert to orbiting (not escorting).
      (let [game-map (build-test-map ["~~~~~"
                                      "~~~~~"
                                      "~~~~~"
                                      "~~~~~"
                                      "~~~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [2 2 :contents]
               {:type :carrier :owner :computer :hits 8
                :carrier-id 1 :carrier-mode :holding
                :group-battleship-id 1 :group-submarine-ids []})
        (swap! atoms/game-map assoc-in [0 0 :contents]
               {:type :battleship :owner :computer :hits 8
                :escort-id 1 :escort-mode :pursuing
                :escort-carrier-id 1
                :pursuit-target [4 4] :pursuit-steps-remaining 1})
        (ship/process-ship [0 0] :battleship)
        (let [bb (get-in @atoms/game-map [0 0 :contents])]
          (should= :orbiting (:escort-mode bb))
          (should-be-nil (:pursuit-target bb)))))) ;; end carrier group escort
) ;; end process-ship

(describe "carrier positioning helpers"
  (before (reset-all-atoms!))

  (context "compute-distant-city-pairs"
    (it "returns empty set when no computer cities"
      (reset! atoms/game-map (build-test-map ["~~~" "###"]))
      (should (empty? (ship/compute-distant-city-pairs))))

    (it "returns empty set when only one computer city"
      (reset! atoms/game-map (build-test-map ["X~~" "###"]))
      (should (empty? (ship/compute-distant-city-pairs))))

    (it "returns empty set when cities are close (distance <= 32)"
      ;; Two cities 10 apart (< 32)
      (reset! atoms/game-map (build-test-map ["X~~~~~~~~~X" "###########"]))
      (should (empty? (ship/compute-distant-city-pairs))))

    (it "returns pair when cities are distant (distance > 32)"
      ;; X at 0, X at 36 = distance 36 > 32
      (reset! atoms/game-map (build-test-map ["X~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~X"
                                              "#####################################"]))
      (let [pairs (ship/compute-distant-city-pairs)]
        (should= 1 (count pairs))
        (should= #{[0 0] [36 0]} (first pairs))))

    (it "returns multiple pairs when multiple distant city combinations exist"
      ;; 80 characters: X at 0, X at 40, X at 79
      ;; Distances: 0-40=40, 40-79=39, 0-79=79 - all > 32
      (let [row (str "X" (apply str (repeat 39 \~)) "X" (apply str (repeat 38 \~)) "X")]
        (reset! atoms/game-map (build-test-map [row (apply str (repeat 80 \#))]))
        (let [pairs (ship/compute-distant-city-pairs)]
          (should= 3 (count pairs)))))

    (it "ignores player cities"
      ;; O is player city, X is computer city - only one computer city
      (reset! atoms/game-map (build-test-map ["O~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~X"
                                              "#####################################"]))
      (should (empty? (ship/compute-distant-city-pairs)))))

  (context "update-distant-city-pairs!"
    (it "updates the distant-city-pairs atom"
      (reset! atoms/game-map (build-test-map ["X~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~X"
                                              "#####################################"]))
      (ship/update-distant-city-pairs!)
      (should= 1 (count @atoms/distant-city-pairs))
      (should= #{[0 0] [36 0]} (first @atoms/distant-city-pairs))))

  (context "find-reserved-pairs"
    (it "returns empty set when no carriers"
      (reset! atoms/game-map (build-test-map ["~~~" "###"]))
      (should (empty? (ship/find-reserved-pairs))))

    (it "returns empty set when carrier has no pair assigned"
      (reset! atoms/game-map (build-test-map ["~c~" "###"]))
      (set-test-unit atoms/game-map "c" :carrier-mode :positioning)
      (should (empty? (ship/find-reserved-pairs))))

    (it "returns pair from positioning carrier"
      (reset! atoms/game-map (build-test-map ["~c~" "###"]))
      (set-test-unit atoms/game-map "c" :carrier-mode :positioning
                     :carrier-pair #{[0 0] [50 0]})
      (let [pairs (ship/find-reserved-pairs)]
        (should= 1 (count pairs))
        (should= #{[0 0] [50 0]} (first pairs))))

    (it "returns pair from holding carrier"
      (reset! atoms/game-map (build-test-map ["~c~" "###"]))
      (set-test-unit atoms/game-map "c" :carrier-mode :holding
                     :carrier-pair #{[0 0] [50 0]})
      (let [pairs (ship/find-reserved-pairs)]
        (should= 1 (count pairs))
        (should= #{[0 0] [50 0]} (first pairs))))

    (it "returns multiple pairs from multiple carriers"
      (reset! atoms/game-map (build-test-map ["~c~c~" "#####"]))
      (set-test-unit atoms/game-map "c1" :carrier-mode :holding
                     :carrier-pair #{[0 0] [50 0]})
      (set-test-unit atoms/game-map "c2" :carrier-mode :positioning
                     :carrier-pair #{[10 0] [60 0]})
      (let [pairs (ship/find-reserved-pairs)]
        (should= 2 (count pairs))))

    (it "ignores player carriers"
      (reset! atoms/game-map (build-test-map ["~C~" "###"]))
      (set-test-unit atoms/game-map "C" :carrier-mode :holding
                     :carrier-pair #{[0 0] [50 0]})
      (should (empty? (ship/find-reserved-pairs)))))

  (context "find-unreserved-pair"
    (it "returns nil when no distant city pairs exist"
      (reset! atoms/game-map (build-test-map ["X~~~~~~~~~X" "###########"]))
      (ship/update-distant-city-pairs!)
      (should-be-nil (ship/find-unreserved-pair)))

    (it "returns a pair when distant pair exists and none reserved"
      (reset! atoms/game-map (build-test-map ["X~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~X"
                                              "#####################################"]))
      (ship/update-distant-city-pairs!)
      (let [pair (ship/find-unreserved-pair)]
        (should= #{[0 0] [36 0]} pair)))

    (it "returns nil when all distant pairs are reserved"
      (reset! atoms/game-map (build-test-map ["X~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~Xc"
                                              "######################################"]))
      (set-test-unit atoms/game-map "c" :carrier-mode :holding
                     :carrier-pair #{[0 0] [35 0]})
      (ship/update-distant-city-pairs!)
      (should-be-nil (ship/find-unreserved-pair)))

    (it "returns unreserved pair when some pairs are reserved"
      ;; 80 chars: X at 0, X at 40, X at 79
      ;; Three pairs, reserve one
      (let [row (str "X" (apply str (repeat 39 \~)) "X" (apply str (repeat 37 \~)) "Xc")]
        (reset! atoms/game-map (build-test-map [row (apply str (repeat 81 \#))])))
      (set-test-unit atoms/game-map "c" :carrier-mode :holding
                     :carrier-pair #{[0 0] [40 0]})
      (ship/update-distant-city-pairs!)
      (let [pair (ship/find-unreserved-pair)]
        (should-not-be-nil pair)
        (should-not= #{[0 0] [40 0]} pair))))

  (context "find-position-between-cities"
    (it "returns midpoint position when cities are in straight line"
      ;; X at 0, X at 36 - midpoint is 18
      (reset! atoms/game-map (build-test-map ["X~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~X"
                                              "####################################"]))
      (let [pos (ship/find-position-between-cities #{[0 0] [35 0]})]
        (should-not-be-nil pos)
        ;; Should be sea cell
        (should= :sea (:type (get-in @atoms/game-map pos)))
        ;; Should be within fighter-fuel distance of both cities
        (let [[c1 c2] (vec #{[0 0] [35 0]})]
          (should (<= (core/distance pos c1) config/fighter-fuel))
          (should (<= (core/distance pos c2) config/fighter-fuel)))))

    (it "returns nil when no sea position reachable from both cities"
      ;; Cities separated by land, no valid path
      (reset! atoms/game-map (build-test-map ["X####################################X"
                                              "######################################"]))
      (should-be-nil (ship/find-position-between-cities #{[0 0] [37 0]})))

    (it "finds position when midpoint is blocked by land"
      ;; X at 0, X at 40 - midpoint area has some land, should find nearby sea
      (let [row (str "X" (apply str (repeat 19 \~)) "#" (apply str (repeat 19 \~)) "X")]
        (reset! atoms/game-map (build-test-map [row (apply str (repeat 41 \#))])))
      (let [pos (ship/find-position-between-cities #{[0 0] [40 0]})]
        (should-not-be-nil pos)
        (should= :sea (:type (get-in @atoms/game-map pos)))
        ;; Should be within fighter-fuel of both
        (should (<= (core/distance pos [0 0]) config/fighter-fuel))
        (should (<= (core/distance pos [40 0]) config/fighter-fuel))))))

;; === Mutation-killing tests ===

(describe "mutation: ship combat and movement"
  (before (reset-all-atoms!))

  (context "attack-enemy (L49, L52)"
    (it "winning attacker occupies enemy position"
      (reset! atoms/game-map [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3 :destroyer-id 1}}
                                {:type :sea :contents {:type :patrol-boat :owner :player :hits 1}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (with-redefs [combat/resolve-combat
                    (fn [a _d] {:winner :attacker :survivor a})
                    combat/clear-escort-on-death (fn [_] nil)]
        (ship/process-ship [0 0] :destroyer))
      (should-be-nil (:contents (get-in @atoms/game-map [0 0])))
      (should= :computer (get-in @atoms/game-map [0 1 :contents :owner])))

    (it "losing attacker does not replace defender"
      (reset! atoms/game-map [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3}}
                                {:type :sea :contents {:type :battleship :owner :player :hits 8}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (with-redefs [combat/resolve-combat
                    (fn [_a _d] {:winner :defender :survivor {:type :battleship :owner :player :hits 7}})
                    combat/clear-escort-on-death (fn [_] nil)]
        (ship/process-ship [0 0] :destroyer))
      (should= 8 (get-in @atoms/game-map [0 1 :contents :hits])))

    (it "dead-unit is defender when attacker wins (L49)"
      (reset! atoms/game-map [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3
                                                       :destroyer-id 1 :escort-mode :escorting
                                                       :escort-transport-id 1}}
                                {:type :sea :contents {:type :patrol-boat :owner :player :hits 1}}
                                {:type :sea :contents {:type :transport :owner :computer :hits 3
                                                       :transport-id 1 :escort-destroyer-id 1
                                                       :transport-mission :loading :army-count 0}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (with-redefs [combat/resolve-combat
                    (fn [a _d] {:winner :attacker :survivor a})]
        (ship/process-ship [0 0] :destroyer))
      (should= 1 (get-in @atoms/game-map [0 2 :contents :escort-destroyer-id]))))

  (context "passable neighbors exclude computer units (L27)"
    (it "destroyer does not overwrite own submarine when hunting"
      ;; Only neighbor of [0,0] is [0,1] (computer sub). Player ship visible at [0,3].
      ;; Original: computer sub NOT passable -> can't move. Mutation: passable -> overwrites sub.
      (reset! atoms/game-map [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3}}
                                {:type :sea :contents {:type :submarine :owner :computer :hits 2}}
                                {:type :sea}
                                {:type :sea :contents {:type :patrol-boat :owner :player :hits 1}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (ship/process-ship [0 0] :destroyer)
      (should= :submarine (get-in @atoms/game-map [0 1 :contents :type]))))

  (context "no retreat when not threatened (L113)"
    (it "undamaged ship attacks instead of retreating"
      (reset! atoms/game-map [[{:type :sea}
                                {:type :sea :contents {:type :destroyer :owner :computer :hits 3}}
                                {:type :sea :contents {:type :patrol-boat :owner :player :hits 1}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (with-redefs [threat/should-retreat? (constantly false)
                    threat/retreat-move (constantly [0 0])
                    combat/resolve-combat
                    (fn [a _d] {:winner :attacker :survivor a})
                    combat/clear-escort-on-death (fn [_] nil)]
        (ship/process-ship [0 1] :destroyer))
      (should-be-nil (:contents (get-in @atoms/game-map [0 0])))
      (should= :computer (get-in @atoms/game-map [0 2 :contents :owner]))))

  (context "escorting distance boundary (L450)"
    (it "escorting destroyer at distance 1 stays put"
      (reset! atoms/game-map [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3
                                                       :destroyer-id 1 :escort-mode :escorting
                                                       :escort-transport-id 1}}
                                {:type :sea :contents {:type :transport :owner :computer :hits 3
                                                       :transport-id 1 :escort-destroyer-id 1
                                                       :transport-mission :loading :army-count 0}}
                                {:type :sea}]
                               [{:type :sea} {:type :sea} {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (ship/process-ship [0 0] :destroyer)
      (should= :destroyer (get-in @atoms/game-map [0 0 :contents :type]))))

  (context "legacy escort distance boundary (L902)"
    (it "destroyer at distance 2 from transport explores instead of following"
      (reset! atoms/game-map [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3}}
                                {:type :sea}
                                {:type :sea :contents {:type :transport :owner :computer
                                                       :army-count 3 :hits 3}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (ship/process-ship [0 0] :destroyer)
      (should= :destroyer (get-in @atoms/game-map [0 0 :contents :type])))))

(describe "mutation: patrol boat navigation"
  (before (reset-all-atoms!))

  (context "in-bounds? boundary (L194)"
    (it "patrol boat moves to first-coord 0"
      (let [game-map (build-test-map ["~~~~~"
                                      "~~~~~"
                                      "~~~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [1 2 :contents]
               {:type :patrol-boat :owner :computer :hits 1
                :patrol-country-id 1 :patrol-number 2 :patrol-heading 270})
        (ship/process-ship [1 2] :patrol-boat)
        (should= :patrol-boat (get-in @atoms/game-map [0 2 :contents :type]))))

    (it "patrol boat moves to second-coord 0"
      (let [game-map (build-test-map ["~~~~~"
                                      "~~~~~"
                                      "~~~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [2 1 :contents]
               {:type :patrol-boat :owner :computer :hits 1
                :patrol-country-id 1 :patrol-number 2 :patrol-heading 0})
        (ship/process-ship [2 1] :patrol-boat)
        (should= :patrol-boat (get-in @atoms/game-map [2 0 :contents :type])))))

  (context "detect-reflection-surface (L195, L203, L204, L206)"
    (it "reflects vertically at right edge (c=max-c)"
      (let [game-map (build-test-map ["~~~~~"
                                      "~~~~~"
                                      "~~~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [4 1 :contents]
               {:type :patrol-boat :owner :computer :hits 1
                :patrol-country-id 1 :patrol-number 2 :patrol-heading 90})
        (with-redefs [rand-int (constantly 10)]
          (ship/process-ship [4 1] :patrol-boat))
        ;; heading 90 east -> [5,1] out of bounds. detect [4,1]: c=4>=max-c -> :vertical
        ;; reflect 90 :vertical = 270
        (should= 270 (get-in @atoms/game-map [4 1 :contents :patrol-heading]))))

    (it "reflects horizontally at bottom-right corner [4,2]"
      (let [game-map (build-test-map ["~~~~~"
                                      "~~~~~"
                                      "~~~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [4 2 :contents]
               {:type :patrol-boat :owner :computer :hits 1
                :patrol-country-id 1 :patrol-number 2 :patrol-heading 135})
        (with-redefs [rand-int (constantly 10)]
          (ship/process-ship [4 2] :patrol-boat))
        ;; heading 135 SE -> [5,3] out of bounds. detect [4,2]: r=2>=max-r -> :horizontal
        ;; reflect 135 :horizontal = (540-135)%360 = 45
        (should= 45 (get-in @atoms/game-map [4 2 :contents :patrol-heading]))))

    (it "corner [0,0] reflects horizontally not vertically"
      (let [game-map (build-test-map ["~~~~~"
                                      "~~~~~"
                                      "~~~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [0 0 :contents]
               {:type :patrol-boat :owner :computer :hits 1
                :patrol-country-id 1 :patrol-number 2 :patrol-heading 315})
        (with-redefs [rand-int (constantly 10)]
          (ship/process-ship [0 0] :patrol-boat))
        ;; heading 315 NW -> [-1,-1] out of bounds. detect [0,0]: r=0<=0 -> :horizontal
        ;; reflect 315 :horizontal = (540-315)%360 = 225
        (should= 225 (get-in @atoms/game-map [0 0 :contents :patrol-heading]))))

    (it "position [0,1] reflects vertically not horizontally"
      (let [game-map (build-test-map ["~~~~~"
                                      "~~~~~"
                                      "~~~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [0 1 :contents]
               {:type :patrol-boat :owner :computer :hits 1
                :patrol-country-id 1 :patrol-number 2 :patrol-heading 315})
        (with-redefs [rand-int (constantly 10)]
          (ship/process-ship [0 1] :patrol-boat))
        ;; heading 315 NW -> [-1,0] out of bounds. detect [0,1]: r=1 not edge. c=0<=0 -> :vertical
        ;; reflect 315 :vertical = (360-315)%360 = 45
        (should= 45 (get-in @atoms/game-map [0 1 :contents :patrol-heading]))))

    (it "bottom edge [0,2] with corner reflects horizontally"
      (let [game-map (build-test-map ["~~~~~"
                                      "~~~~~"
                                      "~~~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [0 2 :contents]
               {:type :patrol-boat :owner :computer :hits 1
                :patrol-country-id 1 :patrol-number 2 :patrol-heading 225})
        (with-redefs [rand-int (constantly 10)]
          (ship/process-ship [0 2] :patrol-boat))
        ;; heading 225 SW -> [-1,3] out of bounds. detect [0,2]: r=2>=max-r -> :horizontal
        ;; reflect 225 :horizontal = (540-225)%360 = 315
        (should= 315 (get-in @atoms/game-map [0 2 :contents :patrol-heading]))))

    (it "reflects horizontally not vertically at c=1 (L207)"
      ;; Patrol boat at [1,1] heading 90 east. next-pos [2,1] has player ship = explored coast.
      ;; detect-reflection-surface [1,1]: c=1, not at any edge = nil = defaults to :horizontal.
      ;; Mutation (0->1): (<= c 1) = true = :vertical (wrong).
      ;; :horizontal reflect of 90 = 90. :vertical reflect of 90 = 270.
      (let [game-map (build-test-map ["~~~~"
                                      "~~~~"
                                      "~~#~"
                                      "~~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [1 1 :contents]
               {:type :patrol-boat :owner :computer :hits 1
                :patrol-country-id 1 :patrol-number 2 :patrol-heading 90})
        (swap! atoms/game-map assoc-in [2 1 :contents]
               {:type :destroyer :owner :player :hits 3})
        (with-redefs [rand-int (constantly 10)]
          (ship/process-ship [1 1] :patrol-boat))
        ;; Should reflect horizontally (heading stays 90), not vertically (would be 270)
        (should= 90 (get-in @atoms/game-map [1 1 :contents :patrol-heading]))))))

(describe "mutation: carrier and escort operations"
  (before (reset-all-atoms!))

  (context "compute-distant-city-pairs boundary (L485)"
    (it "cities at exactly fighter-fuel distance are not paired"
      (let [fuel config/fighter-fuel
            row (vec (for [j (range (inc fuel))]
                       (cond
                         (= j 0) {:type :city :city-status :computer}
                         (= j fuel) {:type :city :city-status :computer}
                         :else {:type :sea})))]
        (reset! atoms/game-map [row])
        (should (empty? (ship/compute-distant-city-pairs))))))

  (context "find-unreserved-pair lazy init (L511)"
    (it "initializes distant-city-pairs when nil"
      (reset! atoms/game-map (build-test-map ["X~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~X"
                                              "#####################################"]))
      (reset! atoms/distant-city-pairs nil)
      (let [pair (ship/find-unreserved-pair)]
        (should-not-be-nil pair))))

  (context "find-position-between-cities (L524, L525, L534, L535)"
    (it "midpoint first-coord is correct (L524)"
      ;; 61 cols x 1 row. Cities at [10,0] and [50,0]. Midpoint at [30,0].
      (let [game-map (vec (for [c (range 61)]
                            [(cond
                               (= c 10) {:type :city :city-status :computer}
                               (= c 50) {:type :city :city-status :computer}
                               :else {:type :sea})]))]
        (reset! atoms/game-map game-map)
        (let [pos (ship/find-position-between-cities #{[10 0] [50 0]})]
          (should-not-be-nil pos)
          ;; Should be near midpoint col 30, not near col 18
          (should= 30 (first pos)))))

    (it "midpoint second-coord is correct (L525)"
      ;; 1 col x 61 rows. Cities at [0,10] and [0,50]. Midpoint at [0,30].
      (let [col (vec (for [r (range 61)]
                       (cond
                         (= r 10) {:type :city :city-status :computer}
                         (= r 50) {:type :city :city-status :computer}
                         :else {:type :sea})))]
        (reset! atoms/game-map [col])
        (let [pos (ship/find-position-between-cities #{[0 10] [0 50]})]
          (should-not-be-nil pos)
          ;; Should be near midpoint row 30, not near row 18
          (should= 30 (second pos)))))

    (it "position at exactly fuel distance is included"
      (let [fuel config/fighter-fuel
            row (vec (for [j (range (inc (* 2 fuel)))]
                       (cond
                         (= j 0) {:type :city :city-status :computer}
                         (= j (* 2 fuel)) {:type :city :city-status :computer}
                         :else {:type :sea})))]
        (reset! atoms/game-map [row])
        (let [pos (ship/find-position-between-cities #{[0 0] [0 (* 2 fuel)]})]
          (should-not-be-nil pos)
          (should= [0 fuel] pos)))))

  (context "find-refueling-sites (L547-L551)"
    (it "includes computer cities"
      (reset! atoms/game-map [[{:type :city :city-status :computer} {:type :sea}]])
      (should= [[0 0]] (ship/find-refueling-sites)))

    (it "excludes player cities"
      (reset! atoms/game-map [[{:type :city :city-status :player} {:type :sea}]])
      (should (empty? (ship/find-refueling-sites))))

    (it "includes holding computer carriers"
      (reset! atoms/game-map [[{:type :sea :contents {:type :carrier :owner :computer
                                                       :carrier-mode :holding}} {:type :sea}]])
      (should= [[0 0]] (ship/find-refueling-sites)))

    (it "excludes positioning computer carriers"
      (reset! atoms/game-map [[{:type :sea :contents {:type :carrier :owner :computer
                                                       :carrier-mode :positioning}} {:type :sea}]])
      (should (empty? (ship/find-refueling-sites))))

    (it "excludes player carriers"
      (reset! atoms/game-map [[{:type :sea :contents {:type :carrier :owner :player
                                                       :carrier-mode :holding}} {:type :sea}]])
      (should (empty? (ship/find-refueling-sites)))))

  (context "pair-still-valid? (L625, L626)"
    (it "holding carrier stays put when pair cities are valid"
      (let [cells (vec (for [j (range 60)]
                         (cond
                           (= j 0) {:type :city :city-status :computer}
                           (= j 30) {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                            :carrier-mode :holding
                                                            :carrier-pair #{[0 0] [0 59]}}}
                           (= j 59) {:type :city :city-status :computer}
                           :else {:type :sea})))]
        (reset! atoms/game-map [cells])
        (reset! atoms/computer-map [cells])
        (ship/update-distant-city-pairs!)
        (ship/process-ship [0 30] :carrier)
        (should= :holding (get-in @atoms/game-map [0 30 :contents :carrier-mode])))))

  (context "carrier submarine slot cap (L689)"
    (it "submarine does not adopt carrier with 2 existing subs"
      (reset! atoms/game-map [[{:type :sea :contents {:type :submarine :owner :computer :hits 2
                                                       :escort-id 3 :escort-mode :seeking}}
                                {:type :sea}
                                {:type :sea}
                                {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                       :carrier-id 1 :carrier-mode :holding
                                                       :group-battleship-id nil
                                                       :group-submarine-ids [1 2]}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (ship/process-ship [0 0] :submarine)
      (let [sub (first (for [c (range 4)
                             :let [unit (get-in @atoms/game-map [0 c :contents])]
                             :when (= :submarine (:type unit))]
                         unit))]
        (should= :seeking (:escort-mode sub)))))

  (context "initial-orbit-angle (L698, L700)"
    (it "battleship starts with orbit-angle 0"
      (reset! atoms/game-map [[{:type :sea :contents {:type :battleship :owner :computer :hits 8
                                                       :escort-id 1 :escort-mode :seeking}}
                                {:type :sea}
                                {:type :sea}
                                {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                       :carrier-id 1 :carrier-mode :holding
                                                       :group-battleship-id nil
                                                       :group-submarine-ids []}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (ship/process-ship [0 0] :battleship)
      (let [bb (first (for [c (range 4)
                            :let [unit (get-in @atoms/game-map [0 c :contents])]
                            :when (= :battleship (:type unit))]
                        unit))]
        (should= 0 (:orbit-angle bb))))

    (it "first submarine starts with orbit-angle 5"
      (reset! atoms/game-map [[{:type :sea :contents {:type :submarine :owner :computer :hits 2
                                                       :escort-id 2 :escort-mode :seeking}}
                                {:type :sea}
                                {:type :sea}
                                {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                       :carrier-id 1 :carrier-mode :holding
                                                       :group-battleship-id nil
                                                       :group-submarine-ids []}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (ship/process-ship [0 0] :submarine)
      (let [sub (first (for [c (range 4)
                             :let [unit (get-in @atoms/game-map [0 c :contents])]
                             :when (= :submarine (:type unit))]
                         unit))]
        (should= 5 (:orbit-angle sub))))

    (it "second submarine starts with orbit-angle 11"
      (reset! atoms/game-map [[{:type :sea :contents {:type :submarine :owner :computer :hits 2
                                                       :escort-id 3 :escort-mode :seeking}}
                                {:type :sea}
                                {:type :sea}
                                {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                       :carrier-id 1 :carrier-mode :holding
                                                       :group-battleship-id nil
                                                       :group-submarine-ids [2]}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (ship/process-ship [0 0] :submarine)
      (let [sub (first (for [c (range 4)
                             :let [unit (get-in @atoms/game-map [0 c :contents])]
                             :when (= :submarine (:type unit))]
                         unit))]
        (should= 11 (:orbit-angle sub)))))

  (context "orbit advances forward (L741)"
    (it "orbit advances forward not backward"
      ;; Carrier at [3,3]. BB at angle 2 = [1,3]. Land at angle 3 = [1,4].
      ;; Angle 4 = [1,5] is sea. Forward search finds 4, backward finds 1.
      ;; build-test-map transposes: pos [1,4] = col 1, row 4 -> string 4, char 1.
      (let [game-map (build-test-map ["~~~~~~~"
                                      "~~~~~~~"
                                      "~~~~~~~"
                                      "~~~~~~~"
                                      "~#~~~~~"
                                      "~~~~~~~"
                                      "~~~~~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [3 3 :contents]
               {:type :carrier :owner :computer :hits 8
                :carrier-id 1 :carrier-mode :holding
                :group-battleship-id 1 :group-submarine-ids []})
        (swap! atoms/game-map assoc-in [1 3 :contents]
               {:type :battleship :owner :computer :hits 8
                :escort-id 1 :escort-mode :orbiting
                :escort-carrier-id 1 :orbit-angle 2})
        (ship/process-ship [1 3] :battleship)
        (let [bb (first (for [c (range 7) r (range 7)
                              :let [unit (get-in @atoms/game-map [c r :contents])]
                              :when (= :battleship (:type unit))]
                          unit))]
          (should= 4 (:orbit-angle bb)))))))

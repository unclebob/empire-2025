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
      ;; Wide coastal strip. Only coastal neighbor from [0,0] is [1,0] which is in history.
      ;; Should still move there as fallback, then continue along coast at speed 4.
      (reset! atoms/game-map (build-test-map ["~~~~~" "#####"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [0 0 :contents]
             {:type :patrol-boat :owner :computer :hits 1
              :patrol-country-id 1 :patrol-direction :clockwise :patrol-mode :patrolling
              :patrol-history [[1 0]]})
      (ship/process-ship [0 0] :patrol-boat)
      ;; Boat used fallback (history cell) then continued along coast
      (should-be-nil (:contents (get-in @atoms/game-map [0 0])))
      (let [new-pos (first (for [c (range 5) r (range 2)
                                 :when (= :patrol-boat (get-in @atoms/game-map [c r :contents :type]))]
                             [c r]))]
        (should-not-be-nil new-pos)))

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
      ;; Find where patrol boat moved (moves up to 4 steps along coast)
      (let [new-pos (first (for [c (range 5) r (range 3)
                                 :when (= :patrol-boat (get-in @atoms/game-map [c r :contents :type]))]
                             [c r]))
            unit (get-in @atoms/game-map (conj new-pos :contents))]
        ;; History should have recent positions (limited to 3 entries)
        (should (seq (:patrol-history unit)))))

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
      ;; Large map so boat can move 4 steps south without hitting edge
      (let [game-map (build-test-map (repeat 10 "~~~~~~~~~~"))]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [2 2 :contents]
               {:type :patrol-boat :owner :computer :hits 1
                :patrol-country-id 1 :patrol-number 2})
        (with-redefs [rand-int (constantly 180)]  ; heading south
          (ship/process-ship [2 2] :patrol-boat))
        ;; Should have moved 4 cells south via heading-based sailing (speed 4)
        (let [unit (:contents (get-in @atoms/game-map [2 6]))]
          (should= :patrol-boat (:type unit))
          (should= 180 (:patrol-heading unit)))))

    (it "sailing patrol boat switches to coastline exploration at unexplored coast"
      ;; Patrol boat at [2 2] heading east. Land at col 4 is unexplored.
      ;; [3 2] is sea adjacent to unexplored land -> should switch to coastline mode.
      ;; After switching, remaining steps continue as coastline patrol.
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
        ;; Boat should have switched to coastline-exploring mode
        (let [new-pos (first (for [c (range 5) r (range 5)
                                   :when (= :patrol-boat (get-in @atoms/game-map [c r :contents :type]))]
                               [c r]))
              unit (get-in @atoms/game-map (conj new-pos :contents))]
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
      ;; Patrol boat at top edge heading north -> should reflect and then move
      (let [game-map (build-test-map (repeat 10 "~~~~~~~~~~"))]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [5 0 :contents]
               {:type :patrol-boat :owner :computer :hits 1
                :patrol-country-id 1 :patrol-number 3 :patrol-heading 0})
        (with-redefs [rand-int (constantly 10)]
          (ship/process-ship [5 0] :patrol-boat))
        ;; Should have reflected heading and moved away from border
        (should-be-nil (:contents (get-in @atoms/game-map [5 0])))
        (let [new-pos (first (for [c (range 10) r (range 10)
                                   :when (= :patrol-boat (get-in @atoms/game-map [c r :contents :type]))]
                               [c r]))
              unit (get-in @atoms/game-map (conj new-pos :contents))]
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
) ;; end process-ship

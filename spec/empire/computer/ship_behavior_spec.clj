(ns empire.computer.ship-behavior-spec
  "Tests for VMS Empire style computer ship movement."
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.ship :as ship]
            [empire.computer.ship-carrier :as ship-carrier]
            [empire.computer.core :as core]
            [empire.config.core :as config]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-unit set-test-world! update-test-world!]]
            [empire.game-mechanics.containers.helpers :as uc]
            [empire.game-mechanics.services.combat :as combat]
            [empire.computer.threat :as threat]))
(describe "process-ship"
  (before (reset-all-atoms!))

  (context "dock behavior"
    (it "damaged computer ship docks at adjacent friendly city"
      (set-test-world! (build-test-map ["BdX"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (set-test-unit (test-utils/game-map-atom) "d" :hits 2)
      (test-utils/update-test-state! :computer-map assoc-in [1 0 :contents :hits] 2)
      (ship/process-ship [1 0] :destroyer)
      ;; Ship should be removed from map
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [1 0 :contents]))
      ;; Ship should be in city X's shipyard
      (let [city (get-in (test-utils/read-test-state :game-map) [2 0])
            shipyard (uc/get-shipyard-ships city)]
        (should= 1 (count shipyard))
        (should= :destroyer (:type (first shipyard)))
        (should= 2 (:hits (first shipyard))))))

  (context "attack behavior"
    (it "attacks adjacent player ship"
      (set-test-world! [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3}}
                                {:type :sea :contents {:type :patrol-boat :owner :player :hits 1}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [_result (ship/process-ship [0 0] :destroyer)]
        ;; Combat should have occurred
        (let [cell0 (get-in (test-utils/read-test-state :game-map) [0 0])
              cell1 (get-in (test-utils/read-test-state :game-map) [0 1])]
          (should (or (nil? (:contents cell0))
                      (nil? (:contents cell1))
                      (= :computer (:owner (:contents cell1)))))))))

  (context "escort behavior"
    (it "destroyer moves toward transport"
      (set-test-world! [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3}}
                                {:type :sea}
                                {:type :sea}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :army-count 3}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (ship/process-ship [0 0] :destroyer)
      ;; Destroyer should have moved toward transport
      (should= :destroyer (get-in (test-utils/read-test-state :game-map) [0 1 :contents :type]))))

  (context "exploration behavior"
    (it "explores toward unexplored sea"
      (set-test-computer-map! [[{:type :sea :contents {:type :submarine :owner :computer :hits 2}}
                                    {:type :sea}
                                    nil]])
      (set-test-world! [[{:type :sea :contents {:type :submarine :owner :computer :hits 2}}
                                {:type :sea}
                                {:type :sea}]])
      (ship/process-ship [0 0] :submarine)
      ;; Ship should have moved toward unexplored
      (should= :submarine (get-in (test-utils/read-test-state :game-map) [0 1 :contents :type])))

    (it "stays put when all sea is explored"
      (set-test-world! [[{:type :sea :contents {:type :submarine :owner :computer :hits 2}}
                                {:type :sea}
                                {:type :land}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (ship/process-ship [0 0] :submarine)
      ;; Ship stays put - no unexplored territory
      (should= :submarine (get-in (test-utils/read-test-state :game-map) [0 0 :contents :type])))

    (it "explores toward unexplored sea without NW bias"
      ;; 5x5 all-sea map. Ship at center, unexplored in SE corner.
      (let [game-map (build-test-map ["~~~~~"
                                      "~~~~~"
                                      "~~~~~"
                                      "~~~~~"
                                      "~~~~~"])]
        (set-test-world! game-map)
        (set-test-computer-map! (build-test-map ["~~~~~"
                                                    "~~~~~"
                                                    "~~~~~"
                                                    "~~~~~"
                                                    "~~~~-"]))
        (update-test-world! assoc-in [2 2 :contents]
               {:type :destroyer :owner :computer :hits 3})
        (test-utils/update-test-state! :computer-map assoc-in [2 2 :contents]
                                       {:type :destroyer :owner :computer :hits 3})
        (ship/process-ship [2 2] :destroyer)
        ;; Should have moved
        (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [2 2])))
        ;; Find where ship moved
        (let [new-pos (first (for [r (range 5) c (range 5)
                                   :when (= :destroyer (get-in (test-utils/read-test-state :game-map) [r c :contents :type]))]
                               [r c]))]
          ;; Should move toward SE, not NW
          (should-not= [1 1] new-pos)
          (should (or (> (first new-pos) 2)
                      (> (second new-pos) 2)))))))

  (context "hunting behavior"
    (it "moves toward visible player ship"
      (set-test-world! [[{:type :sea :contents {:type :battleship :owner :computer :hits 8}}
                                {:type :sea}
                                {:type :sea}
                                {:type :sea :contents {:type :destroyer :owner :player :hits 3}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (ship/process-ship [0 0] :battleship)
      ;; Battleship should have moved toward player ship
      (should= :battleship (get-in (test-utils/read-test-state :game-map) [0 1 :contents :type]))))

  (context "ignores non-computer ships"
    (it "returns nil for player ship"
      (set-test-world! [[{:type :sea :contents {:type :destroyer :owner :player :hits 3}}]])
      (should-be-nil (ship/process-ship [0 0] :destroyer)))

    (it "returns nil for wrong ship type"
      (set-test-world! [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3}}]])
      (should-be-nil (ship/process-ship [0 0] :patrol-boat))))

)

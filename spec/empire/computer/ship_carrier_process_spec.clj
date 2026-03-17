(ns empire.computer.ship-carrier-process-spec
  "Tests for VMS Empire style computer ship movement - carrier group escort, positioning helpers, mutation: ship combat."
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.ship :as ship]
            [empire.computer.core :as core]
            [empire.config.core :as config]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-unit set-test-world! update-test-world!]]
            [empire.game-mechanics.containers.helpers :as uc]
            [empire.game-mechanics.services.combat :as combat]
            [empire.computer.threat :as threat]))
(describe "process-ship"
  (before (reset-all-atoms!))

  (context "carrier group escort behavior"
    (it "seeking battleship adopts carrier with open slot"
      ;; Battleship at [0,0] seeking, carrier at [0,3] holding with no BB
      (set-test-world! [[{:type :sea :contents {:type :battleship :owner :computer :hits 8
                                                       :escort-id 1 :escort-mode :seeking}}
                                {:type :sea}
                                {:type :sea}
                                {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                       :carrier-id 1 :carrier-mode :holding
                                                       :group-battleship-id nil
                                                       :group-submarine-ids []}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (ship/process-ship [0 0] :battleship)
      ;; Battleship should have adopted carrier and moved toward it
      (let [bb (get-in (test-utils/read-test-state :game-map) [0 1 :contents])
            carrier (get-in (test-utils/read-test-state :game-map) [0 3 :contents])]
        (should= :battleship (:type bb))
        (should= :intercepting (:escort-mode bb))
        (should= 1 (:escort-carrier-id bb))
        (should= 1 (:group-battleship-id carrier))))

    (it "intercepting escort transitions to orbiting when at radius 2"
      ;; Battleship at [0,0], carrier at [0,2] (Chebyshev distance 2)
      (set-test-world! [[{:type :sea :contents {:type :battleship :owner :computer :hits 8
                                                       :escort-id 1 :escort-mode :intercepting
                                                       :escort-carrier-id 1 :orbit-angle 0}}
                                {:type :sea}
                                {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                       :carrier-id 1 :carrier-mode :holding
                                                       :group-battleship-id 1
                                                       :group-submarine-ids []}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (ship/process-ship [0 0] :battleship)
      ;; Should transition to orbiting
      (let [bb (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
        (should= :orbiting (:escort-mode bb))))

    (it "orbiting escort advances along ring"
      ;; 5x5 all-sea map. Carrier at [2,2], battleship at [0,0] (orbit angle 0 = [-2,-2])
      (let [game-map (build-test-map ["~~~~~"
                                       "~~~~~"
                                       "~~~~~"
                                       "~~~~~"
                                       "~~~~~"])]
        (set-test-world! game-map)
        (set-test-computer-map! game-map)
        (update-test-world! assoc-in [2 2 :contents]
               {:type :carrier :owner :computer :hits 8
                :carrier-id 1 :carrier-mode :holding
                :group-battleship-id 1 :group-submarine-ids []})
        (update-test-world! assoc-in [0 0 :contents]
               {:type :battleship :owner :computer :hits 8
                :escort-id 1 :escort-mode :orbiting
                :escort-carrier-id 1 :orbit-angle 0})
        (ship/process-ship [0 0] :battleship)
        ;; Should have moved from [0,0] to next orbit position
        ;; Orbit angle 0 = [-2,-2] = [0,0] relative to carrier at [2,2]
        ;; Next valid angle 1 = [-2,-1] = [0,1]
        (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))
        (should= :battleship (get-in (test-utils/read-test-state :game-map) [0 1 :contents :type]))
        (should= 1 (get-in (test-utils/read-test-state :game-map) [0 1 :contents :orbit-angle]))))

    (it "escort reverts to seeking when carrier is destroyed"
      ;; Battleship orbiting, no carrier on map
      (set-test-world! [[{:type :sea :contents {:type :battleship :owner :computer :hits 8
                                                       :escort-id 1 :escort-mode :orbiting
                                                       :escort-carrier-id 99 :orbit-angle 3}}
                                {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (ship/process-ship [0 0] :battleship)
      (let [bb (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
        (should= :seeking (:escort-mode bb))
        (should-be-nil (:escort-carrier-id bb))))

    (it "seeking submarine adopts carrier with open submarine slot"
      ;; Submarine at [0,0], carrier at [0,3] with 0 subs
      (set-test-world! [[{:type :sea :contents {:type :submarine :owner :computer :hits 2
                                                       :escort-id 2 :escort-mode :seeking}}
                                {:type :sea}
                                {:type :sea}
                                {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                       :carrier-id 1 :carrier-mode :holding
                                                       :group-battleship-id nil
                                                       :group-submarine-ids []}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (ship/process-ship [0 0] :submarine)
      (let [sub (get-in (test-utils/read-test-state :game-map) [0 1 :contents])
            carrier (get-in (test-utils/read-test-state :game-map) [0 3 :contents])]
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
        (set-test-world! game-map)
        (set-test-computer-map! game-map)
        (update-test-world! assoc-in [3 3 :contents]
               {:type :carrier :owner :computer :hits 8
                :carrier-id 1 :carrier-mode :holding
                :group-battleship-id 1 :group-submarine-ids []})
        (update-test-world! assoc-in [1 1 :contents]
               {:type :battleship :owner :computer :hits 8
                :escort-id 1 :escort-mode :orbiting
                :escort-carrier-id 1 :orbit-angle 0})
        (update-test-world! assoc-in [3 4 :contents]
               {:type :transport :owner :player :hits 3})
        (set-test-computer-map! (test-utils/read-test-state :game-map))
        (ship/process-ship [1 1] :battleship)
        ;; Battleship should enter pursuit mode
        (let [bb (first (for [c (range 7) r (range 7)
                              :let [unit (get-in (test-utils/read-test-state :game-map) [c r :contents])]
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
        (set-test-world! game-map)
        (set-test-computer-map! game-map)
        (update-test-world! assoc-in [2 2 :contents]
               {:type :carrier :owner :computer :hits 8
                :carrier-id 1 :carrier-mode :holding
                :group-battleship-id 1 :group-submarine-ids []})
        (update-test-world! assoc-in [0 0 :contents]
               {:type :battleship :owner :computer :hits 8
                :escort-id 1 :escort-mode :pursuing
                :escort-carrier-id 1
                :pursuit-target [4 4] :pursuit-steps-remaining 1})
        (ship/process-ship [0 0] :battleship)
        (let [bb (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
          (should= :orbiting (:escort-mode bb))
          (should-be-nil (:pursuit-target bb)))))) ;; end carrier group escort

  (context "carrier holding mode (L187-193)"
    (it "holding carrier with invalid pair switches to repositioning"
      ;; Carrier holding with pair #{[0 0] [0 4]}, but [0 4] is now player city
      (set-test-world! [[{:type :city :city-status :computer}
                                {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                       :carrier-id 1 :carrier-mode :holding
                                                       :carrier-pair #{[0 0] [0 4]}}}
                                {:type :sea}
                                {:type :sea}
                                {:type :city :city-status :player}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (ship/process-ship [0 1] :carrier)
      (let [unit (get-in (test-utils/read-test-state :game-map) [0 1 :contents])]
        (should= :repositioning (:carrier-mode unit))
        (should-be-nil (:carrier-pair unit))))

    (it "holding carrier with valid pair stays in holding"
      ;; Both cities still computer-owned
      (set-test-world! [[{:type :city :city-status :computer}
                                {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                       :carrier-id 1 :carrier-mode :holding
                                                       :carrier-pair #{[0 0] [0 3]}}}
                                {:type :sea}
                                {:type :city :city-status :computer}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (ship/process-ship [0 1] :carrier)
      (should= :holding (get-in (test-utils/read-test-state :game-map) [0 1 :contents :carrier-mode]))))
) ;; end process-ship

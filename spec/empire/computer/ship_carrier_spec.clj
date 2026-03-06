(ns empire.computer.ship-carrier-spec
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

(describe "carrier positioning helpers"
  (before (reset-all-atoms!))

  (context "compute-distant-city-pairs"
    (it "returns empty set when no computer cities"
      (set-test-world! (build-test-map ["~~~" "###"]))
      (should (empty? (ship/compute-distant-city-pairs))))

    (it "returns empty set when only one computer city"
      (set-test-world! (build-test-map ["X~~" "###"]))
      (should (empty? (ship/compute-distant-city-pairs))))

    (it "returns empty set when cities are close (distance <= 32)"
      ;; Two cities 10 apart (< 32)
      (set-test-world! (build-test-map ["X~~~~~~~~~X" "###########"]))
      (should (empty? (ship/compute-distant-city-pairs))))

    (it "returns pair when cities are distant (distance > 32)"
      ;; X at 0, X at 36 = distance 36 > 32
      (set-test-world! (build-test-map ["X~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~X"
                                              "#####################################"]))
      (let [pairs (ship/compute-distant-city-pairs)]
        (should= 1 (count pairs))
        (should= #{[0 0] [36 0]} (first pairs))))

    (it "returns multiple pairs when multiple distant city combinations exist"
      ;; 80 characters: X at 0, X at 40, X at 79
      ;; Distances: 0-40=40, 40-79=39, 0-79=79 - all > 32
      (let [row (str "X" (apply str (repeat 39 \~)) "X" (apply str (repeat 38 \~)) "X")]
        (set-test-world! (build-test-map [row (apply str (repeat 80 \#))]))
        (let [pairs (ship/compute-distant-city-pairs)]
          (should= 3 (count pairs)))))

    (it "ignores player cities"
      ;; O is player city, X is computer city - only one computer city
      (set-test-world! (build-test-map ["O~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~X"
                                              "#####################################"]))
      (should (empty? (ship/compute-distant-city-pairs)))))

  (context "update-distant-city-pairs!"
    (it "updates the distant-city-pairs atom"
      (set-test-world! (build-test-map ["X~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~X"
                                              "#####################################"]))
      (ship/update-distant-city-pairs!)
      (should= 1 (count (test-utils/read-test-state :distant-city-pairs)))
      (should= #{[0 0] [36 0]} (first (test-utils/read-test-state :distant-city-pairs)))))

  (context "find-reserved-pairs"
    (it "returns empty set when no carriers"
      (set-test-world! (build-test-map ["~~~" "###"]))
      (should (empty? (ship/find-reserved-pairs))))

    (it "returns empty set when carrier has no pair assigned"
      (set-test-world! (build-test-map ["~c~" "###"]))
      (set-test-unit (test-utils/game-map-atom) "c" :carrier-mode :positioning)
      (should (empty? (ship/find-reserved-pairs))))

    (it "returns pair from positioning carrier"
      (set-test-world! (build-test-map ["~c~" "###"]))
      (set-test-unit (test-utils/game-map-atom) "c" :carrier-mode :positioning
                     :carrier-pair #{[0 0] [50 0]})
      (let [pairs (ship/find-reserved-pairs)]
        (should= 1 (count pairs))
        (should= #{[0 0] [50 0]} (first pairs))))

    (it "returns pair from holding carrier"
      (set-test-world! (build-test-map ["~c~" "###"]))
      (set-test-unit (test-utils/game-map-atom) "c" :carrier-mode :holding
                     :carrier-pair #{[0 0] [50 0]})
      (let [pairs (ship/find-reserved-pairs)]
        (should= 1 (count pairs))
        (should= #{[0 0] [50 0]} (first pairs))))

    (it "returns multiple pairs from multiple carriers"
      (set-test-world! (build-test-map ["~c~c~" "#####"]))
      (set-test-unit (test-utils/game-map-atom) "c1" :carrier-mode :holding
                     :carrier-pair #{[0 0] [50 0]})
      (set-test-unit (test-utils/game-map-atom) "c2" :carrier-mode :positioning
                     :carrier-pair #{[10 0] [60 0]})
      (let [pairs (ship/find-reserved-pairs)]
        (should= 2 (count pairs))))

    (it "ignores player carriers"
      (set-test-world! (build-test-map ["~C~" "###"]))
      (set-test-unit (test-utils/game-map-atom) "C" :carrier-mode :holding
                     :carrier-pair #{[0 0] [50 0]})
      (should (empty? (ship/find-reserved-pairs)))))

  (context "find-unreserved-pair"
    (it "returns nil when no distant city pairs exist"
      (set-test-world! (build-test-map ["X~~~~~~~~~X" "###########"]))
      (ship/update-distant-city-pairs!)
      (should-be-nil (ship/find-unreserved-pair)))

    (it "returns a pair when distant pair exists and none reserved"
      (set-test-world! (build-test-map ["X~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~X"
                                              "#####################################"]))
      (ship/update-distant-city-pairs!)
      (let [pair (ship/find-unreserved-pair)]
        (should= #{[0 0] [36 0]} pair)))

    (it "returns nil when all distant pairs are reserved"
      (set-test-world! (build-test-map ["X~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~Xc"
                                              "######################################"]))
      (set-test-unit (test-utils/game-map-atom) "c" :carrier-mode :holding
                     :carrier-pair #{[0 0] [35 0]})
      (ship/update-distant-city-pairs!)
      (should-be-nil (ship/find-unreserved-pair)))

    (it "returns unreserved pair when some pairs are reserved"
      ;; 80 chars: X at 0, X at 40, X at 79
      ;; Three pairs, reserve one
      (let [row (str "X" (apply str (repeat 39 \~)) "X" (apply str (repeat 37 \~)) "Xc")]
        (set-test-world! (build-test-map [row (apply str (repeat 81 \#))])))
      (set-test-unit (test-utils/game-map-atom) "c" :carrier-mode :holding
                     :carrier-pair #{[0 0] [40 0]})
      (ship/update-distant-city-pairs!)
      (let [pair (ship/find-unreserved-pair)]
        (should-not-be-nil pair)
        (should-not= #{[0 0] [40 0]} pair))))

  (context "find-position-between-cities"
    (it "returns midpoint position when cities are in straight line"
      ;; X at 0, X at 36 - midpoint is 18
      (set-test-world! (build-test-map ["X~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~X"
                                              "####################################"]))
      (let [pos (ship/find-position-between-cities #{[0 0] [35 0]})]
        (should-not-be-nil pos)
        ;; Should be sea cell
        (should= :sea (:type (get-in (test-utils/read-test-state :game-map) pos)))
        ;; Should be within fighter-fuel distance of both cities
        (let [[c1 c2] (vec #{[0 0] [35 0]})]
          (should (<= (core/distance pos c1) config/fighter-fuel))
          (should (<= (core/distance pos c2) config/fighter-fuel)))))

    (it "returns nil when no sea position reachable from both cities"
      ;; Cities separated by land, no valid path
      (set-test-world! (build-test-map ["X####################################X"
                                              "######################################"]))
      (should-be-nil (ship/find-position-between-cities #{[0 0] [37 0]})))

    (it "finds position when midpoint is blocked by land"
      ;; X at 0, X at 40 - midpoint area has some land, should find nearby sea
      (let [row (str "X" (apply str (repeat 19 \~)) "#" (apply str (repeat 19 \~)) "X")]
        (set-test-world! (build-test-map [row (apply str (repeat 41 \#))])))
      (let [pos (ship/find-position-between-cities #{[0 0] [40 0]})]
        (should-not-be-nil pos)
        (should= :sea (:type (get-in (test-utils/read-test-state :game-map) pos)))
        ;; Should be within fighter-fuel of both
        (should (<= (core/distance pos [0 0]) config/fighter-fuel))
        (should (<= (core/distance pos [40 0]) config/fighter-fuel))))))

;; === Mutation-killing tests ===

(describe "mutation: ship combat and movement"
  (before (reset-all-atoms!))

  (context "attack-enemy (L49, L52)"
    (it "winning attacker occupies enemy position"
      (set-test-world! [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3 :destroyer-id 1}}
                                {:type :sea :contents {:type :patrol-boat :owner :player :hits 1}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (with-redefs [combat/resolve-combat
                    (fn [a _d] {:winner :attacker :survivor a})
                    combat/clear-escort-on-death (fn [_] nil)]
        (ship/process-ship [0 0] :destroyer))
      (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))
      (should= :computer (get-in (test-utils/read-test-state :game-map) [0 1 :contents :owner])))

    (it "losing attacker updates defender to combat survivor"
      (set-test-world! [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3}}
                                {:type :sea :contents {:type :battleship :owner :player :hits 8}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (with-redefs [combat/resolve-combat
                    (fn [_a _d] {:winner :defender :survivor {:type :battleship :owner :player :hits 7}})
                    combat/clear-escort-on-death (fn [_] nil)]
        (ship/process-ship [0 0] :destroyer))
      (should= :player (get-in (test-utils/read-test-state :game-map) [0 1 :contents :owner]))
      (should= :battleship (get-in (test-utils/read-test-state :game-map) [0 1 :contents :type]))
      (should= 7 (get-in (test-utils/read-test-state :game-map) [0 1 :contents :hits])))

    (it "dead-unit is defender when attacker wins (L49)"
      (set-test-world! [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3
                                                       :destroyer-id 1 :escort-mode :escorting
                                                       :escort-transport-id 1}}
                                {:type :sea :contents {:type :patrol-boat :owner :player :hits 1}}
                                {:type :sea :contents {:type :transport :owner :computer :hits 3
                                                       :transport-id 1 :escort-destroyer-id 1
                                                       :transport-mission :loading :army-count 0}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (with-redefs [combat/resolve-combat
                    (fn [a _d] {:winner :attacker :survivor a})]
        (ship/process-ship [0 0] :destroyer))
      (should= 1 (get-in (test-utils/read-test-state :game-map) [0 2 :contents :escort-destroyer-id]))))

  (context "passable neighbors exclude computer units (L27)"
    (it "destroyer does not overwrite own submarine when hunting"
      ;; Only neighbor of [0,0] is [0,1] (computer sub). Player ship visible at [0,3].
      ;; Original: computer sub NOT passable -> can't move. Mutation: passable -> overwrites sub.
      (set-test-world! [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3}}
                                {:type :sea :contents {:type :submarine :owner :computer :hits 2}}
                                {:type :sea}
                                {:type :sea :contents {:type :patrol-boat :owner :player :hits 1}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (ship/process-ship [0 0] :destroyer)
      (should= :submarine (get-in (test-utils/read-test-state :game-map) [0 1 :contents :type]))))

  (context "no retreat when not threatened (L113)"
    (it "undamaged ship attacks instead of retreating"
      (set-test-world! [[{:type :sea}
                                {:type :sea :contents {:type :destroyer :owner :computer :hits 3}}
                                {:type :sea :contents {:type :patrol-boat :owner :player :hits 1}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (with-redefs [threat/should-retreat? (constantly false)
                    threat/retreat-move (constantly [0 0])
                    combat/resolve-combat
                    (fn [a _d] {:winner :attacker :survivor a})
                    combat/clear-escort-on-death (fn [_] nil)]
        (ship/process-ship [0 1] :destroyer))
      (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))
      (should= :computer (get-in (test-utils/read-test-state :game-map) [0 2 :contents :owner]))))

  (context "escorting distance boundary (L450)"
    (it "escorting destroyer at distance 1 stays put"
      (set-test-world! [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3
                                                       :destroyer-id 1 :escort-mode :escorting
                                                       :escort-transport-id 1}}
                                {:type :sea :contents {:type :transport :owner :computer :hits 3
                                                       :transport-id 1 :escort-destroyer-id 1
                                                       :transport-mission :loading :army-count 0}}
                                {:type :sea}]
                               [{:type :sea} {:type :sea} {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (ship/process-ship [0 0] :destroyer)
      (should= :destroyer (get-in (test-utils/read-test-state :game-map) [0 0 :contents :type]))))

  (context "legacy escort distance boundary (L902)"
    (it "destroyer at distance 2 from transport explores instead of following"
      (set-test-world! [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3}}
                                {:type :sea}
                                {:type :sea :contents {:type :transport :owner :computer
                                                       :army-count 3 :hits 3}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (ship/process-ship [0 0] :destroyer)
      (should= :destroyer (get-in (test-utils/read-test-state :game-map) [0 0 :contents :type])))))

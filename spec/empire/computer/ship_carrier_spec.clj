(ns empire.computer.ship-carrier-spec
  "Tests for VMS Empire style computer ship movement - carrier group escort, positioning helpers, mutation: ship combat."
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

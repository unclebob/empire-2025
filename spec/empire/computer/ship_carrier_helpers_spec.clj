(ns empire.computer.ship-carrier-helpers-spec
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
(describe "carrier positioning helpers"
  (before (reset-all-atoms!))

  (context "compute-distant-city-pairs"
    (it "returns empty set when no computer cities"
      (set-test-world! (build-test-map ["~~~" "###"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (should (empty? (ship/compute-distant-city-pairs))))

    (it "returns empty set when only one computer city"
      (set-test-world! (build-test-map ["X~~" "###"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (should (empty? (ship/compute-distant-city-pairs))))

    (it "returns empty set when cities are close (distance <= 32)"
      ;; Two cities 10 apart (< 32)
      (set-test-world! (build-test-map ["X~~~~~~~~~X" "###########"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (should (empty? (ship/compute-distant-city-pairs))))

    (it "returns pair when cities are distant (distance > 32)"
      ;; X at 0, X at 36 = distance 36 > 32
      (set-test-world! (build-test-map ["X~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~X"
                                              "#####################################"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [pairs (ship/compute-distant-city-pairs)]
        (should= 1 (count pairs))
        (should= #{[0 0] [36 0]} (first pairs))))

    (it "returns multiple pairs when multiple distant city combinations exist"
      ;; 80 characters: X at 0, X at 40, X at 79
      ;; Distances: 0-40=40, 40-79=39, 0-79=79 - all > 32
      (let [row (str "X" (apply str (repeat 39 \~)) "X" (apply str (repeat 38 \~)) "X")]
        (set-test-world! (build-test-map [row (apply str (repeat 80 \#))]))
        (set-test-computer-map! (test-utils/read-test-state :game-map))
        (let [pairs (ship/compute-distant-city-pairs)]
          (should= 3 (count pairs)))))

    (it "ignores player cities"
      ;; O is player city, X is computer city - only one computer city
      (set-test-world! (build-test-map ["O~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~X"
                                              "#####################################"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (should (empty? (ship/compute-distant-city-pairs)))))

    (it "ignores computer cities hidden from computer-map"
      (set-test-world! (build-test-map ["X~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~X"
                                        "#####################################"]))
      (set-test-computer-map! [(vec (concat [{:type :city :city-status :computer}]
                                            (repeat 35 {:type :sea})
                                            [nil]))
                               (vec (repeat 37 {:type :land}))])
      (should (empty? (ship/compute-distant-city-pairs)))))

  (context "update-distant-city-pairs!"
    (it "updates the distant-city-pairs atom"
      (set-test-world! (build-test-map ["X~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~X"
                                              "#####################################"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (ship/update-distant-city-pairs!)
      (should= 1 (count (test-utils/read-test-state :distant-city-pairs)))
      (should= #{[0 0] [36 0]} (first (test-utils/read-test-state :distant-city-pairs)))))

  (context "find-reserved-pairs"
    (it "returns empty set when no carriers"
      (set-test-world! (build-test-map ["~~~" "###"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (should (empty? (ship/find-reserved-pairs))))

    (it "returns empty set when carrier has no pair assigned"
      (set-test-world! (build-test-map ["~c~" "###"]))
      (set-test-unit (test-utils/game-map-atom) "c" :carrier-mode :positioning)
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (should (empty? (ship/find-reserved-pairs))))

    (it "returns pair from positioning carrier"
      (set-test-world! (build-test-map ["~c~" "###"]))
      (set-test-unit (test-utils/game-map-atom) "c" :carrier-mode :positioning
                     :carrier-pair #{[0 0] [50 0]})
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [pairs (ship/find-reserved-pairs)]
        (should= 1 (count pairs))
        (should= #{[0 0] [50 0]} (first pairs))))

    (it "returns pair from holding carrier"
      (set-test-world! (build-test-map ["~c~" "###"]))
      (set-test-unit (test-utils/game-map-atom) "c" :carrier-mode :holding
                     :carrier-pair #{[0 0] [50 0]})
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [pairs (ship/find-reserved-pairs)]
        (should= 1 (count pairs))
        (should= #{[0 0] [50 0]} (first pairs))))

    (it "returns multiple pairs from multiple carriers"
      (set-test-world! (build-test-map ["~c~c~" "#####"]))
      (set-test-unit (test-utils/game-map-atom) "c1" :carrier-mode :holding
                     :carrier-pair #{[0 0] [50 0]})
      (set-test-unit (test-utils/game-map-atom) "c2" :carrier-mode :positioning
                     :carrier-pair #{[10 0] [60 0]})
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [pairs (ship/find-reserved-pairs)]
        (should= 2 (count pairs))))

    (it "ignores player carriers"
      (set-test-world! (build-test-map ["~C~" "###"]))
      (set-test-unit (test-utils/game-map-atom) "C" :carrier-mode :holding
                     :carrier-pair #{[0 0] [50 0]})
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (should (empty? (ship/find-reserved-pairs))))

    (it "ignores carriers hidden from computer-map"
      (set-test-world! (build-test-map ["~c~" "###"]))
      (set-test-unit (test-utils/game-map-atom) "c" :carrier-mode :holding
                     :carrier-pair #{[0 0] [50 0]})
      (set-test-computer-map! [[{:type :sea} nil {:type :sea}]
                               [{:type :land} {:type :land} {:type :land}]])
      (should (empty? (ship/find-reserved-pairs)))))

  (context "find-unreserved-pair"
    (it "returns nil when no distant city pairs exist"
      (set-test-world! (build-test-map ["X~~~~~~~~~X" "###########"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (ship/update-distant-city-pairs!)
      (should-be-nil (ship/find-unreserved-pair)))

    (it "returns a pair when distant pair exists and none reserved"
      (set-test-world! (build-test-map ["X~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~X"
                                              "#####################################"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (ship/update-distant-city-pairs!)
      (let [pair (ship/find-unreserved-pair)]
        (should= #{[0 0] [36 0]} pair)))

    (it "returns nil when all distant pairs are reserved"
      (set-test-world! (build-test-map ["X~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~Xc"
                                              "######################################"]))
      (set-test-unit (test-utils/game-map-atom) "c" :carrier-mode :holding
                     :carrier-pair #{[0 0] [35 0]})
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (ship/update-distant-city-pairs!)
      (should-be-nil (ship/find-unreserved-pair)))

    (it "returns unreserved pair when some pairs are reserved"
      ;; 80 chars: X at 0, X at 40, X at 79
      ;; Three pairs, reserve one
      (let [row (str "X" (apply str (repeat 39 \~)) "X" (apply str (repeat 37 \~)) "Xc")]
        (set-test-world! (build-test-map [row (apply str (repeat 81 \#))])))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
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
      (set-test-computer-map! (test-utils/read-test-state :game-map))
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
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (should-be-nil (ship/find-position-between-cities #{[0 0] [37 0]})))

    (it "finds position when midpoint is blocked by land"
      ;; X at 0, X at 40 - midpoint area has some land, should find nearby sea
      (let [row (str "X" (apply str (repeat 19 \~)) "#" (apply str (repeat 19 \~)) "X")]
        (set-test-world! (build-test-map [row (apply str (repeat 41 \#))])))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [pos (ship/find-position-between-cities #{[0 0] [40 0]})]
        (should-not-be-nil pos)
        (should= :sea (:type (get-in (test-utils/read-test-state :game-map) pos)))
        ;; Should be within fighter-fuel of both
        (should (<= (core/distance pos [0 0]) config/fighter-fuel))
        (should (<= (core/distance pos [40 0]) config/fighter-fuel))))

    (it "returns nil when midpoint sea is hidden on computer-map"
      (let [cells (vec (for [j (range 60)]
                         (cond
                           (= j 0) {:type :city :city-status :computer}
                           (= j 59) {:type :city :city-status :computer}
                           :else {:type :sea})))
            computer-cells (vec (map-indexed (fn [j cell]
                                               (if (<= 11 j 48) nil cell))
                                             cells))]
        (set-test-world! [cells])
        (set-test-computer-map! [computer-cells])
        (should-be-nil (ship/find-position-between-cities #{[0 0] [0 59]})))))

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

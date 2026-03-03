(ns empire.computer.ship-pursuit-spec
  "Tests for VMS Empire style computer ship movement - pursue-and-kill, carrier positioning, find-carrier-position."
  (:require [empire.test-utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.ship :as ship]
            [empire.computer.core :as core]
            [empire.config :as config]
            [empire.test-utils :refer [build-test-map reset-all-atoms! set-test-player-map! set-test-computer-map! set-test-unit set-test-world! update-test-world!]]
            [empire.containers.helpers :as uc]
            [empire.combat :as combat]
            [empire.computer.threat :as threat]))

(describe "process-ship"
  (before (reset-all-atoms!))

  (context "pursue-and-kill behavior"
    (it "escorting destroyer pursues when transport spots enemy"
      ;; Destroyer at [0 0] escorting transport at [0 2].
      ;; Player sub at [0 3] — adjacent to transport, not to destroyer.
      ;; Destroyer should detect sighting via transport and pursue.
      (set-test-world! [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3
                                                       :destroyer-id 1 :escort-mode :escorting
                                                       :escort-transport-id 1}}
                                {:type :sea}
                                {:type :sea :contents {:type :transport :owner :computer :hits 3
                                                       :transport-id 1 :escort-destroyer-id 1
                                                       :transport-mission :loading :army-count 0}}
                                {:type :sea :contents {:type :submarine :owner :player :hits 2}}
                                {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (ship/process-ship [0 0] :destroyer)
      ;; Destroyer should move toward enemy and enter pursuing mode
      (let [destroyer (get-in (test-utils/read-test-state :game-map) [0 1 :contents])]
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
        (set-test-world! game-map)
        (set-test-computer-map! game-map)
        (update-test-world! assoc-in [1 2 :contents]
               {:type :transport :owner :computer :hits 3
                :transport-id 1 :transport-mission :loading :army-count 0})
        (update-test-world! assoc-in [2 2 :contents]
               {:type :destroyer :owner :computer :hits 3
                :destroyer-id 1 :escort-mode :pursuing
                :escort-transport-id 1
                :pursuit-target [3 2] :pursuit-steps-remaining 5})
        (ship/process-ship [2 2] :destroyer)
        ;; Destroyer should have moved and decremented steps
        (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [2 2])))
        (let [new-pos (first (for [c (range 5) r (range 5)
                                   :when (= :destroyer (get-in (test-utils/read-test-state :game-map) [c r :contents :type]))]
                               [c r]))
              destroyer (get-in (test-utils/read-test-state :game-map) (conj new-pos :contents))]
          (should-not-be-nil new-pos)
          (should= :pursuing (:escort-mode destroyer))
          (should= 4 (:pursuit-steps-remaining destroyer)))))

    (it "pursuing destroyer returns to escorting when steps exhausted"
      ;; Destroyer with 1 step remaining should revert to escorting
      (set-test-world! [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3
                                                       :destroyer-id 1 :escort-mode :pursuing
                                                       :escort-transport-id 1
                                                       :pursuit-target [0 3]
                                                       :pursuit-steps-remaining 1}}
                                {:type :sea}
                                {:type :sea :contents {:type :transport :owner :computer :hits 3
                                                       :transport-id 1 :transport-mission :loading
                                                       :army-count 0}}
                                {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (ship/process-ship [0 0] :destroyer)
      (let [destroyer (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
        (should= :escorting (:escort-mode destroyer))
        (should-be-nil (:pursuit-target destroyer))
        (should-be-nil (:pursuit-steps-remaining destroyer))))

    (it "pursuing destroyer ends pursuit when all candidate cells visible"
      ;; Destroyer at [1 0] pursuing target [1 1]. Transport at [1 2].
      ;; 3x3 map. All neighbors of [1 1] are visible to destroyer or transport.
      ;; No hidden cells for enemy — pursuit should end.
      (set-test-world! (build-test-map ["~~~"
                                              "~~~"
                                              "~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [1 0 :contents]
             {:type :destroyer :owner :computer :hits 3
              :destroyer-id 1 :escort-mode :pursuing
              :escort-transport-id 1
              :pursuit-target [1 1] :pursuit-steps-remaining 4})
      (update-test-world! assoc-in [1 2 :contents]
             {:type :transport :owner :computer :hits 3
              :transport-id 1 :transport-mission :loading :army-count 0})
      (ship/process-ship [1 0] :destroyer)
      ;; All neighbors of [1 1] visible to group — pursuit ends
      (let [destroyer (first (for [c (range 3) r (range 3)
                                   :let [unit (get-in (test-utils/read-test-state :game-map) [c r :contents])]
                                   :when (= :destroyer (:type unit))]
                               unit))]
        (should= :escorting (:escort-mode destroyer))
        (should-be-nil (:pursuit-target destroyer))))

    (it "pursuing destroyer attacks adjacent enemy via priority 1"
      ;; Destroyer pursuing, player sub adjacent. Priority 1 should attack.
      (set-test-world! [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3
                                                       :destroyer-id 1 :escort-mode :pursuing
                                                       :escort-transport-id 1
                                                       :pursuit-target [0 3]
                                                       :pursuit-steps-remaining 3}}
                                {:type :sea :contents {:type :submarine :owner :player :hits 2}}
                                {:type :sea}
                                {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (ship/process-ship [0 0] :destroyer)
      ;; Combat should have occurred (priority 1 attack)
      (let [cell0 (get-in (test-utils/read-test-state :game-map) [0 0])
            cell1 (get-in (test-utils/read-test-state :game-map) [0 1])]
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
        (set-test-world! [cells])
        (set-test-computer-map! [cells])
        (ship/update-distant-city-pairs!)
        (ship/process-ship [0 5] :carrier)
        ;; Carrier should have moved from [0,5] to [0,6]
        (should= :carrier (get-in (test-utils/read-test-state :game-map) [0 6 :contents :type]))))

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
        (set-test-world! [cells])
        (set-test-computer-map! [cells])
        (ship/update-distant-city-pairs!)
        (ship/process-ship [0 30] :carrier)
        (should= :holding (get-in (test-utils/read-test-state :game-map) [0 30 :contents :carrier-mode]))
        (should-be-nil (get-in (test-utils/read-test-state :game-map) [0 30 :contents :carrier-target]))))

    (it "carrier in holding mode stays put"
      (set-test-world! [[{:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                       :carrier-mode :holding}}
                                {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (ship/process-ship [0 0] :carrier)
      (should= :carrier (get-in (test-utils/read-test-state :game-map) [0 0 :contents :type]))
      (should= :holding (get-in (test-utils/read-test-state :game-map) [0 0 :contents :carrier-mode])))

    (it "positioning carrier without target finds position when distant cities exist"
      ;; Two distant cities (60 apart), carrier without target
      (let [cells (vec (for [j (range 60)]
                         (cond
                           (= j 0) {:type :city :city-status :computer}
                           (= j 5) {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                           :carrier-mode :positioning}}
                           (= j 59) {:type :city :city-status :computer}
                           :else {:type :sea})))]
        (set-test-world! [cells])
        (set-test-computer-map! [cells])
        (ship/update-distant-city-pairs!)
        (ship/process-ship [0 5] :carrier)
        ;; Carrier should have moved and gotten a target
        (let [carrier-pos (first (for [c (range 60)
                                       :when (= :carrier (get-in (test-utils/read-test-state :game-map) [0 c :contents :type]))]
                                   [0 c]))
              unit (get-in (test-utils/read-test-state :game-map) (conj carrier-pos :contents))]
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
        (set-test-world! [cells])
        (set-test-computer-map! [cells])
        (ship/update-distant-city-pairs!)
        (ship/process-ship [0 1] :carrier)
        ;; Carrier should have switched to holding (no distant pairs)
        (should= :holding (get-in (test-utils/read-test-state :game-map) [0 1 :contents :carrier-mode]))))

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
        (set-test-world! [(vec cells) (vec (repeat 60 {:type :sea}))])
        (set-test-computer-map! (test-utils/read-test-state :game-map))
        (ship/update-distant-city-pairs!)
        (ship/process-ship [0 10] :carrier)
        ;; Carrier should have moved from [0,10] (navigating around land)
        (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 10])))
        (let [new-pos (first (for [r (range 2) c (range 60)
                                   :when (= :carrier (get-in (test-utils/read-test-state :game-map) [r c :contents :type]))]
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
        (set-test-world! [cells])
        (set-test-computer-map! [cells])
        (ship/update-distant-city-pairs!)
        (ship/process-ship [0 5] :carrier)
        ;; Target [0,30] was invalid (occupied), carrier should go to holding
        ;; (no other unreserved pairs since this carrier's pair is still assigned)
        (let [carrier-pos (first (for [c (range 60)
                                       :when (= :carrier (get-in (test-utils/read-test-state :game-map) [0 c :contents :type]))]
                                   [0 c]))
              unit (get-in (test-utils/read-test-state :game-map) (conj carrier-pos :contents))]
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
        (set-test-world! [cells])
        (set-test-computer-map! [cells])
        (ship/update-distant-city-pairs!)
        (ship/process-ship [0 30] :carrier)
        ;; Carrier's pair is invalid (city [0 0] is now player's), should reposition
        (let [unit (get-in (test-utils/read-test-state :game-map) [0 30 :contents])]
          (should= :repositioning (:carrier-mode unit))
          (should-be-nil (:carrier-pair unit))))))

  (context "find-carrier-position"
    (before (reset-all-atoms!))

    (it "returns nil when only one computer city (no pairs)"
      (let [cells (vec (concat [{:type :city :city-status :computer}]
                                (repeat 39 {:type :sea})))]
        (set-test-world! [cells])
        (ship/update-distant-city-pairs!)
        (should-be-nil (ship/find-carrier-position))))

    (it "returns nil when cities are close (no distant pairs)"
      ;; Two cities 20 apart (< 32)
      (set-test-world! (build-test-map ["X~~~~~~~~~~~~~~~~~~X" "####################"]))
      (ship/update-distant-city-pairs!)
      (should-be-nil (ship/find-carrier-position)))

    (it "returns map with position and pair when distant cities exist"
      ;; Two cities 59 cells apart (> 32), needs carrier
      (let [cells (vec (for [j (range 60)]
                         (cond
                           (= j 0) {:type :city :city-status :computer}
                           (= j 59) {:type :city :city-status :computer}
                           :else {:type :sea})))]
        (set-test-world! [cells])
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
      (set-test-world! (build-test-map ["X~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~Xc"
                                              "######################################"]))
      (set-test-unit (test-utils/game-map-atom) "c" :carrier-mode :holding
                     :carrier-pair #{[0 0] [36 0]})
      (ship/update-distant-city-pairs!)
      (should-be-nil (ship/find-carrier-position))))
) ;; end process-ship

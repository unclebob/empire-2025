(ns empire.computer.ship-mutations-spec
  "Tests for VMS Empire style computer ship movement - mutation: patrol boat navigation + carrier and escort operations."
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.ship :as ship]
            [empire.config.core :as config]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-player-map! set-test-computer-map! set-test-unit set-test-world! update-test-world!]]
            [empire.game-mechanics.containers.helpers :as uc]
            [empire.game-mechanics.services.combat :as combat]
            [empire.computer.shared.threat :as threat]))

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
        (set-test-world! [row])
        (should (empty? (ship/compute-distant-city-pairs))))))

  (context "find-unreserved-pair lazy init (L511)"
    (it "initializes distant-city-pairs when nil"
      (set-test-world! (build-test-map ["X~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~X"
                                              "#####################################"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (test-utils/set-test-state! :distant-city-pairs nil)
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
        (set-test-world! game-map)
        (set-test-computer-map! (test-utils/read-test-state :game-map))
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
        (set-test-world! [col])
        (set-test-computer-map! (test-utils/read-test-state :game-map))
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
        (set-test-world! [row])
        (set-test-computer-map! (test-utils/read-test-state :game-map))
        (let [pos (ship/find-position-between-cities #{[0 0] [0 (* 2 fuel)]})]
          (should-not-be-nil pos)
          (should= [0 fuel] pos)))))

  (context "find-refueling-sites (L547-L551)"
    (it "includes computer cities"
      (set-test-world! [[{:type :city :city-status :computer} {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (should= [[0 0]] (ship/find-refueling-sites)))

    (it "excludes player cities"
      (set-test-world! [[{:type :city :city-status :player} {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (should (empty? (ship/find-refueling-sites))))

    (it "includes holding computer carriers"
      (set-test-world! [[{:type :sea :contents {:type :carrier :owner :computer
                                                       :carrier-mode :holding}} {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (should= [[0 0]] (ship/find-refueling-sites)))

    (it "includes positioning computer carriers"
      (set-test-world! [[{:type :sea :contents {:type :carrier :owner :computer
                                                       :carrier-mode :positioning}} {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (should= [[0 0]] (ship/find-refueling-sites)))

    (it "excludes player carriers"
      (set-test-world! [[{:type :sea :contents {:type :carrier :owner :player
                                                       :carrier-mode :holding}} {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
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
        (set-test-world! [cells])
        (set-test-computer-map! [cells])
        (ship/update-distant-city-pairs!)
        (ship/process-ship [0 30] :carrier)
        (should= :holding (get-in (test-utils/read-test-state :game-map) [0 30 :contents :carrier-mode]))))

    (it "holding carrier repositions when paired city is hidden on computer-map"
      (let [cells (vec (for [j (range 60)]
                         (cond
                           (= j 0) {:type :city :city-status :computer}
                           (= j 30) {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                            :carrier-mode :holding
                                                            :carrier-pair #{[0 0] [0 59]}}}
                           (= j 59) {:type :city :city-status :computer}
                           :else {:type :sea})))]
        (set-test-world! [cells])
        (set-test-computer-map! [(vec (for [j (range 60)]
                                        (if (= j 59) nil (nth cells j))))])
        (ship/process-ship [0 30] :carrier)
        (should= :repositioning
                 (get-in (test-utils/read-test-state :game-map) [0 30 :contents :carrier-mode])))))

  (context "carrier submarine slot cap (L689)"
    (it "submarine does not adopt carrier with 2 existing subs"
      (set-test-world! [[{:type :sea :contents {:type :submarine :owner :computer :hits 2
                                                       :escort-id 3 :escort-mode :seeking}}
                                {:type :sea}
                                {:type :sea}
                                {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                       :carrier-id 1 :carrier-mode :holding
                                                       :group-battleship-id nil
                                                       :group-submarine-ids [1 2]}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (ship/process-ship [0 0] :submarine)
      (let [sub (first (for [c (range 4)
                             :let [unit (get-in (test-utils/read-test-state :game-map) [0 c :contents])]
                             :when (= :submarine (:type unit))]
                         unit))]
        (should= :seeking (:escort-mode sub)))))

  (context "initial-orbit-angle (L698, L700)"
    (it "battleship starts with orbit-angle 0"
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
      (let [bb (first (for [c (range 4)
                            :let [unit (get-in (test-utils/read-test-state :game-map) [0 c :contents])]
                            :when (= :battleship (:type unit))]
                        unit))]
        (should= 0 (:orbit-angle bb))))

    (it "first submarine starts with orbit-angle 5"
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
      (let [sub (first (for [c (range 4)
                             :let [unit (get-in (test-utils/read-test-state :game-map) [0 c :contents])]
                             :when (= :submarine (:type unit))]
                         unit))]
        (should= 5 (:orbit-angle sub))))

    (it "second submarine starts with orbit-angle 11"
      (set-test-world! [[{:type :sea :contents {:type :submarine :owner :computer :hits 2
                                                       :escort-id 3 :escort-mode :seeking}}
                                {:type :sea}
                                {:type :sea}
                                {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                       :carrier-id 1 :carrier-mode :holding
                                                       :group-battleship-id nil
                                                       :group-submarine-ids [2]}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (ship/process-ship [0 0] :submarine)
      (let [sub (first (for [c (range 4)
                             :let [unit (get-in (test-utils/read-test-state :game-map) [0 c :contents])]
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
        (set-test-world! game-map)
        (set-test-computer-map! game-map)
        (update-test-world! assoc-in [3 3 :contents]
               {:type :carrier :owner :computer :hits 8
                :carrier-id 1 :carrier-mode :holding
                :group-battleship-id 1 :group-submarine-ids []})
        (update-test-world! assoc-in [1 3 :contents]
               {:type :battleship :owner :computer :hits 8
                :escort-id 1 :escort-mode :orbiting
                :escort-carrier-id 1 :orbit-angle 2})
        (set-test-computer-map! (test-utils/read-test-state :game-map))
        (ship/process-ship [1 3] :battleship)
        (let [bb (first (for [c (range 7) r (range 7)
                              :let [unit (get-in (test-utils/read-test-state :game-map) [c r :contents])]
                              :when (= :battleship (:type unit))]
                          unit))]
          (should= 4 (:orbit-angle bb)))))))

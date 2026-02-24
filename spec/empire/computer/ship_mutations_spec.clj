(ns empire.computer.ship-mutations-spec
  "Tests for VMS Empire style computer ship movement - mutation: patrol boat navigation + carrier and escort operations."
  (:require [speclj.core :refer :all]
            [empire.computer.ship :as ship]
            [empire.computer.core :as core]
            [empire.config :as config]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [build-test-map reset-all-atoms! set-test-unit]]
            [empire.containers.helpers :as uc]
            [empire.combat :as combat]
            [empire.computer.threat :as threat]))

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

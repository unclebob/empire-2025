(ns empire.computer.ship-carrier-group-spec
  (:require [empire.test-utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.ship-carrier-group :as cg]
            [empire.computer.ship :as ship]
            [empire.computer.ship-core :as ship-core]
            [empire.computer.core :as core]
            [empire.test-utils :refer [build-test-map reset-all-atoms! set-test-player-map! set-test-computer-map! set-test-world! update-test-world!]]
            [empire.movement.visibility :as visibility]))

(describe "ship-carrier-group"
  (before (reset-all-atoms!))

  (context "find-carrier-with-open-slot default case (L28)"
    (it "destroyer does not adopt carrier (non-battleship/submarine)"
      (set-test-world! [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3
                                                 :escort-id 1 :escort-mode :seeking}}
                         {:type :sea}
                         {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                :carrier-id 1 :carrier-mode :holding
                                                :group-battleship-id nil
                                                :group-submarine-ids []}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (cg/process-carrier-group-escort [0 0] :destroyer)
      (let [unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
        (should= :seeking (:escort-mode unit)))))

  (context "submarine slot cap (L30)"
    (it "submarine with exactly 2 subs already does not adopt"
      (set-test-world! [[{:type :sea :contents {:type :submarine :owner :computer :hits 2
                                                 :escort-id 3 :escort-mode :seeking}}
                         {:type :sea}
                         {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                :carrier-id 1 :carrier-mode :holding
                                                :group-battleship-id nil
                                                :group-submarine-ids [1 2]}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (cg/process-carrier-group-escort [0 0] :submarine)
      (let [sub (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
        (should= :seeking (:escort-mode sub))))

    (it "submarine with 1 sub already can adopt"
      (set-test-world! [[{:type :sea :contents {:type :submarine :owner :computer :hits 2
                                                 :escort-id 3 :escort-mode :seeking}}
                         {:type :sea}
                         {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                :carrier-id 1 :carrier-mode :holding
                                                :group-battleship-id nil
                                                :group-submarine-ids [1]}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (cg/process-carrier-group-escort [0 0] :submarine)
      (let [sub (first (for [c (range 3)
                             :let [unit (get-in (test-utils/read-test-state :game-map) [0 c :contents])]
                             :when (= :submarine (:type unit))]
                         unit))]
        (should= :intercepting (:escort-mode sub)))))

  (context "initial-orbit-angle (L39)"
    (it "battleship gets orbit-angle 0"
      (let [initial-orbit-angle #'empire.computer.ship-carrier-group/initial-orbit-angle
            carrier {:carrier-id 1 :group-submarine-ids []}]
        (should= 0 (initial-orbit-angle :battleship carrier))))

    (it "first submarine gets orbit-angle 5"
      (set-test-world! [[{:type :sea :contents {:type :submarine :owner :computer :hits 2
                                                 :escort-id 2 :escort-mode :seeking}}
                         {:type :sea}
                         {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                :carrier-id 1 :carrier-mode :holding
                                                :group-battleship-id nil
                                                :group-submarine-ids []}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (cg/process-carrier-group-escort [0 0] :submarine)
      (let [sub (first (for [c (range 3)
                             :let [unit (get-in (test-utils/read-test-state :game-map) [0 c :contents])]
                             :when (= :submarine (:type unit))]
                         unit))]
        (should= 5 (:orbit-angle sub))))

    (it "second submarine gets orbit-angle 11"
      (set-test-world! [[{:type :sea :contents {:type :submarine :owner :computer :hits 2
                                                 :escort-id 3 :escort-mode :seeking}}
                         {:type :sea}
                         {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                :carrier-id 1 :carrier-mode :holding
                                                :group-battleship-id nil
                                                :group-submarine-ids [2]}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (cg/process-carrier-group-escort [0 0] :submarine)
      (let [sub (first (for [c (range 3)
                             :let [unit (get-in (test-utils/read-test-state :game-map) [0 c :contents])]
                             :when (= :submarine (:type unit))]
                         unit))]
        (should= 11 (:orbit-angle sub)))))

  (context "orbit-target-pos (L67)"
    (it "computes correct orbit position from angle"
      (let [orbit-target-pos #'empire.computer.ship-carrier-group/orbit-target-pos]
        (should= [1 1] (orbit-target-pos [3 3] 0))
        (should= [1 4] (orbit-target-pos [3 3] 3))
        (should= [5 5] (orbit-target-pos [3 3] 8)))))

  (context "valid-orbit-pos? (L73)"
    (let [valid-orbit-pos? #'empire.computer.ship-carrier-group/valid-orbit-pos?]
      (it "empty sea cell is valid"
        (set-test-world! [[{:type :sea}]])
        (should (valid-orbit-pos? [0 0])))

      (it "occupied sea cell is invalid"
        (set-test-world! [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3}}]])
        (should-not (valid-orbit-pos? [0 0])))

      (it "land cell is invalid"
        (set-test-world! [[{:type :land}]])
        (should-not (valid-orbit-pos? [0 0])))))

  (context "transition-to-orbiting nil orbit-angle (L102)"
    (it "uses angle 0 when orbit-angle is nil"
      ;; 7x7 sea map. Carrier at [3,3]. Escort at [2,2] (within radius 2).
      ;; orbit-angle is nil (not set). Should default to 0 and find angle 0 position.
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
        (update-test-world! assoc-in [2 2 :contents]
                            {:type :battleship :owner :computer :hits 8
                             :escort-id 1 :escort-mode :intercepting
                             :escort-carrier-id 1})
        (cg/process-carrier-group-escort [2 2] :battleship)
        ;; orbit-angle 0 maps to offset [-2,-2] from [3,3] = [1,1]
        (let [bb (first (for [r (range 7) c (range 7)
                              :let [unit (get-in (test-utils/read-test-state :game-map) [r c :contents])]
                              :when (= :battleship (:type unit))]
                          unit))]
          (should= :orbiting (:escort-mode bb))
          (should= 0 (:orbit-angle bb))))))

  (context "process-escort-orbiting nil orbit-angle (L129)"
    (it "uses angle 0 when orbit-angle is nil during orbiting"
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
        ;; BB at angle-0 position [1,1], orbit-angle nil
        (update-test-world! assoc-in [1 1 :contents]
                            {:type :battleship :owner :computer :hits 8
                             :escort-id 1 :escort-mode :orbiting
                             :escort-carrier-id 1})
        (cg/process-carrier-group-escort [1 1] :battleship)
        ;; With nil orbit-angle defaulting to 0, inc(0)=1 -> angle 1 = [-2,-1] from [3,3] = [1,2]
        (let [bb (first (for [r (range 7) c (range 7)
                              :let [unit (get-in (test-utils/read-test-state :game-map) [r c :contents])]
                              :when (= :battleship (:type unit))]
                          unit))]
          (should= 1 (:orbit-angle bb))))))

)

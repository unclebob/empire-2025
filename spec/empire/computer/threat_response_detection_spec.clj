(ns empire.computer.threat-response-detection-spec
  (:require [empire.computer.core :as core]
            [empire.computer.threat-response :as threat-response]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-world! update-test-world!]]
            [speclj.core :refer :all]))

(defn computer-units
  [pred]
  (for [i (range (count (test-utils/read-test-state :game-map)))
        j (range (count (first (test-utils/read-test-state :game-map))))
        :let [u (get-in (test-utils/read-test-state :game-map) [i j :contents])]
        :when (and u (= :computer (:owner u)) (pred u))]
    [[i j] u]))

(describe "threat-response detection and invasion activation"
  (before (reset-all-atoms!))

  (it "assigns up to 4 closest fighters when enemy fighter detected"
    (let [gm (build-test-map ["f~~"
                              "~~~"
                              "f~~"
                              "~~~"
                              "f~~"
                              "~~~"
                              "f~~"
                              "~~F"
                              "f~~"])]
      (set-test-world! gm)
      (set-test-computer-map! gm)
      (let [enemy-cell (get-in (test-utils/read-test-state :game-map) [2 7])]
        (threat-response/handle-detection! [2 7] enemy-cell))
      (let [assigned (computer-units #(= :fighter-sweep (:threat-mission %)))
            assigned-positions (set (map first assigned))]
        (should= 4 (count assigned))
        (should (not (contains? assigned-positions [0 0])))
        (doseq [[_ unit] assigned]
          (should= [2 7] (:threat-center unit))))))

  (it "find-computer-unit-positions ignores computer fighters missing from computer-map"
    (let [gm (build-test-map ["f~~"
                              "~~~"
                              "f~F"])]
      (set-test-world! gm)
      (set-test-computer-map! gm)
      (test-utils/update-test-computer-map! assoc-in [0 2] nil)
      (should= [[0 0]]
               (vec (@#'threat-response/find-computer-unit-positions
                     #(= :fighter (:type %)))))))

  (it "assigns 2 patrol boats and 2 battleships when enemy ship detected"
    (let [gm (build-test-map ["p~~~"
                              "~b~~"
                              "p~~~"
                              "~b~~"
                              "p~~~"
                              "~b~~"
                              "~~~~"
                              "~~~~"
                              "~~~D"])]
      (set-test-world! gm)
      (set-test-computer-map! gm)
      (threat-response/handle-detection! [3 8] (get-in (test-utils/read-test-state :game-map) [3 8]))
      (let [assigned (computer-units #(= :sea-scout (:threat-mission %)))
            patrols (count (filter #(= :patrol-boat (:type (second %))) assigned))
            battleships (count (filter #(= :battleship (:type (second %))) assigned))]
        (should= 4 (count assigned))
        (should= 2 patrols)
        (should= 2 battleships))))

  (it "activates major invasion and assigns loaded transport to invading mission"
    (let [gm (build-test-map ["t~~~"
                              "~~~~"
                              "##O#"
                              "~~~~"])]
      (set-test-world! gm)
      (set-test-computer-map! gm)
      (update-test-world! assoc-in [0 0 :contents :army-count] 4)
      (update-test-world! assoc-in [0 0 :contents :transport-mission] :sailing)
      (threat-response/handle-detection! [2 2] (get-in (test-utils/read-test-state :game-map) [2 2]))
      (threat-response/refresh-major-invasion-assignments!)
      (let [transport (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
        (should (:active? (test-utils/read-test-state :major-invasion-state)))
        (should-contain [2 2] (:detection-points (test-utils/read-test-state :major-invasion-state)))
        (should= :invading (:transport-mission transport))
        (should= [2 2] (:invasion-target transport))
        (should (seq (:invasion-path transport))))
      (should (threat-response/major-invasion-target-land? [1 2]))))

  (it "selects a radius-2 invasion unload target when it yields a shorter approach"
    (let [gm (build-test-map ["~~~~~"
                              "~###~"
                              "~#O#~"
                              "t~~~~"
                              "~~~~~"])]
      (set-test-world! gm)
      (set-test-computer-map! gm)
      (update-test-world! assoc-in [0 3 :contents :army-count] 4)
      (update-test-world! assoc-in [0 3 :contents :transport-mission] :sailing)
      (threat-response/handle-detection! [2 2] (get-in (test-utils/read-test-state :game-map) [2 2]))
      (threat-response/refresh-major-invasion-assignments!)
      (let [transport (get-in (test-utils/read-test-state :game-map) [0 3 :contents])]
        (should (#{:invading :unloading} (:transport-mission transport)))
        (should (<= (core/chebyshev-distance (:invasion-target transport) [2 2]) 2)))))

  (it "ignores non-threat detections"
    (let [gm (build-test-map ["~~~"])]
      (set-test-world! gm)
      (set-test-computer-map! gm)
      (should-be-nil
       (threat-response/handle-detection! [0 0] (get-in (test-utils/read-test-state :game-map) [0 0])))
      (should-not (:active? (test-utils/read-test-state :major-invasion-state)))))

  (it "coalesces nearby major invasion detections to avoid repeated recompute"
    (let [recompute-count (atom 0)
          city-cell {:type :city :city-status :player}]
      (test-utils/set-test-state! :major-invasion-state {:active? true
                                                         :decision :ready
                                                         :detection-points []
                                                         :target-land-set #{}
                                                         :started-round 1})
      (with-redefs [empire.computer.threat-response/recompute-major-invasion-target-land!
                    (fn [] (swap! recompute-count inc))]
        (threat-response/handle-detection! [10 10] city-cell)
        (threat-response/handle-detection! [11 10] city-cell)
        (threat-response/handle-detection! [20 20] city-cell))
      (should= 2 @recompute-count)
      (should= 2 (count (:detection-points (test-utils/read-test-state :major-invasion-state))))))

  (it "assigns naval invasion pathing toward coastal staging when city is not directly sea-reachable"
    (let [gm (build-test-map ["t~~~~~~"
                              "#######"
                              "#~~~~~#"
                              "#~#O#~#"
                              "#~~~~~#"
                              "#######"
                              "~~~~~~~"])
          bfs-calls (atom 0)]
      (set-test-world! gm)
      (set-test-computer-map! gm)
      (update-test-world! assoc-in [0 0 :contents :army-count] 4)
      (update-test-world! assoc-in [0 0 :contents :transport-mission] :sailing)
      (test-utils/set-test-state! :major-invasion-state {:active? true
                                                         :detection-points [[3 3]]
                                                         :target-land-set #{[3 3]}
                                                         :sea-reachable-detection-points #{}
                                                         :target-land-revision 1})
      (with-redefs [empire.game-mechanics.movement.pathfinding-bfs/bfs-to-land-ho-target
                    (fn [& _]
                      (swap! bfs-calls inc)
                      nil)]
        (threat-response/refresh-major-invasion-assignments!))
      (should (< 0 @bfs-calls))
      (should= :sailing (get-in (test-utils/read-test-state :game-map) [0 0 :contents :transport-mission]))))

  (it "defers invasion on first detection when no sea path exists"
    (let [gm (build-test-map ["aO"])]
      (set-test-world! gm)
      (set-test-computer-map! gm)
      (test-utils/set-test-state! :round-number 7)
      (threat-response/handle-detection! [1 0] (get-in (test-utils/read-test-state :game-map) [1 0]))
      (let [state (test-utils/read-test-state :major-invasion-state)]
        (should-not (:active? state))
        (should= :deferred (:decision state))
        (should= :no-sea-path (:failure-reason state))
        (should= 17 (:next-review-round state)))))

  (it "defers invasion on first detection with no transports and fewer than six armies"
    (let [gm (build-test-map ["paaaaO"])
          cm (build-test-map ["p~~~~O"])]
      (set-test-world! gm)
      (set-test-computer-map! cm)
      (test-utils/set-test-state! :round-number 4)
      (threat-response/handle-detection! [5 0] (get-in (test-utils/read-test-state :game-map) [5 0]))
      (let [state (test-utils/read-test-state :major-invasion-state)]
        (should-not (:active? state))
        (should= :deferred (:decision state))
        (should= :insufficient-resources (:failure-reason state))
        (should= 14 (:next-review-round state)))))

  (it "re-evaluates deferred invasion every ten rounds"
    (let [gm (build-test-map ["paaaaO"])
          cm (build-test-map ["p~~~~O"])]
      (set-test-world! gm)
      (set-test-computer-map! cm)
      (test-utils/set-test-state! :round-number 0)
      (threat-response/handle-detection! [5 0] (get-in (test-utils/read-test-state :game-map) [5 0]))
      (set-test-world! (build-test-map ["t~~~~O"]))
      (set-test-computer-map! (build-test-map ["t~~~~O"]))
      (test-utils/set-test-state! :round-number 9)
      (threat-response/on-round-start!)
      (should= :deferred (:decision (test-utils/read-test-state :major-invasion-state)))
      (test-utils/set-test-state! :round-number 10)
      (threat-response/on-round-start!)
      (let [state (test-utils/read-test-state :major-invasion-state)]
        (should (:active? state))
        (should= :ready (:decision state))
        (should-be-nil (:failure-reason state)))))

  (it "forces patrol boats to explore while deferred for no-sea-path"
    (set-test-world! (build-test-map ["p~"]))
    (set-test-computer-map! (build-test-map ["p~"]))
    (test-utils/set-test-state! :major-invasion-state {:active? false
                                                       :decision :deferred
                                                       :failure-reason :no-sea-path
                                                       :next-review-round 10
                                                       :detection-points [[2 0]]
                                                       :target-land-set #{}
                                                       :started-round 1})
    (threat-response/on-round-start!)
    (should= :exploring (get-in (test-utils/read-test-state :game-map) [0 0 :contents :patrol-mode])))

  (it "falls back after unsustainable losses and schedules ten-round review"
    (set-test-world! (build-test-map ["tO"]))
    (set-test-computer-map! (build-test-map ["tO"]))
    (update-test-world! assoc-in [0 0 :contents]
                        {:type :transport :owner :computer
                         :major-invasion true
                         :transport-mission :invading
                         :army-count 0
                         :invasion-target [1 0]})
    (test-utils/set-test-state! :round-number 3)
    (test-utils/set-test-state! :major-invasion-state {:active? true
                                                       :decision :ready
                                                       :failure-reason nil
                                                       :next-review-round nil
                                                       :detection-points [[1 0]]
                                                       :target-land-set #{[1 0]}
                                                       :started-round 1
                                                       :first-landing-round 2})
    (threat-response/on-round-start!)
    (let [state (test-utils/read-test-state :major-invasion-state)
          transport (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
      (should-not (:active? state))
      (should= :deferred (:decision state))
      (should= :unsustainable-losses (:failure-reason state))
      (should= 13 (:next-review-round state))
      (should= :sailing (:transport-mission transport))
      (should-be-nil (:major-invasion transport)))))

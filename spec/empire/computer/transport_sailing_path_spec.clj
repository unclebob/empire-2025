(ns empire.computer.transport.sailing-path-spec
  "Tests for VMS Empire style computer transport movement."
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.shared.grid :as grid]
            [empire.computer.transport :as transport]
            [empire.computer.transport.core :as tc]
            [empire.computer.transport.sailing-support :as sailing-support]

            [empire.computer.land-objectives :as land-objectives]
            [empire.player.production :as player-prod]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-world! update-test-world!]]))
  (context "sail-path sailing"
    (it "empty sailing transport with empty sail-path heads toward claimed land"
      (set-test-world! (build-test-map ["~~t~~~~~#"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [8 0 :country-id] 7)
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [2 0 :contents]
             {:type :transport :owner :computer
              :transport-mission :sailing :army-count 0
              :sail-path []})
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [2 0])
      (let [t (first (for [c (range 9) r (range 1)
                           :let [unit (get-in (test-utils/read-test-state :game-map) [c r :contents])]
                           :when (= :transport (:type unit))]
                       unit))]
        (should= :sailing (:transport-mission t))
        (should-not-be-nil (:sail-path t))))

    (it "sail-to-load prefers the claimed coast closest to distance four"
      (let [computer-map (build-test-map ["t~~~~~~"
                                          "~#~~~#~"])]
        (set-test-world! computer-map)
        (update-test-world! assoc-in [1 1 :country-id] 1)
        (update-test-world! assoc-in [5 1 :country-id] 2)
        (set-test-computer-map! (test-utils/read-test-state :game-map))
        (should= [[1 0] [2 0] [3 0] [4 0]]
                 (sailing-support/compute-sail-to-load-path [0 0]))))

    (it "sail-to-load degrades smoothly when no target is exactly four away"
      (let [computer-map (build-test-map ["t~~~"
                                          "~#~#"])]
        (set-test-world! computer-map)
        (update-test-world! assoc-in [1 1 :country-id] 1)
        (update-test-world! assoc-in [3 1 :country-id] 2)
        (set-test-computer-map! (test-utils/read-test-state :game-map))
        (should= [[1 0] [2 0] [3 0]]
                 (sailing-support/compute-sail-to-load-path [0 0]))))

    (it "follows sail-path two steps per turn (speed 2)"
      (set-test-world! (build-test-map ["t~~~~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :transport :owner :computer
              :transport-mission :sailing :army-count 6
              :sail-path [[1 0] [2 0] [3 0]]})
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [0 0])
      (let [t (:contents (get-in (test-utils/read-test-state :game-map) [2 0]))]
        (should= :transport (:type t))
        (should= [[3 0]] (:sail-path t))))

    (it "follows sail-path for the full transport speed"
      (set-test-world! (build-test-map ["t~~~~~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :transport :owner :computer
              :transport-mission :sailing :army-count 6
              :sail-path [[1 0] [2 0] [3 0] [4 0]]})
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (with-redefs [empire.config.units.dispatcher/speed (constantly 3)]
        (transport/process-transport [0 0]))
      (let [t (:contents (get-in (test-utils/read-test-state :game-map) [3 0]))]
        (should= :transport (:type t))
        (should= [[4 0]] (:sail-path t))))

    (it "sailing with armies and empty path takes a safe random route when no unclaimed target exists"
      (set-test-world! (build-test-map ["t~~"
                                        "###"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      ;; Exclude all adjacent land by matching country-id. The transport now stays in
      ;; place when there is no reachable unclaimed or unexplored unload target.
      (doseq [c (range 3)]
        (update-test-world! assoc-in [c 1 :country-id] 1))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [0 0 :contents]
                         {:type :transport :owner :computer
                          :transport-mission :sailing :army-count 3
                          :country-id 1
                          :sail-path []})
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (with-redefs [sailing-support/compute-sail-to-unload-path (constantly nil)]
        (transport/process-transport [0 0]))
      (should= :transport (get-in (test-utils/read-test-state :game-map) [0 0 :contents :type]))
      (should= :sailing (get-in (test-utils/read-test-state :game-map) [0 0 :contents :transport-mission]))))

    (it "sailing transport in city launches to adjacent sea when path is empty"
      (set-test-world! (build-test-map ["~O~"
                                        "~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [1 0 :contents]
                         {:type :transport :owner :computer
                          :transport-mission :sailing :army-count 1
                          :sail-path []})
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (with-redefs [sailing-support/compute-sail-path (constantly nil)]
        (transport/process-transport [1 0]))
      (let [tpos (first (for [c (range 3) r (range 2)
                              :when (= :transport (get-in (test-utils/read-test-state :game-map) [c r :contents :type]))]
                          [c r]))]
        (should-not-be-nil tpos)
        (should-not= [1 0] tpos)
        (should (contains? #{[0 0] [2 0] [1 1]} tpos))))

    (it "continues in same direction when sail-path exhausted after 1 step"
      ;; t at [0 0], sail-path [[1 0]] — only 1 step.
      ;; After step 1 to [1 0], path is empty. Continue in same direction to [2 0].
      (set-test-world! (build-test-map ["t~~~~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :transport :owner :computer
              :transport-mission :sailing :army-count 6
              :sail-path [[1 0]]})
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [0 0])
      (let [t (:contents (get-in (test-utils/read-test-state :game-map) [2 0]))]
        (should= :transport (:type t))
        (should= [] (:sail-path t))))

    (it "stops after 1 step when continuation hits land"
      ;; t at [0 0], path [[1 0]], land at [2 0]
      ;; After step 1, continuation direction blocked by land — stays at [1 0]
      (set-test-world! (build-test-map ["t~#~~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :transport :owner :computer
              :transport-mission :sailing :army-count 6
              :sail-path [[1 0]]})
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [0 0])
      (let [t (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))]
        (should= :transport (:type t))
        (should= [] (:sail-path t))))

    (it "retreats one cell back when blocked by enemy"
      ;; t at [2 0], enemy D at [3 0], sail-path [[3 0] [4 0]]
      ;; Transport should retreat to [1 0] and prepend [2 0] to path
      (set-test-world! (build-test-map ["~~tD~~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [2 0 :contents]
             {:type :transport :owner :computer
              :transport-mission :sailing :army-count 6
              :sail-path [[3 0] [4 0]]})
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [2 0])
      (let [t (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))]
        (should= :transport (:type t))
        (should= [[2 0] [3 0] [4 0]] (:sail-path t))))

    (it "unloads at unowned land when sail-path is empty"
      ;; t at [2 0], land at [3 0], sail-path []
      (set-test-world! (build-test-map ["~~t#"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [2 0 :contents]
             {:type :transport :owner :computer
              :transport-mission :sailing :army-count 6
              :sail-path []})
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [2 0])
      (let [t (:contents (get-in (test-utils/read-test-state :game-map) [2 0]))]
        (should= 5 (:army-count t))
        (should= :army (get-in (test-utils/read-test-state :game-map) [3 0 :contents :type]))))

    (it "computes sail-path toward fog-of-war when entering sailing"
      ;; t at [0 0], fog starts at col 3 on computer map
      ;; game map: t~~~~~~~~
      ;; comp map: t~~......
      (set-test-world! (build-test-map ["t~~~~~~~~"]))
      (set-test-computer-map!
              (vec (for [c (range 9)]
                     [(if (< c 3)
                        (get-in (test-utils/read-test-state :game-map) [c 0])
                        nil)])))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :transport :owner :computer
              :transport-mission :loading :army-count 6})
      (set-test-computer-map!
              (assoc-in (test-utils/read-test-state :computer-map)
                        [0 0 :contents]
                        {:type :transport :owner :computer
                         :transport-mission :loading :army-count 6}))
      (transport/process-transport [0 0])
      (let [t (first (for [c (range 9)
                           :let [unit (get-in (test-utils/read-test-state :game-map) [c 0 :contents])]
                           :when (= :transport (:type unit))]
                       unit))]
        (should= :sailing (:transport-mission t))
        (should-not-be-nil (:sail-path t))
        (should (vector? (:sail-path t)))
        (should (pos? (count (:sail-path t))))))

    (it "exhausted sail-path with armies recomputes sailing path instead of forced unload"
      ;; t at [4 0], fog on both sides, sail-path []
      ;; Should stay in :sailing and obtain movement/path rather than flip-flopping.
      (set-test-world! (build-test-map ["~~~~~~~~~"]))
      (set-test-computer-map!
              (vec (for [c (range 9)]
                     [(if (<= 3 c 5)
                        (get-in (test-utils/read-test-state :game-map) [c 0])
                        nil)])))
      (update-test-world! assoc-in [4 0 :contents]
             {:type :transport :owner :computer
              :transport-mission :sailing :army-count 6
              :sail-path []})
      (set-test-computer-map!
              (assoc-in (test-utils/read-test-state :computer-map)
                        [4 0 :contents]
                        {:type :transport :owner :computer
                         :transport-mission :sailing :army-count 6
                         :sail-path []}))
      (transport/process-transport [4 0])
      (let [t-pos (first (for [c (range 9)
                               :let [u (get-in (test-utils/read-test-state :game-map) [c 0 :contents])]
                               :when (= :transport (:type u))]
                           [c 0]))
            t (get-in (test-utils/read-test-state :game-map) (conj t-pos :contents))]
        (should= :sailing (:transport-mission t))
        (should (or (not= [4 0] t-pos)
                    (seq (:sail-path t))))))

  (context "process-invading-mission (L89)"
    (it "transitions to unloading when path is empty (L95)"
      (set-test-world! [[{:type :sea :contents {:type :transport :owner :computer
                                                       :transport-mission :invading
                                                       :invasion-path []
                                                       :army-count 4}}
                                {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [0 0])
      (should= :unloading (get-in (test-utils/read-test-state :game-map) [0 0 :contents :transport-mission])))

    (it "empty invasion path with target keeps moving toward target instead of forcing unload"
      (set-test-world! (build-test-map ["t~~~~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [0 0 :contents]
                         {:type :transport :owner :computer
                          :transport-mission :invading
                          :invasion-path []
                          :invasion-target [6 0]
                          :army-count 4})
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [0 0])
      ;; Should advance toward target and remain in invading mode (not forced unloading).
      (should= :transport (get-in (test-utils/read-test-state :game-map) [2 0 :contents :type]))
      (should= :invading (get-in (test-utils/read-test-state :game-map) [2 0 :contents :transport-mission])))

    (it "invading transport without a strictly closer step still moves instead of stalling"
      ;; ~#~   equal-distance sea cells at [0,0] and [0,2]
      ;; t##   direct and diagonal closer cells are blocked
      ;; ~#~
      ;; Target is off-map far east so current pos is not in unload radius.
      (set-test-world! (build-test-map ["~#~"
                                        "t##"
                                        "~#~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [0 1 :contents]
                         {:type :transport :owner :computer
                          :transport-mission :invading
                          :invasion-path []
                          :invasion-target [4 1]
                          :army-count 3})
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [0 1])
      ;; It may end the round back at origin after two steps, but it should
      ;; not stall; a move stamps invasion-last-pos.
      (should-not-be-nil (get-in (test-utils/read-test-state :game-map) [0 1 :contents :invasion-last-pos])))

    (it "transitions to unloading after exhausting 2-step path (L112)"
      ;; Path has exactly 2 steps — after taking both, remaining is empty,
      ;; so transport transitions to unloading at step2.
      (set-test-world! [[{:type :sea} {:type :sea} {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :transport :owner :computer
              :transport-mission :invading
              :invasion-path [[0 1] [0 2]]
              :army-count 4})
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [0 0])
      (should= :unloading (get-in (test-utils/read-test-state :game-map) [0 2 :contents :transport-mission]))
      (should-be-nil (:invasion-path (get-in (test-utils/read-test-state :game-map) [0 2 :contents]))))

    (it "sail-path continuation through computer-occupied sea (L18, L26)"
      ;; Transport sails through sea cell with friendly ship.
      ;; continue-pos should see computer-occupied cell as passable.
      (set-test-world! [[{:type :sea} {:type :sea}
                                {:type :sea :contents {:type :destroyer :owner :computer}}
                                {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :transport :owner :computer
              :transport-mission :sailing :army-count 6
              :sail-path [[0 1]]})
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [0 0])
      ;; Should have moved to [0 1], then continued to [0 2] (computer-occupied passable)
      ;; or stopped at [0 1] if computer unit blocks move. Either way, moved past [0 0].
      (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))))

    (it "backs away and switches to unloading when enemy ships are near invasion destination"
      ;; Target at [4 0] has a nearby player ship. Transport at [2 0] should
      ;; retreat away from target and switch to unloading.
      (set-test-world! (build-test-map ["~~t~P~~"
                                        "~~~~~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [2 0 :contents]
                         {:type :transport :owner :computer
                          :transport-mission :invading
                          :invasion-path []
                          :invasion-target [4 0]
                          :army-count 4})
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [2 0])
      (let [transport-pos (first (for [c (range 7)
                                       r (range 2)
                                       :let [u (get-in (test-utils/read-test-state :game-map) [c r :contents])]
                                       :when (= :transport (:type u))]
                                   [c r]))
            transport-unit (get-in (test-utils/read-test-state :game-map) (conj transport-pos :contents))]
        (should= :unloading (:transport-mission transport-unit))
        (should (> (grid/chebyshev-distance transport-pos [4 0]) 2)))))

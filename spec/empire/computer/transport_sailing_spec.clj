(ns empire.computer.transport-sailing-spec
  "Tests for VMS Empire style computer transport movement."
  (:require [speclj.core :refer :all]
            [empire.computer.transport :as transport]

            [empire.computer.land-objectives :as land-objectives]
            [empire.player.production :as player-prod]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [build-test-map reset-all-atoms!]]))

(describe "process-transport"
  (before (reset-all-atoms!))

  (context "unload-event-id filtering"
    (it "mint-unload-event-id always mints new ID even when one exists"
      (reset-all-atoms!)
      (reset! atoms/next-unload-event-id 100)
      (let [game-map (build-test-map ["t~"])]
        (reset! atoms/game-map game-map)
        (swap! atoms/game-map assoc-in [0 0 :contents :unload-event-id] 42)
        (let [transport (get-in @atoms/game-map [0 0 :contents])]
          (#'transport/mint-unload-event-id [0 0] transport)
          (should= 100 (get-in @atoms/game-map [0 0 :contents :unload-event-id]))
          (should= 101 @atoms/next-unload-event-id)))))

  (context "unload-country-id"
    (it "mint-unload-country-id mints fresh country-id onto transport"
      (reset-all-atoms!)
      (reset! atoms/next-country-id 50)
      (let [game-map (build-test-map ["t~"])]
        (reset! atoms/game-map game-map)
        (#'transport/mint-unload-country-id [0 0])
        (should= 50 (get-in @atoms/game-map [0 0 :contents :unload-country-id]))
        (should= 51 @atoms/next-country-id)))

    (it "start-sailing sets unload-country-id on transport"
      (reset-all-atoms!)
      (reset! atoms/next-country-id 10)
      (let [game-map (build-test-map ["###"
                                      "~t~"
                                      "~~~"
                                      "~~~"
                                      "~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map
                (vec (for [c (range 3)]
                       (vec (for [r (range 5)]
                              (if (< r 4) (get-in game-map [c r]) nil))))))
        (swap! atoms/game-map assoc-in [1 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 6})
        (transport/process-transport [1 1])
        (let [t (first (for [c (range 3) r (range 5)
                             :let [unit (get-in @atoms/game-map [c r :contents])]
                             :when (= :transport (:type unit))]
                         unit))]
          (should= :sailing (:transport-mission t))
          (should-not-be-nil (:unload-country-id t)))))

    (it "unloaded army gets country-id from transport"
      (reset-all-atoms!)
      (reset! atoms/game-map [[{:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :sailing :army-count 2
                                                        :sail-path [[0 2]]
                                                        :unload-country-id 77}}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 1])
      (let [army (:contents (get-in @atoms/game-map [0 0]))]
        (should= :army (:type army))
        (should= 77 (:country-id army))))

    (it "unloaded army stamps land with country-id"
      (reset-all-atoms!)
      (reset! atoms/game-map [[{:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :sailing :army-count 1
                                                        :sail-path [[0 2]]
                                                        :unload-country-id 88}}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 1])
      (should= 88 (:country-id (get-in @atoms/game-map [0 0]))))

    (it "transport records unloaded country-id after unloading"
      (reset-all-atoms!)
      (reset! atoms/round-number 5)
      (reset! atoms/game-map [[{:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :sailing :army-count 1
                                                        :sail-path [[0 2]]
                                                        :unload-country-id 33}}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 1])
      (let [transport (:contents (get-in @atoms/game-map [0 1]))]
        (should= 5 (get-in transport [:unloaded-countries 33]))))

    (it "transport does not reload armies it just unloaded (cycle-breaking)"
      (reset-all-atoms!)
      (reset! atoms/round-number 5)
      (reset! atoms/next-country-id 100)
      ;; Transport at [0,1] with 1 army, adjacent to empty land at [0,0].
      ;; After unloading, army gets country-id 100 and land gets stamped.
      ;; Transport transitions to loading but should NOT reload that army
      ;; because its country-id was just recorded in unloaded-countries.
      (reset! atoms/game-map [[{:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :unloading :army-count 1
                                                        :unload-country-id 100}}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 1])
      ;; Army should be on land
      (should= :army (get-in @atoms/game-map [0 0 :contents :type]))
      ;; Transport should have 0 armies (did NOT reload)
      (let [transport (:contents (get-in @atoms/game-map [0 1]))]
        (should= :loading (:transport-mission transport))
        (should= 0 (:army-count transport)))))

  (context "transport without army-count field"
    (it "loads armies even when army-count is missing from transport"
      ;; Bug: transports spawned without :army-count caused NPE in load-adjacent-armies
      ;; because (+ nil to-load) throws. Armies were removed but count never incremented.
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :computer :hits 1 :mode :sentry}}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :loading}}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      ;; Note: transport has NO :army-count key at all
      (transport/process-transport [0 1])
      ;; Army should be loaded (removed from land)
      (should-be-nil (:contents (get-in @atoms/game-map [0 0])))
      ;; Transport should have army-count 1
      (let [t-pos (first (for [c (range 3)
                                :when (= :transport (get-in @atoms/game-map [0 c :contents :type]))]
                            [0 c]))
            transport (get-in @atoms/game-map (conj t-pos :contents))]
        (should= 1 (:army-count transport))))

    (it "newly spawned computer transport has army-count 0"
      (reset! atoms/round-number 5)
      (reset! atoms/game-map [[{:type :city :city-status :computer :country-id 1}]])
      (reset! atoms/computer-map @atoms/game-map)
      (reset! atoms/production {[0 0] {:item :transport :remaining-rounds 1}})
      (player-prod/update-production)
      (let [unit (:contents (get-in @atoms/game-map [0 0]))]
        (should= :transport (:type unit))
        (should= 0 (:army-count unit)))))


  (context "sail threshold"
    (it "does not sail with fewer than 4 armies"
      ;; Transport with 2 armies, no adjacent loadable armies.
      ;; Land has same country-id as transport to prevent opportunistic unload.
      (let [game-map (build-test-map ["###"
                                      "~t~"
                                      "~~~"
                                      "~~~"])]
        (reset! atoms/game-map game-map)
        (doseq [c (range 3)]
          (swap! atoms/game-map assoc-in [c 0 :country-id] 1))
        (reset! atoms/computer-map
                (vec (for [c (range 3)]
                       (vec (for [r (range 4)]
                              (if (< r 3) (get-in game-map [c r]) nil))))))
        (swap! atoms/game-map assoc-in [1 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 2
                :country-id 1})
        (transport/process-transport [1 1])
        (let [t (first (for [c (range 3) r (range 4)
                             :let [unit (get-in @atoms/game-map [c r :contents])]
                             :when (= :transport (:type unit))]
                         unit))]
          (should= :loading (:transport-mission t)))))

    (it "does not sail when loadable army within 3 coastal cells"
      ;; Transport with 4 armies at [1,1]. Army at [5,0] is 3 coastal hops away.
      ;; Land has same country-id as transport to prevent opportunistic unload.
      ;; ######   row 0: land, army at col 5
      ;; ~t~~~~   row 1: transport at [1,1]
      (let [game-map (build-test-map ["#####a"
                                      "~t~~~~"])]
        (reset! atoms/game-map game-map)
        (doseq [c (range 6)]
          (swap! atoms/game-map assoc-in [c 0 :country-id] 1))
        (reset! atoms/computer-map
                (vec (for [c (range 6)]
                       (vec (for [r (range 2)]
                              (get-in game-map [c r]))))))
        (swap! atoms/game-map assoc-in [1 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 4
                :country-id 1})
        (transport/process-transport [1 1])
        (let [t (first (for [c (range 6) r (range 2)
                             :let [unit (get-in @atoms/game-map [c r :contents])]
                             :when (= :transport (:type unit))]
                         unit))]
          (should= :loading (:transport-mission t)))))

    (it "sails when loadable army is beyond 3 coastal cells"
      ;; Transport with 4 armies at [1,1]. Army at [6,0] is 4 coastal hops away.
      ;; Land has same country-id as transport to prevent opportunistic unload.
      (let [game-map (build-test-map ["######a"
                                      "~t~~~~~"])]
        (reset! atoms/game-map game-map)
        (doseq [c (range 7)]
          (swap! atoms/game-map assoc-in [c 0 :country-id] 1))
        (reset! atoms/computer-map
                (vec (for [c (range 7)]
                       (vec (for [r (range 2)]
                              (get-in game-map [c r]))))))
        (swap! atoms/game-map assoc-in [1 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 4
                :country-id 1})
        (transport/process-transport [1 1])
        (let [t (first (for [c (range 7) r (range 2)
                             :let [unit (get-in @atoms/game-map [c r :contents])]
                             :when (= :transport (:type unit))]
                         unit))]
          (should= :sailing (:transport-mission t))))))

  (context "full transport sailing"
    (it "full loading transport enters sailing toward fog"
      ;; Full transport with no adjacent loadable armies enters sailing
      (let [game-map (build-test-map ["t~~~~~"
                                      "~~~~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map
                (vec (for [c (range 6)]
                       (vec (for [r (range 2)]
                              (if (< c 4)
                                (get-in game-map [c r])
                                nil))))))
        (swap! atoms/game-map assoc-in [0 0 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 6})
        (transport/process-transport [0 0])
        (let [t (first (for [c (range 6) r (range 2)
                               :let [unit (get-in @atoms/game-map [c r :contents])]
                               :when (= :transport (:type unit))]
                           unit))]
          (should= :sailing (:transport-mission t)))))

    (it "full transport sails even when nearby armies exist"
      ;; Full transport in narrow channel with armies on adjacent land
      ;; Should still sail because it can't load any more
      ;; a##    army at [0,0], land at [1,0] and [2,0]
      ;; ~t~    transport at [1,1], sea at [0,1] and [2,1]
      ;; ~~~    open sea rows 2-4
      ;; ~~~
      ;; ~~~
      (let [game-map (build-test-map ["a##"
                                      "~t~"
                                      "~~~"
                                      "~~~"
                                      "~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map
                (vec (for [c (range 3)]
                       (vec (for [r (range 5)]
                              (if (< r 3) (get-in game-map [c r]) nil))))))
        (swap! atoms/game-map assoc-in [1 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 6})
        (transport/process-transport [1 1])
        (let [t (first (for [c (range 3) r (range 5)
                             :let [unit (get-in @atoms/game-map [c r :contents])]
                             :when (= :transport (:type unit))]
                         unit))]
          (should-not-be-nil t)
          (should= :sailing (:transport-mission t))))))

  ;; smart sailing heading tests removed — replaced by sail-path sailing

  (context "sail-path sailing"
    (it "empty sailing transport with empty sail-path transitions to loading"
      (reset! atoms/game-map (build-test-map ["~~t#"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [2 0 :contents]
             {:type :transport :owner :computer
              :transport-mission :sailing :army-count 0
              :sail-path []})
      (transport/process-transport [2 0])
      (let [t (first (for [c (range 4) r (range 1)
                           :let [unit (get-in @atoms/game-map [c r :contents])]
                           :when (= :transport (:type unit))]
                       unit))]
        (should= :loading (:transport-mission t))))

    (it "follows sail-path two steps per turn (speed 2)"
      (reset! atoms/game-map (build-test-map ["t~~~~~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [0 0 :contents]
             {:type :transport :owner :computer
              :transport-mission :sailing :army-count 6
              :sail-path [[1 0] [2 0] [3 0]]})
      (transport/process-transport [0 0])
      (let [t (:contents (get-in @atoms/game-map [2 0]))]
        (should= :transport (:type t))
        (should= [[3 0]] (:sail-path t))))

    (it "continues in same direction when sail-path exhausted after 1 step"
      ;; t at [0 0], sail-path [[1 0]] — only 1 step.
      ;; After step 1 to [1 0], path is empty. Continue in same direction to [2 0].
      (reset! atoms/game-map (build-test-map ["t~~~~~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [0 0 :contents]
             {:type :transport :owner :computer
              :transport-mission :sailing :army-count 6
              :sail-path [[1 0]]})
      (transport/process-transport [0 0])
      (let [t (:contents (get-in @atoms/game-map [2 0]))]
        (should= :transport (:type t))
        (should= [] (:sail-path t))))

    (it "stops after 1 step when continuation hits land"
      ;; t at [0 0], path [[1 0]], land at [2 0]
      ;; After step 1, continuation direction blocked by land — stays at [1 0]
      (reset! atoms/game-map (build-test-map ["t~#~~~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [0 0 :contents]
             {:type :transport :owner :computer
              :transport-mission :sailing :army-count 6
              :sail-path [[1 0]]})
      (transport/process-transport [0 0])
      (let [t (:contents (get-in @atoms/game-map [1 0]))]
        (should= :transport (:type t))
        (should= [] (:sail-path t))))

    (it "retreats one cell back when blocked by enemy"
      ;; t at [2 0], enemy D at [3 0], sail-path [[3 0] [4 0]]
      ;; Transport should retreat to [1 0] and prepend [2 0] to path
      (reset! atoms/game-map (build-test-map ["~~tD~~~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [2 0 :contents]
             {:type :transport :owner :computer
              :transport-mission :sailing :army-count 6
              :sail-path [[3 0] [4 0]]})
      (transport/process-transport [2 0])
      (let [t (:contents (get-in @atoms/game-map [1 0]))]
        (should= :transport (:type t))
        (should= [[2 0] [3 0] [4 0]] (:sail-path t))))

    (it "unloads at unowned land when sail-path is empty"
      ;; t at [2 0], land at [3 0], sail-path []
      (reset! atoms/game-map (build-test-map ["~~t#"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [2 0 :contents]
             {:type :transport :owner :computer
              :transport-mission :sailing :army-count 6
              :sail-path []})
      (transport/process-transport [2 0])
      (let [t (:contents (get-in @atoms/game-map [2 0]))]
        (should= 5 (:army-count t))
        (should= :army (get-in @atoms/game-map [3 0 :contents :type]))))

    (it "computes sail-path toward fog-of-war when entering sailing"
      ;; t at [0 0], fog starts at col 3 on computer map
      ;; game map: t~~~~~~~~
      ;; comp map: t~~......
      (reset! atoms/game-map (build-test-map ["t~~~~~~~~"]))
      (reset! atoms/computer-map
              (vec (for [c (range 9)]
                     [(if (< c 3)
                        (get-in @atoms/game-map [c 0])
                        nil)])))
      (swap! atoms/game-map assoc-in [0 0 :contents]
             {:type :transport :owner :computer
              :transport-mission :loading :army-count 6})
      (transport/process-transport [0 0])
      (let [t (first (for [c (range 9)
                           :let [unit (get-in @atoms/game-map [c 0 :contents])]
                           :when (= :transport (:type unit))]
                       unit))]
        (should= :sailing (:transport-mission t))
        (should-not-be-nil (:sail-path t))
        (should (vector? (:sail-path t)))
        (should (pos? (count (:sail-path t))))))

    (it "exhausted sail-path with armies transitions to unloading"
      ;; t at [4 0], fog on both sides, sail-path []
      ;; Should transition to :unloading (then start-sailing on next round if no coast)
      (reset! atoms/game-map (build-test-map ["~~~~~~~~~"]))
      (reset! atoms/computer-map
              (vec (for [c (range 9)]
                     [(if (<= 3 c 5)
                        (get-in @atoms/game-map [c 0])
                        nil)])))
      (swap! atoms/game-map assoc-in [4 0 :contents]
             {:type :transport :owner :computer
              :transport-mission :sailing :army-count 6
              :sail-path []})
      (transport/process-transport [4 0])
      (let [t (:contents (get-in @atoms/game-map [4 0]))]
        (should= :unloading (:transport-mission t)))))

  (context "load armies after move-toward-position"
    (it "loads army adjacent to destination when moving toward pickup continent"
      ;; a##a#    army at [0,0], land row 0, army at [3,0]
      ;; ~t~~~    transport at [1,1] heading toward pickup-continent-pos [4,0]
      ;; ~~~~~    sea
      ;; Transport moves from [1,1] toward [4,0], arrives at [2,1].
      ;; Army at [3,0] is adjacent to [2,1] — should be loaded.
      (let [game-map (build-test-map ["a##a#"
                                      "~t~~~"
                                      "~~~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [1 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 0
                :pickup-continent-pos [4 0]})
        (transport/process-transport [1 1])
        ;; Army at [0,0] should be loaded (adjacent to start pos)
        (should-be-nil (:contents (get-in @atoms/game-map [0 0])))
        ;; Army at [3,0] should also be loaded (adjacent to destination)
        (should-be-nil (:contents (get-in @atoms/game-map [3 0])))
        ;; Transport should have 2 armies loaded
        (let [t (first (for [c (range 5) r (range 3)
                             :let [unit (get-in @atoms/game-map [c r :contents])]
                             :when (= :transport (:type unit))]
                         unit))]
          (should= 2 (:army-count t)))))))

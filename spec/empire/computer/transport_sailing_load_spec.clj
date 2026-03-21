(ns empire.computer.transport.sailing-load-spec
  "Tests for VMS Empire style computer transport movement."
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.transport :as transport]
            [empire.computer.transport.core :as tc]

            [empire.computer.land-objectives :as land-objectives]
            [empire.player.production :as player-prod]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-world! update-test-world!]]))
(describe "process-transport"
  (before (reset-all-atoms!))

  (context "unload-event-id filtering"
    (it "mint-unload-event-id always mints new ID even when one exists"
      (reset-all-atoms!)
      (test-utils/set-test-state! :next-unload-event-id 100)
      (let [game-map (build-test-map ["t~"])]
        (set-test-world! game-map)
        (set-test-computer-map! game-map)
        (update-test-world! assoc-in [0 0 :contents :unload-event-id] 42)
        (let [transport (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
          (tc/mint-unload-event-id [0 0] transport)
          (should= 100 (get-in (test-utils/read-test-state :game-map) [0 0 :contents :unload-event-id]))
          (should= 101 (test-utils/read-test-state :next-unload-event-id))))))

  (context "unload-country-id"
    (it "mint-unload-country-id mints fresh country-id onto transport"
      (reset-all-atoms!)
      (test-utils/set-test-state! :next-country-id 50)
      (let [game-map (build-test-map ["t~"])]
        (set-test-world! game-map)
        (set-test-computer-map! game-map)
        (tc/mint-unload-country-id [0 0])
        (should= 50 (get-in (test-utils/read-test-state :game-map) [0 0 :contents :unload-country-id]))
        (should= 51 (test-utils/read-test-state :next-country-id))))

    (it "start-sailing sets unload-country-id on transport"
      (reset-all-atoms!)
      (test-utils/set-test-state! :next-country-id 10)
      (let [game-map (build-test-map ["###"
                                      "~t~"
                                      "~~~"
                                      "~~~"
                                      "~~~"])]
        (set-test-world! game-map)
        (set-test-computer-map!
                (vec (for [c (range 3)]
                       (vec (for [r (range 5)]
                              (if (< r 4) (get-in game-map [c r]) nil))))))
        (update-test-world! assoc-in [1 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 6
                :load-manifest []})
        (with-redefs [empire.computer.transport.sailing/compute-sail-path (constantly [[1 2] [1 3]])]
          (transport/process-transport [1 1]))
        (let [t (first (for [c (range 3) r (range 5)
                             :let [unit (get-in (test-utils/read-test-state :game-map) [c r :contents])]
                             :when (= :transport (:type unit))]
                         unit))]
          (should= :sail-to-unload (:transport-mission t))
          (should-not-be-nil (:unload-country-id t)))))

    (it "unloaded army gets country-id from transport"
      (reset-all-atoms!)
      (set-test-world! [[{:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :sailing :army-count 2
                                                        :sail-path [[0 2]]
                                                        :unload-country-id 77}}
                                {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [0 1])
      (let [army (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))]
        (should= :army (:type army))
        (should= 77 (:country-id army))))

    (it "unloaded army stamps land with country-id"
      (reset-all-atoms!)
      (set-test-world! [[{:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :sailing :army-count 1
                                                        :sail-path [[0 2]]
                                                        :unload-country-id 88}}
                                {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [0 1])
      (should= 88 (:country-id (get-in (test-utils/read-test-state :game-map) [0 0]))))

    (it "transport records unloaded country-id after unloading"
      (reset-all-atoms!)
      (test-utils/set-test-state! :round-number 5)
      (set-test-world! [[{:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :sailing :army-count 1
                                                        :sail-path [[0 2]]
                                                        :unload-country-id 33}}
                                {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [0 1])
      (let [transport (:contents (get-in (test-utils/read-test-state :game-map) [0 1]))]
        (should= 5 (get-in transport [:unloaded-countries 33]))))

    (it "transport does not reload armies it just unloaded (cycle-breaking)"
      (reset-all-atoms!)
      (test-utils/set-test-state! :round-number 5)
      (test-utils/set-test-state! :next-country-id 100)
      ;; Transport at [0,1] with 1 army, adjacent to empty land at [0,0].
      ;; After unloading, army gets country-id 100 and land gets stamped.
      ;; Transport transitions to loading but should NOT reload that army
      ;; because its country-id was just recorded in unloaded-countries.
      (set-test-world! [[{:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :unloading :army-count 1
                                                        :unload-country-id 100}}
                                {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [0 1])
      ;; Army should be on land
      (should= :army (get-in (test-utils/read-test-state :game-map) [0 0 :contents :type]))
      ;; Transport should have 0 armies (did NOT reload)
      (let [transport (:contents (get-in (test-utils/read-test-state :game-map) [0 1]))]
        (should= :sail-to-load (:transport-mission transport))
        (should= 0 (:army-count transport)))))

  (context "transport without army-count field"
    (it "loads armies even when army-count is missing from transport"
      ;; Bug: transports spawned without :army-count caused NPE in load-adjacent-armies
      ;; because (+ nil to-load) throws. Armies were removed but count never incremented.
      (set-test-world! [[{:type :land :contents {:type :army :owner :computer :hits 1 :mode :sentry}}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :loading}}
                                {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      ;; Note: transport has NO :army-count key at all
      (transport/process-transport [0 1])
      ;; Army should be loaded (removed from land)
      (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))
      ;; Transport should have army-count 1
      (let [t-pos (first (for [c (range 3)
                                :when (= :transport (get-in (test-utils/read-test-state :game-map) [0 c :contents :type]))]
                            [0 c]))
            transport (get-in (test-utils/read-test-state :game-map) (conj t-pos :contents))]
        (should= 1 (:army-count transport))))

    (it "newly spawned computer transport has army-count 0"
      (test-utils/set-test-state! :round-number 5)
      (set-test-world! [[{:type :city :city-status :computer :country-id 1}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (test-utils/set-test-state! :production {[0 0] {:item :transport :remaining-rounds 1}})
      (player-prod/update-production)
      (let [unit (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))]
        (should= :transport (:type unit))
        (should= 0 (:army-count unit)))))


  (context "sail threshold"
    (it "transport with fewer than 4 armies and no plan enters hold-sail-to-load"
      ;; Transport with 2 armies, no adjacent loadable armies.
      ;; Land has same country-id as transport to prevent opportunistic unload.
      (let [game-map (build-test-map ["###"
                                      "~t~"
                                      "~~~"
                                      "~~~"])]
        (set-test-world! game-map)
        (doseq [c (range 3)]
          (update-test-world! assoc-in [c 0 :country-id] 1))
        (set-test-computer-map!
                (vec (for [c (range 3)]
                       (vec (for [r (range 4)]
                              (if (< r 3) (get-in game-map [c r]) nil))))))
        (update-test-world! assoc-in [1 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 2
                :country-id 1})
        (transport/process-transport [1 1])
        (let [t (first (for [c (range 3) r (range 4)
                             :let [unit (get-in (test-utils/read-test-state :game-map) [c r :contents])]
                             :when (= :transport (:type unit))]
                         unit))]
          (should= :hold-sail-to-load (:transport-mission t)))))

    (it "transport with nearby loadable army and no plan enters hold-sail-to-load"
      ;; Transport with 4 armies at [1,1]. Army at [5,0] is 3 coastal hops away.
      ;; Land has same country-id as transport to prevent opportunistic unload.
      ;; ######   row 0: land, army at col 5
      ;; ~t~~~~   row 1: transport at [1,1]
      (let [game-map (build-test-map ["#####a"
                                      "~t~~~~"])]
        (set-test-world! game-map)
        (doseq [c (range 6)]
          (update-test-world! assoc-in [c 0 :country-id] 1))
        (set-test-computer-map!
                (vec (for [c (range 6)]
                       (vec (for [r (range 2)]
                              (get-in game-map [c r]))))))
        (update-test-world! assoc-in [1 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 4
                :country-id 1})
        (transport/process-transport [1 1])
        (let [t (first (for [c (range 6) r (range 2)
                             :let [unit (get-in (test-utils/read-test-state :game-map) [c r :contents])]
                             :when (= :transport (:type unit))]
                         unit))]
          (should= :hold-sail-to-load (:transport-mission t)))))

    (it "transport beyond the old coastal threshold still enters hold-sail-to-load without a plan"
      ;; Transport with 4 armies at [1,1]. Army at [6,0] is 4 coastal hops away.
      ;; Land has same country-id as transport to prevent opportunistic unload.
      (let [game-map (build-test-map ["######a"
                                      "~t~~~~~"])]
        (set-test-world! game-map)
        (doseq [c (range 7)]
          (update-test-world! assoc-in [c 0 :country-id] 1))
        (set-test-computer-map!
                (vec (for [c (range 7)]
                       (vec (for [r (range 2)]
                              (get-in game-map [c r]))))))
        (update-test-world! assoc-in [1 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 4
                :country-id 1})
        (transport/process-transport [1 1])
        (let [t (first (for [c (range 7) r (range 2)
                             :let [unit (get-in (test-utils/read-test-state :game-map) [c r :contents])]
                             :when (= :transport (:type unit))]
                         unit))]
          (should= :hold-sail-to-load (:transport-mission t))))))

)

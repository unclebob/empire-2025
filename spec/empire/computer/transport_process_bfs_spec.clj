(ns empire.computer.transport-process-bfs-spec
  "Tests for VMS Empire style computer transport movement."
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.transport :as transport]
            [empire.computer.land-objectives :as land-objectives]
            [empire.player.production :as player-prod]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-world! update-test-world!]]))

(describe "process-transport"
  (before (reset-all-atoms!))

  (context "load capacity"
    (it "transport with 4 armies loads exactly 2 from 5 adjacent armies"
      ;; aaaaa   5 armies at row 0
      ;; ~~t~~   transport at [2,1] with 4 armies -> capacity = 6-4 = 2
      (set-test-world! (build-test-map ["aaaaa"
                                               "~~t~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [2 1 :contents]
             {:type :transport :owner :computer
              :transport-mission :loading :army-count 4})
      (transport/process-transport [2 1])
      ;; Find transport — should have 6 armies (4 + 2 loaded)
      (let [t (some (fn [[c r]]
                      (let [u (get-in (test-utils/read-test-state :game-map) [c r :contents])]
                        (when (= :transport (:type u)) u)))
                    (for [c (range 5) r (range 2)] [c r]))]
        (should= 6 (:army-count t)))))

  (context "unload-event-id propagation"
    (it "opportunistic unload gives army the transport's unload-event-id"
      (set-test-world! [[{:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :sailing
                                                        :army-count 1
                                                        :unload-event-id 99
                                                        :sail-path [[0 2]]}}
                                {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [0 1])
      (should= 99 (:unload-event-id (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))))))

  (context "BFS army type and owner filtering"
    (it "4-army transport near player army sails (not loadable)"
      ;; All adjacent land occupied by player army — no empty land for opportunistic unload
      ;; A    player army at [0,0]
      ;; t~   transport at [0,1] with 4 armies
      (set-test-world! [[{:type :land :contents {:type :army :owner :player :hits 1}}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :loading :army-count 4}}
                                {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [0 1])
      (let [t (some (fn [c]
                      (let [u (get-in (test-utils/read-test-state :game-map) [0 c :contents])]
                        (when (= :transport (:type u)) u)))
                    (range 3))]
        (should= :hold-sail-to-load (:transport-mission t))))

    (it "4-army transport near computer fighter sails (not loadable)"
      ;; f    computer fighter at [0,0] — wrong type
      ;; t~   transport at [0,1]
      (set-test-world! [[{:type :land :contents {:type :fighter :owner :computer :hits 1 :fuel 20}}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :loading :army-count 4}}
                                {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [0 1])
      (let [t (some (fn [c]
                      (let [u (get-in (test-utils/read-test-state :game-map) [0 c :contents])]
                        (when (= :transport (:type u)) u)))
                    (range 3))]
        (should= :hold-sail-to-load (:transport-mission t))))

    (it "4-army transport near army with matching unload-event-id sails"
      ;; a    army at [0,0] with unload-event-id 5
      ;; t~   transport at [0,1] with unload-event-id 5
      (set-test-world! [[{:type :land :contents {:type :army :owner :computer :hits 1
                                                        :unload-event-id 5}}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :loading :army-count 4
                                                        :unload-event-id 5}}
                                {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [0 1])
      (let [t (some (fn [c]
                      (let [u (get-in (test-utils/read-test-state :game-map) [0 c :contents])]
                        (when (= :transport (:type u)) u)))
                    (range 3))]
        (should= :hold-sail-to-load (:transport-mission t)))))

  (context "BFS multi-hop"
    (it "loadable army 2 coastal hops away without a plan enters hold-sail-to-load"
      ;; ##a   army at [2,0], land at [0,0],[1,0]
      ;; ##~   land at [0,1],[1,1], sea at [2,1]
      ;; ~t~   sea at [0,2], transport at [1,2], sea at [2,2]
      ;; Transport at [1,2]. Adjacent sea [2,1] is coastal. [2,1] is adjacent
      ;; to army at [2,0]. So BFS finds loadable army at depth 1.
      (set-test-world! (build-test-map ["##a"
                                               "##~"
                                               "~t~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [1 2 :contents]
             {:type :transport :owner :computer
              :transport-mission :loading :army-count 4})
      (transport/process-transport [1 2])
      ;; Without a manifest-backed plan, the transport now leaves legacy loading and
      ;; waits in hold-sail-to-load for a fresh plan.
      (let [t (some (fn [[c r]]
                      (let [u (get-in (test-utils/read-test-state :game-map) [c r :contents])]
                        (when (= :transport (:type u)) u)))
                    (for [c (range 3) r (range 3)] [c r]))]
        (should= :hold-sail-to-load (:transport-mission t)))))

  (context "BFS unloadable land"
    (it "unloading transport with unloadable land nearby crawls not re-sails"
      ;; ######     land at row 0 (country-id 1 = excluded)
      ;; ~t~~~~#    transport at [1,1], land at [6,1] is unloadable (no country-id)
      ;; Long coast — transport should crawl toward unloadable land, not re-sail.
      (set-test-world! (build-test-map ["######~"
                                               "~t~~~~#"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      ;; Mark row-0 land as excluded (country-id 1)
      (doseq [c (range 6)]
        (update-test-world! assoc-in [c 0 :country-id] 1))
      (update-test-world! assoc-in [1 1 :contents]
             {:type :transport :owner :computer
              :transport-mission :unloading :army-count 2
              :country-id 1
              :pickup-country-id 1})
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (with-redefs [rand (constantly 0.0)]
        (transport/process-transport [1 1]))
      ;; Transport should have crawled rightward (speed 2)
      ;; Find transport and verify it's still unloading
      (let [t (some (fn [[c r]]
                      (let [u (get-in (test-utils/read-test-state :game-map) [c r :contents])]
                        (when (= :transport (:type u)) u)))
                    (for [c (range 7) r (range 2)] [c r]))]
        (should= :unloading (:transport-mission t)))))

    (it "unloads immediately after first unloading crawl step when adjacent land becomes available"
      ;; ###   land row (0,0) and (1,0) excluded by country-id 1; (2,0) unloadable
      ;; t~~   transport starts at [0,1], can crawl to [1,1]
      (set-test-world! (build-test-map ["###"
                                        "t~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [0 0 :country-id] 1)
      (update-test-world! assoc-in [1 0 :country-id] 1)
      (update-test-world! assoc-in [0 1 :contents]
                         {:type :transport :owner :computer
                          :transport-mission :unloading :army-count 1
                          :country-id 1
                          :pickup-country-id 1})
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [0 1])
      ;; Same round unload should happen at [2,0] after first crawl step.
      (should= :army (get-in (test-utils/read-test-state :game-map) [2 0 :contents :type]))
      (let [t (some (fn [[c r]]
                      (let [u (get-in (test-utils/read-test-state :game-map) [c r :contents])]
                        (when (= :transport (:type u)) u)))
                    (for [c (range 3) r (range 2)] [c r]))]
        (should= 0 (:army-count t)))))

  (context "stored-path sailing"
    (it "sailing with 1-element sail-path stops at the stored step"
      ;; ~~~   row 0
      ;; t~~   transport at [0,0], sail-path [[1,0]]
      ;; ~~~   row 2 — transport should stop at [1,0]
      (set-test-world! (build-test-map ["~~~"
                                               "t~~"
                                               "~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :transport :owner :computer
              :transport-mission :sailing :army-count 2
              :sail-path [[1 0]]})
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [0 0])
      (should= :transport (get-in (test-utils/read-test-state :game-map) [1 0 :contents :type]))))

  (context "sailing blocked retreat"
    (it "sailing transport blocked by player unit retreats"
      ;; ~D~   player destroyer at [1,0] blocks the path
      ;; t~~   transport at [0,1], sail-path [[1,0],[2,0]]
      ;; ~~~   row 2
      (set-test-world! [[{:type :sea}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :sailing :army-count 2
                                                        :sail-path [[1 0]]}}
                                {:type :sea}]
                               [{:type :sea :contents {:type :destroyer :owner :player :hits 3}}
                                {:type :sea}
                                {:type :sea}]
                               [{:type :sea}
                                {:type :sea}
                                {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [0 1])
      ;; Transport should have retreated to a passable neighbor (not [1,0])
      (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 1])))
      (let [t (some (fn [[c r]]
                      (let [u (get-in (test-utils/read-test-state :game-map) [c r :contents])]
                        (when (= :transport (:type u)) [c r])))
                    (for [c (range 3) r (range 3)] [c r]))]
        (should-not-be-nil t)
        (should-not= [1 0] t))))

  (context "sailing remaining path"
    (it "multi-step sail-path preserves remaining after 2 steps"
      ;; ~~~~   row 0
      ;; t~~~   transport at [0,0], sail-path [[1,0],[2,0],[3,0]]
      (set-test-world! (build-test-map ["~~~~"
                                               "~~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :transport :owner :computer
              :transport-mission :sailing :army-count 2
              :sail-path [[1 0] [2 0] [3 0]]})
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [0 0])
      ;; After 2 steps, transport at [2,0] with remaining [[3,0]]
      (should= :transport (get-in (test-utils/read-test-state :game-map) [2 0 :contents :type]))
      (should= [[3 0]] (get-in (test-utils/read-test-state :game-map) [2 0 :contents :sail-path]))))

  (context "score-target-city"
    (it "prefers city on continent with more attackable cities"
      ;; Two free cities on separate continents, equidistant from transport.
      ;; Continent A at [0,0] has 1 free city.
      ;; Continent B at [4,0]-[4,1] has 2 free cities.
      ;; Transport at [2,2]. More attackable cities -> lower score -> preferred.
      (let [game-map (build-test-map ["+~~~++"
                                       "~~~~##"
                                       "~~t~~~"])]
        (set-test-world! game-map)
        (set-test-computer-map! game-map)
        (let [target (transport/find-unload-target nil [2 2])]
          ;; Should prefer continent B (2 free cities vs 1)
          (should-not-be-nil target)
          (should (#{[4 0] [5 0]} target))))))

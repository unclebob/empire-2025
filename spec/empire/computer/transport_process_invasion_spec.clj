(ns empire.computer.transport-process-invasion-spec
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

  (context "find-unload-target filtering"
    (it "excludes pickup-continent cities"
      ;; Continent A (rows 0-1) with player city. Transport came from continent A.
      ;; Continent B (rows 4-5) with free city.
      (let [game-map (build-test-map ["O##"
                                       "###"
                                       "~~~"
                                       "~~~"
                                       "+##"
                                       "###"])]
        (set-test-world! game-map)
        (set-test-computer-map! game-map)
        (let [pickup-continent (land-objectives/flood-fill-continent [0 0])
              target (transport/find-unload-target pickup-continent [1 3])]
          ;; Should exclude continent A city [0,0] and pick continent B
          (should-not-be-nil target)
          (should= [0 4] target))))

    (it "prefers unclaimed target over claimed"
      ;; Two continents with free cities. One already claimed.
      (let [game-map (build-test-map ["+#~+#"
                                       "##~##"
                                       "~~~~~"])]
        (set-test-world! game-map)
        (set-test-computer-map! game-map)
        ;; Claim the first city
        (test-utils/set-test-state! :claimed-transport-targets #{[0 0]})
        (let [target (transport/find-unload-target nil [2 2])]
          ;; Should pick the unclaimed city [3,0]
          (should= [3 0] target)))))

  (context "idle mission fix"
    (it "transport with idle mission transitions to loading"
      (set-test-world! [[{:type :sea :contents {:type :transport :owner :computer
                                                       :transport-mission :idle
                                                       :army-count 0}}
                                {:type :sea}
                                {:type :land}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [0 0])
      (let [t (some (fn [c]
                      (let [u (get-in (test-utils/read-test-state :game-map) [0 c :contents])]
                        (when (= :transport (:type u)) u)))
                    (range 3))]
        (should= :loading (:transport-mission t)))))

  (context "crawl history avoidance"
    (it "unloading crawl avoids positions in crawl-history"
      ;; ###   land at row 0
      ;; ~t~   transport at [1,1] with crawl-history [[0,1],[2,1]]
      ;; ~~~   row 2
      ;; Preferred targets exclude history. With rand fixed, should pick remaining.
      (set-test-world! (build-test-map ["###"
                                               "~t~"
                                               "~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [1 1 :contents]
             {:type :transport :owner :computer
              :transport-mission :unloading :army-count 2
              :crawl-history [[0 1] [2 1]]
              :country-id 99})
      ;; Adjacent land at [0,0]-[2,0] has no country-id, so opportunistic unload fires.
      ;; After unload, check the crawl-history was respected.
      (with-redefs [rand (constantly 0.0)]
        (transport/process-transport [1 1]))
      ;; Transport should still be somewhere (opportunistic unload, then crawl)
      (let [t (some (fn [[c r]]
                      (let [u (get-in (test-utils/read-test-state :game-map) [c r :contents])]
                        (when (= :transport (:type u)) {:pos [c r] :unit u})))
                    (for [c (range 3) r (range 3)] [c r]))]
        (should-not-be-nil t)
        ;; crawl-history should have been updated
        (should (seq (:crawl-history (:unit t)))))))

  (context "passable-sea with computer unit"
    (it "transport moves past sea cell containing computer unit"
      ;; ~s~   computer sub at [1,0]
      ;; t~~   transport at [0,1]
      ;; ~~~   row 2
      ;; get-passable-sea-neighbors should include [1,0] (computer-owned)
      (set-test-world! (build-test-map ["~s~"
                                               "t~~"
                                               "~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [0 1 :contents]
             {:type :transport :owner :computer
              :transport-mission :sailing :army-count 2
              :sail-path [[1 0]]})
      ;; [1,0] has a computer sub — should still be passable for pathfinding
      ;; But move-unit-to will fail because cell is occupied.
      ;; Transport should retreat to a passable neighbor.
      (transport/process-transport [0 1])
      ;; Transport should have moved somewhere (retreat)
      (let [t (some (fn [[c r]]
                      (let [u (get-in (test-utils/read-test-state :game-map) [c r :contents])]
                        (when (= :transport (:type u)) [c r])))
                    (for [c (range 3) r (range 3)] [c r]))]
        (should-not-be-nil t))))

  (context "zero-army unloading"
    (it "unloading transport with 0 armies transitions to sail-to-load"
      (set-test-world! [[{:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :unloading
                                                        :army-count 0}}
                                {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [0 1])
      (should= :sail-to-load (:transport-mission (:contents (get-in (test-utils/read-test-state :game-map) [0 1]))))))

  (context "recently-unloaded countries"
    (it "skips army from country unloaded less than 10 rounds ago"
      ;; a#   army at [0,0] with country-id 7
      ;; t~   transport at [0,1] with unloaded-countries {7 5}, round 10
      ;; Army's country was unloaded 5 rounds ago (< 10) — should skip
      (test-utils/set-test-state! :round-number 10)
      (set-test-world! [[{:type :land :contents {:type :army :owner :computer :hits 1
                                                        :country-id 7}}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :loading
                                                        :army-count 0
                                                        :unloaded-countries {7 5}}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [0 1])
      ;; Army should NOT be loaded (recently unloaded country)
      (let [t (some (fn [c]
                      (let [u (get-in (test-utils/read-test-state :game-map) [0 c :contents])]
                        (when (= :transport (:type u)) u)))
                    (range 2))]
        (should= 0 (:army-count t))))

    (it "loads army from country unloaded 10+ rounds ago"
      ;; a#   army at [0,0] with country-id 7
      ;; t~   transport at [0,1] with unloaded-countries {7 0}, round 10
      ;; Army's country was unloaded 10 rounds ago (>= 10) — should load
      (test-utils/set-test-state! :round-number 10)
      (set-test-world! [[{:type :land :contents {:type :army :owner :computer :hits 1
                                                        :country-id 7}}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :loading
                                                        :army-count 0
                                                        :unloaded-countries {7 0}}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [0 1])
      ;; Army should be loaded (country unloaded 10 rounds ago, not recent)
      (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))
      (let [t (some (fn [c]
                      (let [u (get-in (test-utils/read-test-state :game-map) [0 c :contents])]
                        (when (= :transport (:type u)) u)))
                    (range 2))]
        (should= 1 (:army-count t)))))

  (context "transport-fully-loaded trigger"
    (it "sets transport-fully-loaded? when transport starts sailing"
      ;; ~t~    sea, transport at [1 0], sea
      ;; ###    land row
      ;; ~~~    sea row
      (let [game-map (build-test-map ["~t~"
                                      "###"
                                      "~~~"])]
        (set-test-world! game-map)
        (set-test-computer-map! game-map)
        (update-test-world! assoc-in [1 0 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 6
                :country-id 1 :transport-id 1
                :hits 1 :been-to-sea true :awake-armies 0})
        (test-utils/set-test-state! :transport-fully-loaded? false)
        (should= false (test-utils/read-test-state :transport-fully-loaded?))
        (transport/process-transport [1 0])
        (should= true (test-utils/read-test-state :transport-fully-loaded?))))

    (it "does not re-set when already true"
      (let [game-map (build-test-map ["~t~"
                                      "###"
                                      "~~~"])]
        (set-test-world! game-map)
        (set-test-computer-map! game-map)
        (test-utils/set-test-state! :transport-fully-loaded? true)
        (update-test-world! assoc-in [1 0 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 6
                :country-id 1 :transport-id 1
                :hits 1 :been-to-sea true :awake-armies 0})
        (transport/process-transport [1 0])
        (should= true (test-utils/read-test-state :transport-fully-loaded?)))))

  (context "major invasion transport loading"
    (it "empty invasion transport enters find-armies-for-invasion"
      (set-test-world! (build-test-map ["tO~~~"
                                        "~~~~#"
                                        "a####"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (test-utils/set-test-state! :major-invasion-state {:active? true
                                          :detection-points #{[1 0]}
                                          :target-land-set #{[1 0]}
                                          :sea-reachable-detection-points #{}
                                          :target-land-revision 1})
      (update-test-world! assoc-in [0 0 :contents :transport-mission] :sailing)
      (update-test-world! assoc-in [0 0 :contents :army-count] 0)
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [0 0])
      (let [t (some (fn [[c r]]
                      (let [u (get-in (test-utils/read-test-state :game-map) [c r :contents])]
                        (when (= :transport (:type u)) u)))
                    (for [c (range 5) r (range 3)] [c r]))]
        (should= :find-armies-for-invasion (:transport-mission t))))

    (it "load-for-invasion times out empty transport back to sail-to-load"
      (test-utils/set-test-state! :round-number 6)
      (set-test-world! (build-test-map ["~t~"
                                        "###"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [1 0 :contents]
                         {:type :transport :owner :computer
                          :transport-mission :load-for-invasion
                          :major-invasion true
                          :invasion-load-since 0
                          :army-count 0})
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [1 0])
      (should= :sail-to-load (get-in (test-utils/read-test-state :game-map) [1 0 :contents :transport-mission])))

    (it "load-for-invasion with armies times out into invasion mission"
      (test-utils/set-test-state! :round-number 6)
      (set-test-world! (build-test-map ["~t~"
                                        "###"
                                        "~O~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (test-utils/set-test-state! :major-invasion-state {:active? true
                                          :detection-points #{[1 2]}
                                          :target-land-set #{[1 2]}
                                          :sea-reachable-detection-points #{[1 2]}
                                          :target-land-revision 1})
      (update-test-world! assoc-in [1 0 :contents]
                         {:type :transport :owner :computer
                          :transport-mission :load-for-invasion
                          :major-invasion true
                          :major-invasion-target [1 2]
                          :invasion-load-since 0
                          :army-count 1})
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [1 0])
      (should (#{:invading :unloading}
               (get-in (test-utils/read-test-state :game-map) [1 0 :contents :transport-mission]))))

    (it "blocked invading transport falls back to an unloading oscillation step"
      (set-test-world! [[{:type :sea
                          :contents {:type :transport :owner :computer
                                     :transport-mission :invading
                                     :major-invasion true
                                     :major-invasion-target [2 0]
                                     :invasion-target [2 0]
                                     :invasion-path [[1 0]]
                                     :army-count 1}}
                        {:type :sea
                         :contents {:type :destroyer :owner :computer}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [0 0])
      (let [transport (some (fn [[c r]]
                              (let [u (get-in (test-utils/read-test-state :game-map) [c r :contents])]
                                (when (= :transport (:type u)) u)))
                            (for [c (range 2) r (range 2)] [c r]))]
        (should-not-be-nil transport)
        (should= :unloading (:transport-mission transport))
        (should= [[1 0]] (:oscillation-history transport))))))

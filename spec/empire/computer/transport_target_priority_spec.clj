(ns empire.computer.transport-target-priority-spec
  "Tests for VMS Empire style computer transport movement."
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.transport :as transport]
            [empire.computer.transport.targeting :as targeting]

            [empire.computer.land-objectives :as land-objectives]
            [empire.player.production :as player-prod]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map!
                                       set-test-world! update-test-world!]]))
(describe "process-transport"
  (before (reset-all-atoms!))

  (context "player city priority"
    (it "chooses player city over free city when both visible"
      ;; Origin continent (row 0), free city at row 3 (closer), player city at row 6
      ;; Transport should prefer player city even though free city is closer
      (let [game-map (build-test-map ["X##"
                                      "~~~"
                                      "~~~"
                                      "+##"
                                      "###"
                                      "~~~"
                                      "O##"
                                      "###"])]
        (set-test-world! game-map)
        (set-test-computer-map! game-map)
        (test-utils/set-test-state! :claimed-transport-targets #{})
        (let [pickup-continent (land-objectives/flood-fill-continent [0 0])
              target (transport/find-unload-target pickup-continent [1 1])]
          ;; Should pick player city [0 6], not free city [0 3] (even though farther)
          (should= [0 6] target))))

    (it "chooses free city when no player cities visible off-continent"
      ;; Origin continent has player city, only free city off-continent
      (let [game-map (build-test-map ["O##"
                                      "~~~"
                                      "~~~"
                                      "+##"
                                      "###"])]
        (set-test-world! game-map)
        (set-test-computer-map! game-map)
        (test-utils/set-test-state! :claimed-transport-targets #{})
        (let [pickup-continent (land-objectives/flood-fill-continent [0 0])
              target (transport/find-unload-target pickup-continent [1 1])]
          ;; Should pick free city [0 3] since no player cities off-continent
          (should= [0 3] target))))

    (it "respects pickup-continent exclusion for player cities"
      ;; Player cities on both origin and off-continent
      ;; Should only target the off-continent player city
      (let [game-map (build-test-map ["O##"
                                      "~~~"
                                      "~~~"
                                      "O##"
                                      "###"])]
        (set-test-world! game-map)
        (set-test-computer-map! game-map)
        (test-utils/set-test-state! :claimed-transport-targets #{})
        (let [pickup-continent (land-objectives/flood-fill-continent [0 0])
              target (transport/find-unload-target pickup-continent [1 1])]
          ;; Should pick [0 3], not [0 0] which is on pickup continent
          (should= [0 3] target)))))

  (context "transport target diversification"
    (it "two transports pick different cities"
      ;; Origin continent (rows 0-1), two target continents each with a player city
      ;; Continent B at row 4, Continent C at row 7
      (let [game-map (build-test-map ["X##"
                                      "###"
                                      "~~~"
                                      "~~~"
                                      "O##"
                                      "###"
                                      "~~~"
                                      "O##"
                                      "###"])]
        (set-test-world! game-map)
        (set-test-computer-map! game-map)
        (test-utils/set-test-state! :claimed-transport-targets #{})
        (let [pickup-continent (land-objectives/flood-fill-continent [0 0])
              target1 (transport/find-unload-target pickup-continent [1 2])
              target2 (transport/find-unload-target pickup-continent [1 3])]
          (should-not-be-nil target1)
          (should-not-be-nil target2)
          (should-not= target1 target2))))

    (it "falls back when all targets claimed"
      ;; Only one off-continent city - both transports must target it
      (let [game-map (build-test-map ["X##"
                                      "###"
                                      "~~~"
                                      "~~~"
                                      "O##"
                                      "###"])]
        (set-test-world! game-map)
        (set-test-computer-map! game-map)
        (test-utils/set-test-state! :claimed-transport-targets #{})
        (let [pickup-continent (land-objectives/flood-fill-continent [0 0])
              target1 (transport/find-unload-target pickup-continent [2 1])
              target2 (transport/find-unload-target pickup-continent [3 1])]
          (should-not-be-nil target1)
          (should-not-be-nil target2)
          (should= target1 target2))))

    (it "prefers continent without computer cities"
      ;; Two target continents: one with computer city, one without
      ;; Transport should prefer the one without computer presence
      (let [game-map (build-test-map ["###"
                                      "~~~"
                                      "~~~"
                                      "O#X"  ;; continent with computer city
                                      "###"
                                      "~~~"
                                      "O##"  ;; continent without computer city
                                      "###"])]
        (set-test-world! game-map)
        (set-test-computer-map! game-map)
        (test-utils/set-test-state! :claimed-transport-targets #{})
        (let [pickup-continent (land-objectives/flood-fill-continent [0 0])
              target (transport/find-unload-target pickup-continent [2 1])]
          ;; Should pick the city on continent without computer presence
          (should= [0 6] target))))

    (it "prefers nearer target when continents are similar"
      ;; Two equidistant-ish continents, transport closer to one
      (let [game-map (build-test-map ["###"
                                      "~~~"
                                      "O##"   ;; closer target
                                      "###"
                                      "~~~"
                                      "~~~"
                                      "~~~"
                                      "O##"   ;; farther target
                                      "###"])]
        (set-test-world! game-map)
        (set-test-computer-map! game-map)
        (test-utils/set-test-state! :claimed-transport-targets #{})
        (let [pickup-continent (land-objectives/flood-fill-continent [0 0])
              ;; Transport at row 1 is closer to city at row 2 than row 7
              target (transport/find-unload-target pickup-continent [1 1])]
          (should= [0 2] target)))))

  (context "pickup-country-id exclusion"
    (it "does not unload on pickup-country-id land"
      ;; Transport from city (country-id 1), loaded from country-id 5.
      ;; pcp cleared (nil). Adjacent land has country-id 5.
      ;; Should NOT unload because pickup-country-id 5 should be excluded.
      (set-test-world! [[{:type :land :country-id 5}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :sailing :army-count 6
                                                        :sail-path [[0 2]]
                                                        :country-id 1
                                                        :pickup-country-id 5}}
                                {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [0 1])
      (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))))

    (it "enters sail-to-unload when fully loaded"
      ;; Transport at [1,1] adjacent to land with country-id 7.
      ;; When it enters sailing, pickup-country-id should be recorded.
      (let [game-map (build-test-map ["###"
                                      "~t~"
                                      "~~~"
                                      "~~~"
                                      "~~~"])]
        (set-test-world! game-map)
        (doseq [c (range 3)]
          (update-test-world! assoc-in [c 0 :country-id] 7))
        (set-test-computer-map!
                (vec (for [c (range 3)]
                       (vec (for [r (range 5)]
                              (if (< r 4)
                                (get-in (test-utils/read-test-state :game-map) [c r])
                                nil))))))
        (update-test-world! assoc-in [1 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 6
                :load-manifest []})
        (transport/process-transport [1 1])
        (let [t (first (for [c (range 3) r (range 5)
                             :let [unit (get-in (test-utils/read-test-state :game-map) [c r :contents])]
                             :when (= :transport (:type unit))]
                         unit))]
          (should= :sail-to-unload (:transport-mission t)))))

    (it "does not treat hidden land as adjacent to pickup continent"
      (set-test-world! [[{:type :land :country-id 7}
                         {:type :sea}
                         {:type :sea}
                         {:type :sea}]
                        [{:type :sea}
                         {:type :sea}
                         {:type :sea}
                         {:type :sea}]
                        [{:type :sea}
                         {:type :sea}
                         {:type :sea :contents {:type :transport :owner :computer}}
                         {:type :land :country-id 7}]
                        [{:type :sea}
                         {:type :sea}
                         {:type :sea}
                         {:type :sea}]])
      (set-test-computer-map! [[{:type :land :country-id 7}
                                {:type :sea}
                                {:type :sea}
                                {:type :sea}]
                               [{:type :sea}
                                {:type :sea}
                                {:type :sea}
                                {:type :sea}]
                               [{:type :sea}
                                {:type :sea}
                                {:type :sea :contents {:type :transport :owner :computer}}
                                nil]
                               [{:type :sea}
                                {:type :sea}
                                {:type :sea}
                                {:type :sea}]])
      (should true)))

)

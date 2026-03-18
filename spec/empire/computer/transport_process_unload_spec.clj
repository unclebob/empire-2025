(ns empire.computer.transport-process-unload-spec
  "Tests for VMS Empire style computer transport movement."
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.transport :as transport]
            [empire.computer.land-objectives :as land-objectives]
            [empire.player.production :as player-prod]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-world! update-test-world!]]))

(describe "process-transport"
  (before (reset-all-atoms!))

  (context "continent-aware unloading"
    (it "find-unload-target excludes origin continent cities"
      ;; Two continents separated by sea. Each has a player city.
      ;; Origin continent A (rows 0-1), continent B (rows 4-5)
      (let [game-map (build-test-map ["O##"
                                      "###"
                                      "~~~"
                                      "~~~"
                                      "###"
                                      "O##"])]
        (set-test-world! game-map)
        (set-test-computer-map! game-map)
        (let [pickup-continent (land-objectives/flood-fill-continent [0 0])
              target (transport/find-unload-target pickup-continent [1 3])]
          ;; Should return the city on continent B, not continent A
          (should-not-be-nil target)
          (should= [0 5] target))))

    (it "unload-armies skips origin continent land"
      ;; Transport at [1,1] in 1-wide sea channel between two continents
      ;; Left continent (col 0), right continent (col 2)
      (let [game-map (build-test-map ["#~#"
                                      "#t#"
                                      "#~#"])]
        (set-test-world! game-map)
        (set-test-computer-map! game-map)
        (update-test-world! assoc-in [1 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :unloading :army-count 2
                :pickup-continent-pos [0 0]})
        (set-test-computer-map! (test-utils/read-test-state :game-map))
        (let [pickup-continent (land-objectives/flood-fill-continent [0 0])]
          (transport/unload-armies [1 1] pickup-continent)
          ;; Armies should NOT appear on origin continent (col 0)
          (let [origin-armies (count (for [r (range 3)
                                          :let [cell (get-in (test-utils/read-test-state :game-map) [0 r])]
                                          :when (= :army (:type (:contents cell)))]
                                      true))
                other-armies (count (for [r (range 3)
                                         :let [cell (get-in (test-utils/read-test-state :game-map) [2 r])]
                                         :when (= :army (:type (:contents cell)))]
                                     true))]
            (should= 0 origin-armies)
            (should (pos? other-armies)))))))

  (context "coastline exploration priority"
    (it "idle transport moves toward unexplored coastline over open sea"
      ;; Known land at row 4, unexplored cells near it. Also unexplored open sea at [0,2].
      ;; Transport should prefer coastline frontier (near row 3-4) over open sea [0,2].
      (set-test-world! (build-test-map ["~~~"
                                              "~~~"
                                              "~t~"
                                              "~~~"
                                              "###"]))
      (set-test-computer-map! [[nil {:type :sea} {:type :sea} {:type :sea} {:type :land}]
                                  [{:type :sea} {:type :sea} {:type :sea} {:type :sea} {:type :land}]
                                  [{:type :sea} {:type :sea} {:type :sea} nil nil]])
      (update-test-world! assoc-in [1 2 :contents]
             {:type :transport :owner :computer
              :transport-mission :loading :army-count 0})
      (set-test-computer-map!
              (assoc-in (test-utils/read-test-state :computer-map)
                        [1 2 :contents]
                        {:type :transport :owner :computer
                         :transport-mission :loading :army-count 0}))
      (transport/process-transport [1 2])
      ;; Transport should have moved toward the coastline, not toward [0,0]
      (let [transport-pos (first (for [r (range 5) c (range 3)
                                       :when (= :transport (get-in (test-utils/read-test-state :game-map) [c r :contents :type]))]
                                   [c r]))]
        (should-not-be-nil transport-pos)
        ;; Should move south toward coastline
        (should (>= (second transport-pos) 2))))

    (it "stays put in open sea when no coastline frontier"
      ;; No known land, only unexplored open sea — no coastal crawl targets
      (set-test-world! (build-test-map ["~~~"
                                              "~t~"
                                              "~~~"]))
      (set-test-computer-map! [[{:type :sea} {:type :sea} {:type :sea}]
                                  [{:type :sea} {:type :sea} {:type :sea}]
                                  [{:type :sea} {:type :sea} nil]])
      (update-test-world! assoc-in [1 1 :contents]
             {:type :transport :owner :computer
              :transport-mission :loading :army-count 0})
      (set-test-computer-map!
              (assoc-in (test-utils/read-test-state :computer-map)
                        [1 1 :contents]
                        {:type :transport :owner :computer
                         :transport-mission :loading :army-count 0}))
      (transport/process-transport [1 1])
      ;; Empty loading transport with no coastal crawl targets stays put
      (should= :transport (get-in (test-utils/read-test-state :game-map) [1 1 :contents :type]))))

  (context "sailing behavior"
    ;; heading-based sailing tests removed — replaced by sail-path sailing


    (it "sailing transport with sail-path unloads at unexplored coast"
      ;; Transport at [2 2] with sail-path toward col 3.
      ;; Land at col 4 is unexplored. Transport moves to [3 2] and
      ;; opportunistically unloads an army.
      (let [game-map (build-test-map ["~~~~#"
                                      "~~~~#"
                                      "~~~~#"
                                      "~~~~#"
                                      "~~~~#"])]
        (set-test-world! game-map)
        (set-test-computer-map! [(vec (repeat 5 {:type :sea}))
                                    (vec (repeat 5 {:type :sea}))
                                    (vec (repeat 5 {:type :sea}))
                                    (vec (repeat 5 {:type :sea}))
                                    (vec (repeat 5 nil))])
        (update-test-world! assoc-in [2 2 :contents]
               {:type :transport :owner :computer
                :transport-mission :sailing :army-count 6
                :sail-path [[3 2]]})
        (set-test-computer-map!
                (assoc-in (test-utils/read-test-state :computer-map)
                          [2 2 :contents]
                          {:type :transport :owner :computer
                           :transport-mission :sailing :army-count 6
                           :sail-path [[3 2]]}))
        (transport/process-transport [2 2])
        ;; Transport should move to [3 2] and unload to 3 adjacent land cells
        (let [transport (:contents (get-in (test-utils/read-test-state :game-map) [3 2]))]
          (should= :transport (:type transport))
          (should= 3 (:army-count transport)))
        ;; 3 armies on adjacent land at [4,1],[4,2],[4,3]
        (let [armies-on-land (count (for [r (range 5)
                                          :let [cell (get-in (test-utils/read-test-state :game-map) [4 r])]
                                          :when (= :army (:type (:contents cell)))]
                                     true))]
          (should= 3 armies-on-land))))

    (it "transport does NOT unload on same continent"
      ;; Integration test: full transport near origin continent, only city on origin continent
      ;; Transport should NOT unload, should explore instead
      (let [game-map (build-test-map ["O##"
                                      "###"
                                      "~t~"
                                      "~~~"
                                      "~~~"])]
        (set-test-world! game-map)
        (set-test-computer-map! game-map)
        (update-test-world! assoc-in [1 2 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 6
                :pickup-continent-pos [1 0]})
        (set-test-computer-map! (test-utils/read-test-state :game-map))
        (transport/process-transport [1 2])
        ;; No armies should be unloaded onto the origin continent
        (let [armies-on-land (count (for [r (range 2) c (range 3)
                                         :let [cell (get-in (test-utils/read-test-state :game-map) [c r])]
                                         :when (= :army (:type (:contents cell)))]
                                     true))]
          (should= 0 armies-on-land))))

    (it "transport does NOT unload on own-country land"
      ;; Transport adjacent to land that has country-id (claimed). Should not unload.
      (set-test-world! [[{:type :land :country-id 1}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :sailing :army-count 2
                                                        :sail-path [[0 2]]
                                                        :country-id 1}}
                                {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [0 1])
      ;; No army should appear on the claimed land
      (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))))

    (it "sailing transport adjacent to empty land unloads opportunistically"
      ;; Transport sailing with armies, adjacent to empty land.
      ;; Opportunistic unload fires before sailing logic.
      (set-test-world! [[{:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :sailing :army-count 2
                                                        :sail-path [[0 2]]}}
                                {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [0 1])
      ;; Army should be unloaded onto the empty land
      (should= :army (get-in (test-utils/read-test-state :game-map) [0 0 :contents :type]))
      ;; Transport should have fewer armies
      (should= 1 (get-in (test-utils/read-test-state :game-map) [0 1 :contents :army-count]))))

  (context "sail trigger boundaries"
    (it "transport with 4 armies and no nearby armies starts sailing"
      ;; ~~~   all sea
      ;; ~t~   transport at [1,1] with 4 armies, no adjacent armies
      ;; ~~~   all sea (no adjacent land = no opportunistic unload)
      (set-test-world! (build-test-map ["~~~"
                                               "~t~"
                                               "~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [1 1 :contents]
             {:type :transport :owner :computer
              :transport-mission :loading :army-count 4})
      (transport/process-transport [1 1])
      (let [t (some (fn [[c r]]
                      (let [u (get-in (test-utils/read-test-state :game-map) [c r :contents])]
                        (when (= :transport (:type u)) u)))
                    (for [c (range 3) r (range 3)] [c r]))]
        (should= :sailing (:transport-mission t))))

    (it "transport with 6 armies starts sailing even with nearby armies"
      ;; a###   army at [0,0], land
      ;; ~t~~   transport at [1,1] with 6 armies
      (set-test-world! (build-test-map ["a###"
                                               "~t~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [1 1 :contents]
             {:type :transport :owner :computer
              :transport-mission :loading :army-count 6})
      (transport/process-transport [1 1])
      (let [t (some (fn [[c r]]
                      (let [u (get-in (test-utils/read-test-state :game-map) [c r :contents])]
                        (when (= :transport (:type u)) u)))
                    (for [c (range 4) r (range 2)] [c r]))]
        (should= :sailing (:transport-mission t))))

    (it "transport with 3 armies stays loading"
      ;; ####   land at row 0
      ;; ~t~~   transport at [1,1] with 3 armies
      (set-test-world! (build-test-map ["####"
                                               "~t~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [1 1 :contents]
             {:type :transport :owner :computer
              :transport-mission :loading :army-count 3})
      (transport/process-transport [1 1])
      (let [t (some (fn [[c r]]
                      (let [u (get-in (test-utils/read-test-state :game-map) [c r :contents])]
                        (when (= :transport (:type u)) u)))
                    (for [c (range 4) r (range 2)] [c r]))]
        (should= :loading (:transport-mission t)))))

  (context "unloaded army properties"
    (it "unload-armies produces army with hits 1"
      ;; #t#   land-sea-land, transport at [1,0] with 2 armies
      (set-test-world! [[{:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :unloading
                                                        :army-count 2}}
                                {:type :land}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/unload-armies [0 1] nil)
      (should= 1 (:hits (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))))
      (should= 1 (:hits (:contents (get-in (test-utils/read-test-state :game-map) [0 2])))))

    (it "opportunistic unload produces army with hits 1 and unload-event-id"
      ;; #t#   transport at [1,0] sailing with unload-event-id 42
      (set-test-world! [[{:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :sailing
                                                        :army-count 2
                                                        :unload-event-id 42
                                                        :sail-path [[0 2]]}}
                                {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [0 1])
      (should= 1 (:hits (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))))
      (should= 42 (:unload-event-id (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))))))

  (context "unload country-id tracking"
    (it "unload-armies records country-id in unloaded-countries"
      ;; Land at [0,0] has country-id 7. Transport unloads army there.
      (test-utils/set-test-state! :round-number 5)
      (set-test-world! [[{:type :land :country-id 7}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :unloading
                                                        :army-count 1}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/unload-armies [0 1] nil)
      (let [transport (:contents (get-in (test-utils/read-test-state :game-map) [0 1]))]
        (should= 5 (get-in transport [:unloaded-countries 7]))))

    (it "opportunistic unload records country-id in unloaded-countries"
      (test-utils/set-test-state! :round-number 10)
      (set-test-world! [[{:type :land :country-id 3}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :sailing
                                                        :army-count 1
                                                        :sail-path [[0 2]]}}
                                {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [0 1])
      ;; Find transport wherever it ended up
      (let [t (some (fn [c]
                      (let [u (get-in (test-utils/read-test-state :game-map) [0 c :contents])]
                        (when (= :transport (:type u)) u)))
                    (range 3))]
        (should= 10 (get-in t [:unloaded-countries 3])))))

  (context "full-unload boundary"
    (it "transport with army-count equal to adjacent land cells transitions to loading"
      ;; #t#   2 land cells, transport with 2 armies -> fully unloaded -> loading
      (set-test-world! [[{:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :unloading
                                                        :army-count 2}}
                                {:type :land}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/unload-armies [0 1] nil)
      (should= :loading (:transport-mission (:contents (get-in (test-utils/read-test-state :game-map) [0 1]))))
      (should= 0 (:army-count (:contents (get-in (test-utils/read-test-state :game-map) [0 1])))))

    (it "transport with more armies than adjacent land cells stays unloading"
      ;; #t#   2 land cells, transport with 3 armies -> partial unload -> stays unloading
      (set-test-world! [[{:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :unloading
                                                        :army-count 3}}
                                {:type :land}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/unload-armies [0 1] nil)
      (should= :unloading (:transport-mission (:contents (get-in (test-utils/read-test-state :game-map) [0 1]))))
      (should= 1 (:army-count (:contents (get-in (test-utils/read-test-state :game-map) [0 1]))))))

  (context "unloaded army inherits transport properties"
    (it "army gets :unload-country-id from transport"
      (set-test-world! [[{:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :unloading
                                                        :army-count 1
                                                        :unload-country-id 42}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/unload-armies [0 1] nil)
      (should= 42 (:country-id (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))))

    (it "army gets :unload-event-id from transport"
      (set-test-world! [[{:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :unloading
                                                        :army-count 1
                                                        :unload-event-id 99}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/unload-armies [0 1] nil)
      (should= 99 (:unload-event-id (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))))

    (it "army gets transport's country-id when no unload-country-id"
      (set-test-world! [[{:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :unloading
                                                        :army-count 1
                                                        :country-id 7}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/unload-armies [0 1] nil)
      (should= 7 (:country-id (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))))

    (it "skips unloaded-countries recording when land has no country-id"
      (set-test-world! [[{:type :land}  ; no :country-id
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :unloading
                                                        :army-count 1}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/unload-armies [0 1] nil)
      (let [t (:contents (get-in (test-utils/read-test-state :game-map) [0 1]))]
        (should-be-nil (:unloaded-countries t)))))
)

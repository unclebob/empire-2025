(ns empire.computer.production-spec
  "Tests for VMS Empire style computer production."
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.production :as production]
            [empire.computer.ship :as ship]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-world! update-test-world!]]))

;; ===== 1. utility functions =====

(describe "utility functions"
  (before (reset-all-atoms!))

  (context "city-is-coastal?"

    (it "returns true when city has adjacent sea"
      (set-test-world! (build-test-map ["~X#"]))
      (should (production/city-is-coastal? [1 0])))

    (it "returns false when city has no adjacent sea"
      (set-test-world! (build-test-map ["#X#"]))
      (should-not (production/city-is-coastal? [1 0]))))

  (context "count-computer-units"

    (it "counts computer units by type"
      (set-test-world! (build-test-map ["aad"]))
      (let [counts (production/count-computer-units)]
        (should= 2 (get counts :army))
        (should= 1 (get counts :destroyer))))

    (it "ignores player units"
      (set-test-world! (build-test-map ["aAD"]))
      (let [counts (production/count-computer-units)]
        (should= 1 (get counts :army))
        (should-be-nil (get counts :destroyer)))))

  (context "count-computer-cities"

    (it "counts computer cities"
      (set-test-world! (build-test-map ["X#X~O"]))
      (should= 2 (production/count-computer-cities)))

    (it "ignores player and free cities"
      (set-test-world! (build-test-map ["O+X"]))
      (should= 1 (production/count-computer-cities))))

  (context "count-country-armies default army-count (L79)"

    (it "counts 0 armies aboard transport with no :army-count key"
      ;; Transport with no :army-count key should default to 0, not 1
      (set-test-world! (build-test-map ["~t"]))
      (update-test-world! assoc-in [1 0 :contents :country-id] 1)
      (update-test-world! assoc-in [1 0 :contents :army-count] nil)
      (update-test-world! update-in [1 0 :contents] dissoc :army-count)
      (production/rebuild-country-stats!)
      (should= 0 (production/count-country-armies 1)))))

;; ===== 2. production decisions =====

(describe "production decisions"
  (before (reset-all-atoms!))

  (context "priority-based production"

    (it "country city produces fighter via per-country priority when 0 fighters exist"
      ;; 2-row map: coastal city, armies fill all coastal cells, transport+escort, 4 patrol boats
      ;; Row 0: ~ X a a t d ~ p p p p
      ;; Row 1: ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~
      ;; Coastal cells: [2,0],[3,0] — filled by armies → army priority satisfied
      (set-test-world! (build-test-map ["~Xaatd~pppp"
                                               "~~~~~~~~~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [1 0 :country-id] 1)
      (doseq [col [2 3]]
        (update-test-world! assoc-in [col 0 :country-id] 1)
        (update-test-world! assoc-in [col 0 :contents :country-id] 1))
      (update-test-world! assoc-in [4 0 :contents :country-id] 1)
      (update-test-world! assoc-in [4 0 :contents :transport-id] 1)
      (update-test-world! assoc-in [4 0 :contents :escort-destroyer-id] 1)
      (doseq [col [7 8 9 10]]
        (update-test-world! assoc-in [col 0 :contents :country-id] 1))
      (production/rebuild-country-stats!)
      (should= :fighter (production/decide-production [1 0])))

    (it "inland country city skips coastal priorities and produces army"
      ;; Inland city in a country with unfilled coastal cells
      ;; Row 0: ~ # X #
      ;; Row 1: ~ # # #
      ;; City at [2,0] is inland (surrounded by land). Country has coastal cell [1,0] unfilled.
      (set-test-world! (build-test-map ["~#X#"
                                               "~###"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (doseq [col [1 2 3]]
        (update-test-world! assoc-in [col 0 :country-id] 1)
        (update-test-world! assoc-in [col 1 :country-id] 1))
      (production/rebuild-country-stats!)
      (should= :army (production/decide-production [2 0]))))

  (context "decide-production"

    (it "coastal country city with 6+ coastal armies produces transport"
      ;; 2-row map with armies on coastal cells (land adjacent to sea)
      ;; Row 0: ~ X a a a a a a
      ;; Row 1: ~ ~ ~ ~ ~ ~ ~ ~
      ;; Armies at [2-7,0] are adjacent to sea at row 1
      (set-test-world! (build-test-map ["~Xaaaaaa"
                                               "~~~~~~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [1 0 :country-id] 1)
      (doseq [col (range 2 8)]
        (update-test-world! assoc-in [col 0 :country-id] 1)
        (update-test-world! assoc-in [col 0 :contents :country-id] 1))
      (production/rebuild-country-stats!)
      (should= :transport (production/decide-production [1 0]))))

  (context "country-aware production"

    (it "coastal city produces transport when country has 6+ coastal armies waiting"
      ;; 2-row map: armies on coastal cells, no transports
      (set-test-world! (build-test-map ["~Xaaaaaa"
                                               "~~~~~~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [1 0 :country-id] 1)
      (doseq [col (range 2 8)]
        (update-test-world! assoc-in [col 0 :country-id] 1)
        (update-test-world! assoc-in [col 0 :contents :country-id] 1))
      (production/rebuild-country-stats!)
      (should= :transport (production/decide-production [1 0])))

    (it "coastal city does not produce transport when country has fewer than 6 armies"
      ;; 2-row map: only 5 coastal armies
      (set-test-world! (build-test-map ["~Xaaaaa"
                                               "~~~~~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [1 0 :country-id] 1)
      (doseq [col (range 2 7)]
        (update-test-world! assoc-in [col 0 :country-id] 1)
        (update-test-world! assoc-in [col 0 :contents :country-id] 1))
      (production/rebuild-country-stats!)
      (should-not= :transport (production/decide-production [1 0])))

    (it "two coastal cities can both produce transports simultaneously"
      ;; Two coastal cities, plenty of coastal armies, no transports
      ;; Row 0: ~ X a a a a a a X a a a a a
      ;; Row 1: ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~
      (set-test-world! (build-test-map ["~XaaaaaaXaaaaa"
                                               "~~~~~~~~~~~~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [1 0 :country-id] 1)
      (update-test-world! assoc-in [8 0 :country-id] 1)
      (doseq [col (concat (range 2 8) (range 9 14))]
        (update-test-world! assoc-in [col 0 :country-id] 1)
        (update-test-world! assoc-in [col 0 :contents :country-id] 1))
      (production/rebuild-country-stats!)
      (let [first-decision (production/decide-production [1 0])]
        (test-utils/set-test-state! :production {[1 0] {:item :transport :remaining-rounds 20}})
        (should= :transport first-decision)
        (should= :transport (production/decide-production [8 0]))))

    (it "does not produce transport when existing transport has room"
      ;; Transport with army-count < 6 and :loading mission
      (set-test-world! (build-test-map ["~Xaaaaaaa~t"
                                               "~~~~~~~~~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [1 0 :country-id] 1)
      (doseq [col (range 2 9)]
        (update-test-world! assoc-in [col 0 :country-id] 1)
        (update-test-world! assoc-in [col 0 :contents :country-id] 1))
      (update-test-world! assoc-in [10 0 :contents :country-id] 1)
      (update-test-world! assoc-in [10 0 :contents :transport-id] 1)
      (update-test-world! assoc-in [10 0 :contents :army-count] 2)
      (update-test-world! assoc-in [10 0 :contents :transport-mission] :loading)
      (production/rebuild-country-stats!)
      (should-not= :transport (production/decide-production [1 0])))

    (it "landlocked city does not produce transport even when country needs one"
      (set-test-world! (build-test-map ["###"
                                               "#X#"
                                               "###"]))
      (set-test-computer-map! (build-test-map ["###"
                                                   "#X#"
                                                   "###"]))
      (update-test-world! assoc-in [1 1 :country-id] 1)
      (production/rebuild-country-stats!)
      (should-not= :transport (production/decide-production [1 1])))

    (it "produces army when coastal cells not filled"
      ;; 2-row map: coastal city, 2 armies but unfilled coastal cells, 4 patrol boats
      ;; Row 0: ~ X # a a # # ~ p p p p
      ;; Row 1: ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~
      ;; Coastal cells of country: [2,0],[3,0],[4,0],[5,0],[6,0] — all adj to sea at row 1
      ;; Armies only at [3,0] and [4,0], still unfilled coastal cells
      (set-test-world! (build-test-map ["~X#aa##~pppp"
                                               "~~~~~~~~~~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [1 0 :country-id] 1)
      (doseq [col [2 3 4 5 6]]
        (update-test-world! assoc-in [col 0 :country-id] 1))
      (update-test-world! assoc-in [3 0 :contents :country-id] 1)
      (update-test-world! assoc-in [4 0 :contents :country-id] 1)
      (doseq [col [8 9 10 11]]
        (update-test-world! assoc-in [col 0 :contents :country-id] 1))
      (production/rebuild-country-stats!)
      (should= :army (production/decide-production [1 0])))

    (it "does not produce army when another city in country is already producing armies"
      ;; City 1 at [1,0] already producing army, city 2 at [3,0] should not also produce army
      ;; 2-row map so cities have coastal cells
      (set-test-world! (build-test-map ["~X~X~"
                                               "~~~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [1 0 :country-id] 1)
      (update-test-world! assoc-in [3 0 :country-id] 1)
      (test-utils/set-test-state! :production {[1 0] {:item :army :remaining-rounds 3}})
      (production/rebuild-country-stats!)
      (should-not= :army (production/decide-production [3 0]))))

  (context "country-city-producing? coordination"

    (it "returns true when another city in country is producing the unit type"
      (set-test-world! (build-test-map ["~X~X~"]))
      (update-test-world! assoc-in [1 0 :country-id] 1)
      (update-test-world! assoc-in [3 0 :country-id] 1)
      (test-utils/set-test-state! :production {[1 0] {:item :transport :remaining-rounds 10}})
      (should (production/country-city-producing? [3 0] 1 :transport)))

    (it "returns false when no other city in country is producing the unit type"
      (set-test-world! (build-test-map ["~X~X~"]))
      (update-test-world! assoc-in [1 0 :country-id] 1)
      (update-test-world! assoc-in [3 0 :country-id] 1)
      (test-utils/set-test-state! :production {})
      (should-not (production/country-city-producing? [3 0] 1 :transport)))

    (it "returns false when city producing is from different country"
      (set-test-world! (build-test-map ["~X~X~"]))
      (update-test-world! assoc-in [1 0 :country-id] 2)
      (update-test-world! assoc-in [3 0 :country-id] 1)
      (test-utils/set-test-state! :production {[1 0] {:item :transport :remaining-rounds 10}})
      (should-not (production/country-city-producing? [3 0] 1 :transport)))

    (it "produces transport even when another city in country is already producing one"
      ;; 2-row map: coastal armies fill all coastal cells → army priority met
      ;; Another city already producing transport — second city should also produce transport
      (set-test-world! (build-test-map ["~X~Xaaaaaa"
                                               "~~~~~~~~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [1 0 :country-id] 1)
      (update-test-world! assoc-in [3 0 :country-id] 1)
      (doseq [col (range 4 10)]
        (update-test-world! assoc-in [col 0 :country-id] 1)
        (update-test-world! assoc-in [col 0 :contents :country-id] 1))
      (test-utils/set-test-state! :production {[1 0] {:item :transport :remaining-rounds 10}})
      (production/rebuild-country-stats!)
      (should= :transport (production/decide-production [3 0])))

    (it "does not produce destroyer when another city in country is already producing"
      ;; Two coastal cities, same country, 200 armies in transport, 4 patrol boats, first producing destroyer
      (set-test-world! (build-test-map ["~X~X~t~pppp"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [1 0 :country-id] 1)
      (update-test-world! assoc-in [3 0 :country-id] 1)
      (update-test-world! assoc-in [5 0 :contents :country-id] 1)
      (update-test-world! assoc-in [5 0 :contents :army-count] 200)
      (doseq [col [7 8 9 10]]
        (update-test-world! assoc-in [col 0 :contents :country-id] 1))
      (test-utils/set-test-state! :production {[1 0] {:item :destroyer :remaining-rounds 10}})
      (production/rebuild-country-stats!)
      (should= :fighter (production/decide-production [3 0]))))

  (context "army limit 2/3 of coastal cells"

    (it "army limit reached at 2/3 of coastal cells"
      ;; 6 coastal land cells (cols 2-7, row 0) adjacent to sea at row 1
      ;; Place 4 armies -> 2/3 * 6 = 4, so limit reached
      (set-test-world! (build-test-map ["~X######~"
                                               "~~~~~~~~~"]))
      (doseq [col (range 2 8)]
        (update-test-world! assoc-in [col 0 :country-id] 1))
      (doseq [col (range 2 6)]
        (update-test-world! assoc-in [col 0 :contents]
               {:type :army :owner :computer :hits 1 :country-id 1}))
      (production/rebuild-country-stats!)
      (should (#'empire.computer.production/country-army-limit-reached? 1)))

    (it "army limit not reached below 2/3 of coastal cells"
      ;; 6 coastal land cells (cols 2-7, row 0) adjacent to sea at row 1
      ;; Place 3 armies -> 3 < 4, so limit NOT reached
      (set-test-world! (build-test-map ["~X######~"
                                               "~~~~~~~~~"]))
      (doseq [col (range 2 8)]
        (update-test-world! assoc-in [col 0 :country-id] 1))
      (doseq [col (range 2 5)]
        (update-test-world! assoc-in [col 0 :contents]
               {:type :army :owner :computer :hits 1 :country-id 1}))
      (production/rebuild-country-stats!)
      (should-not (#'empire.computer.production/country-army-limit-reached? 1))))

  (context "process-computer-city"

    (it "sets production when none exists and city has a country-id"
      (set-test-world! (build-test-map ["X+#"]))
      (set-test-computer-map! (build-test-map ["X+#"]))
      (update-test-world! assoc-in [0 0 :country-id] 1)
      (test-utils/set-test-state! :production {})
      (production/rebuild-country-stats!)
      (production/process-computer-city [0 0])
      (should-not-be-nil (get (test-utils/read-test-state :production) [0 0])))

    (it "does not change existing production"
      (set-test-world! (build-test-map ["X#"]))
      (set-test-computer-map! (build-test-map ["X#"]))
      (test-utils/set-test-state! :production {[0 0] {:item :fighter :remaining-rounds 10}})
      (production/process-computer-city [0 0])
      (should= :fighter (:item (get (test-utils/read-test-state :production) [0 0])))))

  (context "early production"

    (it "produces patrol boat from coastal city when trigger fired"
      (let [game-map (build-test-map ["X~~"
                                      "###"
                                      "~~~"])]
        (set-test-world! game-map)
        (test-utils/set-test-state! :transport-fully-loaded? true)
        (test-utils/set-test-state! :early-patrol-boat-produced? false)
        (update-test-world! assoc-in [0 0 :country-id] 1)
        (production/rebuild-country-stats!)
        (should= :patrol-boat (production/decide-production [0 0]))
        (should (test-utils/read-test-state :early-patrol-boat-produced?))))

    (it "produces satellite from inland city after patrol boat flag set"
      (let [game-map (build-test-map ["X~~~"
                                      "####"
                                      "#X##"
                                      "####"])]
        (set-test-world! game-map)
        (test-utils/set-test-state! :transport-fully-loaded? true)
        (test-utils/set-test-state! :early-patrol-boat-produced? true)
        (test-utils/set-test-state! :early-satellite-produced? false)
        (update-test-world! assoc-in [1 2 :country-id] 1)
        (production/rebuild-country-stats!)
        (should= :satellite (production/decide-production [1 2]))))

    (it "does not produce satellite before patrol boat flag set"
      (let [game-map (build-test-map ["X~~~"
                                      "####"
                                      "#X##"
                                      "####"])]
        (set-test-world! game-map)
        (test-utils/set-test-state! :transport-fully-loaded? true)
        (test-utils/set-test-state! :early-patrol-boat-produced? false)
        (test-utils/set-test-state! :early-satellite-produced? false)
        (update-test-world! assoc-in [1 2 :country-id] 1)
        (production/rebuild-country-stats!)
        (should-not= :satellite (production/decide-production [1 2]))))

    (it "prefers inland city for satellite over coastal"
      (let [game-map (build-test-map ["X~~~"
                                      "####"
                                      "#X##"
                                      "####"])]
        (set-test-world! game-map)
        (test-utils/set-test-state! :transport-fully-loaded? true)
        (test-utils/set-test-state! :early-patrol-boat-produced? true)
        (test-utils/set-test-state! :early-satellite-produced? false)
        (update-test-world! assoc-in [0 0 :country-id] 1)
        (update-test-world! assoc-in [1 2 :country-id] 1)
        (production/rebuild-country-stats!)
        ;; [0 0] is coastal — should skip satellite, fall through
        (should-not= :satellite (production/decide-production [0 0]))))

    (it "coastal city produces satellite when no inland city exists"
      (let [game-map (build-test-map ["X~~"
                                      "~~~"
                                      "~~~"])]
        (set-test-world! game-map)
        (test-utils/set-test-state! :transport-fully-loaded? true)
        (test-utils/set-test-state! :early-patrol-boat-produced? true)
        (test-utils/set-test-state! :early-satellite-produced? false)
        (update-test-world! assoc-in [0 0 :country-id] 1)
        (production/rebuild-country-stats!)
        (should= :satellite (production/decide-production [0 0]))))))

(run-specs)

(ns empire.computer.production-army-spec
  "Tests for VMS Empire style computer production."
  (:require [speclj.core :refer :all]
            [empire.computer.production :as production]
            [empire.computer.ship :as ship]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-world! update-test-world!]]))

;; ===== 3. army production =====

(describe "army production"
  (before (reset-all-atoms!))

  (context "coastal army production"

    (it "count-country-coastal-cells counts land cells with country-id adjacent to sea"
      ;; Map: ~###~  (row 0)
      ;; Country-id 1 on all land cells. Cells [1,0] and [3,0] are adjacent to sea.
      (set-test-world! (build-test-map ["~###~"]))
      (doseq [col [1 2 3]]
        (update-test-world! assoc-in [col 0 :country-id] 1))
      (production/rebuild-country-stats!)
      (should= 2 (production/count-country-coastal-cells 1)))

    (it "count-country-coastal-cells ignores land with different country-id"
      (set-test-world! (build-test-map ["~###~"]))
      (update-test-world! assoc-in [1 0 :country-id] 1)
      (update-test-world! assoc-in [2 0 :country-id] 2)
      (update-test-world! assoc-in [3 0 :country-id] 1)
      (production/rebuild-country-stats!)
      (should= 2 (production/count-country-coastal-cells 1)))

    (it "produces army when coastal cells not yet filled"
      ;; Coastal city with country-id 1, 2 coastal cells, 0 armies on them
      ;; Transport priority not met (< 6 armies), so army comes first
      (set-test-world! (build-test-map ["~X#~"]))
      (set-test-computer-map! @atoms/game-map)
      (doseq [col [1 2]]
        (update-test-world! assoc-in [col 0 :country-id] 1))
      (production/rebuild-country-stats!)
      (should= :army (production/decide-production [1 0])))

    (it "does not produce army when all coastal cells filled"
      ;; 1-row map: ~X a~ — city at [1,0] with country-id 1
      ;; Coastal cells: [1,0] is a city (not counted as fillable land), [2,0] is land adj to sea
      ;; Army at [2,0] fills the only coastal cell
      ;; Transport also needs >=6 armies, so no transport either
      (set-test-world! (build-test-map ["~Xa~"]))
      (set-test-computer-map! @atoms/game-map)
      (doseq [col [1 2]]
        (update-test-world! assoc-in [col 0 :country-id] 1))
      (update-test-world! assoc-in [2 0 :contents :country-id] 1)
      (production/rebuild-country-stats!)
      (should-not= :army (production/decide-production [1 0])))

    (it "does not produce army when all coastal cells occupied by non-army units"
      ;; Coastal cell [2,0] has a fighter — still considered occupied
      (set-test-world! (build-test-map ["~Xf~"]))
      (set-test-computer-map! @atoms/game-map)
      (doseq [col [1 2]]
        (update-test-world! assoc-in [col 0 :country-id] 1))
      (update-test-world! assoc-in [2 0 :contents :country-id] 1)
      (production/rebuild-country-stats!)
      (should-not= :army (production/decide-production [1 0]))))

  (context "country-coastal-cells-explored?"

    (it "returns true when all coastal cells visible on computer-map"
      (set-test-world! (build-test-map ["~###~"]))
      (set-test-computer-map! (build-test-map ["~###~"]))
      (doseq [col [1 2 3]]
        (update-test-world! assoc-in [col 0 :country-id] 1))
      (production/rebuild-country-stats!)
      (should (production/country-coastal-cells-explored? 1)))

    (it "returns false when some coastal cells unexplored"
      (set-test-world! (build-test-map ["~###~"]))
      ;; Computer map: [0,0]=sea visible, [1,0]=unexplored (nil), rest visible
      (set-test-computer-map! [[{:type :sea}] [nil] [{:type :land}] [{:type :land}] [{:type :sea}]])
      (doseq [col [1 2 3]]
        (update-test-world! assoc-in [col 0 :country-id] 1))
      (production/rebuild-country-stats!)
      (should-not (production/country-coastal-cells-explored? 1)))

    (it "returns true when country has no coastal cells"
      (set-test-world! (build-test-map ["###"]))
      (set-test-computer-map! (build-test-map ["###"]))
      (doseq [col [0 1 2]]
        (update-test-world! assoc-in [col 0 :country-id] 1))
      (production/rebuild-country-stats!)
      (should (production/country-coastal-cells-explored? 1))))

  (context "army overproduction fix"

    (it "count-country-armies includes armies aboard transports"
      ;; 2 armies on map + transport with 3 armies aboard = 5 total
      (set-test-world! (build-test-map ["aa~t"]))
      (update-test-world! assoc-in [0 0 :contents :country-id] 1)
      (update-test-world! assoc-in [1 0 :contents :country-id] 1)
      (update-test-world! assoc-in [3 0 :contents :country-id] 1)
      (update-test-world! assoc-in [3 0 :contents :army-count] 3)
      (production/rebuild-country-stats!)
      (should= 5 (production/count-country-armies 1)))

    (it "count-country-armies does not count transport cargo from different country"
      ;; 2 armies country 1 + transport country 2 with 3 armies = 2 for country 1
      (set-test-world! (build-test-map ["aa~t"]))
      (update-test-world! assoc-in [0 0 :contents :country-id] 1)
      (update-test-world! assoc-in [1 0 :contents :country-id] 1)
      (update-test-world! assoc-in [3 0 :contents :country-id] 2)
      (update-test-world! assoc-in [3 0 :contents :army-count] 3)
      (production/rebuild-country-stats!)
      (should= 2 (production/count-country-armies 1))))

  (context "army production capped at coastal cell count"

    (it "does not produce army when country armies equal total coastal cells"
      ;; Country 1: 2 coastal land cells, 2 armies already on them
      ;; All per-country priorities satisfied (patrol boats at cap, 2 fighters, transport+escort)
      ;; Global priorities unsatisfied → fallback should NOT produce army
      ;; Map row 0: ~ X a a t d ~ p p p p f f
      ;; Map row 1: ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~
      ;; Coastal land cells: [2,0] and [3,0] (adjacent to sea at row 1)
      (set-test-world! (build-test-map ["~Xaatd~ppppff"
                                               "~~~~~~~~~~~~~"]))
      (set-test-computer-map! @atoms/game-map)
      (update-test-world! assoc-in [1 0 :country-id] 1)
      (doseq [col [2 3]]
        (update-test-world! assoc-in [col 0 :country-id] 1)
        (update-test-world! assoc-in [col 0 :contents :country-id] 1))
      (update-test-world! assoc-in [4 0 :contents :country-id] 1)
      (update-test-world! assoc-in [4 0 :contents :transport-id] 1)
      (update-test-world! assoc-in [4 0 :contents :escort-destroyer-id] 1)
      (doseq [col [7 8 9 10]]
        (update-test-world! assoc-in [col 0 :contents :country-id] 1))
      (update-test-world! assoc-in [11 0 :contents :country-id] 1)
      (update-test-world! assoc-in [12 0 :contents :country-id] 1)
      ;; 2 armies, 2 coastal cells → limit reached → should NOT produce army
      (production/rebuild-country-stats!)
      (should-not= :army (production/decide-production [1 0])))

    (it "produces army when country armies below total coastal cells"
      ;; Country 1: 4 coastal land cells, only 2 armies
      ;; Map row 0: ~ X a a # # t d ~ p p p p f f
      ;; Map row 1: ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~
      ;; Coastal land cells: [2,0], [3,0], [4,0], [5,0] (all adjacent to sea at row 1)
      ;; Only 2 armies at [2,0] and [3,0], cells [4,0],[5,0] unoccupied coastal → produces army
      ;; 2/3 limit: 2/3 * 4 = 2.67, 2 armies < 2.67 → limit not reached
      (set-test-world! (build-test-map ["~Xaa##td~ppppff"
                                               "~~~~~~~~~~~~~~~"]))
      (set-test-computer-map! @atoms/game-map)
      (update-test-world! assoc-in [1 0 :country-id] 1)
      (doseq [col [2 3 4 5]]
        (update-test-world! assoc-in [col 0 :country-id] 1))
      (update-test-world! assoc-in [2 0 :contents :country-id] 1)
      (update-test-world! assoc-in [3 0 :contents :country-id] 1)
      (update-test-world! assoc-in [6 0 :contents :country-id] 1)
      (update-test-world! assoc-in [6 0 :contents :transport-id] 1)
      (update-test-world! assoc-in [6 0 :contents :escort-destroyer-id] 1)
      (doseq [col [9 10 11 12]]
        (update-test-world! assoc-in [col 0 :contents :country-id] 1))
      (update-test-world! assoc-in [13 0 :contents :country-id] 1)
      (update-test-world! assoc-in [14 0 :contents :country-id] 1)
      ;; 2 armies < 2/3 of 4 coastal cells → unoccupied coastal cell exists → produces army
      (production/rebuild-country-stats!)
      (should= :army (production/decide-production [1 0])))

    (it "produces army when armies are only aboard transport (not on land)"
      ;; Country 1: 2 coastal land cells [2,0] and [3,0]
      ;; 2 armies aboard transport at [5,0] — but transport armies don't count
      ;; Coastal cells are UNOCCUPIED → should produce army
      ;; Row 0: ~ X # # ~ t
      ;; Row 1: ~ ~ ~ ~ ~ ~
      (set-test-world! (build-test-map ["~X##~t"
                                               "~~~~~~"]))
      (set-test-computer-map! @atoms/game-map)
      (update-test-world! assoc-in [1 0 :country-id] 1)
      (doseq [col [2 3]]
        (update-test-world! assoc-in [col 0 :country-id] 1))
      (update-test-world! assoc-in [5 0 :contents :country-id] 1)
      (update-test-world! assoc-in [5 0 :contents :army-count] 2)
      ;; 0 land armies < 2 coastal cells → army limit not reached → produces army
      (production/rebuild-country-stats!)
      (should= :army (production/decide-production [1 0]))))

  (context "country-coastal-cells-explored? country filter (L113)"

    (it "only checks cells of the given country-id"
      ;; Country 1 has all coastal cells explored, country 2 has unexplored ones
      ;; If L113 = -> not=, it would check country 2's cells instead and return false
      (set-test-world! (build-test-map ["~##~"]))
      (set-test-computer-map! [[{:type :sea}] [{:type :land}] [nil] [{:type :sea}]])
      (update-test-world! assoc-in [1 0 :country-id] 1)
      (update-test-world! assoc-in [2 0 :country-id] 2)
      ;; Country 1's coastal cell [1,0] is explored (non-nil in computer-map)
      ;; Country 2's coastal cell [2,0] is unexplored (nil in computer-map)
      (production/rebuild-country-stats!)
      (should (production/country-coastal-cells-explored? 1))))

  (context "country-coastal-cells-explored? sea check (L114)"

    (it "only considers cells adjacent to sea as coastal"
      ;; L114: = -> not= would invert the sea check, making interior cells appear "coastal"
      ;; Map: #### — all land, no sea. Interior cell [1,0] unexplored on computer-map.
      ;; With correct code: no coastal cells → every? over empty seq → true
      ;; With mutation: interior cells wrongly "coastal" → unexplored → false
      (set-test-world! (build-test-map ["~###~"]))
      (set-test-computer-map! [[{:type :sea}] [{:type :land}] [nil] [{:type :land}] [{:type :sea}]])
      (doseq [col [1 2 3]]
        (update-test-world! assoc-in [col 0 :country-id] 1))
      ;; Cell [2,0] is interior (not adjacent to sea) and unexplored on computer-map (nil)
      ;; Cell [1,0] is coastal (adj to [0,0] sea) and explored
      ;; Cell [3,0] is coastal (adj to [4,0] sea) and explored
      ;; So all coastal cells are explored → should return true
      ;; If mutation inverts sea check, [2,0] becomes "coastal" (adj to non-sea) → nil → false
      (production/rebuild-country-stats!)
      (should (production/country-coastal-cells-explored? 1))))

  (context "country-has-waiting-armies? coastal check (L148)"

    (it "does not flag inland army as waiting"
      ;; Army on interior land (not adjacent to sea) should not trigger waiting-armies
      ;; L148: if = -> not=, non-sea neighbors would satisfy the coastal check
      (set-test-world! (build-test-map ["####"
                                               "####"]))
      (doseq [col (range 4) row (range 2)]
        (update-test-world! assoc-in [col row :country-id] 1))
      (update-test-world! assoc-in [1 0 :contents]
             {:type :army :owner :computer :country-id 1 :hits 1 :mode :sentry})
      (production/rebuild-country-stats!)
      (should-not (production/country-has-waiting-armies? 1))))

  (context "has-unoccupied-coastal-cells? country filter (L247)"

    (it "only checks cells of the given country-id"
      ;; Country 1's coastal cells are all occupied, country 2 has unoccupied ones
      ;; L247 vicinity: if country filter flipped, would find country 2's unoccupied cell
      (set-test-world! (build-test-map ["~a#~"]))
      (update-test-world! assoc-in [1 0 :country-id] 1)
      (update-test-world! assoc-in [1 0 :contents :country-id] 1)
      (update-test-world! assoc-in [2 0 :country-id] 2)
      ;; Country 1's only coastal cell [1,0] is occupied (has army)
      ;; Country 2's coastal cell [2,0] is unoccupied (no contents)
      (production/rebuild-country-stats!)
      (should-not (production/has-unoccupied-coastal-cells? 1))))

  (context "has-unoccupied-coastal-cells? sea check (L248)"

    (it "does not count interior unoccupied cells as coastal"
      ;; L248: = -> not= inverts sea check — interior cells would appear "coastal"
      ;; Map: #### — all land interior cells
      ;; Country 1 has unoccupied interior cell but NO coastal cells
      (set-test-world! (build-test-map ["####"
                                               "####"]))
      (doseq [col (range 4) row (range 2)]
        (update-test-world! assoc-in [col row :country-id] 1))
      ;; Interior cells [1,0], [2,0] are unoccupied but not coastal
      ;; With mutation, they'd wrongly be found as "coastal unoccupied"
      (production/rebuild-country-stats!)
      (should-not (production/has-unoccupied-coastal-cells? 1))))

  (context "country-has-other-coastal-city? self-exclusion (L263)"

    (it "does not count the city itself as 'another' coastal city"
      ;; L263: not= -> = would check if city-pos IS [i j] instead of ISN'T
      ;; Single coastal computer city — no OTHER coastal city exists
      (set-test-world! (build-test-map ["~X~"]))
      (update-test-world! assoc-in [1 0 :country-id] 1)
      ;; City [1,0] is the only coastal city in country 1
      ;; should-rotate needs other-coastal-city to return true; with only self, should return falsy
      (production/rebuild-country-stats!)
      (should-not (#'production/country-has-other-coastal-city? [1 0] 1)))))

;; ===== 4. transport and patrol boat production =====

(describe "transport and patrol boat production"
  (before (reset-all-atoms!))

  (context "transport waiting-armies production"

    (it "produces transport when armies await pickup and existing transport is full"
      ;; Coastal city, country-id 1, 6+ armies, 1 full transport
      (set-test-world! (build-test-map ["~X#aaaaaaa~t"]))
      (set-test-computer-map! @atoms/game-map)
      (update-test-world! assoc-in [1 0 :country-id] 1)
      (doseq [col (range 3 10)]
        (update-test-world! assoc-in [col 0 :country-id] 1)
        (update-test-world! assoc-in [col 0 :contents :country-id] 1))
      (update-test-world! assoc-in [11 0 :contents :country-id] 1)
      (update-test-world! assoc-in [11 0 :contents :transport-id] 1)
      (update-test-world! assoc-in [11 0 :contents :army-count] 6)
      (update-test-world! assoc-in [11 0 :contents :transport-mission] :unloading)
      (production/rebuild-country-stats!)
      (should= :transport (production/decide-production [1 0])))

    (it "does not produce transport when existing transport has room"
      ;; Coastal city, country-id 1, 6+ armies, 1 non-full transport
      (set-test-world! (build-test-map ["~X#aaaaaaa~t"]))
      (set-test-computer-map! @atoms/game-map)
      (update-test-world! assoc-in [1 0 :country-id] 1)
      (doseq [col (range 3 10)]
        (update-test-world! assoc-in [col 0 :country-id] 1)
        (update-test-world! assoc-in [col 0 :contents :country-id] 1))
      (update-test-world! assoc-in [11 0 :contents :country-id] 1)
      (update-test-world! assoc-in [11 0 :contents :transport-id] 1)
      (update-test-world! assoc-in [11 0 :contents :army-count] 3)
      (update-test-world! assoc-in [11 0 :contents :transport-mission] :loading)
      (production/rebuild-country-stats!)
      (should-not= :transport (production/decide-production [1 0]))))

  (context "patrol boat 4-cap and post-4 switch"

    (it "produces patrol boat when country has fewer than 4"
      ;; 2-row: coastal city, armies fill coastal cells, transport+escort, 0 patrol boats
      (set-test-world! (build-test-map ["~Xaatd"
                                               "~~~~~~"]))
      (set-test-computer-map! @atoms/game-map)
      (update-test-world! assoc-in [1 0 :country-id] 1)
      (doseq [col [2 3]]
        (update-test-world! assoc-in [col 0 :country-id] 1)
        (update-test-world! assoc-in [col 0 :contents :country-id] 1))
      (update-test-world! assoc-in [4 0 :contents :country-id] 1)
      (update-test-world! assoc-in [4 0 :contents :transport-id] 1)
      (update-test-world! assoc-in [4 0 :contents :escort-destroyer-id] 1)
      (production/rebuild-country-stats!)
      (should= :patrol-boat (production/decide-production [1 0])))

    (it "does not produce patrol boat when country has 4"
      ;; 2-row: armies fill coastal cells, 4 patrol boats
      (set-test-world! (build-test-map ["~Xaatd~pppp"
                                               "~~~~~~~~~~~"]))
      (set-test-computer-map! @atoms/game-map)
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
      (should-not= :patrol-boat (production/decide-production [1 0]))))

  (context "patrol boat production"

    (it "produces patrol boat when country has none"
      ;; 2-row: coastal armies fill all coastal cells, transport+escort, 0 patrol boats
      (set-test-world! (build-test-map ["~Xaatd"
                                               "~~~~~~"]))
      (set-test-computer-map! @atoms/game-map)
      (update-test-world! assoc-in [1 0 :country-id] 1)
      (doseq [col [2 3]]
        (update-test-world! assoc-in [col 0 :country-id] 1)
        (update-test-world! assoc-in [col 0 :contents :country-id] 1))
      (update-test-world! assoc-in [4 0 :contents :country-id] 1)
      (update-test-world! assoc-in [4 0 :contents :transport-id] 1)
      (update-test-world! assoc-in [4 0 :contents :escort-destroyer-id] 1)
      (production/rebuild-country-stats!)
      (should= :patrol-boat (production/decide-production [1 0])))

    (it "does not produce patrol boat when country already has 4"
      ;; 2-row: same but with 4 patrol boats
      (set-test-world! (build-test-map ["~Xaatd~pppp"
                                               "~~~~~~~~~~~"]))
      (set-test-computer-map! @atoms/game-map)
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
      (should-not= :patrol-boat (production/decide-production [1 0]))))

  (context "transport full boundary (L164)"

    (it "considers transport with exactly 6 armies as full"
      ;; L164: >= -> > would miss the boundary case of exactly 6
      (set-test-world! (build-test-map ["~Xaaaaaaa~t"
                                               "~~~~~~~~~~~"]))
      (set-test-computer-map! @atoms/game-map)
      (update-test-world! assoc-in [1 0 :country-id] 1)
      (doseq [col (range 2 9)]
        (update-test-world! assoc-in [col 0 :country-id] 1)
        (update-test-world! assoc-in [col 0 :contents :country-id] 1))
      (update-test-world! assoc-in [10 0 :contents :country-id] 1)
      (update-test-world! assoc-in [10 0 :contents :transport-id] 1)
      (update-test-world! assoc-in [10 0 :contents :army-count] 6)
      (production/rebuild-country-stats!)
      (should= :transport (production/decide-production [1 0])))

    (it "considers transport with 0 army-count as not full"
      ;; L164: 0 -> 1 default would make transport with no army-count appear to have 1
      (set-test-world! (build-test-map ["~Xaaaaaaa~t"
                                               "~~~~~~~~~~~"]))
      (set-test-computer-map! @atoms/game-map)
      (update-test-world! assoc-in [1 0 :country-id] 1)
      (doseq [col (range 2 9)]
        (update-test-world! assoc-in [col 0 :country-id] 1)
        (update-test-world! assoc-in [col 0 :contents :country-id] 1))
      (update-test-world! assoc-in [10 0 :contents :country-id] 1)
      (update-test-world! assoc-in [10 0 :contents :transport-id] 1)
      ;; Transport with no army-count should be considered not full (default 0)
      (production/rebuild-country-stats!)
      (should-not= :transport (production/decide-production [1 0]))))

  (context "should-rotate-transport? (L270)"

    (it "rotates when same city was last transport producer"
      ;; L270: = -> not= would invert the check
      ;; Place enough armies for transport priority
      (set-test-world! (build-test-map ["~X~Xaaaaaaa"
                                               "~~~~~~~~~~~"]))
      (set-test-computer-map! @atoms/game-map)
      (update-test-world! assoc-in [1 0 :country-id] 1)
      (update-test-world! assoc-in [3 0 :country-id] 1)
      (doseq [col (range 4 11)]
        (update-test-world! assoc-in [col 0 :country-id] 1)
        (update-test-world! assoc-in [col 0 :contents :country-id] 1))
      (reset! atoms/last-transport-city {1 [1 0]})
      (production/rebuild-country-stats!)
      ;; Should NOT produce transport at [1,0] because rotation says skip
      (should-not= :transport (production/decide-production [1 0])))))

(run-specs)

(ns empire.computer.production-spec
  "Tests for VMS Empire style computer production."
  (:require [speclj.core :refer :all]
            [empire.computer.production :as production]
            [empire.computer.ship :as ship]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [build-test-map reset-all-atoms!]]))

(defn- add-sea-column
  "Adds a column of sea cells to make column 0 cells coastal."
  []
  (let [rows (count (first @atoms/game-map))
        sea-col (vec (repeat rows {:type :sea}))]
    (swap! atoms/game-map conj sea-col)
    (swap! atoms/computer-map conj sea-col)))

(defn- satisfy-coastal-per-country
  "Stamp coastal city with country-id and add units to satisfy all per-country priorities.
   Places armies on coastal land cells and adds 4 patrol boats."
  [city-col]
  (swap! atoms/game-map assoc-in [0 city-col :country-id] 1)
  ;; Two fighters
  (swap! atoms/game-map assoc-in [0 1 :contents]
         {:type :fighter :owner :computer :mode :awake :hits 1 :fuel 20 :country-id 1})
  (swap! atoms/game-map assoc-in [0 3 :contents]
         {:type :fighter :owner :computer :mode :awake :hits 1 :fuel 20 :country-id 1})
  ;; 1 transport with escort
  (swap! atoms/game-map assoc-in [0 5 :contents]
         {:type :transport :owner :computer :country-id 1 :transport-id 1
          :escort-destroyer-id 1 :army-count 0 :hits 3})
  ;; 4 patrol boats (new cap)
  (doseq [j [7 9 11 13]]
    (swap! atoms/game-map assoc-in [0 j :contents]
           {:type :patrol-boat :owner :computer :patrol-country-id 1 :hits 1}))
  ;; Fill any coastal land cells with armies to satisfy coastal-fill guard
  (let [game-map @atoms/game-map
        col0 (first game-map)]
    (doseq [j (range (count col0))
            :let [cell (nth col0 j)]
            :when (and (= :land (:type cell))
                       (nil? (:contents cell))
                       (= 1 (:country-id cell)))]
      (swap! atoms/game-map assoc-in [0 j :contents]
             {:type :army :owner :computer :country-id 1 :hits 1 :mode :sentry}))))

(describe "city-is-coastal?"
  (before (reset-all-atoms!))

  (it "returns true when city has adjacent sea"
    (reset! atoms/game-map (build-test-map ["~X#"]))
    (should (production/city-is-coastal? [1 0])))

  (it "returns false when city has no adjacent sea"
    (reset! atoms/game-map (build-test-map ["#X#"]))
    (should-not (production/city-is-coastal? [1 0]))))

(describe "count-computer-units"
  (before (reset-all-atoms!))

  (it "counts computer units by type"
    (reset! atoms/game-map (build-test-map ["aad"]))
    (let [counts (production/count-computer-units)]
      (should= 2 (get counts :army))
      (should= 1 (get counts :destroyer))))

  (it "ignores player units"
    (reset! atoms/game-map (build-test-map ["aAD"]))
    (let [counts (production/count-computer-units)]
      (should= 1 (get counts :army))
      (should-be-nil (get counts :destroyer)))))

(describe "count-computer-cities"
  (before (reset-all-atoms!))

  (it "counts computer cities"
    (reset! atoms/game-map (build-test-map ["X#X~O"]))
    (should= 2 (production/count-computer-cities)))

  (it "ignores player and free cities"
    (reset! atoms/game-map (build-test-map ["O+X"]))
    (should= 1 (production/count-computer-cities))))

(describe "priority-based production"
  (before (reset-all-atoms!))

  (it "country city produces fighter via per-country priority when 0 fighters exist"
    ;; 2-row map: coastal city, armies fill all coastal cells, transport+escort, 4 patrol boats
    ;; Row 0: ~ X a a t d ~ p p p p
    ;; Row 1: ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~
    ;; Coastal cells: [2,0],[3,0] — filled by armies → army priority satisfied
    (reset! atoms/game-map (build-test-map ["~Xaatd~pppp"
                                             "~~~~~~~~~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (doseq [col [2 3]]
      (swap! atoms/game-map assoc-in [col 0 :country-id] 1)
      (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
    (swap! atoms/game-map assoc-in [4 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [4 0 :contents :transport-id] 1)
    (swap! atoms/game-map assoc-in [4 0 :contents :escort-destroyer-id] 1)
    (doseq [col [7 8 9 10]]
      (swap! atoms/game-map assoc-in [col 0 :contents :patrol-country-id] 1))
    (should= :fighter (production/decide-production [1 0])))

  (it "inland country city skips coastal priorities and produces army"
    ;; Inland city in a country with unfilled coastal cells
    ;; Row 0: ~ # X #
    ;; Row 1: ~ # # #
    ;; City at [2,0] is inland (surrounded by land). Country has coastal cell [1,0] unfilled.
    (reset! atoms/game-map (build-test-map ["~#X#"
                                             "~###"]))
    (reset! atoms/computer-map @atoms/game-map)
    (doseq [col [1 2 3]]
      (swap! atoms/game-map assoc-in [col 0 :country-id] 1)
      (swap! atoms/game-map assoc-in [col 1 :country-id] 1))
    (should= :army (production/decide-production [2 0]))))

(describe "decide-production"
  (before (reset-all-atoms!))

  (it "coastal country city with 6+ coastal armies produces transport"
    ;; 2-row map with armies on coastal cells (land adjacent to sea)
    ;; Row 0: ~ X a a a a a a
    ;; Row 1: ~ ~ ~ ~ ~ ~ ~ ~
    ;; Armies at [2-7,0] are adjacent to sea at row 1
    (reset! atoms/game-map (build-test-map ["~Xaaaaaa"
                                             "~~~~~~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (doseq [col (range 2 8)]
      (swap! atoms/game-map assoc-in [col 0 :country-id] 1)
      (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
    (should= :transport (production/decide-production [1 0]))))

(describe "country-aware production"
  (before (reset-all-atoms!))

  (it "coastal city produces transport when country has 6+ coastal armies waiting"
    ;; 2-row map: armies on coastal cells, no transports
    (reset! atoms/game-map (build-test-map ["~Xaaaaaa"
                                             "~~~~~~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (doseq [col (range 2 8)]
      (swap! atoms/game-map assoc-in [col 0 :country-id] 1)
      (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
    (should= :transport (production/decide-production [1 0])))

  (it "coastal city does not produce transport when country has fewer than 6 armies"
    ;; 2-row map: only 5 coastal armies
    (reset! atoms/game-map (build-test-map ["~Xaaaaa"
                                             "~~~~~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (doseq [col (range 2 7)]
      (swap! atoms/game-map assoc-in [col 0 :country-id] 1)
      (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
    (should-not= :transport (production/decide-production [1 0])))

  (it "does not produce transport when existing transport has room"
    ;; Transport with army-count < 6 and :loading mission
    (reset! atoms/game-map (build-test-map ["~Xaaaaaaa~t"
                                             "~~~~~~~~~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (doseq [col (range 2 9)]
      (swap! atoms/game-map assoc-in [col 0 :country-id] 1)
      (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
    (swap! atoms/game-map assoc-in [10 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [10 0 :contents :transport-id] 1)
    (swap! atoms/game-map assoc-in [10 0 :contents :army-count] 2)
    (swap! atoms/game-map assoc-in [10 0 :contents :transport-mission] :loading)
    (should-not= :transport (production/decide-production [1 0])))

  (it "landlocked city does not produce transport even when country needs one"
    (reset! atoms/game-map (build-test-map ["###"
                                             "#X#"
                                             "###"]))
    (reset! atoms/computer-map (build-test-map ["###"
                                                 "#X#"
                                                 "###"]))
    (swap! atoms/game-map assoc-in [1 1 :country-id] 1)
    (should-not= :transport (production/decide-production [1 1])))

  (it "produces army when coastal cells not filled"
    ;; 2-row map: coastal city, 2 armies but unfilled coastal cells, 4 patrol boats
    ;; Row 0: ~ X # a a # # ~ p p p p
    ;; Row 1: ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~
    ;; Coastal cells of country: [2,0],[3,0],[4,0],[5,0],[6,0] — all adj to sea at row 1
    ;; Armies only at [3,0] and [4,0], still unfilled coastal cells
    (reset! atoms/game-map (build-test-map ["~X#aa##~pppp"
                                             "~~~~~~~~~~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (doseq [col [2 3 4 5 6]]
      (swap! atoms/game-map assoc-in [col 0 :country-id] 1))
    (swap! atoms/game-map assoc-in [3 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [4 0 :contents :country-id] 1)
    (doseq [col [8 9 10 11]]
      (swap! atoms/game-map assoc-in [col 0 :contents :patrol-country-id] 1))
    (should= :army (production/decide-production [1 0])))

  (it "does not produce army when another city in country is already producing armies"
    ;; City 1 at [1,0] already producing army, city 2 at [3,0] should not also produce army
    ;; 2-row map so cities have coastal cells
    (reset! atoms/game-map (build-test-map ["~X~X~"
                                             "~~~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (swap! atoms/game-map assoc-in [3 0 :country-id] 1)
    (reset! atoms/production {[1 0] {:item :army :remaining-rounds 3}})
    (should-not= :army (production/decide-production [3 0])))

)

(describe "country-city-producing? coordination"
  (before (reset-all-atoms!))

  (it "returns true when another city in country is producing the unit type"
    (reset! atoms/game-map (build-test-map ["~X~X~"]))
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (swap! atoms/game-map assoc-in [3 0 :country-id] 1)
    (reset! atoms/production {[1 0] {:item :transport :remaining-rounds 10}})
    (should (production/country-city-producing? [3 0] 1 :transport)))

  (it "returns false when no other city in country is producing the unit type"
    (reset! atoms/game-map (build-test-map ["~X~X~"]))
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (swap! atoms/game-map assoc-in [3 0 :country-id] 1)
    (reset! atoms/production {})
    (should-not (production/country-city-producing? [3 0] 1 :transport)))

  (it "returns false when city producing is from different country"
    (reset! atoms/game-map (build-test-map ["~X~X~"]))
    (swap! atoms/game-map assoc-in [1 0 :country-id] 2)
    (swap! atoms/game-map assoc-in [3 0 :country-id] 1)
    (reset! atoms/production {[1 0] {:item :transport :remaining-rounds 10}})
    (should-not (production/country-city-producing? [3 0] 1 :transport)))

  (it "does not produce transport when another city in country is already producing"
    ;; 2-row map: coastal armies fill all coastal cells → army priority met
    ;; With another city producing transport, next is patrol-boat
    (reset! atoms/game-map (build-test-map ["~X~Xaaaaaa"
                                             "~~~~~~~~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (swap! atoms/game-map assoc-in [3 0 :country-id] 1)
    (doseq [col (range 4 10)]
      (swap! atoms/game-map assoc-in [col 0 :country-id] 1)
      (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
    (reset! atoms/production {[1 0] {:item :transport :remaining-rounds 10}})
    (should-not= :transport (production/decide-production [3 0])))

  (it "does not produce destroyer when another city in country is already producing"
    ;; Two coastal cities, same country, 200 armies in transport, 4 patrol boats, first producing destroyer
    (reset! atoms/game-map (build-test-map ["~X~X~t~pppp"]))
    (reset! atoms/computer-map @atoms/game-map)
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (swap! atoms/game-map assoc-in [3 0 :country-id] 1)
    (swap! atoms/game-map assoc-in [5 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [5 0 :contents :army-count] 200)
    (doseq [col [7 8 9 10]]
      (swap! atoms/game-map assoc-in [col 0 :contents :patrol-country-id] 1))
    (reset! atoms/production {[1 0] {:item :destroyer :remaining-rounds 10}})
    (should= :fighter (production/decide-production [3 0]))))

(describe "coastal army production"
  (before (reset-all-atoms!))

  (it "count-country-coastal-cells counts land cells with country-id adjacent to sea"
    ;; Map: ~###~  (row 0)
    ;; Country-id 1 on all land cells. Cells [1,0] and [3,0] are adjacent to sea.
    (reset! atoms/game-map (build-test-map ["~###~"]))
    (doseq [col [1 2 3]]
      (swap! atoms/game-map assoc-in [col 0 :country-id] 1))
    (should= 2 (production/count-country-coastal-cells 1)))

  (it "count-country-coastal-cells ignores land with different country-id"
    (reset! atoms/game-map (build-test-map ["~###~"]))
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (swap! atoms/game-map assoc-in [2 0 :country-id] 2)
    (swap! atoms/game-map assoc-in [3 0 :country-id] 1)
    (should= 2 (production/count-country-coastal-cells 1)))

  (it "count-country-coastal-armies counts armies on coastal cells"
    ;; Map: ~a#a~  armies at [1,0] and [3,0] which are coastal
    (reset! atoms/game-map (build-test-map ["~a#a~"]))
    (doseq [col [1 2 3]]
      (swap! atoms/game-map assoc-in [col 0 :country-id] 1))
    (swap! atoms/game-map assoc-in [1 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [3 0 :contents :country-id] 1)
    (should= 2 (production/count-country-coastal-armies 1)))

  (it "count-country-coastal-armies ignores armies on interior cells"
    ;; Map: ~a a~  army at [2,0] (interior, not adjacent to sea)
    ;; Actually with this map col 2 IS adjacent to sea via col 0 or col 4... let me use a bigger map
    ;; Map row 0: ~####~
    ;; Map row 1: ~#aa#~
    ;; Army at [2,1] and [3,1] are interior
    (reset! atoms/game-map (build-test-map ["~####~"
                                             "~#aa#~"]))
    (doseq [col [1 2 3 4]]
      (swap! atoms/game-map assoc-in [col 0 :country-id] 1)
      (swap! atoms/game-map assoc-in [col 1 :country-id] 1))
    (swap! atoms/game-map assoc-in [2 1 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [3 1 :contents :country-id] 1)
    (should= 0 (production/count-country-coastal-armies 1)))

  (it "produces army when coastal cells not yet filled"
    ;; Coastal city with country-id 1, 2 coastal cells, 0 armies on them
    ;; Transport priority not met (< 6 armies), so army comes first
    (reset! atoms/game-map (build-test-map ["~X#~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (doseq [col [1 2]]
      (swap! atoms/game-map assoc-in [col 0 :country-id] 1))
    (should= :army (production/decide-production [1 0])))

  (it "does not produce army when all coastal cells filled"
    ;; 1-row map: ~X a~ — city at [1,0] with country-id 1
    ;; Coastal cells: [1,0] is a city (not counted as fillable land), [2,0] is land adj to sea
    ;; Army at [2,0] fills the only coastal cell
    ;; Transport also needs >=6 armies, so no transport either
    (reset! atoms/game-map (build-test-map ["~Xa~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (doseq [col [1 2]]
      (swap! atoms/game-map assoc-in [col 0 :country-id] 1))
    (swap! atoms/game-map assoc-in [2 0 :contents :country-id] 1)
    (should-not= :army (production/decide-production [1 0])))

  (it "does not produce army when all coastal cells occupied by non-army units"
    ;; Coastal cell [2,0] has a fighter — still considered occupied
    (reset! atoms/game-map (build-test-map ["~Xf~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (doseq [col [1 2]]
      (swap! atoms/game-map assoc-in [col 0 :country-id] 1))
    (swap! atoms/game-map assoc-in [2 0 :contents :country-id] 1)
    (should-not= :army (production/decide-production [1 0]))))

(describe "country-coastal-cells-explored?"
  (before (reset-all-atoms!))

  (it "returns true when all coastal cells visible on computer-map"
    (reset! atoms/game-map (build-test-map ["~###~"]))
    (reset! atoms/computer-map (build-test-map ["~###~"]))
    (doseq [col [1 2 3]]
      (swap! atoms/game-map assoc-in [col 0 :country-id] 1))
    (should (production/country-coastal-cells-explored? 1)))

  (it "returns false when some coastal cells unexplored"
    (reset! atoms/game-map (build-test-map ["~###~"]))
    ;; Computer map: [0,0]=sea visible, [1,0]=unexplored (nil), rest visible
    (reset! atoms/computer-map [[{:type :sea}] [nil] [{:type :land}] [{:type :land}] [{:type :sea}]])
    (doseq [col [1 2 3]]
      (swap! atoms/game-map assoc-in [col 0 :country-id] 1))
    (should-not (production/country-coastal-cells-explored? 1)))

  (it "returns true when country has no coastal cells"
    (reset! atoms/game-map (build-test-map ["###"]))
    (reset! atoms/computer-map (build-test-map ["###"]))
    (doseq [col [0 1 2]]
      (swap! atoms/game-map assoc-in [col 0 :country-id] 1))
    (should (production/country-coastal-cells-explored? 1))))

(describe "transport waiting-armies production"
  (before (reset-all-atoms!))

  (it "produces transport when armies await pickup and existing transport is full"
    ;; Coastal city, country-id 1, 6+ armies, 1 full transport
    (reset! atoms/game-map (build-test-map ["~X#aaaaaaa~t"]))
    (reset! atoms/computer-map @atoms/game-map)
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (doseq [col (range 3 10)]
      (swap! atoms/game-map assoc-in [col 0 :country-id] 1)
      (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
    (swap! atoms/game-map assoc-in [11 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [11 0 :contents :transport-id] 1)
    (swap! atoms/game-map assoc-in [11 0 :contents :army-count] 6)
    (swap! atoms/game-map assoc-in [11 0 :contents :transport-mission] :unloading)
    (should= :transport (production/decide-production [1 0])))

  (it "does not produce transport when existing transport has room"
    ;; Coastal city, country-id 1, 6+ armies, 1 non-full transport
    (reset! atoms/game-map (build-test-map ["~X#aaaaaaa~t"]))
    (reset! atoms/computer-map @atoms/game-map)
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (doseq [col (range 3 10)]
      (swap! atoms/game-map assoc-in [col 0 :country-id] 1)
      (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
    (swap! atoms/game-map assoc-in [11 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [11 0 :contents :transport-id] 1)
    (swap! atoms/game-map assoc-in [11 0 :contents :army-count] 3)
    (swap! atoms/game-map assoc-in [11 0 :contents :transport-mission] :loading)
    (should-not= :transport (production/decide-production [1 0]))))

(describe "patrol boat 4-cap and post-4 switch"
  (before (reset-all-atoms!))

  (it "produces patrol boat when country has fewer than 4"
    ;; 2-row: coastal city, armies fill coastal cells, transport+escort, 0 patrol boats
    (reset! atoms/game-map (build-test-map ["~Xaatd"
                                             "~~~~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (doseq [col [2 3]]
      (swap! atoms/game-map assoc-in [col 0 :country-id] 1)
      (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
    (swap! atoms/game-map assoc-in [4 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [4 0 :contents :transport-id] 1)
    (swap! atoms/game-map assoc-in [4 0 :contents :escort-destroyer-id] 1)
    (should= :patrol-boat (production/decide-production [1 0])))

  (it "does not produce patrol boat when country has 4"
    ;; 2-row: armies fill coastal cells, 4 patrol boats
    (reset! atoms/game-map (build-test-map ["~Xaatd~pppp"
                                             "~~~~~~~~~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (doseq [col [2 3]]
      (swap! atoms/game-map assoc-in [col 0 :country-id] 1)
      (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
    (swap! atoms/game-map assoc-in [4 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [4 0 :contents :transport-id] 1)
    (swap! atoms/game-map assoc-in [4 0 :contents :escort-destroyer-id] 1)
    (doseq [col [7 8 9 10]]
      (swap! atoms/game-map assoc-in [col 0 :contents :patrol-country-id] 1))
    (should-not= :patrol-boat (production/decide-production [1 0]))))

(describe "army overproduction fix"
  (before (reset-all-atoms!))

  (it "count-country-armies includes armies aboard transports"
    ;; 2 armies on map + transport with 3 armies aboard = 5 total
    (reset! atoms/game-map (build-test-map ["aa~t"]))
    (swap! atoms/game-map assoc-in [0 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [1 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [3 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [3 0 :contents :army-count] 3)
    (should= 5 (production/count-country-armies 1)))

  (it "count-country-armies does not count transport cargo from different country"
    ;; 2 armies country 1 + transport country 2 with 3 armies = 2 for country 1
    (reset! atoms/game-map (build-test-map ["aa~t"]))
    (swap! atoms/game-map assoc-in [0 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [1 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [3 0 :contents :country-id] 2)
    (swap! atoms/game-map assoc-in [3 0 :contents :army-count] 3)
    (should= 2 (production/count-country-armies 1)))

)

(describe "satellite production gate"
  (before (reset-all-atoms!))

  (it "produces satellite when >15 cities and none alive"
    (let [city-row (vec (for [i (range 32)]
                          (if (even? i)
                            {:type :city :city-status :computer :country-id 1}
                            {:type :land :country-id 1})))
          game-map (vec [city-row])]
      (reset! atoms/game-map game-map)
      (reset! atoms/computer-map game-map)
      (add-sea-column)
      (satisfy-coastal-per-country 0)
      (should= :satellite (production/decide-production [0 0]))))

  (it "does not produce satellite when one already alive"
    (let [city-row (vec (for [i (range 32)]
                          (if (even? i)
                            {:type :city :city-status :computer :country-id 1}
                            {:type :land :country-id 1})))
          sat-row [{:type :land :contents {:type :satellite :owner :computer :direction [1 0] :turns-remaining 50}}
                   {:type :land}]
          game-map (vec [city-row sat-row])]
      (reset! atoms/game-map game-map)
      (reset! atoms/computer-map game-map)
      (add-sea-column)
      (satisfy-coastal-per-country 0)
      (should-be-nil (production/decide-production [0 0]))))

  (it "does not produce satellite when <=15 cities"
    (let [city-row (vec (for [i (range 30)]
                          (if (even? i)
                            {:type :city :city-status :computer :country-id 1}
                            {:type :land :country-id 1})))
          game-map (vec [city-row])]
      (reset! atoms/game-map game-map)
      (reset! atoms/computer-map game-map)
      (add-sea-column)
      (satisfy-coastal-per-country 0)
      (should-be-nil (production/decide-production [0 0])))))

(describe "process-computer-city"
  (before (reset-all-atoms!))

  (it "sets production when none exists and city has a country-id"
    (reset! atoms/game-map (build-test-map ["X+#"]))
    (reset! atoms/computer-map (build-test-map ["X+#"]))
    (swap! atoms/game-map assoc-in [0 0 :country-id] 1)
    (reset! atoms/production {})
    (production/process-computer-city [0 0])
    (should-not-be-nil (get @atoms/production [0 0])))

  (it "does not change existing production"
    (reset! atoms/game-map (build-test-map ["X#"]))
    (reset! atoms/computer-map (build-test-map ["X#"]))
    (reset! atoms/production {[0 0] {:item :fighter :remaining-rounds 10}})
    (production/process-computer-city [0 0])
    (should= :fighter (:item (get @atoms/production [0 0])))))

(describe "patrol boat production"
  (before (reset-all-atoms!))

  (it "produces patrol boat when country has none"
    ;; 2-row: coastal armies fill all coastal cells, transport+escort, 0 patrol boats
    (reset! atoms/game-map (build-test-map ["~Xaatd"
                                             "~~~~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (doseq [col [2 3]]
      (swap! atoms/game-map assoc-in [col 0 :country-id] 1)
      (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
    (swap! atoms/game-map assoc-in [4 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [4 0 :contents :transport-id] 1)
    (swap! atoms/game-map assoc-in [4 0 :contents :escort-destroyer-id] 1)
    (should= :patrol-boat (production/decide-production [1 0])))

  (it "does not produce patrol boat when country already has 4"
    ;; 2-row: same but with 4 patrol boats
    (reset! atoms/game-map (build-test-map ["~Xaatd~pppp"
                                             "~~~~~~~~~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (doseq [col [2 3]]
      (swap! atoms/game-map assoc-in [col 0 :country-id] 1)
      (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
    (swap! atoms/game-map assoc-in [4 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [4 0 :contents :transport-id] 1)
    (swap! atoms/game-map assoc-in [4 0 :contents :escort-destroyer-id] 1)
    (doseq [col [7 8 9 10]]
      (swap! atoms/game-map assoc-in [col 0 :contents :patrol-country-id] 1))
    (should-not= :patrol-boat (production/decide-production [1 0]))))

(describe "destroyer escort production"
  (before (reset-all-atoms!))

  (it "produces destroyer when country has unadopted transport and global cap allows"
    ;; 2-row: armies fill coastal cells, unadopted transport, 4 patrol boats
    (reset! atoms/game-map (build-test-map ["~Xaat~pppp"
                                             "~~~~~~~~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (doseq [col [2 3]]
      (swap! atoms/game-map assoc-in [col 0 :country-id] 1)
      (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
    (swap! atoms/game-map assoc-in [4 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [4 0 :contents :transport-id] 1)
    (doseq [col [6 7 8 9]]
      (swap! atoms/game-map assoc-in [col 0 :contents :patrol-country-id] 1))
    (should= :destroyer (production/decide-production [1 0])))

  (it "does not produce destroyer when global cap reached"
    ;; 2-row: same but 1 destroyer already → destroyers >= transports
    (reset! atoms/game-map (build-test-map ["~Xaat~ppppd"
                                             "~~~~~~~~~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (doseq [col [2 3]]
      (swap! atoms/game-map assoc-in [col 0 :country-id] 1)
      (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
    (swap! atoms/game-map assoc-in [4 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [4 0 :contents :transport-id] 1)
    (doseq [col [6 7 8 9]]
      (swap! atoms/game-map assoc-in [col 0 :contents :patrol-country-id] 1))
    (should-not= :destroyer (production/decide-production [1 0]))))

(describe "carrier production gate"
  (before (reset-all-atoms!))

  (it "produces carrier when >10 cities, <2 producing, valid position exists"
    ;; 12 cities: 6 at j=0,2,4,6,8,10 and 6 at j=50,52,54,56,58,60
    ;; Distance 0 to 50 = 50 > 32, creating a distant pair that needs carrier
    (let [cells (vec (for [j (range 80)]
                       (cond
                         (and (even? j) (<= j 10)) {:type :city :city-status :computer}
                         (<= j 10) {:type :land}
                         (and (even? j) (>= j 50) (<= j 60)) {:type :city :city-status :computer}
                         (and (>= j 50) (<= j 60)) {:type :land}
                         :else {:type :sea})))]
      (reset! atoms/game-map [cells])
      (reset! atoms/computer-map [cells])
      (satisfy-coastal-per-country 10)
      (ship/update-distant-city-pairs!)
      (should= :carrier (production/decide-production [0 10]))))

  (it "does not produce carrier when <=10 cities"
    (let [cells (vec (for [j (range 50)]
                       (cond
                         (and (even? j) (<= j 18)) {:type :city :city-status :computer}
                         (<= j 18) {:type :land}
                         :else {:type :sea})))]
      (reset! atoms/game-map [cells])
      (reset! atoms/computer-map [cells])
      (satisfy-coastal-per-country 18)
      (should-not= :carrier (production/decide-production [0 18]))))

  (it "does not produce carrier when 2 already producing"
    (let [cells (vec (for [j (range 60)]
                       (cond
                         (and (even? j) (<= j 22)) {:type :city :city-status :computer}
                         (<= j 22) {:type :land}
                         :else {:type :sea})))]
      (reset! atoms/game-map [cells])
      (reset! atoms/computer-map [cells])
      (satisfy-coastal-per-country 22)
      (reset! atoms/production {[0 0] {:item :carrier :remaining-rounds 10}
                                [0 2] {:item :carrier :remaining-rounds 10}})
      (should-not= :carrier (production/decide-production [0 22]))))

  (it "does not produce carrier when 8 already exist"
    (let [cells (vec (for [j (range 80)]
                       (cond
                         (and (even? j) (<= j 22)) {:type :city :city-status :computer}
                         (<= j 22) {:type :land}
                         (<= 30 j 37) {:type :sea :contents {:type :carrier :owner :computer :hits 8}}
                         :else {:type :sea})))]
      (reset! atoms/game-map [cells])
      (reset! atoms/computer-map [cells])
      (satisfy-coastal-per-country 22)
      (should-not= :carrier (production/decide-production [0 22]))))

  (it "does not produce carrier when no valid position exists"
    (let [cells (vec (for [j (range 44)]
                       (cond
                         (and (even? j) (<= j 22)) {:type :city :city-status :computer}
                         (<= j 22) {:type :land}
                         :else {:type :sea})))]
      (reset! atoms/game-map [cells])
      (reset! atoms/computer-map [cells])
      (satisfy-coastal-per-country 22)
      (should-not= :carrier (production/decide-production [0 22])))))

(describe "battleship production gate"
  (before (reset-all-atoms!))

  (it "produces battleship when battleships < carriers"
    (let [cells (vec (for [j (range 60)]
                       (cond
                         (and (even? j) (<= j 22)) {:type :city :city-status :computer}
                         (<= j 22) {:type :land}
                         (= j 48) {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                          :carrier-id 1 :carrier-mode :holding
                                                          :group-battleship-id nil
                                                          :group-submarine-ids []}}
                         :else {:type :sea})))]
      (reset! atoms/game-map [cells])
      (reset! atoms/computer-map [cells])
      (satisfy-coastal-per-country 22)
      (reset! atoms/production {[0 0] {:item :carrier :remaining-rounds 10}
                                [0 2] {:item :carrier :remaining-rounds 10}})
      (should= :battleship (production/decide-production [0 22]))))

  (it "does not produce battleship when battleships >= carriers"
    (let [cells (vec (for [j (range 60)]
                       (cond
                         (and (even? j) (<= j 22)) {:type :city :city-status :computer}
                         (<= j 22) {:type :land}
                         (= j 30) {:type :sea :contents {:type :carrier :owner :computer :hits 8}}
                         (= j 31) {:type :sea :contents {:type :battleship :owner :computer :hits 8}}
                         :else {:type :sea})))]
      (reset! atoms/game-map [cells])
      (reset! atoms/computer-map [cells])
      (satisfy-coastal-per-country 22)
      (reset! atoms/production {[0 0] {:item :carrier :remaining-rounds 10}
                                [0 2] {:item :carrier :remaining-rounds 10}})
      (should-not= :battleship (production/decide-production [0 22])))))

(describe "submarine production gate"
  (before (reset-all-atoms!))

  (it "produces submarine when submarines < 2 * carriers"
    (let [cells (vec (for [j (range 60)]
                       (cond
                         (and (even? j) (<= j 22)) {:type :city :city-status :computer}
                         (<= j 22) {:type :land}
                         (= j 30) {:type :sea :contents {:type :carrier :owner :computer :hits 8}}
                         (= j 31) {:type :sea :contents {:type :battleship :owner :computer :hits 8}}
                         :else {:type :sea})))]
      (reset! atoms/game-map [cells])
      (reset! atoms/computer-map [cells])
      (satisfy-coastal-per-country 22)
      (reset! atoms/production {[0 0] {:item :carrier :remaining-rounds 10}
                                [0 2] {:item :carrier :remaining-rounds 10}})
      (should= :submarine (production/decide-production [0 22]))))

  (it "does not produce submarine when submarines >= 2 * carriers"
    (let [cells (vec (for [j (range 60)]
                       (cond
                         (and (even? j) (<= j 22)) {:type :city :city-status :computer}
                         (<= j 22) {:type :land}
                         (= j 30) {:type :sea :contents {:type :carrier :owner :computer :hits 8}}
                         (= j 31) {:type :sea :contents {:type :battleship :owner :computer :hits 8}}
                         (= j 32) {:type :sea :contents {:type :submarine :owner :computer :hits 2}}
                         (= j 33) {:type :sea :contents {:type :submarine :owner :computer :hits 2}}
                         :else {:type :sea})))]
      (reset! atoms/game-map [cells])
      (reset! atoms/computer-map [cells])
      (satisfy-coastal-per-country 22)
      (reset! atoms/production {[0 0] {:item :carrier :remaining-rounds 10}
                                [0 2] {:item :carrier :remaining-rounds 10}})
      (should-not= :submarine (production/decide-production [0 22])))))

(describe "fighter country production limit"
  (before (reset-all-atoms!))

  (it "produces fighter when country has 0 fighters and all other per-country priorities met"
    ;; Coastal city, 1 transport (with escort), 20 armies, 4 patrol boats
    (reset! atoms/game-map (build-test-map ["~X#aaaaaaaaaaaaaaaaaaaatd~pppp"]))
    (reset! atoms/computer-map @atoms/game-map)
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (doseq [col (range 3 23)]
      (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
    (swap! atoms/game-map assoc-in [23 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [23 0 :contents :transport-id] 1)
    (swap! atoms/game-map assoc-in [23 0 :contents :escort-destroyer-id] 1)
    (doseq [col [26 27 28 29]]
      (swap! atoms/game-map assoc-in [col 0 :contents :patrol-country-id] 1))
    (should= :fighter (production/decide-production [1 0])))

  (it "produces fighter when country has 1 fighter and all other per-country priorities met"
    ;; Same as above with 1 fighter added
    (reset! atoms/game-map (build-test-map ["~X#aaaaaaaaaaaaaaaaaaaatd~ppppf"]))
    (reset! atoms/computer-map @atoms/game-map)
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (doseq [col (range 3 23)]
      (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
    (swap! atoms/game-map assoc-in [23 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [23 0 :contents :transport-id] 1)
    (swap! atoms/game-map assoc-in [23 0 :contents :escort-destroyer-id] 1)
    (doseq [col [26 27 28 29]]
      (swap! atoms/game-map assoc-in [col 0 :contents :patrol-country-id] 1))
    (swap! atoms/game-map assoc-in [30 0 :contents :country-id] 1)
    (should= :fighter (production/decide-production [1 0])))

  (it "produces nil when country already has 2 fighters and all priorities met"
    ;; 4 patrol boats, 2 fighters, transport with escort, 20 armies
    (reset! atoms/game-map (build-test-map ["~X#aaaaaaaaaaaaaaaaaaaatd~ppppff"]))
    (reset! atoms/computer-map @atoms/game-map)
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (doseq [col (range 3 23)]
      (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
    (swap! atoms/game-map assoc-in [23 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [23 0 :contents :transport-id] 1)
    (swap! atoms/game-map assoc-in [23 0 :contents :escort-destroyer-id] 1)
    (doseq [col [26 27 28 29]]
      (swap! atoms/game-map assoc-in [col 0 :contents :patrol-country-id] 1))
    (swap! atoms/game-map assoc-in [30 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [31 0 :contents :country-id] 1)
    (should-be-nil (production/decide-production [1 0]))))

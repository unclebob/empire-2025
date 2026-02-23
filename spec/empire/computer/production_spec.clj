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
    (let [city-row (vec (for [i (range 34)]
                          (cond
                            (even? i)
                            {:type :city :city-status :computer :country-id 1}
                            (#{1 3} i)
                            {:type :land :country-id 1
                             :contents {:type :fighter :owner :computer
                                        :country-id 1 :hits 1 :fuel 20}}
                            (= 5 i)
                            {:type :land :country-id 1
                             :contents {:type :transport :owner :computer
                                        :country-id 1 :transport-id 1
                                        :escort-destroyer-id 1 :army-count 17 :hits 3}}
                            (#{7 9 11 13} i)
                            {:type :land :country-id 1
                             :contents {:type :patrol-boat :owner :computer
                                        :patrol-country-id 1 :hits 1}}
                            :else
                            {:type :land :country-id 1
                             :contents {:type :army :owner :player :hits 1}})))]
      (reset! atoms/game-map [city-row (vec (repeat 34 {:type :sea}))])
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [1 0 :contents]
             {:type :satellite :owner :computer :direction [1 0] :turns-remaining 50})
      ;; Army limit reached (transport armies >= coastal cells) → city stays idle
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
      (should= :army (production/decide-production [0 0])))))

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

  (it "falls back to army when country already has 2 fighters and army limit not reached"
    ;; 4 patrol boats, 2 fighters, transport with escort, 2 armies on 3+ coastal cells
    ;; Row 0: ~ X # a a # # # t d ~ p p p p f f
    ;; Row 1: ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~
    ;; Coastal cells: [2,0],[3,0],[4,0],[5,0],[6,0],[7,0] (6 coastal land, 2 armies < 6)
    (reset! atoms/game-map (build-test-map ["~X#aa###td~ppppff"
                                             "~~~~~~~~~~~~~~~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (doseq [col (range 2 8)]
      (swap! atoms/game-map assoc-in [col 0 :country-id] 1))
    (swap! atoms/game-map assoc-in [3 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [4 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [8 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [8 0 :contents :transport-id] 1)
    (swap! atoms/game-map assoc-in [8 0 :contents :escort-destroyer-id] 1)
    (doseq [col [11 12 13 14]]
      (swap! atoms/game-map assoc-in [col 0 :contents :patrol-country-id] 1))
    (swap! atoms/game-map assoc-in [15 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [16 0 :contents :country-id] 1)
    (should= :army (production/decide-production [1 0]))))

;; --- Mutation-killing tests ---

(describe "count-country-armies default army-count (L79)"
  (before (reset-all-atoms!))

  (it "counts 0 armies aboard transport with no :army-count key"
    ;; Transport with no :army-count key should default to 0, not 1
    (reset! atoms/game-map (build-test-map ["~t"]))
    (swap! atoms/game-map assoc-in [1 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [1 0 :contents :army-count] nil)
    (swap! atoms/game-map update-in [1 0 :contents] dissoc :army-count)
    (should= 0 (production/count-country-armies 1))))

(describe "country-coastal-cells-explored? country filter (L113)"
  (before (reset-all-atoms!))

  (it "only checks cells of the given country-id"
    ;; Country 1 has all coastal cells explored, country 2 has unexplored ones
    ;; If L113 = -> not=, it would check country 2's cells instead and return false
    (reset! atoms/game-map (build-test-map ["~##~"]))
    (reset! atoms/computer-map [[{:type :sea}] [{:type :land}] [nil] [{:type :sea}]])
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (swap! atoms/game-map assoc-in [2 0 :country-id] 2)
    ;; Country 1's coastal cell [1,0] is explored (non-nil in computer-map)
    ;; Country 2's coastal cell [2,0] is unexplored (nil in computer-map)
    (should (production/country-coastal-cells-explored? 1))))

(describe "country-has-waiting-armies? coastal check (L148)"
  (before (reset-all-atoms!))

  (it "does not flag inland army as waiting"
    ;; Army on interior land (not adjacent to sea) should not trigger waiting-armies
    ;; L148: if = -> not=, non-sea neighbors would satisfy the coastal check
    (reset! atoms/game-map (build-test-map ["####"
                                             "####"]))
    (doseq [col (range 4) row (range 2)]
      (swap! atoms/game-map assoc-in [col row :country-id] 1))
    (swap! atoms/game-map assoc-in [1 0 :contents]
           {:type :army :owner :computer :country-id 1 :hits 1 :mode :sentry})
    (should-not (production/country-has-waiting-armies? 1))))

(describe "transport full boundary (L164)"
  (before (reset-all-atoms!))

  (it "considers transport with exactly 6 armies as full"
    ;; L164: >= -> > would miss the boundary case of exactly 6
    (reset! atoms/game-map (build-test-map ["~Xaaaaaaa~t"
                                             "~~~~~~~~~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (doseq [col (range 2 9)]
      (swap! atoms/game-map assoc-in [col 0 :country-id] 1)
      (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
    (swap! atoms/game-map assoc-in [10 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [10 0 :contents :transport-id] 1)
    (swap! atoms/game-map assoc-in [10 0 :contents :army-count] 6)
    (should= :transport (production/decide-production [1 0])))

  (it "considers transport with 0 army-count as not full"
    ;; L164: 0 -> 1 default would make transport with no army-count appear to have 1
    (reset! atoms/game-map (build-test-map ["~Xaaaaaaa~t"
                                             "~~~~~~~~~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (doseq [col (range 2 9)]
      (swap! atoms/game-map assoc-in [col 0 :country-id] 1)
      (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
    (swap! atoms/game-map assoc-in [10 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [10 0 :contents :transport-id] 1)
    ;; Transport with no army-count should be considered not full (default 0)
    (should-not= :transport (production/decide-production [1 0]))))

(describe "has-unoccupied-coastal-cells? country filter (L247)"
  (before (reset-all-atoms!))

  (it "only checks cells of the given country-id"
    ;; Country 1's coastal cells are all occupied, country 2 has unoccupied ones
    ;; L247 vicinity: if country filter flipped, would find country 2's unoccupied cell
    (reset! atoms/game-map (build-test-map ["~a#~"]))
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (swap! atoms/game-map assoc-in [1 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [2 0 :country-id] 2)
    ;; Country 1's only coastal cell [1,0] is occupied (has army)
    ;; Country 2's coastal cell [2,0] is unoccupied (no contents)
    (should-not (production/has-unoccupied-coastal-cells? 1))))

(describe "should-rotate-transport? (L270)"
  (before (reset-all-atoms!))

  (it "rotates when same city was last transport producer"
    ;; L270: = -> not= would invert the check
    (reset! atoms/game-map (build-test-map ["~X~X~"
                                             "~~~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (swap! atoms/game-map assoc-in [3 0 :country-id] 1)
    (reset! atoms/last-transport-city {1 [1 0]})
    ;; City [1,0] was last producer for country 1, and there's another coastal city [3,0]
    ;; So [1,0] should rotate (skip transport).
    ;; Place enough armies for transport priority
    (reset! atoms/game-map (build-test-map ["~X~Xaaaaaaa"
                                             "~~~~~~~~~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (swap! atoms/game-map assoc-in [3 0 :country-id] 1)
    (doseq [col (range 4 11)]
      (swap! atoms/game-map assoc-in [col 0 :country-id] 1)
      (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
    (reset! atoms/last-transport-city {1 [1 0]})
    ;; Should NOT produce transport at [1,0] because rotation says skip
    (should-not= :transport (production/decide-production [1 0]))))

(describe "destroyer transport default (L297)"
  (before (reset-all-atoms!))

  (it "does not produce destroyer when no transports exist"
    ;; L297: 0 -> 1 for transport default would make (< destroyers 1) true
    ;; when no transports exist, allowing destroyer production inappropriately
    (reset! atoms/game-map (build-test-map ["~Xaa~pppp"
                                             "~~~~~~~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (doseq [col [2 3]]
      (swap! atoms/game-map assoc-in [col 0 :country-id] 1)
      (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
    (doseq [col [5 6 7 8]]
      (swap! atoms/game-map assoc-in [col 0 :contents :patrol-country-id] 1))
    ;; No transports exist → should not produce destroyer
    (should-not= :destroyer (production/decide-production [1 0]))))

(describe "count-carrier-producers (L312)"
  (before (reset-all-atoms!))

  (it "counts cities producing carriers"
    ;; L312: = -> not= would count non-carrier producers instead
    (reset! atoms/production {[0 0] {:item :carrier :remaining-rounds 10}
                              [0 2] {:item :army :remaining-rounds 5}})
    (should= 1 (#'production/count-carrier-producers)))

  (it "counts zero when no cities producing carriers"
    (reset! atoms/production {[0 0] {:item :army :remaining-rounds 5}})
    (should= 0 (#'production/count-carrier-producers))))

(describe "carrier threshold boundary (L322-L324)"
  (before (reset-all-atoms!))

  (it "does not produce carrier when exactly at city threshold"
    ;; L322: > -> >= would produce carrier at exactly 10 cities
    ;; Need exactly 10 computer cities (= carrier-city-threshold), with distant pairs
    (let [cells (vec (for [j (range 80)]
                       (cond
                         (and (even? j) (<= j 18)) {:type :city :city-status :computer :country-id 1}
                         (<= j 18) {:type :land :country-id 1}
                         ;; Distant cities to create carrier pair
                         (and (even? j) (>= j 60) (<= j 60)) {:type :city :city-status :computer}
                         :else {:type :sea})))]
      ;; 10 cities at j=0,2,...,18 (no distant city adds to count beyond 10)
      ;; Actually distant city would make 11. Use 9 close + distant pair setup via mock.
      (reset! atoms/game-map [cells])
      (reset! atoms/computer-map [cells])
      (satisfy-coastal-per-country 18)
      (ship/update-distant-city-pairs!)
      ;; Count is 10 + 1 distant = 11... need exact 10.
      ;; Use with-redefs to mock find-carrier-position and count
      (with-redefs [ship/find-carrier-position (fn [] {:position [0 25] :pair #{[0 0] [0 50]}})]
        ;; 10 cities at j=0,2,...,18 = exactly 10 = threshold
        ;; With > mutation to >=, (>= 10 10) would be true
        (let [cells2 (vec (for [j (range 40)]
                            (cond
                              (and (even? j) (<= j 18)) {:type :city :city-status :computer :country-id 1}
                              (<= j 18) {:type :land :country-id 1}
                              :else {:type :sea})))]
          (reset! atoms/game-map [cells2])
          (reset! atoms/computer-map [cells2])
          (satisfy-coastal-per-country 18)
          (should-not= :carrier (production/decide-production [0 18]))))))

  (it "does not produce carrier when at max live carriers"
    ;; L323: < -> <= would produce carrier when at max (8)
    (with-redefs [ship/find-carrier-position (fn [] {:position [0 40] :pair #{[0 0] [0 50]}})]
      (let [cells (vec (for [j (range 80)]
                         (cond
                           (and (even? j) (<= j 22)) {:type :city :city-status :computer :country-id 1}
                           (<= j 22) {:type :land :country-id 1}
                           (<= 30 j 37) {:type :sea :contents {:type :carrier :owner :computer :hits 8}}
                           :else {:type :sea})))]
        (reset! atoms/game-map [cells])
        (reset! atoms/computer-map [cells])
        (satisfy-coastal-per-country 22)
        ;; 12 cities > 10 threshold, 8 live carriers = max → should not produce
        (should-not= :carrier (production/decide-production [0 22])))))

  (it "does not produce carrier when at max producers"
    ;; L324: < -> <= would produce carrier with 2 already producing
    (with-redefs [ship/find-carrier-position (fn [] {:position [0 40] :pair #{[0 0] [0 50]}})]
      (let [cells (vec (for [j (range 80)]
                         (cond
                           (and (even? j) (<= j 22)) {:type :city :city-status :computer :country-id 1}
                           (<= j 22) {:type :land :country-id 1}
                           :else {:type :sea})))]
        (reset! atoms/game-map [cells])
        (reset! atoms/computer-map [cells])
        (satisfy-coastal-per-country 22)
        (reset! atoms/production {[0 0] {:item :carrier :remaining-rounds 10}
                                  [0 2] {:item :carrier :remaining-rounds 10}})
        ;; 12 cities, 0 live carriers, but 2 producing = max → should not produce another
        (should-not= :carrier (production/decide-production [0 22]))))))

(describe "country-coastal-cells-explored? sea check (L114)"
  (before (reset-all-atoms!))

  (it "only considers cells adjacent to sea as coastal"
    ;; L114: = -> not= would invert the sea check, making interior cells appear "coastal"
    ;; Map: #### — all land, no sea. Interior cell [1,0] unexplored on computer-map.
    ;; With correct code: no coastal cells → every? over empty seq → true
    ;; With mutation: interior cells wrongly "coastal" → unexplored → false
    (reset! atoms/game-map (build-test-map ["~###~"]))
    (reset! atoms/computer-map [[{:type :sea}] [{:type :land}] [nil] [{:type :land}] [{:type :sea}]])
    (doseq [col [1 2 3]]
      (swap! atoms/game-map assoc-in [col 0 :country-id] 1))
    ;; Cell [2,0] is interior (not adjacent to sea) and unexplored on computer-map (nil)
    ;; Cell [1,0] is coastal (adj to [0,0] sea) and explored
    ;; Cell [3,0] is coastal (adj to [4,0] sea) and explored
    ;; So all coastal cells are explored → should return true
    ;; If mutation inverts sea check, [2,0] becomes "coastal" (adj to non-sea) → nil → false
    (should (production/country-coastal-cells-explored? 1))))

(describe "has-unoccupied-coastal-cells? sea check (L248)"
  (before (reset-all-atoms!))

  (it "does not count interior unoccupied cells as coastal"
    ;; L248: = -> not= inverts sea check — interior cells would appear "coastal"
    ;; Map: #### — all land interior cells
    ;; Country 1 has unoccupied interior cell but NO coastal cells
    (reset! atoms/game-map (build-test-map ["####"
                                             "####"]))
    (doseq [col (range 4) row (range 2)]
      (swap! atoms/game-map assoc-in [col row :country-id] 1))
    ;; Interior cells [1,0], [2,0] are unoccupied but not coastal
    ;; With mutation, they'd wrongly be found as "coastal unoccupied"
    (should-not (production/has-unoccupied-coastal-cells? 1))))

(describe "country-has-other-coastal-city? self-exclusion (L263)"
  (before (reset-all-atoms!))

  (it "does not count the city itself as 'another' coastal city"
    ;; L263: not= -> = would check if city-pos IS [i j] instead of ISN'T
    ;; Single coastal computer city — no OTHER coastal city exists
    (reset! atoms/game-map (build-test-map ["~X~"]))
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    ;; City [1,0] is the only coastal city in country 1
    ;; should-rotate needs other-coastal-city to return true; with only self, should return falsy
    (should-not (#'production/country-has-other-coastal-city? [1 0] 1))))

(describe "army production capped at coastal cell count"
  (before (reset-all-atoms!))

  (it "does not produce army when country armies equal total coastal cells"
    ;; Country 1: 2 coastal land cells, 2 armies already on them
    ;; All per-country priorities satisfied (patrol boats at cap, 2 fighters, transport+escort)
    ;; Global priorities unsatisfied → fallback should NOT produce army
    ;; Map row 0: ~ X a a t d ~ p p p p f f
    ;; Map row 1: ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~
    ;; Coastal land cells: [2,0] and [3,0] (adjacent to sea at row 1)
    (reset! atoms/game-map (build-test-map ["~Xaatd~ppppff"
                                             "~~~~~~~~~~~~~"]))
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
    (swap! atoms/game-map assoc-in [11 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [12 0 :contents :country-id] 1)
    ;; 2 armies, 2 coastal cells → limit reached → should NOT produce army
    (should-not= :army (production/decide-production [1 0])))

  (it "produces army when country armies below total coastal cells"
    ;; Country 1: 3 coastal land cells, only 2 armies
    ;; Map row 0: ~ X a a # t d ~ p p p p f f
    ;; Map row 1: ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~
    ;; Coastal land cells: [2,0], [3,0], [4,0] (all adjacent to sea at row 1)
    ;; Only 2 armies at [2,0] and [3,0], cell [4,0] is unoccupied coastal → produces army
    (reset! atoms/game-map (build-test-map ["~Xaa#td~ppppff"
                                             "~~~~~~~~~~~~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (doseq [col [2 3 4]]
      (swap! atoms/game-map assoc-in [col 0 :country-id] 1))
    (swap! atoms/game-map assoc-in [2 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [3 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [5 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [5 0 :contents :transport-id] 1)
    (swap! atoms/game-map assoc-in [5 0 :contents :escort-destroyer-id] 1)
    (doseq [col [8 9 10 11]]
      (swap! atoms/game-map assoc-in [col 0 :contents :patrol-country-id] 1))
    (swap! atoms/game-map assoc-in [12 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [13 0 :contents :country-id] 1)
    ;; 2 armies < 3 coastal cells → unoccupied coastal cell exists → produces army
    (should= :army (production/decide-production [1 0]))))

(describe "submarine carrier-default (L335)"
  (before (reset-all-atoms!))

  (it "does not produce submarine when no carriers exist"
    ;; L335: 0 -> 1 for carrier count default would make (* 2 1) = 2
    ;; allowing submarines when there are no carriers
    (let [cells (vec (for [j (range 60)]
                       (cond
                         (and (even? j) (<= j 22)) {:type :city :city-status :computer :country-id 1}
                         (<= j 22) {:type :land :country-id 1}
                         (= j 30) {:type :sea :contents {:type :battleship :owner :computer :hits 8}}
                         :else {:type :sea})))]
      (reset! atoms/game-map [cells])
      (reset! atoms/computer-map [cells])
      (satisfy-coastal-per-country 22)
      ;; No carriers, 1 battleship → battleships >= carriers, skip BB
      ;; No carriers → submarines should not be produced (0 < 2*0 = 0 is false)
      (should-not= :submarine (production/decide-production [0 22])))))

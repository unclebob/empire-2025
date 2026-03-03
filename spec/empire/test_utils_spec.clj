(ns empire.test-utils-spec
  (:require [speclj.core :refer :all]
            [empire.test-utils :refer :all]))

(describe "build-test-map"
  (it "returns a vector"
    (should (vector? (build-test-map ["~"]))))

  (it "converts ~ to sea cell"
    (should= [[{:type :sea}]] (build-test-map ["~"])))

  (it "converts # to land cell"
    (should= [[{:type :land}]] (build-test-map ["#"])))

  (it "converts = to labeled sea cell"
    (should= [[{:type :sea :label "="}]] (build-test-map ["="])))

  (it "converts % to labeled land cell"
    (should= [[{:type :land :label "%"}]] (build-test-map ["%"])))

  (it "converts + to free city"
    (should= [[{:type :city :city-status :free}]] (build-test-map ["+"])))

  (it "converts O to player city"
    (should= [[{:type :city :city-status :player}]] (build-test-map ["O"])))

  (it "converts X to computer city"
    (should= [[{:type :city :city-status :computer}]] (build-test-map ["X"])))

  (it "converts * to land with waypoint marker"
    (should= [[{:type :land :waypoint true}]] (build-test-map ["*"])))

  (it "converts - to unexplored (nil)"
    (should= [[nil]] (build-test-map ["-"])))

  (it "converts A to army on land"
    (should= [[{:type :land :contents {:type :army :owner :player :hits 1}}]]
             (build-test-map ["A"])))

  (it "converts T to transport on sea"
    (should= [[{:type :sea :contents {:type :transport :owner :player :hits 1 :army-count 0 :awake-armies 0 :been-to-sea true}}]]
             (build-test-map ["T"])))

  (it "converts D to destroyer on sea"
    (should= [[{:type :sea :contents {:type :destroyer :owner :player :hits 3}}]]
             (build-test-map ["D"])))

  (it "converts P to patrol-boat on sea"
    (should= [[{:type :sea :contents {:type :patrol-boat :owner :player :hits 1}}]]
             (build-test-map ["P"])))

  (it "converts C to carrier on sea"
    (should= [[{:type :sea :contents {:type :carrier :owner :player :hits 8 :fighter-count 0 :awake-fighters 0}}]]
             (build-test-map ["C"])))

  (it "converts B to battleship on sea"
    (should= [[{:type :sea :contents {:type :battleship :owner :player :hits 10}}]]
             (build-test-map ["B"])))

  (it "converts S to submarine on sea"
    (should= [[{:type :sea :contents {:type :submarine :owner :player :hits 2}}]]
             (build-test-map ["S"])))

  (it "converts F to fighter over land"
    (should= [[{:type :land :contents {:type :fighter :owner :player :hits 1 :fuel 32}}]]
             (build-test-map ["F"])))

  (it "converts J to fighter over sea"
    (should= [[{:type :sea :contents {:type :fighter :owner :player :hits 1 :fuel 32}}]]
             (build-test-map ["J"])))

  (it "converts V to satellite over land"
    (should= [[{:type :land :contents {:type :satellite :owner :player :hits 1 :turns-remaining 50}}]]
             (build-test-map ["V"])))

  (it "builds multi-cell rows"
    (should= [[{:type :land}] [{:type :land}] [{:type :sea}] [{:type :sea}]]
             (build-test-map ["##~~"])))

  (it "builds multi-row maps"
    (should= [[{:type :land} {:type :sea}]
              [{:type :sea} {:type :land}]]
             (build-test-map ["#~" "~#"])))

  (it "throws on unknown character"
    (should-throw (build-test-map ["!"])))

  ;; Enemy unit conversions (lowercase)
  (it "converts a to enemy army on land"
    (should= [[{:type :land :contents {:type :army :owner :computer :hits 1}}]]
             (build-test-map ["a"])))

  (it "converts t to enemy transport on sea"
    (should= [[{:type :sea :contents {:type :transport :owner :computer :hits 1 :army-count 0 :awake-armies 0 :been-to-sea true}}]]
             (build-test-map ["t"])))

  (it "converts d to enemy destroyer on sea"
    (should= [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3}}]]
             (build-test-map ["d"])))

  (it "converts p to enemy patrol-boat on sea"
    (should= [[{:type :sea :contents {:type :patrol-boat :owner :computer :hits 1}}]]
             (build-test-map ["p"])))

  (it "converts c to enemy carrier on sea"
    (should= [[{:type :sea :contents {:type :carrier :owner :computer :hits 8 :fighter-count 0 :awake-fighters 0}}]]
             (build-test-map ["c"])))

  (it "converts b to enemy battleship on sea"
    (should= [[{:type :sea :contents {:type :battleship :owner :computer :hits 10}}]]
             (build-test-map ["b"])))

  (it "converts s to enemy submarine on sea"
    (should= [[{:type :sea :contents {:type :submarine :owner :computer :hits 2}}]]
             (build-test-map ["s"])))

  (it "converts f to enemy fighter over land"
    (should= [[{:type :land :contents {:type :fighter :owner :computer :hits 1 :fuel 32}}]]
             (build-test-map ["f"])))

  (it "converts j to enemy fighter over sea"
    (should= [[{:type :sea :contents {:type :fighter :owner :computer :hits 1 :fuel 32}}]]
             (build-test-map ["j"])))

  (it "converts v to enemy satellite over land"
    (should= [[{:type :land :contents {:type :satellite :owner :computer :hits 1 :turns-remaining 50}}]]
             (build-test-map ["v"])))

  (it "builds map with mixed player and enemy units"
    (should= [[{:type :sea :contents {:type :transport :owner :player :hits 1 :army-count 0 :awake-armies 0 :been-to-sea true}}]
              [{:type :sea :contents {:type :transport :owner :computer :hits 1 :army-count 0 :awake-armies 0 :been-to-sea true}}]]
             (build-test-map ["Tt"]))))

(describe "set-test-unit"
  (it "sets a single key-value on the first unit"
    (let [gm (atom (build-test-map ["T"]))]
      (set-test-unit gm "T" :mode :sentry)
      (should= :sentry (get-in @gm [0 0 :contents :mode]))))

  (it "sets multiple key-values on a unit"
    (let [gm (atom (build-test-map ["T"]))]
      (set-test-unit gm "T" :mode :coastline-follow :army-count 2 :fuel 50)
      (should= :coastline-follow (get-in @gm [0 0 :contents :mode]))
      (should= 2 (get-in @gm [0 0 :contents :army-count]))
      (should= 50 (get-in @gm [0 0 :contents :fuel]))))

  (it "finds unit in multi-row map"
    (let [gm (atom (build-test-map ["##"
                              "#T"]))]
      (set-test-unit gm "T" :mode :awake)
      (should= :awake (get-in @gm [1 1 :contents :mode]))))

  (it "finds second unit with T2 notation"
    (let [gm (atom (build-test-map ["T~T"]))]
      (set-test-unit gm "T2" :mode :sentry)
      (should= nil (get-in @gm [0 0 :contents :mode]))
      (should= :sentry (get-in @gm [2 0 :contents :mode]))))

  (it "finds army with A notation"
    (let [gm (atom (build-test-map ["A"]))]
      (set-test-unit gm "A" :mode :moving :hits 1)
      (should= :moving (get-in @gm [0 0 :contents :mode]))
      (should= 1 (get-in @gm [0 0 :contents :hits]))))

  (it "throws when unit not found"
    (let [gm (atom (build-test-map ["~~"]))]
      (should-throw (set-test-unit gm "T" :mode :awake))))

  ;; Enemy unit tests
  (it "sets properties on enemy unit with lowercase spec"
    (let [gm (atom (build-test-map ["t"]))]
      (set-test-unit gm "t" :mode :sentry :hits 2)
      (should= :sentry (get-in @gm [0 0 :contents :mode]))
      (should= 2 (get-in @gm [0 0 :contents :hits]))))

  (it "finds second enemy unit with t2 notation"
    (let [gm (atom (build-test-map ["t~t"]))]
      (set-test-unit gm "t2" :mode :awake)
      (should= nil (get-in @gm [0 0 :contents :mode]))
      (should= :awake (get-in @gm [2 0 :contents :mode]))))

  (it "distinguishes player and enemy units by case"
    (let [gm (atom (build-test-map ["Tt"]))]
      (set-test-unit gm "T" :mode :sentry)
      (set-test-unit gm "t" :mode :awake)
      (should= :sentry (get-in @gm [0 0 :contents :mode]))
      (should= :awake (get-in @gm [1 0 :contents :mode]))))

  (it "throws when enemy unit not found using lowercase spec"
    (let [gm (atom (build-test-map ["T"]))]
      (should-throw (set-test-unit gm "t" :mode :awake)))))

(describe "get-test-unit"
  (it "returns nil when unit not found"
    (let [gm (atom (build-test-map ["~~"]))]
      (should= nil (get-test-unit gm "T"))))

  (it "returns position and unit for first matching unit"
    (let [gm (atom (build-test-map ["T"]))]
      (set-test-unit gm "T" :mode :awake)
      (let [result (get-test-unit gm "T")]
        (should= [0 0] (:pos result))
        (should= :transport (:type (:unit result)))
        (should= :awake (:mode (:unit result))))))

  (it "finds unit in multi-row map"
    (let [gm (atom (build-test-map ["##"
                              "#T"]))]
      (let [result (get-test-unit gm "T")]
        (should= [1 1] (:pos result))
        (should= :transport (:type (:unit result))))))

  (it "finds second unit with T2 notation"
    (let [gm (atom (build-test-map ["T~T"]))]
      (set-test-unit gm "T1" :mode :sentry)
      (set-test-unit gm "T2" :mode :awake)
      (let [result (get-test-unit gm "T2")]
        (should= [2 0] (:pos result))
        (should= :awake (:mode (:unit result))))))

  (it "filters by mode when specified"
    (let [gm (atom (build-test-map ["TT"]))]
      (set-test-unit gm "T1" :mode :sentry)
      (set-test-unit gm "T2" :mode :awake)
      (let [result (get-test-unit gm "T" :mode :awake)]
        (should= [1 0] (:pos result))
        (should= :awake (:mode (:unit result))))))

  (it "returns nil when no unit matches filter"
    (let [gm (atom (build-test-map ["T"]))]
      (set-test-unit gm "T" :mode :sentry)
      (should= nil (get-test-unit gm "T" :mode :awake))))

  (it "filters by multiple criteria"
    (let [gm (atom (build-test-map ["TTT"]))]
      (set-test-unit gm "T1" :mode :sentry :hits 1)
      (set-test-unit gm "T2" :mode :awake :hits 1)
      (set-test-unit gm "T3" :mode :awake :hits 3)
      (let [result (get-test-unit gm "T" :mode :awake :hits 3)]
        (should= [2 0] (:pos result))
        (should= 3 (:hits (:unit result))))))

  (it "works with different unit types"
    (let [gm (atom (build-test-map ["V"]))]
      (set-test-unit gm "V" :target [5 5])
      (let [result (get-test-unit gm "V")]
        (should= [0 0] (:pos result))
        (should= :satellite (:type (:unit result)))
        (should= [5 5] (:target (:unit result))))))

  ;; Enemy unit tests
  (it "returns nil when enemy unit not found"
    (let [gm (atom (build-test-map ["T"]))]
      (should= nil (get-test-unit gm "t"))))

  (it "finds enemy unit with lowercase spec"
    (let [gm (atom (build-test-map ["t"]))]
      (set-test-unit gm "t" :mode :awake)
      (let [result (get-test-unit gm "t")]
        (should= [0 0] (:pos result))
        (should= :transport (:type (:unit result)))
        (should= :computer (:owner (:unit result)))
        (should= :awake (:mode (:unit result))))))

  (it "finds second enemy unit with t2 notation"
    (let [gm (atom (build-test-map ["t~t"]))]
      (set-test-unit gm "t2" :mode :awake)
      (let [result (get-test-unit gm "t2")]
        (should= [2 0] (:pos result))
        (should= :awake (:mode (:unit result))))))

  (it "distinguishes player and enemy units by case"
    (let [gm (atom (build-test-map ["Tt"]))]
      (set-test-unit gm "T" :mode :sentry)
      (set-test-unit gm "t" :mode :awake)
      (should= [0 0] (:pos (get-test-unit gm "T")))
      (should= :player (:owner (:unit (get-test-unit gm "T"))))
      (should= [1 0] (:pos (get-test-unit gm "t")))
      (should= :computer (:owner (:unit (get-test-unit gm "t"))))))

  (it "filters enemy units by mode"
    (let [gm (atom (build-test-map ["tt"]))]
      (set-test-unit gm "t1" :mode :sentry)
      (set-test-unit gm "t2" :mode :awake)
      (let [result (get-test-unit gm "t" :mode :awake)]
        (should= [1 0] (:pos result))))))

(describe "get-test-city"
  (it "returns nil when city not found"
    (let [gm (atom (build-test-map ["~~"]))]
      (should= nil (get-test-city gm "O"))))

  (it "returns position and cell for player city"
    (let [gm (atom (build-test-map ["O"]))]
      (let [result (get-test-city gm "O")]
        (should= [0 0] (:pos result))
        (should= :city (:type (:cell result)))
        (should= :player (:city-status (:cell result))))))

  (it "returns position and cell for computer city"
    (let [gm (atom (build-test-map ["X"]))]
      (let [result (get-test-city gm "X")]
        (should= [0 0] (:pos result))
        (should= :computer (:city-status (:cell result))))))

  (it "returns position and cell for free city"
    (let [gm (atom (build-test-map ["+"]))]
      (let [result (get-test-city gm "+")]
        (should= [0 0] (:pos result))
        (should= :free (:city-status (:cell result))))))

  (it "finds city in multi-row map"
    (let [gm (atom (build-test-map ["##"
                              "#O"]))]
      (let [result (get-test-city gm "O")]
        (should= [1 1] (:pos result)))))

  (it "finds second city with O2 notation"
    (let [gm (atom (build-test-map ["O~O"]))]
      (let [result (get-test-city gm "O2")]
        (should= [2 0] (:pos result)))))

  (it "distinguishes between city types"
    (let [gm (atom (build-test-map ["O+X"]))]
      (should= [0 0] (:pos (get-test-city gm "O")))
      (should= [1 0] (:pos (get-test-city gm "+")))
      (should= [2 0] (:pos (get-test-city gm "X")))))

  (it "returns nil for wrong city type"
    (let [gm (atom (build-test-map ["O"]))]
      (should= nil (get-test-city gm "X"))
      (should= nil (get-test-city gm "+")))))

(describe "get-test-cell"
  (it "finds first = cell"
    (let [gm (atom (build-test-map ["~=~"]))]
      (let [result (get-test-cell gm "=")]
        (should= [1 0] (:pos result))
        (should= :sea (:type (:cell result)))
        (should= "=" (:label (:cell result))))))

  (it "finds second = cell with =2"
    (let [gm (atom (build-test-map ["=~=" "~~~"]))]
      (let [result (get-test-cell gm "=2")]
        (should= [2 0] (:pos result))
        (should= :sea (:type (:cell result))))))

  (it "finds first % cell"
    (let [gm (atom (build-test-map ["%#%"]))]
      (let [result (get-test-cell gm "%")]
        (should= [0 0] (:pos result))
        (should= :land (:type (:cell result)))
        (should= "%" (:label (:cell result))))))

  (it "finds second % cell with %2"
    (let [gm (atom (build-test-map ["%#%"]))]
      (let [result (get-test-cell gm "%2")]
        (should= [2 0] (:pos result))
        (should= :land (:type (:cell result))))))

  (it "finds = cell in multi-column map"
    (let [gm (atom (build-test-map ["~~" "~="]))]
      (let [result (get-test-cell gm "=")]
        (should= [1 1] (:pos result)))))

  (it "returns nil for missing label"
    (let [gm (atom (build-test-map ["~~~"]))]
      (should= nil (get-test-cell gm "="))))

  (it "returns nil when index exceeds count"
    (let [gm (atom (build-test-map ["=~~"]))]
      (should= nil (get-test-cell gm "=2")))))

(describe "message-matches?"
  (it "matches plain string as substring"
    (should (message-matches? "hello" "say hello world")))

  (it "rejects plain string not present"
    (should-not (message-matches? "goodbye" "say hello world")))

  (it "matches template with %s placeholder"
    (should (message-matches? "Must be coastal city to produce %s." "Must be coastal city to produce Destroyer.")))

  (it "rejects template with %s when text doesn't match"
    (should-not (message-matches? "Must be coastal city to produce %s." "You can build anything here.")))

  (it "matches template with %d placeholder"
    (should (message-matches? "Dest: %d,%d" "Dest: 10,20")))

  (it "rejects template with %d when no digits present"
    (should-not (message-matches? "Dest: %d,%d" "Dest: abc,def")))

  (it "matches template with mixed %s and %d"
    (should (message-matches? "%s docked for repair. %d/%d hits remain."
                              "Destroyer docked for repair. 2/3 hits remain.")))

  (it "matches template with multiple %s placeholders"
    (should (message-matches? "Damaged %s needs attention%s%s%s" "Damaged Battleship needs attention - hits:5")))

  (it "matches format template embedded in longer message"
    (should (message-matches? "%s. %s destroyed." "c-3,S-1. Submarine destroyed.")))

  (it "escapes regex special characters in template"
    (should (message-matches? "What? Really." "What? Really."))))

(describe "visibility-mask"
  (it "returns true for non-nil cells and false for nil cells"
    (let [grid (build-test-map ["#." ".#"])]
      (should= [[true false] [false true]] (visibility-mask grid))))

  (it "returns all false for all-nil grid"
    (let [grid (build-test-map [".." ".."])]
      (should= [[false false] [false false]] (visibility-mask grid))))

  (it "returns all true for all-non-nil grid"
    (let [grid (build-test-map ["##" "##"])]
      (should= [[true true] [true true]] (visibility-mask grid))))

  (it "works with single-cell grid"
    (should= [[true]] (visibility-mask (build-test-map ["#"])))
    (should= [[false]] (visibility-mask (build-test-map ["."])))))

(describe "territory-mask"
  (it "returns nil for nil cells"
    (let [grid (build-test-map ["."])]
      (should= [[nil]] (territory-mask grid))))

  (it "returns :sea for sea cells"
    (let [grid (build-test-map ["~"])]
      (should= [[:sea]] (territory-mask grid))))

  (it "returns country-id when present"
    (let [grid (build-test-map ["#"])]
      (swap! (atom grid) identity)
      (let [grid-with-id (assoc-in grid [0 0 :country-id] 3)]
        (should= [[3]] (territory-mask grid-with-id)))))

  (it "returns nil for land without country-id"
    (let [grid (build-test-map ["#"])]
      (should= [[nil]] (territory-mask grid))))

  (it "handles mixed cells"
    (let [grid (-> (build-test-map ["~#" "#~"])
                   (assoc-in [0 1 :country-id] 1))]
      (should= [[:sea 1] [nil :sea]] (territory-mask grid)))))

(describe "build-territory-expected"
  (it "converts ~ to :sea"
    (should= [[:sea]] (build-territory-expected ["~"])))

  (it "converts . to nil"
    (should= [[nil]] (build-territory-expected ["."])))

  (it "converts digit to number"
    (should= [[0]] (build-territory-expected ["0"]))
    (should= [[5]] (build-territory-expected ["5"]))
    (should= [[9]] (build-territory-expected ["9"])))

  (it "converts non-matching char to nil"
    (should= [[nil]] (build-territory-expected ["#"])))

  (it "handles multi-cell row"
    (should= [[:sea] [nil] [1]] (build-territory-expected ["~.1"])))

  (it "handles multi-row transposition"
    (should= [[:sea 1] [nil 2]] (build-territory-expected ["~." "12"]))))

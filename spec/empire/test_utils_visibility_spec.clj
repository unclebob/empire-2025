(ns empire.test-utils-visibility-spec
  (:require [speclj.core :refer :all]
            [empire.test.utils :refer :all]))
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
    (should (message-matches? "Damaged %s%s%s%s" "Damaged Battleship (hits:5/8) - Enemy spotted.")))

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

(ns empire.properties.neighbors-spec
  (:require [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [empire.game-mechanics.spatial.neighbors :as neighbors]
            [empire.properties.check :as p]
            [speclj.core :refer :all]))

(def cell-gen
  (gen/hash-map :type (gen/elements [:land :sea :city :unexplored])))

(def map-gen
  (gen/let [width (gen/choose 2 6)
            height (gen/choose 2 6)]
    (gen/vector (gen/vector cell-gen width) height)))

(def offsets-gen
  (gen/elements [neighbors/neighbor-offsets neighbors/orthogonal-offsets]))

(def neighbor-case-gen
  (gen/let [the-map map-gen
            offsets offsets-gen
            pos (let [height (count the-map)
                      width (count (first the-map))]
                  (gen/tuple (gen/choose 0 (dec height))
                             (gen/choose 0 (dec width))))]
    {:the-map the-map :offsets offsets :pos pos}))

(describe "neighbor query properties"
  (it "neighbor offsets never include the origin"
    (p/check 20
             (prop/for-all [offsets offsets-gen]
               (not (some #{[0 0]} offsets)))))

  (it "matching-neighbor count agrees with the collected neighbors"
    (p/check 75
             (prop/for-all [{:keys [the-map offsets pos]} neighbor-case-gen]
               (let [pred #(= :sea (:type %))
                     matches (neighbors/get-matching-neighbors pos the-map offsets pred)
                     n (neighbors/count-matching-neighbors pos the-map offsets pred)]
                 (and (= n (count matches))
                      (= (boolean (seq matches))
                         (boolean (neighbors/any-neighbor-matches? pos the-map offsets pred)))))))))

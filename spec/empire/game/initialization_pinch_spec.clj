(ns empire.game.initialization-pinch-spec
  (:require [empire.game.initialization :as init]
            [empire.test.utils :refer [reset-all-atoms! build-test-map]]
            [speclj.core :refer :all]))

(describe "diagonal-pinch?"
  (it "detects land-at-corners sea-at-corners pattern"
    (let [game-map (build-test-map ["~#" "#~"])]
      (should (init/diagonal-pinch? game-map [0 0]))))

  (it "returns false for non-pinch"
    (let [game-map (build-test-map ["~#" "~~"])]
      (should-not (init/diagonal-pinch? game-map [0 0]))))

  (it "returns false for all-land"
    (let [game-map (build-test-map ["##" "##"])]
      (should-not (init/diagonal-pinch? game-map [0 0]))))

  (it "returns false for all-sea"
    (let [game-map (build-test-map ["~~" "~~"])]
      (should-not (init/diagonal-pinch? game-map [0 0])))))

(describe "eliminate-pinches"
  (it "fills one sea corner to break the pinch"
    (let [game-map (build-test-map ["~#" "#~"])
          result (init/eliminate-pinches game-map)]
      ;; One of the sea cells should now be land
      (should (or (= :land (:type (get-in result [0 0])))
                  (= :land (:type (get-in result [1 1])))))))

  (it "leaves non-pinch maps unchanged"
    (let [game-map (build-test-map ["~#" "~~"])
          result (init/eliminate-pinches game-map)]
      (should= game-map result)))

  (it "handles multiple pinches"
    (let [game-map (build-test-map ["~#~#"
                                    "#~#~"])
          result (init/eliminate-pinches game-map)]
      ;; No pinches should remain
      (should-not (some #(init/diagonal-pinch? result %)
                        (for [i (range (dec (count result)))
                              j (range (dec (count (first result))))]
                          [i j]))))))

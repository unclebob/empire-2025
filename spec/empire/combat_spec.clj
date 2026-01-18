(ns empire.combat-spec
  (:require [speclj.core :refer :all]
            [empire.combat :as combat]
            [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.test-utils :refer [build-test-map get-test-city get-test-unit reset-all-atoms!]]))

(describe "hostile-city?"
  (before (reset-all-atoms!))
  (it "returns true for free city"
    (reset! atoms/game-map @(build-test-map ["+"]))
    (let [city-coords (:pos (get-test-city atoms/game-map "+"))]
      (should (combat/hostile-city? city-coords))))

  (it "returns true for computer city"
    (reset! atoms/game-map @(build-test-map ["X"]))
    (let [city-coords (:pos (get-test-city atoms/game-map "X"))]
      (should (combat/hostile-city? city-coords))))

  (it "returns false for player city"
    (reset! atoms/game-map @(build-test-map ["O"]))
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (should-not (combat/hostile-city? city-coords))))

  (it "returns false for non-city cells"
    (reset! atoms/game-map @(build-test-map ["#"]))
    (should-not (combat/hostile-city? [0 0])))

  (it "returns false for sea cells"
    (reset! atoms/game-map @(build-test-map ["~"]))
    (should-not (combat/hostile-city? [0 0]))))

(describe "attempt-conquest"
  (before (reset-all-atoms!))
  (with-stubs)

  (it "removes army from original cell on success"
    (with-redefs [rand (constantly 0.1)]
      (reset! atoms/game-map @(build-test-map ["A+"]))
      (let [army-coords (:pos (get-test-unit atoms/game-map "A"))
            city-coords (:pos (get-test-city atoms/game-map "+"))]
        (combat/attempt-conquest army-coords city-coords)
        (should= nil (:contents (get-in @atoms/game-map army-coords))))))

  (it "converts city to player on success"
    (with-redefs [rand (constantly 0.1)]
      (reset! atoms/game-map @(build-test-map ["A+"]))
      (let [army-coords (:pos (get-test-unit atoms/game-map "A"))
            city-coords (:pos (get-test-city atoms/game-map "+"))]
        (combat/attempt-conquest army-coords city-coords)
        (should= :player (:city-status (get-in @atoms/game-map city-coords))))))

  (it "removes army from original cell on failure"
    (with-redefs [rand (constantly 0.9)]
      (reset! atoms/game-map @(build-test-map ["A+"]))
      (reset! atoms/line3-message "")
      (let [army-coords (:pos (get-test-unit atoms/game-map "A"))
            city-coords (:pos (get-test-city atoms/game-map "+"))]
        (combat/attempt-conquest army-coords city-coords)
        (should= nil (:contents (get-in @atoms/game-map army-coords))))))

  (it "keeps city status on failure"
    (with-redefs [rand (constantly 0.9)]
      (reset! atoms/game-map @(build-test-map ["A+"]))
      (reset! atoms/line3-message "")
      (let [army-coords (:pos (get-test-unit atoms/game-map "A"))
            city-coords (:pos (get-test-city atoms/game-map "+"))]
        (combat/attempt-conquest army-coords city-coords)
        (should= :free (:city-status (get-in @atoms/game-map city-coords))))))

  (it "sets failure message on failed conquest"
    (with-redefs [rand (constantly 0.9)]
      (reset! atoms/game-map @(build-test-map ["A+"]))
      (reset! atoms/line3-message "")
      (let [army-coords (:pos (get-test-unit atoms/game-map "A"))
            city-coords (:pos (get-test-city atoms/game-map "+"))]
        (combat/attempt-conquest army-coords city-coords)
        (should= (:conquest-failed config/messages) @atoms/line3-message))))

  (it "returns true regardless of outcome"
    (with-redefs [rand (constantly 0.5)]
      (reset! atoms/game-map @(build-test-map ["A+"]))
      (let [army-coords (:pos (get-test-unit atoms/game-map "A"))
            city-coords (:pos (get-test-city atoms/game-map "+"))]
        (should (combat/attempt-conquest army-coords city-coords))))))

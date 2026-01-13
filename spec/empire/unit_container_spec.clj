(ns empire.unit-container-spec
  (:require [speclj.core :refer :all]
            [empire.unit-container :as uc]))

(describe "get-count"
  (it "returns count when key exists"
    (should= 5 (uc/get-count {:fighter-count 5} :fighter-count)))

  (it "returns 0 when key is missing"
    (should= 0 (uc/get-count {} :fighter-count)))

  (it "returns 0 for nil entity"
    (should= 0 (uc/get-count nil :fighter-count))))

(describe "get-awake-count"
  (it "returns awake count when key exists"
    (should= 3 (uc/get-awake-count {:awake-fighters 3} :awake-fighters)))

  (it "returns 0 when key is missing"
    (should= 0 (uc/get-awake-count {} :awake-fighters))))

(describe "has-awake?"
  (it "returns true when awake count is positive"
    (should (uc/has-awake? {:awake-fighters 1} :awake-fighters)))

  (it "returns false when awake count is zero"
    (should-not (uc/has-awake? {:awake-fighters 0} :awake-fighters)))

  (it "returns false when key is missing"
    (should-not (uc/has-awake? {} :awake-fighters))))

(describe "add-unit"
  (it "increments existing count"
    (should= {:fighter-count 3} (uc/add-unit {:fighter-count 2} :fighter-count)))

  (it "initializes count to 1 when missing"
    (should= {:fighter-count 1} (uc/add-unit {} :fighter-count)))

  (it "initializes count to 1 when nil"
    (should= {:fighter-count 1} (uc/add-unit {:fighter-count nil} :fighter-count))))

(describe "add-awake-unit"
  (it "increments both count and awake count"
    (let [result (uc/add-awake-unit {:fighter-count 2 :awake-fighters 1} :fighter-count :awake-fighters)]
      (should= 3 (:fighter-count result))
      (should= 2 (:awake-fighters result))))

  (it "initializes both counts when missing"
    (let [result (uc/add-awake-unit {} :fighter-count :awake-fighters)]
      (should= 1 (:fighter-count result))
      (should= 1 (:awake-fighters result)))))

(describe "remove-awake-unit"
  (it "decrements both count and awake count"
    (let [result (uc/remove-awake-unit {:fighter-count 3 :awake-fighters 2} :fighter-count :awake-fighters)]
      (should= 2 (:fighter-count result))
      (should= 1 (:awake-fighters result)))))

(describe "wake-all"
  (it "sets awake count equal to total count"
    (let [result (uc/wake-all {:fighter-count 5 :awake-fighters 0} :fighter-count :awake-fighters)]
      (should= 5 (:awake-fighters result))))

  (it "handles missing awake key"
    (let [result (uc/wake-all {:fighter-count 3} :fighter-count :awake-fighters)]
      (should= 3 (:awake-fighters result)))))

(describe "sleep-all"
  (it "sets awake count to zero"
    (let [result (uc/sleep-all {:awake-fighters 5} :awake-fighters)]
      (should= 0 (:awake-fighters result)))))

(describe "full?"
  (it "returns true when at capacity"
    (should (uc/full? {:army-count 6} :army-count 6)))

  (it "returns true when over capacity"
    (should (uc/full? {:army-count 7} :army-count 6)))

  (it "returns false when under capacity"
    (should-not (uc/full? {:army-count 5} :army-count 6)))

  (it "returns false when count is missing"
    (should-not (uc/full? {} :army-count 6))))

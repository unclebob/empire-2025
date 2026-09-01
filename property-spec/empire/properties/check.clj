(ns empire.properties.check
  (:require [clojure.test.check :as tc]
            [speclj.core :refer [should should-fail]]))

(defn check
  [times property]
  (let [result (tc/quick-check times property)]
    (when-not (:pass? result)
      (should-fail (pr-str (select-keys result [:fail :shrunk :seed]))))
    (should (:pass? result))))

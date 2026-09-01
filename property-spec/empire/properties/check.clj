(ns empire.properties.check
  (:require [clojure.test.check :as tc]
            [speclj.core :refer [should]]))

(defn check
  [times property]
  (let [result (tc/quick-check times property)]
    (should (:pass? result))))

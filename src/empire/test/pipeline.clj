(ns empire.test.pipeline
  (:require [empire.pipeline :as pipeline]))

(def run-step! pipeline/run-step!)

(defn -main
  [& _]
  (pipeline/run-unit-pipeline! run-step!))

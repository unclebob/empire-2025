(ns empire.acceptance.pipeline
  (:require [empire.pipeline :as pipeline]))

(def run-step! pipeline/run-step!)

(defn -main
  [& _]
  (pipeline/run-acceptance-pipeline! run-step!))

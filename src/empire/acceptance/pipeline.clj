(ns empire.acceptance.pipeline
  (:require [empire.pipeline :as pipeline]))

(def run-step! pipeline/run-step!)

(defn -main
  [& _]
  (pipeline/run-acceptance-pipeline! run-step!))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-06-19T12:38:01.477834-05:00", :module-hash "-1249242894", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 2, :hash "-390787608"} {:id "def/run-step!", :kind "def", :line 4, :end-line 4, :hash "-1888683731"} {:id "defn/-main", :kind "defn", :line 6, :end-line 8, :hash "299860335"}]}
;; clj-mutate-manifest-end

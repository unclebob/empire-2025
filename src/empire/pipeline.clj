(ns empire.pipeline
  (:require [clojure.java.shell :as shell]
            [clojure.string :as str]))

(defn run-step!
  [label cmd]
  (println label)
  (let [{:keys [exit out err]} (apply shell/sh cmd)]
    (when (seq out)
      (print out))
    (when (seq err)
      (binding [*out* *err*]
        (print err)))
    (when (not= 0 exit)
      (throw (ex-info (str label " failed")
                      {:cmd (str/join " " cmd)
                       :exit exit}))))
  :ok)

(def acceptance-steps
  [["Parsing acceptance scenarios..." ["clj" "-M:parse-tests"]]
   ["Generating acceptance specs..." ["clj" "-M:generate-specs"]]
   ["Checking acceptance boundaries..." ["bash" "scripts/check-acceptance-boundary.sh"]]
   ["Checking generated acceptance boundaries..." ["bash" "scripts/check-generated-acceptance-boundary.sh"]]
   ["Auditing AI game-map access..." ["bash" "scripts/check-ai-map-access.sh"]]
   ["Checking architecture dependencies..." ["clj" "-M:check-dependencies"]]
   ["Running generated acceptance specs..." ["clj" "-M:spec" "generated-acceptance-specs/"]]])

(def unit-pipeline-steps
  (into [["Running unit specs..." ["clj" "-M:spec"]]]
        (concat (butlast acceptance-steps)
                [["Checking spec boundaries..." ["bash" "scripts/check-spec-boundary.sh"]]
                 ["Checking spec structure..." ["clj" "-M:spec-structure-check" "spec/"]]
                 (last acceptance-steps)])))

(defn run-steps!
  [run-step steps]
  (doseq [[label cmd] steps]
    (run-step label cmd)))

(defn run-pipeline!
  [run-step steps success-message]
  (run-steps! run-step steps)
  (println success-message))

(defn run-acceptance-pipeline!
  [run-step]
  (run-pipeline! run-step acceptance-steps "Acceptance pipeline passed."))

(defn run-unit-pipeline!
  [run-step]
  (run-pipeline! run-step unit-pipeline-steps "All tests passed."))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-06-19T12:36:48.932882-05:00", :module-hash "331132395", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 3, :hash "1226964709"} {:id "defn/run-step!", :kind "defn", :line 5, :end-line 18, :hash "123005072"} {:id "def/acceptance-steps", :kind "def", :line 20, :end-line 27, :hash "397148604"} {:id "def/unit-pipeline-steps", :kind "def", :line 29, :end-line 34, :hash "-1250844527"} {:id "defn/run-steps!", :kind "defn", :line 36, :end-line 39, :hash "-399332090"} {:id "defn/run-pipeline!", :kind "defn", :line 41, :end-line 44, :hash "1942185206"} {:id "defn/run-acceptance-pipeline!", :kind "defn", :line 46, :end-line 48, :hash "1913063959"} {:id "defn/run-unit-pipeline!", :kind "defn", :line 50, :end-line 52, :hash "-1903501211"}]}
;; clj-mutate-manifest-end

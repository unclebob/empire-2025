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

(ns empire.acceptance.pipeline
  (:require [clojure.java.shell :as shell]
            [clojure.string :as str]))

(defn- run-step!
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

(defn -main
  [& _]
  (run-step! "Parsing acceptance scenarios..." ["clj" "-M:parse-tests"])
  (run-step! "Generating acceptance specs..." ["clj" "-M:generate-specs"])
  (run-step! "Checking acceptance boundaries..." ["bash" "scripts/check-acceptance-boundary.sh"])
  (run-step! "Checking generated acceptance boundaries..." ["bash" "scripts/check-generated-acceptance-boundary.sh"])
  (run-step! "Checking architecture boundaries..." ["bash" "scripts/check-architecture-boundaries.sh"])
  (run-step! "Running generated acceptance specs..." ["clj" "-M:spec" "generated-acceptance-specs/"])
  (println "Acceptance pipeline passed."))

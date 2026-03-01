(ns empire.test.pipeline-fast
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
  (run-step! "Running unit specs..." ["clj" "-M:spec"])
  (run-step! "Running generated acceptance specs..." ["clj" "-M:spec" "generated-acceptance-specs/"])
  (println "Fast all-tests passed."))

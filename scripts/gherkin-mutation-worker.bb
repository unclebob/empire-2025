#!/usr/bin/env bb
(ns gherkin-mutation-worker
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]))

(defn- parse-timeout-ms
  [text]
  (let [value (or text "")]
    (cond
      (str/blank? value) 30000
      (str/ends-with? value "ms") (Long/parseLong (subs value 0 (- (count value) 2)))
      (str/ends-with? value "s") (* 1000 (Long/parseLong (subs value 0 (dec (count value)))))
      (str/ends-with? value "m") (* 60000 (Long/parseLong (subs value 0 (dec (count value)))))
      :else (Long/parseLong value))))

(defn- elapsed-ns
  [start]
  (- (System/nanoTime) start))

(defn- result
  [id outcome output error start]
  {:id (or id "")
   :outcome outcome
   :output (or output "")
   :error (or error "")
   :duration (elapsed-ns start)})

(defn- generated-test-files
  [generated-dir]
  (->> (file-seq (io/file generated-dir))
       (filter #(and (.isFile %)
                     (re-find #"\.(clj|cljc)$" (.getName %))))
       (mapv str)))

(defn run-job
  [req]
  (let [start (System/nanoTime)
        id (:id req)
        feature-json (:feature_json req)
        generated-dir (:generated_dir req)
        timeout-ms (parse-timeout-ms (:timeout req))]
    (cond
      (str/blank? id)
      (result id "infrastructure_error" "" "missing id" start)

      (or (str/blank? feature-json) (not (.isFile (io/file feature-json))))
      (result id "infrastructure_error" "" (str "missing feature_json: " feature-json) start)

      (or (str/blank? generated-dir) (not (.isDirectory (io/file generated-dir))))
      (result id "infrastructure_error" "" (str "missing generated_dir: " generated-dir) start)

      (empty? (generated-test-files generated-dir))
      (result id "infrastructure_error" "" (str "no generated tests in " generated-dir) start)

      :else
      (let [work (future (shell/sh "clj" "-M:spec" generated-dir))
            done (deref work timeout-ms :timeout)]
        (if (= :timeout done)
          (do (future-cancel work)
              (result id "infrastructure_error" "" "runner timeout" start))
          (result id
                  (if (zero? (:exit done)) "test_success" "test_failure")
                  (str (:out done))
                  (str (:err done))
                  start))))))

(defn -main
  [& _]
  (doseq [line (line-seq (io/reader *in*))]
    (when-not (str/blank? line)
      (println (json/generate-string (run-job (json/parse-string line true))))
      (flush))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))

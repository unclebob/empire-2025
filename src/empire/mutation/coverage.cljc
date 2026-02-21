(ns empire.mutation.coverage
  (:require [clojure.string :as str]
            [clojure.java.shell :as shell])
  (:import [java.io File]))

(defn parse-lcov
  "Parse LCOV text into {\"file-path\" #{covered-line-numbers}}."
  [lcov-content]
  (let [lines (str/split-lines lcov-content)]
    (loop [lines lines current-file nil result {}]
      (if (empty? lines)
        result
        (let [line (first lines)]
          (cond
            (str/starts-with? line "SF:")
            (let [file (subs line 3)]
              (recur (rest lines) file (assoc result file #{})))

            (str/starts-with? line "DA:")
            (let [parts (str/split (subs line 3) #",")
                  line-num (parse-long (first parts))
                  count (parse-long (second parts))]
              (if (and current-file (pos? count))
                (recur (rest lines) current-file
                       (update result current-file conj line-num))
                (recur (rest lines) current-file result)))

            :else
            (recur (rest lines) current-file result)))))))

(defn covered-lines
  "Return set of covered lines for source-path from lcov-map.
   Handles both exact and suffix matches."
  [lcov-map source-path]
  (or (get lcov-map source-path)
      (some (fn [[k v]] (when (str/ends-with? k source-path) v))
            lcov-map)))

(defn lcov-path
  "Return the path to the LCOV info file."
  []
  "target/coverage/lcov.info")

(defn run-coverage!
  "Shell out to clj -M:cov. Returns true on success."
  []
  (let [result (shell/sh "clj" "-M:cov")]
    (zero? (:exit result))))

(defn- stale?
  "True if lcov file is missing or older than source file."
  [lcov-file source-path]
  (let [source-file (File. source-path)]
    (or (not (.exists lcov-file))
        (< (.lastModified lcov-file)
           (.lastModified source-file)))))

(defn load-coverage
  "Orchestrator: run coverage if lcov.info missing/stale, parse, return covered lines."
  [source-path]
  (let [lcov-file (File. (lcov-path))]
    (when (stale? lcov-file source-path)
      (run-coverage!))
    (when (.exists lcov-file)
      (covered-lines (parse-lcov (slurp lcov-file)) source-path))))

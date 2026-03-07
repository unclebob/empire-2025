;; mutation-tested: no
(ns empire.architecture.dependency-checker-runner
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn- default-checker-home
  []
  (str (System/getProperty "user.home") "/projects/clojure/dependency-checker"))

(defn- absolute-path
  [base path]
  (let [f (io/file path)]
    (if (.isAbsolute f)
      (.getPath f)
      (.getPath (io/file base path)))))

(defn- parse-args
  [args]
  (if (and (seq args) (not (str/starts-with? (first args) "--")))
    [(first args) (vec (rest args))]
    ["dependency-checker.edn" (vec args)]))

(defn- config-with-absolute-source-paths
  [project-root config]
  (if-let [source-paths (:source-paths config)]
    (assoc config :source-paths
                  (mapv #(absolute-path project-root %) source-paths))
    config))

(defn- rewrite-config-file-for-external-checker!
  [project-root config-path]
  (let [config (config-with-absolute-source-paths project-root
                                                  (-> config-path slurp edn/read-string))
        temp-file (.toFile (java.nio.file.Files/createTempFile
                            "dependency-checker-config-"
                            ".edn"
                            (make-array java.nio.file.attribute.FileAttribute 0)))]
    (spit temp-file (str (pr-str config) "\n"))
    (.getPath temp-file)))

(defn- run-checker!
  [checker-home project-root args]
  (let [[config-path options] (parse-args args)
        config-abs (absolute-path project-root config-path)
        config-for-checker (if (.exists (io/file config-abs))
                             (rewrite-config-file-for-external-checker! project-root config-abs)
                             config-abs)
        cmd (vec (concat ["clj" "-M:check-dependencies" config-for-checker] options))
        pb (doto (ProcessBuilder. ^java.util.List cmd)
             (.directory (io/file checker-home))
             (.inheritIO))]
    (try
      (if (.exists (io/file checker-home "deps.edn"))
        (let [proc (.start pb)]
          (.waitFor proc))
        (do
          (binding [*out* *err*]
            (println (str "External dependency checker not found at " checker-home))
            (println "Set DEPENDENCY_CHECKER_HOME or install it at ~/projects/clojure/dependency-checker."))
          2))
      (finally
        (when (not= config-for-checker config-abs)
          (.delete (io/file config-for-checker)))))))

(defn -main
  [& args]
  (let [project-root (System/getProperty "user.dir")
        checker-home (or (System/getenv "DEPENDENCY_CHECKER_HOME")
                         (default-checker-home))
        status (run-checker! checker-home project-root args)]
    (System/exit status)))

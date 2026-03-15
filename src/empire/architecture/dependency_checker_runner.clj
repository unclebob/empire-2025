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

(defn- installed-checker?
  [checker-home]
  (.exists (io/file checker-home "deps.edn")))

(defn- missing-checker-status
  [checker-home]
  (binding [*out* *err*]
    (println (str "External dependency checker not found at " checker-home))
    (println "Set DEPENDENCY_CHECKER_HOME or install it at ~/projects/clojure/dependency-checker."))
  2)

(defn- run-external-checker!
  [checker-home cmd]
  (let [pb (doto (ProcessBuilder. ^java.util.List cmd)
             (.directory (io/file checker-home))
             (.inheritIO))
        proc (.start pb)]
    (.waitFor proc)))

(defn- delete-temp-config!
  [config-for-checker config-abs]
  (when (not= config-for-checker config-abs)
    (.delete (io/file config-for-checker))))

(defn- run-checker!
  [checker-home project-root args]
  (let [[config-path options] (parse-args args)
        config-abs (absolute-path project-root config-path)
        config-for-checker (if (.exists (io/file config-abs))
                             (rewrite-config-file-for-external-checker! project-root config-abs)
                             config-abs)
        cmd (vec (concat ["clj" "-M:check-dependencies" config-for-checker] options))]
    (try
      (if (installed-checker? checker-home)
        (run-external-checker! checker-home cmd)
        (missing-checker-status checker-home))
      (finally
        (delete-temp-config! config-for-checker config-abs)))))

(defn -main
  [& args]
  (let [project-root (System/getProperty "user.dir")
        checker-home (or (System/getenv "DEPENDENCY_CHECKER_HOME")
                         (default-checker-home))
        status (run-checker! checker-home project-root args)]
    (System/exit status)))

;; mutation-tested: no
(ns empire.architecture.dependency-checker-runner
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

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

(defn- rewrite-config-file-for-checker!
  [project-root config-path]
  (let [config (config-with-absolute-source-paths project-root
                                                  (-> config-path slurp edn/read-string))
        temp-file (.toFile (java.nio.file.Files/createTempFile
                            "dependency-checker-config-"
                            ".edn"
                            (make-array java.nio.file.attribute.FileAttribute 0)))]
    (spit temp-file (str (pr-str config) "\n"))
    (.getPath temp-file)))

(defn- run-checker-command!
  [project-root cmd]
  (let [pb (doto (ProcessBuilder. ^java.util.List cmd)
             (.directory (io/file project-root))
             (.inheritIO))
        proc (.start pb)]
    (.waitFor proc)))

(defn- delete-temp-config!
  [config-for-checker config-abs]
  (when (not= config-for-checker config-abs)
    (.delete (io/file config-for-checker))))

(defn- run-checker!
  [project-root args]
  (let [[config-path options] (parse-args args)
        config-abs (absolute-path project-root config-path)
        config-for-checker (if (.exists (io/file config-abs))
                             (rewrite-config-file-for-checker! project-root config-abs)
                             config-abs)
        cmd (vec (concat ["clj" "-M:check-dependencies" config-for-checker] options))]
    (try
      (run-checker-command! project-root cmd)
      (finally
        (delete-temp-config! config-for-checker config-abs)))))

(defn -main
  [& args]
  (let [project-root (System/getProperty "user.dir")
        status (run-checker! project-root args)]
    (System/exit status)))

(ns empire.architecture.dependency-checker-runner-spec
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [empire.architecture.dependency-checker-runner :as runner]
            [speclj.core :refer :all]))

(defn- temp-dir
  []
  (.toFile (java.nio.file.Files/createTempDirectory
            "dependency-checker-runner-spec"
            (make-array java.nio.file.attribute.FileAttribute 0))))

(describe "dependency-checker runner config rewrite"
  (it "resolves relative paths against the project root"
    (should= "/tmp/project/spec"
             (#'runner/absolute-path "/tmp/project" "spec")))

  (it "preserves absolute paths"
    (should= "/tmp/spec"
             (#'runner/absolute-path "/tmp/project" "/tmp/spec")))

  (it "rewrites relative source-paths to absolute paths rooted at project"
    (let [project-root (.getPath (temp-dir))
          cfg-file (io/file (temp-dir) "dependency-checker.edn")]
      (spit cfg-file (pr-str {:source-paths ["src" "spec"]
                              :component-rules [{:component :app :match "empire.*"}]}))
      (let [rewritten-path (#'runner/rewrite-config-file-for-checker! project-root (.getPath cfg-file))
            rewritten (edn/read-string (slurp rewritten-path))]
        (should= [(str project-root "/src")
                  (str project-root "/spec")]
                 (:source-paths rewritten))
        (.delete (io/file rewritten-path)))))

  (it "preserves absolute source-paths"
    (let [project-root (.getPath (temp-dir))
          absolute-src (.getPath (io/file (temp-dir) "src"))
          cfg-file (io/file (temp-dir) "dependency-checker.edn")]
      (spit cfg-file (pr-str {:source-paths [absolute-src]}))
      (let [rewritten-path (#'runner/rewrite-config-file-for-checker! project-root (.getPath cfg-file))
            rewritten (edn/read-string (slurp rewritten-path))]
        (should= [absolute-src] (:source-paths rewritten))
        (.delete (io/file rewritten-path)))))

  (it "leaves configs without source-paths unchanged"
    (let [config {:component-rules [{:component :app :match "empire.*"}]}]
      (should= config
               (#'runner/config-with-absolute-source-paths "/tmp/project" config))))

  (it "parses explicit config path and options"
    (should= ["custom.edn" ["--scan" "--verbose"]]
             (#'runner/parse-args ["custom.edn" "--scan" "--verbose"])))

  (it "defaults config path when first arg is an option"
    (should= ["dependency-checker.edn" ["--scan"]]
             (#'runner/parse-args ["--scan"])))

  (it "defaults config path and options when no args are given"
    (should= ["dependency-checker.edn" []]
             (#'runner/parse-args [])))

  (it "runs the checker command in the current project root"
    (let [project-root (.getPath (temp-dir))
          cfg-file (io/file project-root "dependency-checker.edn")
          captured (atom nil)]
      (spit cfg-file (pr-str {:source-paths ["src"]}))
      (with-redefs [empire.architecture.dependency-checker-runner/run-checker-command!
                    (fn [dir cmd]
                      (reset! captured {:dir dir :cmd cmd})
                      0)]
        (should= 0 (#'runner/run-checker! project-root []))
        (should= "clj" (first (:cmd @captured)))
        (should= project-root (:dir @captured)))))

  (it "keeps the missing config path when no config file exists"
    (let [captured (atom nil)]
      (with-redefs [empire.architecture.dependency-checker-runner/run-checker-command!
                    (fn [_dir cmd]
                      (reset! captured cmd)
                      0)]
        (should= 0 (#'runner/run-checker! "/tmp/project" ["missing.edn" "--scan"]))
        (should= ["clj" "-M:check-dependencies" "/tmp/project/missing.edn" "--scan"]
                 @captured))))

  (it "deletes rewritten temp configs after a successful checker run"
    (let [project-root (.getPath (temp-dir))
          cfg-file (io/file project-root "dependency-checker.edn")
          rewritten-file (.getPath (io/file (temp-dir) "rewritten.edn"))]
      (spit cfg-file (pr-str {:source-paths ["src"]}))
      (spit rewritten-file "{}")
      (with-redefs [empire.architecture.dependency-checker-runner/rewrite-config-file-for-checker! (constantly rewritten-file)
                    empire.architecture.dependency-checker-runner/run-checker-command! (constantly 0)]
        (should= 0 (#'runner/run-checker! project-root []))
        (should-not (.exists (io/file rewritten-file))))))

  (it "leaves the config path alone when delete-temp-config! sees the original path"
    (let [config-file (io/file (temp-dir) "config.edn")]
      (spit config-file "{}")
      (#'runner/delete-temp-config! (.getPath config-file) (.getPath config-file))
      (should (.exists config-file)))))

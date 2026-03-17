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
  (it "builds the default checker home from the current user home"
    (should= (str (System/getProperty "user.home") "/projects/clojure/dependency-checker")
             (#'runner/default-checker-home)))

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
      (let [rewritten-path (#'runner/rewrite-config-file-for-external-checker! project-root (.getPath cfg-file))
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
      (let [rewritten-path (#'runner/rewrite-config-file-for-external-checker! project-root (.getPath cfg-file))
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

  (it "detects an installed checker by deps.edn"
    (let [checker-home (.getPath (temp-dir))]
      (spit (io/file checker-home "deps.edn") "{:paths []}")
      (should (#'runner/installed-checker? checker-home))))

  (it "prints the missing-checker guidance to stderr"
    (let [stderr (java.io.StringWriter.)]
      (binding [*err* stderr]
        (should= 2 (#'runner/missing-checker-status "/tmp/missing")))
      (should-contain "External dependency checker not found at /tmp/missing" (str stderr))
      (should-contain "DEPENDENCY_CHECKER_HOME" (str stderr))))

  (it "returns status 2 when the external checker home is missing"
    (let [project-root (.getPath (temp-dir))
          checker-home (.getPath (io/file (temp-dir) "missing-home"))]
      (should= 2 (#'runner/run-checker! checker-home project-root []))))

  (it "runs the external checker in an existing checker home"
    (let [project-root (.getPath (temp-dir))
          checker-home (.getPath (temp-dir))
          cfg-file (io/file project-root "dependency-checker.edn")
          captured (atom nil)]
      (spit (io/file checker-home "deps.edn") "{:paths []}")
      (spit cfg-file (pr-str {:source-paths ["src"]}))
      (with-redefs [empire.architecture.dependency-checker-runner/run-external-checker!
                    (fn [home cmd]
                      (reset! captured {:dir home :cmd cmd})
                      0)]
        (should= 0 (#'runner/run-checker! checker-home project-root []))
        (should= "clj" (first (:cmd @captured)))
        (should= checker-home (:dir @captured)))))

  (it "keeps the missing config path when no config file exists"
    (let [captured (atom nil)]
      (with-redefs [empire.architecture.dependency-checker-runner/installed-checker? (constantly true)
                    empire.architecture.dependency-checker-runner/run-external-checker!
                    (fn [_home cmd]
                      (reset! captured cmd)
                      0)]
        (should= 0 (#'runner/run-checker! "/tmp/checker" "/tmp/project" ["missing.edn" "--scan"]))
        (should= ["clj" "-M:check-dependencies" "/tmp/project/missing.edn" "--scan"]
                 @captured))))

  (it "deletes rewritten temp configs after a successful checker run"
    (let [project-root (.getPath (temp-dir))
          checker-home (.getPath (temp-dir))
          cfg-file (io/file project-root "dependency-checker.edn")
          rewritten-file (.getPath (io/file (temp-dir) "rewritten.edn"))]
      (spit (io/file checker-home "deps.edn") "{:paths []}")
      (spit cfg-file (pr-str {:source-paths ["src"]}))
      (spit rewritten-file "{}")
      (with-redefs [empire.architecture.dependency-checker-runner/rewrite-config-file-for-external-checker! (constantly rewritten-file)
                    empire.architecture.dependency-checker-runner/run-external-checker! (constantly 0)]
        (should= 0 (#'runner/run-checker! checker-home project-root []))
        (should-not (.exists (io/file rewritten-file))))))

  (it "leaves the config path alone when delete-temp-config! sees the original path"
    (let [config-file (io/file (temp-dir) "config.edn")]
      (spit config-file "{}")
      (#'runner/delete-temp-config! (.getPath config-file) (.getPath config-file))
      (should (.exists config-file)))))

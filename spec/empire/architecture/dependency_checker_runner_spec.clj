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

  (it "parses explicit config path and options"
    (should= ["custom.edn" ["--scan" "--verbose"]]
             (#'runner/parse-args ["custom.edn" "--scan" "--verbose"])))

  (it "defaults config path when first arg is an option"
    (should= ["dependency-checker.edn" ["--scan"]]
             (#'runner/parse-args ["--scan"])))

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
        (should= checker-home (:dir @captured))))))

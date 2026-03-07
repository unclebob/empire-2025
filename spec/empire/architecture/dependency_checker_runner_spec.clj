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
        (.delete (io/file rewritten-path))))))

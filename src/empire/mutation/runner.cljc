(ns empire.mutation.runner
  (:require [clojure.java.shell :as shell]
            [clojure.string :as str])
  (:import [java.io File]))

(defn source->spec-path
  "Convert source path to spec path.
   src/empire/foo.cljc -> spec/empire/foo_spec.clj"
  [source-path]
  (-> source-path
      (str/replace #"^src/" "spec/")
      (str/replace #"\.cljc$" "_spec.clj")))

(defn spec-exists?
  "True if the spec file exists on disk."
  [spec-path]
  (.exists (File. spec-path)))

(defn run-spec
  "Run a spec file via clj -M:spec. Returns :killed or :survived."
  [spec-path]
  (let [result (shell/sh "clj" "-M:spec" spec-path)]
    (if (zero? (:exit result))
      :survived
      :killed)))

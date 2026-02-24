(ns empire.mutation.runner
  (:require [clojure.string :as str])
  (:import [java.io File]
           [java.util.concurrent TimeUnit]))

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
  "Run a spec file via clj -M:spec. Returns :killed, :survived, or :timeout.
   Optional timeout-ms: kill process after this many milliseconds.
   A timeout indicates an infinite loop — treated as :killed by caller."
  ([spec-path] (run-spec spec-path nil))
  ([spec-path timeout-ms]
   (let [pb (doto (ProcessBuilder. ^java.util.List ["clj" "-M:spec" spec-path])
              (.redirectErrorStream true))
         process (.start pb)
         _ (doto (Thread. (fn [] (try (let [is (.getInputStream process)]
                                        (while (not= -1 (.read is))))
                                      (catch Exception _))))
             (.setDaemon true)
             (.start))
         finished? (if timeout-ms
                     (.waitFor process (long timeout-ms) TimeUnit/MILLISECONDS)
                     (do (.waitFor process) true))]
     (if finished?
       (if (zero? (.exitValue process))
         :survived
         :killed)
       (do (.destroyForcibly process)
           :timeout)))))

(defn run-spec-timed
  "Run spec without timeout. Returns {:result :killed/:survived :elapsed-ms N}."
  [spec-path]
  (let [start (System/currentTimeMillis)
        result (run-spec spec-path)
        elapsed (- (System/currentTimeMillis) start)]
    {:result result :elapsed-ms elapsed}))

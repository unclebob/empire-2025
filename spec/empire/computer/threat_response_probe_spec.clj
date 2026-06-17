(ns empire.computer.threat-response-probe-spec
  (:require [empire.computer.threat-response.probe :as probe]
            [empire.test.utils :as test-utils]
            [speclj.core :refer :all])
  (:import [java.io File]
           [java.nio.file Files]))

(defn- nested-log-file
  []
  (let [base-dir (.toFile (Files/createTempDirectory "probe-spec-" (make-array java.nio.file.attribute.FileAttribute 0)))
        nested-dir (File. base-dir "nested")
        log-file (File. nested-dir "major-invasion-probe.log")]
    (.deleteOnExit base-dir)
    (.deleteOnExit nested-dir)
    (.deleteOnExit log-file)
    {:nested-dir nested-dir
     :log-file log-file}))

(defmacro with-log-path
  [path & body]
  `(let [original-path# @#'probe/log-path]
     (alter-var-root #'probe/log-path (constantly ~path))
     (try
       ~@body
       (finally
         (alter-var-root #'probe/log-path (constantly original-path#))))))

(describe "threat-response probe"
  (before (test-utils/reset-all-atoms!))

  (it "creates the log parent directory when clearing the log"
    (let [{:keys [nested-dir log-file]} (nested-log-file)]
      (with-log-path (.getPath log-file)
        (probe/clear-log!))
      (should (.exists nested-dir))
      (should (.exists log-file))
      (should= "" (slurp log-file))))

  (it "creates the log parent directory when appending a log entry"
    (let [{:keys [nested-dir log-file]} (nested-log-file)
          world [[{:type :land
                   :contents {:type :army
                              :owner :player}}]]]
      (test-utils/set-test-world! world)
      (test-utils/set-test-computer-map! world)
      (test-utils/set-test-state! :round-number 7)
      (test-utils/set-test-state! :major-invasion-state {:active? false})
      (with-log-path (.getPath log-file)
        (probe/log-event! :major-invasion-activated {:source :spec}))
      (should (.exists nested-dir))
      (should (.exists log-file))
      (should-contain "major-invasion-activated" (slurp log-file)))))
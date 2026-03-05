(ns empire.test.pipeline-runner-spec
  (:require [clojure.java.shell :as shell]
            [empire.acceptance.pipeline :as acceptance-pipeline]
            [empire.test.pipeline :as test-pipeline]
            [speclj.core :refer :all]))

(defn- run-step
  [step-var label cmd]
  (step-var label cmd))

(defn- assert-failure!
  [step-var]
  (with-redefs [shell/sh (fn [& _] {:exit 3 :out "" :err "boom"})]
    (try
      (run-step step-var "Broken step" ["clj" "-M:spec"])
      (throw (ex-info "expected run-step! to throw" {}))
      (catch clojure.lang.ExceptionInfo ex
        (should= "Broken step failed" (.getMessage ex))
        (should= {:cmd "clj -M:spec" :exit 3}
                 (select-keys (ex-data ex) [:cmd :exit]))))))

(describe "pipeline run-step!"
  (it "acceptance pipeline prints output and returns :ok on success"
    (let [stdout (with-out-str
                   (with-redefs [shell/sh (fn [& _] {:exit 0 :out "ok-out" :err ""})]
                     (should= :ok (run-step @#'acceptance-pipeline/run-step! "Step A" ["echo" "a"]))))]
      (should-contain "Step A" stdout)
      (should-contain "ok-out" stdout)))

  (it "acceptance pipeline throws on non-zero exit"
    (assert-failure! @#'acceptance-pipeline/run-step!))

  (it "test pipeline prints stderr and returns :ok on success"
    (let [err-buffer (java.io.StringWriter.)
          _ (binding [*err* err-buffer]
              (with-redefs [shell/sh (fn [& _] {:exit 0 :out "" :err "warn"})]
                (should= :ok (run-step @#'test-pipeline/run-step! "Step B" ["echo" "b"]))))
          stderr (str err-buffer)]
      (should-contain "warn" stderr)))

  (it "test pipeline throws on non-zero exit"
    (assert-failure! @#'test-pipeline/run-step!))

  )

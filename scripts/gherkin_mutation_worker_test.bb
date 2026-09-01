#!/usr/bin/env bb
(ns gherkin-mutation-worker-test
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [cheshire.core :as json]))

(def worker "scripts/gherkin-mutation-worker.bb")

(defn- run-job
  [job]
  (let [{:keys [exit out err]} (shell/sh "bb" worker :in (str (json/generate-string job) "\n"))]
    (when-not (zero? exit)
      (throw (ex-info "worker failed" {:exit exit :err err :out out})))
    (json/parse-string (first (str/split-lines out)) true)))

(defn- expect
  [label actual expected]
  (when (not= actual expected)
    (throw (ex-info (str label " expected " (pr-str expected) " got " (pr-str actual))
                    {:actual actual :expected expected})))
  (println "ok" label))

(let [missing (run-job {:id "m1"
                        :feature_json "./tmp/no-such-feature.json"
                        :generated_dir "./tmp"
                        :work_dir "./tmp"
                        :timeout "2s"})]
  (expect "missing feature outcome" (:outcome missing) "infrastructure_error")
  (expect "missing feature id" (:id missing) "m1"))

(let [dir (io/file "./tmp/gherkin-worker-empty")
      _ (do (.mkdirs dir)
            (spit (io/file dir "feature.json") "{}"))
      job (run-job {:id "m2"
                    :feature_json (str (io/file dir "feature.json"))
                    :generated_dir (str dir)
                    :work_dir (str dir)
                    :timeout "2s"})]
  (expect "empty generated outcome" (:outcome job) "infrastructure_error")
  (expect "empty generated id" (:id job) "m2"))

(println "gherkin-mutation-worker tests passed")

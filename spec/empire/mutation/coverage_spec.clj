(ns empire.mutation.coverage-spec
  (:require [speclj.core :refer :all]
            [empire.mutation.coverage :as cov]))

(def sample-lcov
  (str "SF:src/empire/combat.cljc\n"
       "DA:1,5\n"
       "DA:2,0\n"
       "DA:3,3\n"
       "DA:5,1\n"
       "end_of_record\n"
       "SF:src/empire/game_loop.cljc\n"
       "DA:10,0\n"
       "DA:11,2\n"
       "end_of_record\n"))

(describe "parse-lcov"
  (it "parses LCOV text into map of file to covered line set"
    (let [result (cov/parse-lcov sample-lcov)]
      (should= #{1 3 5} (get result "src/empire/combat.cljc"))
      (should= #{11} (get result "src/empire/game_loop.cljc"))))

  (it "returns empty map for empty input"
    (should= {} (cov/parse-lcov "")))

  (it "excludes lines with zero count"
    (let [result (cov/parse-lcov "SF:foo.cljc\nDA:1,0\nDA:2,0\nend_of_record\n")]
      (should= #{} (get result "foo.cljc")))))

(describe "covered-lines"
  (it "returns covered lines for exact path match"
    (let [lcov-map {"src/empire/combat.cljc" #{1 3 5}}]
      (should= #{1 3 5} (cov/covered-lines lcov-map "src/empire/combat.cljc"))))

  (it "returns covered lines for suffix match"
    (let [lcov-map {"/abs/path/src/empire/combat.cljc" #{1 3 5}}]
      (should= #{1 3 5} (cov/covered-lines lcov-map "src/empire/combat.cljc"))))

  (it "returns nil when no match found"
    (should-be-nil (cov/covered-lines {} "src/empire/combat.cljc"))))

(describe "lcov-path"
  (it "returns the expected path"
    (should= "target/coverage/lcov.info" (cov/lcov-path))))

(describe "run-coverage!"
  (it "returns true on success"
    (with-redefs [clojure.java.shell/sh (fn [& _] {:exit 0 :out "" :err ""})]
      (should (cov/run-coverage!))))

  (it "returns false on failure"
    (with-redefs [clojure.java.shell/sh (fn [& _] {:exit 1 :out "" :err ""})]
      (should-not (cov/run-coverage!)))))

(describe "load-coverage"
  (it "runs coverage when lcov.info missing, parses, returns covered lines"
    (let [ran? (atom false)]
      (with-redefs [cov/run-coverage! (fn [] (reset! ran? true)
                                        (spit (cov/lcov-path) sample-lcov)
                                        true)
                    cov/lcov-path (constantly "/tmp/test-lcov.info")]
        (spit "/tmp/test-lcov.info" sample-lcov)
        (let [result (cov/load-coverage "src/empire/combat.cljc")]
          (should= #{1 3 5} result)))))

  (it "returns nil when lcov.info does not exist and coverage fails"
    (with-redefs [cov/run-coverage! (fn [] false)
                  cov/lcov-path (constantly "/tmp/nonexistent-lcov.info")]
      (should-be-nil (cov/load-coverage "src/empire/combat.cljc")))))

(run-specs)

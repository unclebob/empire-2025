(ns empire.acceptance.parser-spec
  (:require [speclj.core :refer :all]
            [empire.acceptance.parser :as parser]
            [empire.config :as config]))

(describe "acceptance test parser"

  (describe "split-into-tests"
    (it "splits a simple single test"
      (let [lines ["; Comment"
                   ""
                   ";==============================================================="
                   "; Army put to sentry mode."
                   ";==============================================================="
                   "GIVEN game map"
                   "  A#"
                   "GIVEN A is waiting for input."
                   ""
                   "WHEN the player presses s."
                   ""
                   "THEN A has mode sentry."]
            tests (parser/split-into-tests lines)]
        (should= 1 (count tests))
        (should= "Army put to sentry mode." (:description (first tests)))
        (should= 6 (:line (first tests)))))

    (it "splits multiple tests"
      (let [lines [";==============================================================="
                   "; Test one."
                   ";==============================================================="
                   "GIVEN game map"
                   "  A#"
                   ""
                   "WHEN the player presses s."
                   ""
                   "THEN A has mode sentry."
                   ""
                   ";==============================================================="
                   "; Test two."
                   ";==============================================================="
                   "GIVEN game map"
                   "  A+"
                   ""
                   "WHEN the player presses d."
                   ""
                   "THEN A is at [1 0]."]
            tests (parser/split-into-tests lines)]
        (should= 2 (count tests))
        (should= "Test one." (:description (first tests)))
        (should= 4 (:line (first tests)))
        (should= "Test two." (:description (second tests)))
        (should= 14 (:line (second tests))))))

  (describe "parse-file integration"
    (it "parses army.txt correctly"
      (let [result (parser/parse-file "acceptanceTests/army.txt")]
        (should= "army.txt" (:source result))
        (should= 12 (count (:tests result)))
        (should= 7 (:line (first (:tests result))))
        (should= "Army put to sentry mode." (:description (first (:tests result))))))

    (it "parses fighter.txt correctly"
      (let [result (parser/parse-file "acceptanceTests/fighter.txt")]
        (should= "fighter.txt" (:source result))
        (should= 12 (count (:tests result)))))

    (it "parses destroyer.txt correctly"
      (let [result (parser/parse-file "acceptanceTests/destroyer.txt")]
        (should= "destroyer.txt" (:source result))
        (should= 4 (count (:tests result)))))

    (it "parses backtick-commands.txt correctly"
      (let [result (parser/parse-file "acceptanceTests/backtick-commands.txt")]
        (should= "backtick-commands.txt" (:source result))
        (should= 13 (count (:tests result)))))

    (it "produces no unrecognized directives across all files"
      (let [files ["acceptanceTests/army.txt" "acceptanceTests/fighter.txt"
                    "acceptanceTests/destroyer.txt" "acceptanceTests/backtick-commands.txt"]
            unrecognized (for [f files
                               :let [result (parser/parse-file f)]
                               t (:tests result)
                               ir (concat (:givens t) (:whens t) (:thens t))
                               :when (= :unrecognized (:type ir))]
                           {:file f :line (:line t) :text (:text ir)})]
        (should= [] (vec unrecognized)))))

  (describe "config key validation"
    (it "warns about missing config key during parse"
      (let [output (with-out-str
                     (parser/validate-config-keys
                       "test.txt"
                       [{:line 10 :thens [{:type :message-contains :area :attention :config-key :nonexistent-key}]}]))]
        (should-contain "WARNING" output)
        (should-contain ":nonexistent-key" output)
        (should-contain "test.txt:10" output)))

    (it "does not warn about valid config key"
      (let [output (with-out-str
                     (parser/validate-config-keys
                       "test.txt"
                       [{:line 10 :thens [{:type :message-contains :area :attention :config-key :army-found-city}]}]))]
        (should= "" output)))))

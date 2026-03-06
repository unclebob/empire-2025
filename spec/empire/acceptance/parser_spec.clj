(ns empire.acceptance.parser-spec
  (:require [speclj.core :refer :all]
            [empire.acceptance.parser :as parser]
            [empire.config.core :as config]))

(describe "acceptance test parser"

  (context "split-into-tests"
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
        (should= 14 (:line (second tests)))))

    (it "captures WHERE lines as a section"
      (let [lines [";==============================================================="
                   "; Parameterized test."
                   ";==============================================================="
                   "GIVEN game map"
                   "  <unit>~"
                   "GIVEN <unit> is waiting for input."
                   ""
                   "WHEN the player presses s."
                   ""
                   "THEN <unit> has mode sentry."
                   ""
                   "WHERE"
                   "  unit"
                   "  D"
                   "  S"]
            tests (parser/split-into-tests lines)]
        (should= 1 (count tests))
        (should= ["unit" "D" "S"] (:where-lines (first tests)))))

    (it "expands single-column WHERE into multiple test groups"
      (let [lines [";==============================================================="
                   "; Ship sentry mode."
                   ";==============================================================="
                   "GIVEN game map"
                   "  <unit>~"
                   "GIVEN <unit> is waiting for input."
                   ""
                   "WHEN the player presses s."
                   ""
                   "THEN <unit> has mode sentry."
                   ""
                   "WHERE"
                   "  unit"
                   "  D"
                   "  S"]
            tests (parser/split-into-tests lines)
            expanded (parser/expand-where-tables tests)]
        (should= 2 (count expanded))
        (should= "Ship sentry mode. (unit=D)" (:description (first expanded)))
        (should= "Ship sentry mode. (unit=S)" (:description (second expanded)))
        (should= ["GIVEN game map" "D~" "GIVEN D is waiting for input."]
                 (:given-lines (first expanded)))
        (should= ["WHEN the player presses s."]
                 (:when-lines (first expanded)))
        (should= ["THEN D has mode sentry."]
                 (:then-lines (first expanded)))))

    (it "expands multi-column WHERE into multiple test groups"
      (let [lines [";==============================================================="
                   "; Direction test."
                   ";==============================================================="
                   "GIVEN game map"
                   "  ###"
                   "  #A#"
                   "  ###"
                   "GIVEN A is waiting for input."
                   ""
                   "WHEN the player presses <key>."
                   ""
                   "THEN at the next step A will be at <target>."
                   ""
                   "WHERE"
                   "  key | target"
                   "  q   | [0 0]"
                   "  w   | [1 0]"]
            tests (parser/split-into-tests lines)
            expanded (parser/expand-where-tables tests)]
        (should= 2 (count expanded))
        (should= "Direction test. (key=q, target=[0 0])" (:description (first expanded)))
        (should= ["WHEN the player presses q."] (:when-lines (first expanded)))
        (should= ["THEN at the next step A will be at [0 0]."] (:then-lines (first expanded)))
        (should= ["WHEN the player presses w."] (:when-lines (second expanded)))
        (should= ["THEN at the next step A will be at [1 0]."] (:then-lines (second expanded)))))

    (it "WHERE tests and normal tests coexist"
      (let [lines [";==============================================================="
                   "; Normal test."
                   ";==============================================================="
                   "GIVEN game map"
                   "  D~"
                   "GIVEN D is waiting for input."
                   ""
                   "WHEN the player presses s."
                   ""
                   "THEN D has mode sentry."
                   ""
                   ";==============================================================="
                   "; Parameterized test."
                   ";==============================================================="
                   "GIVEN game map"
                   "  <unit>~"
                   ""
                   "THEN <unit> has hits <hits>."
                   ""
                   "WHERE"
                   "  unit | hits"
                   "  T    | 1"
                   "  S    | 2"]
            tests (parser/split-into-tests lines)
            expanded (parser/expand-where-tables tests)]
        (should= 3 (count expanded))
        (should= "Normal test." (:description (first expanded)))
        (should= "Parameterized test. (unit=T, hits=1)" (:description (second expanded)))
        (should= "Parameterized test. (unit=S, hits=2)" (:description (nth expanded 2))))))

  (context "parse-file integration"
    (it "parses army.txt correctly"
      (let [result (parser/parse-file "acceptanceTests/army.txt")]
        (should= "army.txt" (:source result))
        (should= 12 (count (:tests result)))
        (should= 7 (:line (first (:tests result))))
        (should= "Army put to sentry mode." (:description (first (:tests result))))))

    (it "parses fighter.txt correctly"
      (let [result (parser/parse-file "acceptanceTests/fighter.txt")]
        (should= "fighter.txt" (:source result))
        (should= 16 (count (:tests result)))))

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
        (should= [] (vec unrecognized))))

    (context "parse-file with WHERE"
      (it "parse-file expands WHERE tables"
        (spit "/tmp/where-test.txt"
              (str ";===============================================================\n"
                   "; Ship sentry.\n"
                   ";===============================================================\n"
                   "GIVEN game map\n"
                   "  <unit>~\n"
                   "GIVEN <unit> is waiting for input.\n"
                   "\n"
                   "WHEN the player presses s.\n"
                   "\n"
                   "THEN <unit> has mode sentry.\n"
                   "\n"
                   "WHERE\n"
                   "  unit\n"
                   "  D\n"
                   "  S\n"
                   "  B\n"))
        (let [result (parser/parse-file "/tmp/where-test.txt")]
          (should= 3 (count (:tests result)))
          (should= "Ship sentry. (unit=D)" (:description (first (:tests result))))
          (let [first-given (-> result :tests first :givens first)]
            (should= :map (:type first-given))
            (should= ["D~"] (:rows first-given)))))

      (it "full pipeline with WHERE produces correct spec count"
        (spit "/tmp/where-pipeline.txt"
              (str ";===============================================================\n"
                   "; Ship speed test.\n"
                   ";===============================================================\n"
                   "GIVEN game map\n"
                   "  <unit>~~=\n"
                   "GIVEN <unit> is waiting for input.\n"
                   "\n"
                   "WHEN the player presses D.\n"
                   "\n"
                   "THEN at next round <unit> will be at =.\n"
                   "\n"
                   "WHERE\n"
                   "  unit\n"
                   "  D\n"
                   "  S\n"
                   "  B\n"
                   "  T\n"))
        (let [result (parser/parse-file "/tmp/where-pipeline.txt")]
          (should= 4 (count (:tests result)))
          (should= "Ship speed test. (unit=D)" (:description (first (:tests result))))
          (should= "Ship speed test. (unit=T)" (:description (last (:tests result))))))))

  (context "config key validation"
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

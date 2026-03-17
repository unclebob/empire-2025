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
        (should= 13 (count (:tests result)))
        (should= 7 (:line (first (:tests result))))
        (should= "Army put to sentry mode." (:description (first (:tests result))))))

    (it "parses fighter.txt correctly"
      (let [result (parser/parse-file "acceptanceTests/fighter.txt")]
        (should= "fighter.txt" (:source result))
        (should= 17 (count (:tests result)))))

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
        (should= "" output))))

  (context "parse-test orchestration"
    (it "passes givens context into when parsing"
      (let [calls (atom nil)]
        (with-redefs [empire.acceptance.parser.given/parse-given (fn [_ _]
                                                                   {:givens [{:type :map :rows ["A~"]}
                                                                             {:type :waiting-for-input :unit "A" :set-mode true}]
                                                                    :context {:units-with-mode #{"A"}}})
                      empire.acceptance.parser.when/parse-when (fn [lines ctx]
                                                                 (reset! calls [lines ctx])
                                                                 {:whens [{:type :advance-game}]})
                      empire.acceptance.parser.then/parse-then (fn [_ _] {:thens [{:type :waiting-for-input :expected false}]})]
          (let [result (parser/parse-test {:line 12
                                           :description "desc"
                                           :given-lines ["GIVEN A is waiting for input."]
                                           :when-lines ["WHEN the game advances."]
                                           :then-lines ["THEN not waiting-for-input."]})]
            (should= ["WHEN the game advances."]
                     (first @calls))
            (should= {:has-waiting-for-input true
                      :unit-types #{"A"}
                      :units-with-mode #{"A"}}
                     (second @calls))
            (should= [{:type :advance-game}] (:whens result))
            (should= [{:type :waiting-for-input :expected false}] (:thens result))))))

  (context "parser cli"
    (it "writes parsed edn files for txt inputs in sorted order"
      (let [files [(proxy [java.io.File] ["/tmp/b.txt"]
                     (getName [] "b.txt")
                     (getPath [] "/tmp/b.txt"))
                   (proxy [java.io.File] ["/tmp/a.txt"]
                     (getName [] "a.txt")
                     (getPath [] "/tmp/a.txt"))
                   (proxy [java.io.File] ["/tmp/ignore.md"]
                     (getName [] "ignore.md")
                     (getPath [] "/tmp/ignore.md"))]
            writes (atom [])]
        (with-redefs [clojure.java.io/file (fn
                                             ([path]
                                              (if (= path "acc")
                                                (proxy [java.io.File] [path]
                                                  (listFiles [] (into-array java.io.File files)))
                                                (java.io.File. path)))
                                             ([parent child]
                                              (java.io.File. parent child)))
                      clojure.java.io/make-parents (fn [_] :ok)
                      parser/parse-file (fn [path]
                                          {:source (last (clojure.string/split path #"/"))
                                           :tests [{:line 1 :description path}]})
                      parser/validate-config-keys (fn [source tests]
                                                    (swap! writes conj [:validate source (count tests)]))
                      empire.acceptance.parser/write-edn (fn [path data]
                                                           (swap! writes conj [:write path (:source data)]))]
          (let [out (with-out-str
                      (parser/-main "acc"))]
            (should-contain "Parsing /tmp/a.txt -> acc/edn/a.edn" out)
            (should-contain "Parsing /tmp/b.txt -> acc/edn/b.edn" out)
            (should= [[:validate "a.txt" 1]
                      [:write "acc/edn/a.edn" "a.txt"]
                      [:validate "b.txt" 1]
                      [:write "acc/edn/b.edn" "b.txt"]]
                     @writes))))))))

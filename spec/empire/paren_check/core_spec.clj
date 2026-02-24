(ns empire.paren-check.core-spec
  (:require [speclj.core :refer :all]
            [empire.paren-check.core :as pc]))

(describe "scan"
  (it "returns OK for empty string"
    (should= {:errors [] :depth 0 :forms []} (pc/scan "")))

  (it "tracks paren depth"
    (should= 0 (:depth (pc/scan "(foo)")))
    (should= 1 (:depth (pc/scan "(foo")))
    (should= 0 (:depth (pc/scan "(foo (bar))"))))

  (it "ignores parens inside strings"
    (should= 0 (:depth (pc/scan "(def x \"(((\")"))))

  (it "ignores parens inside comments"
    (should= 0 (:depth (pc/scan "(def x 1) ; ((("))))

  (it "handles escaped quotes in strings"
    (should= 0 (:depth (pc/scan "(def x \"a\\\"b\")"))))

  (it "ignores parens inside regex literals"
    (should= 0 (:depth (pc/scan "(def x #\"(((\")")))))

(describe "speclj form tracking"
  (it "detects describe form"
    (let [result (pc/scan "(describe \"foo\")")]
      (should= [{:form "describe" :line 1}] (:forms result))))

  (it "detects describe with it children"
    (let [result (pc/scan "(describe \"foo\"\n  (it \"bar\"))")]
      (should= [{:form "describe" :line 1
                 :children [{:form "it" :line 2}]}]
               (:forms result))))

  (it "detects context inside describe"
    (let [result (pc/scan "(describe \"foo\"\n  (context \"ctx\"\n    (it \"bar\")))")]
      (should= [{:form "describe" :line 1
                 :children [{:form "context" :line 2
                             :children [{:form "it" :line 3}]}]}]
               (:forms result))))

  (it "detects before and with-stubs"
    (let [result (pc/scan "(describe \"x\"\n  (before (reset!))\n  (with-stubs)\n  (it \"y\"))")]
      (should= 3 (count (:children (first (:forms result))))))))

(describe "error detection"
  (it "detects (it) inside (it)"
    (let [result (pc/scan "(describe \"x\"\n  (it \"outer\"\n    (it \"inner\")))")]
      (should= 1 (count (:errors result)))
      (should-contain "line 3" (first (:errors result)))
      (should-contain "(it) inside (it)" (first (:errors result)))))

  (it "detects (describe) inside (describe)"
    (let [result (pc/scan "(describe \"x\"\n  (describe \"y\"))")]
      (should= 1 (count (:errors result)))))

  (it "detects (context) inside (context)"
    (let [result (pc/scan "(describe \"x\"\n  (context \"a\"\n    (context \"b\")))")]
      (should= 1 (count (:errors result)))))

  (it "detects (describe) inside (it)"
    (let [result (pc/scan "(describe \"x\"\n  (it \"y\"\n    (describe \"z\")))")]
      (should= 1 (count (:errors result)))))

  (it "detects (before) inside (it)"
    (let [result (pc/scan "(describe \"x\"\n  (it \"y\"\n    (before (reset!))))")]
      (should= 1 (count (:errors result)))))

  (it "detects (context) inside (it)"
    (let [result (pc/scan "(describe \"x\"\n  (it \"y\"\n    (context \"z\")))")]
      (should= 1 (count (:errors result)))))

  (it "no error for correct nesting"
    (let [result (pc/scan "(describe \"x\"\n  (before (reset!))\n  (it \"a\")\n  (it \"b\"))")]
      (should= 0 (count (:errors result)))))

  (it "no error for context with its"
    (let [result (pc/scan "(describe \"x\"\n  (context \"ctx\"\n    (it \"a\")\n    (it \"b\")))")]
      (should= 0 (count (:errors result)))))

  (it "reports unclosed form at EOF"
    (let [result (pc/scan "(describe \"x\"\n  (it \"y\"")]
      (should (<= 1 (count (:errors result))))
      (should-contain "unclosed" (first (:errors result))))))

(run-specs)

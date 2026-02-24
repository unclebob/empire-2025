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
    (should= 0 (:depth (pc/scan "(def x #\"(((\")"))))

  (it "ignores character literal parens"
    (should= 0 (:depth (pc/scan "(def x \\( \\))"))))

  (it "hash not followed by quote does not enter regex mode"
    (should= 0 (:depth (pc/scan "(def x #{1 2 3})"))))

  (it "handles multi-line strings with parens"
    (should= 0 (:depth (pc/scan "(def x \"line1(\nline2)\")")))))

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
      (should= 3 (count (:children (first (:forms result)))))))

  (it "detects multiple top-level describe blocks"
    (let [result (pc/scan "(describe \"a\"\n  (it \"x\"))\n(describe \"b\"\n  (it \"y\"))")]
      (should= 2 (count (:forms result)))
      (should= "describe" (-> result :forms first :form))
      (should= 1 (-> result :forms first :line))
      (should= 3 (-> result :forms second :line))))

  (it "does not track non-speclj forms inside it"
    (let [result (pc/scan "(describe \"x\"\n  (it \"y\"\n    (let [a 1]\n      (should= 1 a))))")]
      (should= 0 (count (:errors result)))
      (should= 1 (count (:children (first (:forms result))))))))

(describe "error detection"
  (it "detects (it) inside (it)"
    (let [result (pc/scan "(describe \"x\"\n  (it \"outer\"\n    (it \"inner\")))")]
      (should= 1 (count (:errors result)))
      (should-contain "line 3" (first (:errors result)))
      (should-contain "(it) inside (it)" (first (:errors result)))))

  (it "detects (describe) inside (describe)"
    (let [result (pc/scan "(describe \"x\"\n  (describe \"y\"))")]
      (should= 1 (count (:errors result)))))

  (it "allows (context) inside (context)"
    (let [result (pc/scan "(describe \"x\"\n  (context \"a\"\n    (context \"b\")))")]
      (should= 0 (count (:errors result)))))

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

  (it "detects (describe) inside (context)"
    (let [result (pc/scan "(describe \"x\"\n  (context \"c\"\n    (describe \"y\")))")]
      (should= 1 (count (:errors result)))
      (should-contain "(describe) inside (context)" (first (:errors result)))))

  (it "detects (with-stubs) inside (it)"
    (let [result (pc/scan "(describe \"x\"\n  (it \"y\"\n    (with-stubs)))")]
      (should= 1 (count (:errors result)))))

  (it "detects (around) inside (it)"
    (let [result (pc/scan "(describe \"x\"\n  (it \"y\"\n    (around [f] (f))))")]
      (should= 1 (count (:errors result)))))

  (it "collects multiple errors in one file"
    (let [result (pc/scan "(describe \"x\"\n  (it \"a\"\n    (it \"b\"))\n  (it \"c\"\n    (it \"d\")))")]
      (should= 2 (count (:errors result)))
      (should-contain "line 3" (first (:errors result)))
      (should-contain "line 5" (second (:errors result)))))

  (it "reports unclosed form at EOF"
    (let [result (pc/scan "(describe \"x\"\n  (it \"y\"")]
      (should (<= 1 (count (:errors result))))
      (should-contain "unclosed" (first (:errors result))))))

(describe "format-tree"
  (it "formats a single describe with children"
    (let [forms [{:form "describe" :line 1
                  :children [{:form "before" :line 2}
                             {:form "it" :line 3}
                             {:form "it" :line 5}]}]
          result (pc/format-tree forms)]
      (should= (str "(describe :line 1\n"
                    "  (before :line 2)\n"
                    "  (it :line 3)\n"
                    "  (it :line 5))")
               result)))

  (it "formats nested context"
    (let [forms [{:form "describe" :line 1
                  :children [{:form "context" :line 2
                              :children [{:form "it" :line 3}]}]}]
          result (pc/format-tree forms)]
      (should= (str "(describe :line 1\n"
                    "  (context :line 2\n"
                    "    (it :line 3)))")
               result)))

  (it "formats leaf node"
    (should= "(describe :line 1)" (pc/format-tree [{:form "describe" :line 1}]))))

(describe "check-file"
  (it "returns OK for a well-formed spec file"
    (should= "OK" (pc/check-file "spec/empire/combat_spec.clj")))

  (it "returns errors for bad structure"
    (let [tmp (java.io.File/createTempFile "bad-spec" ".clj")
          path (.getAbsolutePath tmp)]
      (.deleteOnExit tmp)
      (spit path "(describe \"x\"\n  (it \"a\"\n    (it \"b\")))")
      (let [result (pc/check-file path)]
        (should-not= "OK" result)
        (should-contain "ERROR" result))))

  (it "appends tree when :tree option is true"
    (let [tmp (java.io.File/createTempFile "tree-spec" ".clj")
          path (.getAbsolutePath tmp)]
      (.deleteOnExit tmp)
      (spit path "(describe \"x\"\n  (it \"a\")\n  (it \"b\"))")
      (let [result (pc/check-file path {:tree true})]
        (should-contain "OK" result)
        (should-contain "(describe :line 1" result)
        (should-contain "(it :line 2)" result))))

  (it "appends tree after errors when :tree option is true"
    (let [tmp (java.io.File/createTempFile "tree-err" ".clj")
          path (.getAbsolutePath tmp)]
      (.deleteOnExit tmp)
      (spit path "(describe \"x\"\n  (it \"a\"\n    (it \"b\")))")
      (let [result (pc/check-file path {:tree true})]
        (should-contain "ERROR" result)
        (should-contain "(describe :line 1" result)))))

(describe "check-directory"
  (it "scans all .clj files in a directory"
    (let [results (pc/check-directory "spec/empire/paren_check")]
      (should (pos? (count results)))
      (should (every? #(= "OK" (:result %)) results)))))

(describe "integration — real spec files"
  (it "all spec files pass structure check"
    (let [results (pc/check-directory "spec/")]
      (should (pos? (count results)))
      (let [failures (remove #(= "OK" (:result %)) results)]
        (should= [] (mapv :file failures))))))

(run-specs)

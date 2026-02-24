(ns empire.paren-check.core-spec
  (:require [speclj.core :refer :all]
            [empire.paren-check.core :as pc]))

(describe "scan"
  (it "returns OK for empty string"
    (should= {:errors [] :depth 0} (pc/scan "")))

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

(run-specs)

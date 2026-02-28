(ns empire.acceptance.parser.given.props-spec
  (:require [speclj.core :refer :all]
            [empire.acceptance.parser.given.props :as props]))

(describe "given props parsing helpers"
  (it "parses natural-language awake fighter counts"
    (should= {:type :unit-props
              :unit "C"
              :props {}
              :container-props {:awake-fighters 2}}
             (props/parse-unit-props-line "C has two awake fighters.")))

  (it "ignores literal 'no' in awake-fighter count extractor"
    (should= {:type :unit-props
              :unit "C"
              :props {}
              :container-props {:awake-fighters 0}}
             (props/parse-unit-props-line "C has no awake fighters.")))

  (it "parses airport fighter container state"
    (should= {:type :container-state
              :target "O"
              :props {:fighter-count 1 :awake-fighters 1}}
             (props/parse-container-state-line "O has one fighter in its airport.")))

  (it "parses no-fighter container state"
    (should= {:type :container-state
              :target "C"
              :props {:fighter-count 0}}
             (props/parse-container-state-line "C has no fighters."))))

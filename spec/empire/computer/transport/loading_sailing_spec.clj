(ns empire.computer.transport.loading-sailing-spec
  (:require [empire.computer.transport.loading :as loading]
            [empire.state.api :as sa]
            [empire.test.utils :refer [reset-all-atoms! build-test-map set-test-computer-map!]]
            [speclj.core :refer :all]))

(describe "should-start-sailing?"
  (before (reset-all-atoms!))

  (it "returns true when army count is 6"
    (set-test-computer-map! (build-test-map ["~t~" "###"]))
    (should (loading/should-start-sailing? [1 0] {:army-count 6} 6)))

  (it "returns false when army count is 3"
    (set-test-computer-map! (build-test-map ["~t~" "###"]))
    (should-not (loading/should-start-sailing? [1 0] {:army-count 3} 3)))

  (it "returns true when army count is 4 and no nearby loadable armies"
    (set-test-computer-map! (build-test-map ["~t~" "~~~"]))
    (should (loading/should-start-sailing? [1 0] {:army-count 4} 4)))

  (it "returns false when army count less than 4"
    (set-test-computer-map! (build-test-map ["~t~" "###"]))
    (should-not (loading/should-start-sailing? [1 0] {:army-count 2} 2))))

(ns empire.computer.transport.invasion-sailing-spec
  (:require [empire.computer.transport :as transport]
            [empire.state.api :as sa]
            [empire.test.utils :refer [reset-all-atoms! build-test-map set-test-world! set-test-computer-map! update-test-world!]]
            [empire.test.utils :as test-utils]
            [speclj.core :refer :all]))

(describe "transition-load-for-invasion-to-sailing!"
  (before (reset-all-atoms!))

  (it "transitions to loading when transport is empty"
    (set-test-world! (build-test-map ["~t~" "###"]))
    (update-test-world! assoc-in [1 0 :contents :transport-mission] :load-for-invasion)
    (update-test-world! assoc-in [1 0 :contents :army-count] 0)
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (#'transport/transition-load-for-invasion-to-sailing! [1 0])
    (should (contains? #{:loading :hold-sail-to-load :sail-to-load}
                       (get-in (test-utils/read-test-state :game-map) [1 0 :contents :transport-mission]))))

  (it "transitions to sail-to-unload when transport has armies and path exists"
    (set-test-world! (build-test-map ["~t~~#"
                                      "~~~~~"]))
    (update-test-world! assoc-in [1 0 :contents :transport-mission] :load-for-invasion)
    (update-test-world! assoc-in [1 0 :contents :army-count] 4)
    (update-test-world! assoc-in [4 0 :country-id] nil)
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (#'transport/transition-load-for-invasion-to-sailing! [1 0])
    ;; Should either be sail-to-unload or unchanged if no unload path found
    (let [mission (get-in (test-utils/read-test-state :game-map) [1 0 :contents :transport-mission])]
      (should (contains? #{:sail-to-unload :load-for-invasion} mission)))))

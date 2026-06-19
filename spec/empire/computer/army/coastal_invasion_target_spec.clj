(ns empire.computer.army.coastal-invasion-target-spec
  (:require [empire.computer.army.coastal-invasion :as invasion]
            [empire.state.api :as sa]
            [empire.test.utils :refer [reset-all-atoms! build-test-map set-test-world! set-test-computer-map! update-test-world!]]
            [empire.test.utils :as test-utils]
            [speclj.core :refer :all]))

(defn- make-ctx []
  {:current-world (fn [] (sa/current-world))
   :adjacent-to-ocean? (fn [pos]
                         (some (fn [n]
                                 (= :sea (:type (get-in (sa/current-world) n))))
                               (for [[dr dc] [[-1 0] [1 0] [0 -1] [0 1]]
                                     :let [r (+ (first pos) dr) c (+ (second pos) dc)]
                                     :when (some? (get-in (sa/current-world) [r c]))]
                                 [r c])))})

(describe "local-empty-coast-target"
  (before (reset-all-atoms!))

  (it "finds an empty coastal cell within depth 2"
    ;; a##
    ;; ~#~
    ;; Army at [0,0], land at [1,0] [2,0], sea at [0,1] [2,1]
    ;; [2,0] is coastal (adjacent to [2,1] sea) and empty
    (set-test-world! (build-test-map ["a##" "~#~"]))
    (update-test-world! assoc-in [0 0 :country-id] 1)
    (update-test-world! assoc-in [1 0 :country-id] 1)
    (update-test-world! assoc-in [2 0 :country-id] 1)
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (let [result (invasion/local-empty-coast-target (make-ctx) [0 0] 1)]
      (should-not-be-nil result)))

  (it "returns nil when no empty coastal cell within depth 2"
    (set-test-world! (build-test-map ["a" "#"]))
    (update-test-world! assoc-in [0 0 :country-id] 1)
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (should-be-nil (invasion/local-empty-coast-target (make-ctx) [0 0] 1)))

  (it "does not return the starting position"
    (set-test-world! (build-test-map ["a#~"]))
    (update-test-world! assoc-in [0 0 :country-id] 1)
    (update-test-world! assoc-in [1 0 :country-id] 1)
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (let [result (invasion/local-empty-coast-target (make-ctx) [0 0] 1)]
      (should-not-be-nil result)
      (should-not= [0 0] result))))

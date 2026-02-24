(ns empire.mutation.runner-spec
  (:require [speclj.core :refer :all]
            [empire.mutation.runner :as runner]))

(describe "source->spec-path"
  (it "maps root source to spec"
    (should= "spec/empire/combat_spec.clj"
             (runner/source->spec-path "src/empire/combat.cljc")))

  (it "maps subdirectory source to spec"
    (should= "spec/empire/computer/ship_spec.clj"
             (runner/source->spec-path "src/empire/computer/ship.cljc")))

  (it "handles deeply nested paths"
    (should= "spec/empire/movement/map_utils_spec.clj"
             (runner/source->spec-path "src/empire/movement/map_utils.cljc"))))

(describe "spec-exists?"
  (it "returns true for existing spec"
    (should (runner/spec-exists? "spec/empire/combat_spec.clj")))

  (it "returns false for nonexistent spec"
    (should-not (runner/spec-exists? "spec/empire/nonexistent_spec.clj"))))

(describe "run-spec"
  (it "returns :killed when spec fails (real spec with syntax error)"
    (spit "/tmp/failing_spec.clj"
          "(ns failing-spec (:require [speclj.core :refer :all]))
           (describe \"fail\" (it \"fails\" (should= 1 2)))
           (run-specs)")
    (should= :killed (runner/run-spec "/tmp/failing_spec.clj")))

  (it "returns :survived when spec passes (real spec)"
    (spit "/tmp/passing_spec.clj"
          "(ns passing-spec (:require [speclj.core :refer :all]))
           (describe \"pass\" (it \"passes\" (should= 1 1)))
           (run-specs)")
    (should= :survived (runner/run-spec "/tmp/passing_spec.clj")))

  (it "returns :timeout when spec exceeds timeout"
    (spit "/tmp/hanging_spec.clj"
          "(ns hanging-spec (:require [speclj.core :refer :all]))
           (describe \"hang\" (it \"hangs\" (Thread/sleep 60000) (should= 1 1)))
           (run-specs)")
    (should= :timeout (runner/run-spec "/tmp/hanging_spec.clj" 3000))))

(describe "run-spec-timed"
  (it "returns result and elapsed time"
    (spit "/tmp/timed_spec.clj"
          "(ns timed-spec (:require [speclj.core :refer :all]))
           (describe \"timed\" (it \"passes\" (should= 1 1)))
           (run-specs)")
    (let [{:keys [result elapsed-ms]} (runner/run-spec-timed "/tmp/timed_spec.clj")]
      (should= :survived result)
      (should (pos? elapsed-ms)))))

(run-specs)

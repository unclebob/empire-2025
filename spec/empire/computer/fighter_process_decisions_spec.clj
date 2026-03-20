(ns empire.computer.fighter.process-decisions-spec
  (:require [empire.computer.fighter.process-decisions :as decisions]
            [speclj.core :refer :all]))

(describe "fighter process decisions"
  (it "normalizes a mapped step result"
    (should= {:pos [2 3] :steps-used 2}
             (decisions/step-result {:pos [2 3] :hops 2} nil)))

  (it "normalizes a burned stuck step"
    (should= {:pos [1 1] :steps-used 1}
             (decisions/step-result nil [1 1])))

  (it "halts on landing"
    (should-be-nil (decisions/step-result :landed [1 1])))

  (it "continues only when steps remain and a step exists"
    (should (decisions/continue-steps? 3 {:pos [0 0] :steps-used 1}))
    (should-not (decisions/continue-steps? 0 {:pos [0 0] :steps-used 1}))
    (should-not (decisions/continue-steps? 3 nil))))

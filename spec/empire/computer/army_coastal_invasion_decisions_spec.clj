(ns empire.computer.army-coastal-invasion-decisions-spec
  (:require [empire.computer.army.coastal-invasion-decisions :as sut]
            [speclj.core :refer :all]))

(describe "coastal invasion decisions"
  (it "prefers cached coast target"
    (should= [1 1]
             (sut/resolve-coast-target [1 1] [2 2])))

  (it "allows repath when retry round has arrived"
    (should (sut/retry-repath-now? 10 10))
    (should-not (sut/retry-repath-now? 9 10)))

  (it "settles when already at target"
    (should= {:action :settle}
             (sut/coast-step-action {:pos [1 1] :target [1 1]})))

  (it "uses repath result when direct movement fails"
    (should= {:action :repath :target [3 3]}
             (sut/coast-step-action {:pos [1 1]
                                     :target [2 2]
                                     :move-step? nil
                                     :repath-step? [3 3]}))))

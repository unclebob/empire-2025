(ns empire.computer.ship.patrol-decisions-spec
  (:require [empire.computer.ship.patrol-decisions :as sut]
            [speclj.core :refer :all]))

(describe "adjacent patrol decisions"
  (it "prefers attacking adjacent transport in non-invasion mode"
    (should= {:action :attack :target [1 1]}
             (sut/patrol-action {:major-invasion false
                                 :adjacent-transport [1 1]
                                 :adjacent-enemy [2 2]})))

  (it "flees from adjacent non-transport enemy when no transport is present"
    (should= {:action :flee :target [2 2]}
             (sut/patrol-action {:major-invasion false
                                 :adjacent-enemy [2 2]})))

  (it "attacks any adjacent enemy ship during major invasion"
    (should= {:action :attack :target [3 3]}
             (sut/patrol-action {:major-invasion true
                                 :adjacent-enemy-ship [3 3]})))

  (it "falls back to patrol when nothing is adjacent"
    (should= {:action :patrol}
             (sut/patrol-action {:major-invasion false}))))

(ns empire.game-loop.satellite-decisions-spec
  (:require [empire.game.loop.round-setup.satellite-decisions :as sut]
            [speclj.core :refer :all]))

(describe "satellite decisions"
  (it "expires a satellite with no turns remaining"
    (should= {:action :expire}
             (sut/satellite-step-action {:turns-remaining 0} 4)))

  (it "moves while steps remain"
    (should= {:action :move}
             (sut/satellite-step-action {:turns-remaining 2} 3)))

  (it "finishes the round when out of steps"
    (should= {:action :finish-round}
             (sut/satellite-step-action {:turns-remaining 2} 0)))

  (it "decrements turns at round end"
    (should= {:action :decrement-turns
              :turns-remaining 4}
             (sut/finish-round-action {:turns-remaining 5}))))

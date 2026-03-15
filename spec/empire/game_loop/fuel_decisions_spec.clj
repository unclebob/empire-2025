(ns empire.game-loop.fuel-decisions-spec
  (:require [empire.game.loop.round-setup.fuel-decisions :as sut]
            [speclj.core :refer :all]))

(describe "fuel decisions"
  (it "returns a bingo action at bingo fuel"
    (should= {:action :bingo :fuel 5}
             (sut/fuel-update-action 5 true)))

  (it "scans sentry fighters into fuel actions"
    (should= [{:pos [0 0] :update {:action :burn :fuel 31}}]
             (sut/sentry-fighter-fuel-actions [[{:contents {:type :fighter :mode :sentry :fuel 32}}]]
                                              32
                                              (fn [_ _] false)))))

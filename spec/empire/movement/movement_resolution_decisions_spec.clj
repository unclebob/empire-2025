(ns empire.game-mechanics.movement.movement-resolution-decisions-spec
  (:require [empire.game-mechanics.movement.movement-resolution-decisions :as decisions]
            [speclj.core :refer :all]))

(describe "movement resolution decisions"
  (it "normalizes malformed targets back to origin"
    (should= [1 1] (decisions/normalize-target identity [1 1] nil))
    (should= [1 1] (decisions/normalize-target identity [1 1] [2]))
    (should= [1 1] (decisions/normalize-target identity [1 1] [:x 2]))
    (should= [2 2] (decisions/normalize-target identity [1 1] [2 2])))

  (it "detects friendly and enemy blockers"
    (should (decisions/blocked-by-friendly? {:owner :player} {:contents {:owner :player}}))
    (should-not (decisions/blocked-by-friendly? {:owner :player} {:contents {:owner :computer}}))
    (should (decisions/blocked-by-enemy? {:owner :player} {:contents {:owner :computer}}))
    (should-not (decisions/blocked-by-enemy? {:owner :player} {:contents {:owner :player}})))

  (it "allows combat only for non-satellite combatants on passable terrain"
    (let [enemy-cell {:type :land :contents {:type :army :owner :computer}}
          blocked? (fn [_ _] true)
          passable? (fn [_ _] true)]
      (should (decisions/can-attack-enemy? blocked? passable? {:type :army :owner :player} enemy-cell))
      (should-not (decisions/can-attack-enemy? blocked? passable? {:type :satellite :owner :player} enemy-cell))
      (should-not (decisions/can-attack-enemy? blocked? passable? {:type :army :owner :player}
                                              {:type :land :contents {:type :satellite :owner :computer}}))
      (should-not (decisions/can-attack-enemy? blocked? (fn [_ _] false) {:type :army :owner :player} enemy-cell))))

  (it "sidesteps only for blocked city cases that match unit rules"
    (should (decisions/should-sidestep-city? {:type :army} {:type :city :city-status :player} [1 1]))
    (should-not (decisions/should-sidestep-city? {:type :army} {:type :city :city-status :computer} [1 1]))
    (should (decisions/should-sidestep-city? {:type :fighter :target [2 2]} {:type :city :city-status :computer} [1 1]))
    (should-not (decisions/should-sidestep-city? {:type :fighter :target [1 1]} {:type :city :city-status :computer} [1 1]))
    (should-not (decisions/should-sidestep-city? {:type :fighter :target [1 1]} {:type :land} [1 1])))

  (it "classifies movement actions"
    (should= :sidestep-city (decisions/movement-action {:sidestep-city? true}))
    (should= :sidestep-friendly (decisions/movement-action {:sidestep-city? false :blocked? true :blocked-by-friendly? true :can-attack-enemy? false :woke? false}))
    (should= :combat (decisions/movement-action {:sidestep-city? false :blocked? true :blocked-by-friendly? false :can-attack-enemy? true :woke? false}))
    (should= :woke (decisions/movement-action {:sidestep-city? false :blocked? false :blocked-by-friendly? false :can-attack-enemy? false :woke? true}))
    (should= :normal (decisions/movement-action {:sidestep-city? false :blocked? false :blocked-by-friendly? false :can-attack-enemy? false :woke? false})))

  (it "classifies move-unit phase"
    (should= :dock (decisions/move-unit-phase {:ship-can-dock? true}))
    (should= :move (decisions/move-unit-phase {:ship-can-dock? false}))))

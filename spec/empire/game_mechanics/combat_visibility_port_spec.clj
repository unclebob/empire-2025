(ns empire.game-mechanics.combat-visibility-port-spec
  (:require [empire.game-mechanics.combat-visibility-port :as sut]
            [speclj.core :refer :all]))

(describe "combat visibility port"
  (it "uses a noop port by default"
    (should-be-nil
     (sut/update-visibility! (sut/combat-visibility-port) [0 0] :player)))

  (it "stores and returns the active port"
    (let [port (reify sut/CombatVisibilityPort
                 (update-visibility! [_ _ _] nil))]
      (sut/set-combat-visibility-port! port)
      (should= port (sut/combat-visibility-port))))

  (it "applies each visibility effect through the supplied port"
    (let [calls (atom [])
          port (reify sut/CombatVisibilityPort
                 (update-visibility! [_ pos owner]
                   (swap! calls conj [pos owner])))]
      (sut/apply-visibility-effects! port [{:pos [1 2] :owner :player}
                                           {:pos [3 4] :owner :computer}])
      (should= [[[1 2] :player]
                [[3 4] :computer]]
               @calls))))

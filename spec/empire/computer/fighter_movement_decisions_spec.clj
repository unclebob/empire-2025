(ns empire.computer.fighter-movement-decisions-spec
  (:require [speclj.core :refer :all]
            [empire.computer.fighter-movement-decisions :as decisions]))

(describe "fighter-movement-decisions"
  (it "fills missing hits from unit type"
    (should= 1
             (:hits (decisions/ensure-hits {:type :fighter :owner :computer}))))

  (it "rejects invalid fuel updates for non-computer fighters"
    (should= {:action :invalid}
             (decisions/fuel-action {:type :fighter :owner :player :fuel 3} 32)))

  (it "returns destroy when fuel would drop to zero"
    (should= {:action :destroy}
             (decisions/fuel-action {:type :fighter :owner :computer :fuel 1} 32)))

  (it "classifies attack and patrol follow-up"
    (should= :occupy-target (decisions/attack-result-action :attacker))
    (should= :attacker-lost (decisions/attack-result-action :defender))
    (should= :execute-hop (decisions/patrol-action {:has-target? true :has-hop? true}))
    (should-be-nil (decisions/patrol-action {:has-target? false :has-hop? false})))

  (it "builds an attackable combat context only for numeric-hits combatants"
    (let [world [[{:type :sea
                   :contents {:type :fighter :owner :computer :fuel 4}}
                  {:type :sea
                   :contents {:type :army :owner :player :hits 1}}]]
          ctx (decisions/attack-context world
                                        [0 0]
                                        [0 1]
                                        (constantly true))]
      (should (:attackable? ctx))
      (should= 1 (get-in ctx [:attacker :hits]))
      (should= 1 (get-in ctx [:defender :hits])))))

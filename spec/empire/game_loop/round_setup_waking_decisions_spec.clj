(ns empire.game-loop.round-setup-waking-decisions-spec
  (:require [speclj.core :refer :all]
            [empire.game.loop.round-setup.waking-decisions :as decisions]))

(describe "round-setup-waking-decisions"
  (it "finds player airport fighters to wake"
    (let [world [[{:type :city :city-status :player :fighter-count 2}]
                 [{:type :city :city-status :computer :fighter-count 4}]]]
      (should= [{:pos [0 0] :awake-fighters 2}]
               (vec (decisions/airport-fighter-wakes world)))))

  (it "finds player carrier fighters to wake"
    (let [world [[{:type :sea
                   :contents {:type :carrier :owner :player :fighter-count 3}}]
                 [{:type :sea
                   :contents {:type :carrier :owner :computer :fighter-count 3}}]]]
      (should= [{:pos [0 0] :awake-fighters 3}]
               (vec (decisions/carrier-fighter-wakes world)))))

  (it "marks visible sentries for waking"
    (with-redefs [empire.game-mechanics.movement.wake-conditions/enemy-unit-visible?
                  (fn [_ _ _] true)]
      (let [world [[{:type :land
                     :contents {:type :army :owner :player :mode :sentry}}]]]
        (should= [{:pos [0 0] :reason :enemy-spotted}]
                 (vec (decisions/sentry-enemy-wakes world))))))

  (it "translates fighter wakes into assoc updates"
    (should= [{:path [1 2 :awake-fighters] :value 3}]
             (decisions/wake-updates :awake-fighters
                                     [{:pos [1 2] :awake-fighters 3}])))

  (it "translates sentry wakes into update operations"
    (let [{:keys [path update-fn]} (first (decisions/sentry-wake-updates
                                           [{:pos [2 3] :reason :enemy-spotted}]))]
      (should= [2 3 :contents] path)
      (should= {:mode :awake :reason :enemy-spotted}
               (update-fn {})))))

(ns empire.ui.util.input.actions-production-spec
  (:require [empire.ui.util.input.actions.production :as production]
            [speclj.core :refer :all]))

(describe "city production input actions"
  (it "shows an error when an inland city tries to build a naval unit"
    (let [calls (atom [])]
      (with-redefs [empire.game-mechanics.movement.map-utils/on-coast? (fn [& _] false)
                    empire.config.units.dispatcher/naval-units (fn [item] (= item :destroyer))
                    empire.ui.util.input.actions.helpers/set-error-message! (fn [msg duration]
                                                                              (swap! calls conj [:error msg duration]))
                    empire.player.production/set-city-production (fn [& args] (swap! calls conj [:set args]))
                    empire.ui.util.input.actions.helpers/item-processed! (fn [] (swap! calls conj :processed))]
        (should (production/handle-city-production-decision {:action :set-production :item :destroyer}
                                                            [2 2]
                                                            {:type :city :city-status :player}))
        (should= [[:error "Must be coastal city to produce destroyer." empire.config.core/error-message-duration]]
                 @calls))))

  (it "sets production and marks the item processed for allowed units"
    (let [calls (atom [])]
      (with-redefs [empire.game-mechanics.movement.map-utils/on-coast? (fn [& _] true)
                    empire.config.units.dispatcher/naval-units (constantly false)
                    empire.player.production/set-city-production (fn [coords item]
                                                                   (swap! calls conj [:set coords item]))
                    empire.ui.util.input.actions.helpers/item-processed! (fn [] (swap! calls conj :processed))]
        (should (production/handle-city-production-decision {:action :set-production :item :army}
                                                            [1 1]
                                                            {:type :city :city-status :player}))
        (should= [[:set [1 1] :army] :processed] @calls))))

  (it "skips and clears production only for idle player cities"
    (let [calls (atom [])]
      (with-redefs [empire.game-mechanics.movement.movement-state/get-active-unit (constantly nil)
                    empire.state.api/update-state! (fn [& args] (swap! calls conj args))
                    empire.ui.util.input.actions.helpers/item-processed! (fn [] (swap! calls conj :processed))]
        (should (production/handle-city-production-decision {:action :skip} [3 3] {:type :city :city-status :player}))
        (should (production/handle-city-production-decision {:action :clear-production} [3 3] {:type :city :city-status :player}))
        (should= [[:player-items rest]
                  :processed
                  [:production assoc [3 3] :none]
                  :processed]
                 @calls))))

  (it "ignores non-player cities and cities with an active unit"
    (with-redefs [empire.game-mechanics.movement.movement-state/get-active-unit (constantly :unit)]
      (should-be-nil
       (production/handle-city-production-decision {:action :skip} [0 0] {:type :city :city-status :player}))
      (should-be-nil
       (production/handle-city-production-decision {:action :skip} [0 0] {:type :city :city-status :computer}))))

  (it "allows production when a city needs production even if it has airport fighters"
    (let [calls (atom [])]
      (with-redefs [empire.game-mechanics.movement.movement-state/get-active-unit
                    (fn [cell coords]
                      (if coords nil {:type :fighter :mode :awake :owner :player :from-airport true}))
                    empire.game-mechanics.movement.map-utils/on-coast? (constantly true)
                    empire.config.units.dispatcher/naval-units (constantly false)
                    empire.player.production/set-city-production (fn [coords item]
                                                                   (swap! calls conj [:set coords item]))
                    empire.ui.util.input.actions.helpers/item-processed! (fn [] (swap! calls conj :processed))]
        (should (production/handle-city-production-decision {:action :set-production :item :fighter}
                                                            [0 35]
                                                            {:type :city :city-status :player :fighter-count 5 :awake-fighters 4}))
        (should= [[:set [0 35] :fighter] :processed] @calls))))

  (it "delegates key handling through city-key-action"
    (with-redefs [empire.player.command-decisions/city-key-action (fn [k cell]
                                                                    (should= :a k)
                                                                    (should= {:type :city} cell)
                                                                    {:action :skip})
                  empire.ui.util.input.actions.production/handle-city-production-decision (fn [decision coords cell]
                                                                                             [decision coords cell])]
      (should= [{:action :skip} [4 4] {:type :city}]
               (production/handle-city-production-key :a [4 4] {:type :city})))))

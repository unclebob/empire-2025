(ns empire.game-mechanics.movement.api-spec
  (:require [empire.game-mechanics.movement.api :as sut]
            [empire.game-mechanics.movement.api-decisions :as decisions]
            [empire.game-mechanics.movement.movement-execution :as execution]
            [empire.game-mechanics.movement.movement-pathing :as pathing]
            [empire.game-mechanics.movement.movement-resolution :as resolution]
            [empire.game-mechanics.movement.movement-state :as state]
            [speclj.core :refer :all]))

(describe "movement api"
  (it "delegates pathing helpers"
    (with-redefs [pathing/next-step-pos (fn [from to] {:from from :to to})
                  pathing/chebyshev-distance (fn [from to] [from to])
                  pathing/find-best-sidestep (fn [from target unit-type blocked-dir current-map]
                                               [from target unit-type blocked-dir current-map])]
      (should= {:from [0 0] :to [1 1]}
               (sut/next-step-pos [0 0] [1 1]))
      (should= [[2 2] [3 4]]
               (sut/chebyshev-distance [2 2] [3 4]))
      (should= [[0 0] [1 0] :army :east :map]
               (sut/find-best-sidestep [0 0] [1 0] :army :east :map))))

  (it "delegates execution helpers"
    (with-redefs [execution/process-consumables (fn [unit to-cell] [unit to-cell])
                  execution/do-move (fn [from final-pos cell final-unit]
                                      {:from from :final-pos final-pos :cell cell :unit final-unit})]
      (should= [{:type :fighter} {:type :sea}]
               (sut/process-consumables {:type :fighter} {:type :sea}))
      (should= {:from [0 0] :final-pos [1 0] :cell {:type :land} :unit {:type :army}}
               (sut/do-move [0 0] [1 0] {:type :land} {:type :army}))))

  (it "wraps movement resolution results through api decisions"
    (with-redefs [resolution/move-unit (fn [from target cell current-map]
                                         {:from from :target target :cell cell :map current-map})
                  decisions/move-unit-result (fn [result] {:wrapped result})]
      (should= {:wrapped {:from [0 0] :target [1 1] :cell :cell :map :map}}
               (sut/move-unit [0 0] [1 1] :cell :map))))

  (it "normalizes movement arguments for both arities"
    (let [decision-calls (atom [])
          resolution-calls (atom [])]
      (with-redefs [decisions/set-unit-movement-args (fn [unit target extended?]
                                                       (swap! decision-calls conj [unit target extended?])
                                                       {:unit-coords [:normalized unit]
                                                        :target-coords [:normalized target]
                                                        :extended? (not extended?)})
                    resolution/set-unit-movement (fn
                                                  ([unit target]
                                                   (swap! resolution-calls conj [unit target]))
                                                  ([unit target extended?]
                                                   (swap! resolution-calls conj [unit target extended?])))]
        (sut/set-unit-movement [0 0] [1 1])
        (sut/set-unit-movement [2 2] [3 3] true))
      (should= [[[0 0] [1 1] false]
                [[2 2] [3 3] true]]
               @decision-calls)
      (should= [[[:normalized [0 0]] [:normalized [1 1]]]
                [[:normalized [2 2]] [:normalized [3 3]] false]]
               @resolution-calls)))

  (it "delegates state helpers"
    (with-redefs [state/get-active-unit (fn [cell] [:active cell])
                  state/is-army-aboard-transport? (fn [unit] (= unit :army))
                  state/is-fighter-from-airport? (fn [unit] (= unit :airport))
                  state/is-fighter-from-carrier? (fn [unit] (= unit :carrier))
                  state/movement-context (fn [cell unit] {:cell cell :unit unit})
                  state/set-unit-mode (fn [coords mode] [coords mode])
                  state/add-unit-at (fn
                                      ([coords unit-type] [coords unit-type :default])
                                      ([coords unit-type owner] [coords unit-type owner]))
                  state/wake-at (fn [coords] [:wake coords])]
      (should= [:active :cell] (sut/get-active-unit :cell))
      (should (sut/is-army-aboard-transport? :army))
      (should (sut/is-fighter-from-airport? :airport))
      (should (sut/is-fighter-from-carrier? :carrier))
      (should= {:cell :cell :unit :unit} (sut/movement-context :cell :unit))
      (should= [[4 4] :sentry] (sut/set-unit-mode [4 4] :sentry))
      (should= [[5 5] :army :default] (sut/add-unit-at [5 5] :army))
      (should= [[6 6] :fighter :player] (sut/add-unit-at [6 6] :fighter :player))
      (should= [:wake [7 7]] (sut/wake-at [7 7])))))

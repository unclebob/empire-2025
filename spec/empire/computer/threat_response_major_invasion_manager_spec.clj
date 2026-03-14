(ns empire.computer.threat-response-major-invasion-manager-spec
  (:require [speclj.core :refer :all]
            [empire.computer.threat-response.major-invasion-manager :as manager]))

(defn- update-world-fn
  [world-atom]
  (fn [f & args]
    (apply swap! world-atom f args)))

(defn- update-state-fn
  [state-atom]
  (fn [f & args]
    (apply swap! state-atom f args)))

(describe "threat-response major-invasion manager"
  (it "stands down invasion units after unsustainable losses"
    (let [world (atom [[{:type :sea
                         :contents {:type :transport
                                    :owner :computer
                                    :major-invasion true
                                    :transport-mission :invading
                                    :invasion-target [0 0]
                                    :invasion-path [[0 0]]
                                    :invasion-path-origin [0 0]
                                    :invasion-plan-revision 1
                                    :major-invasion-find-armies-round 3}}]
                       [{:type :land
                         :contents {:type :fighter
                                    :owner :computer
                                    :major-invasion true
                                    :kamikazee true
                                    :kamikazee-route [[0 0]]}}]])
          state (atom {:active? true
                       :decision :ready
                       :target-land-set #{[0 0]}
                       :first-landing-round 2})
          ctx {:load-major-invasion-state (fn [] @state)
               :update-major-invasion-state! (update-state-fn state)
               :current-world (fn [] @world)
               :read-runtime-state (fn [_] nil)
               :update-game-map! (update-world-fn world)
               :next-review-round-fn (constantly 9)
               :current-round-fn (constantly 5)
               :dec-threat-rounds-fn identity
               :find-computer-unit-positions-fn (constantly [])
               :apply-major-invasion-assignment!-fn (fn [& _] nil)
               :refresh-country-defense!-fn (fn [] nil)
               :chebyshev-distance-fn (fn [[x1 y1] [x2 y2]]
                                        (max (Math/abs (- x1 x2))
                                             (Math/abs (- y1 y2))))
               :recompute-major-invasion-target-land!-fn (fn [] nil)
               :recompute-sea-reachable-detection-points!-fn (fn [] nil)}]
      (with-redefs [empire.computer.threat-response.invasion-decision/invasion-armies-on-target-continent (fn [& _] 0)
                    empire.computer.threat-response.invasion-decision/armies-in-transports-to-target-continent (fn [& _] 0)
                    empire.computer.threat-response.kamikazee/refresh-army-targets! (fn [& _] nil)]
        (manager/on-round-start! ctx))
      (should-not (:active? @state))
      (should= :unsustainable-losses (:failure-reason @state))
      (should= :deferred (:decision @state))
      (should= 9 (:next-review-round @state))
      (should= :sailing (get-in @world [0 0 :contents :transport-mission]))
      (should-be-nil (get-in @world [0 0 :contents :major-invasion]))
      (should-be-nil (get-in @world [1 0 :contents :major-invasion]))
      (should-be-nil (get-in @world [1 0 :contents :kamikazee]))))

  (it "forces patrol boats into exploration when sea-pathing is unavailable"
    (let [world (atom [[{:type :sea
                         :contents {:type :patrol-boat
                                    :owner :computer
                                    :patrol-mode :idle
                                    :explore-path [[1 0]]}}]])
          state (atom {:active? false
                       :decision :deferred
                       :failure-reason :no-sea-path})
          ctx {:load-major-invasion-state (fn [] @state)
               :update-major-invasion-state! (update-state-fn state)
               :current-world (fn [] @world)
               :read-runtime-state (fn [_] nil)
               :update-game-map! (update-world-fn world)
               :next-review-round-fn (constantly 9)
               :current-round-fn (constantly 5)
               :dec-threat-rounds-fn identity
               :find-computer-unit-positions-fn (fn [pred]
                                                  (if (pred {:type :patrol-boat})
                                                    [[0 0]]
                                                    []))
               :apply-major-invasion-assignment!-fn (fn [& _] nil)
               :refresh-country-defense!-fn (fn [] nil)
               :chebyshev-distance-fn (fn [& _] 0)
               :recompute-major-invasion-target-land!-fn (fn [] nil)
               :recompute-sea-reachable-detection-points!-fn (fn [] nil)}]
      (manager/on-round-start! ctx)
      (should= :exploring (get-in @world [0 0 :contents :patrol-mode]))
      (should-be-nil (get-in @world [0 0 :contents :explore-path])))))

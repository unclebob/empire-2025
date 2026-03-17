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
  (it "uses built-in recompute functions when callback hooks are absent"
    (let [world (atom [[{:type :sea}]])
          state (atom {:active? false
                       :detection-points #{[0 0]}
                       :target-land-set #{}
                       :target-land-revision 0})
          refreshed? (atom false)
          ctx {:load-major-invasion-state (fn [] @state)
               :update-major-invasion-state! (update-state-fn state)
               :current-world (fn [] @world)
               :read-runtime-state (fn [_] {:computer-map @world})
               :update-game-map! (update-world-fn world)
               :next-review-round-fn (constantly 9)
               :current-round-fn (constantly 5)
               :dec-threat-rounds-fn identity
               :find-computer-unit-positions-fn (constantly [])
               :apply-major-invasion-assignment!-fn (fn [& _] nil)
               :refresh-country-defense!-fn (fn [] nil)
               :computer-sea-unit-types #{:carrier}
               :chebyshev-distance-fn (fn [& _] 0)}]
      (with-redefs [empire.computer.threat-response.invasion-state/recompute-target-land
                    (fn [& _] #{[0 0]})
                    empire.computer.threat-response.invasion-decision/evaluate-invasion-start
                    (fn [_]
                      {:decision :ready
                       :sea-reachable-detection-points #{[0 0]}})
                    empire.computer.threat-response.major-invasion/recompute-sea-reachable-detection-points!
                    (fn [_]
                      (swap! state assoc :sea-reachable-detection-points #{[0 0]}))
                    empire.computer.threat-response.major-invasion-manager/refresh-major-invasion-assignments!
                    (fn [_]
                      (reset! refreshed? true))]
        (manager/evaluate-major-invasion-start! ctx))
      (should= #{[0 0]} (:target-land-set @state))
      (should= 1 (:target-land-revision @state))
      (should= #{[0 0]} (:sea-reachable-detection-points @state))
      (should @refreshed?)))

  (it "recomputes target land from the computer map instead of the game map"
    (let [world (atom [[{:type :land} {:type :land}]])
          computer-map (atom [[{:type :land} {:type :sea}]])
          state (atom {:detection-points #{[0 0]}
                       :target-land-set #{}
                       :target-land-revision 0})
          ctx {:load-major-invasion-state (fn [] @state)
               :update-major-invasion-state! (update-state-fn state)
               :current-world (fn [] @world)
               :read-runtime-state (fn [k]
                                     (case k
                                       :computer-map @computer-map
                                       nil))
               :update-game-map! (update-world-fn world)}]
      (manager/recompute-major-invasion-target-land! ctx)
      (should= #{[0 0]} (:target-land-set @state))
      (should= 1 (:target-land-revision @state))))

  (it "records deferred evaluation state when invasion start is not ready"
    (let [world (atom [[{:type :sea}]])
          state (atom {:active? true
                       :detection-points #{[0 0]}})
          ctx {:load-major-invasion-state (fn [] @state)
               :update-major-invasion-state! (update-state-fn state)
               :current-world (fn [] @world)
               :read-runtime-state (fn [_] {:computer-map @world})
               :update-game-map! (update-world-fn world)
               :next-review-round-fn (constantly 12)
               :current-round-fn (constantly 5)
               :dec-threat-rounds-fn identity
               :find-computer-unit-positions-fn (constantly [])
               :apply-major-invasion-assignment!-fn (fn [& _] nil)
               :refresh-country-defense!-fn (fn [] nil)
               :computer-sea-unit-types #{:carrier}
               :chebyshev-distance-fn (fn [& _] 0)}]
      (with-redefs [empire.computer.threat-response.invasion-decision/evaluate-invasion-start
                    (fn [_]
                      {:decision :deferred
                       :failure-reason :insufficient-resources
                       :sea-reachable-detection-points #{[1 1]}})]
        (manager/evaluate-major-invasion-start! ctx))
      (should-not (:active? @state))
      (should= :deferred (:decision @state))
      (should= :insufficient-resources (:failure-reason @state))
      (should= 12 (:next-review-round @state))
      (should= #{[1 1]} (:sea-reachable-detection-points @state))))

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

  (it "clears invasion flags from non-mission transport without resetting sailing"
    (let [world (atom [[{:type :sea
                         :contents {:type :transport
                                    :owner :computer
                                    :major-invasion true
                                    :transport-mission :sailing}}]])
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
               :chebyshev-distance-fn (fn [& _] 0)
               :recompute-major-invasion-target-land!-fn (fn [] nil)
               :recompute-sea-reachable-detection-points!-fn (fn [] nil)}]
      (with-redefs [empire.computer.threat-response.invasion-decision/invasion-armies-on-target-continent (fn [& _] 0)
                    empire.computer.threat-response.invasion-decision/armies-in-transports-to-target-continent (fn [& _] 0)
                    empire.computer.threat-response.kamikazee/refresh-army-targets! (fn [& _] nil)]
        (manager/on-round-start! ctx))
      (should= :sailing (get-in @world [0 0 :contents :transport-mission]))
      (should-be-nil (get-in @world [0 0 :contents :major-invasion]))))

  (it "stands down resettable transport missions even without a major-invasion flag"
    (let [world (atom [[{:type :sea
                         :contents {:type :transport
                                    :owner :computer
                                    :transport-mission :invading
                                    :invasion-target [0 0]}}]])
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
               :chebyshev-distance-fn (fn [& _] 0)
               :recompute-major-invasion-target-land!-fn (fn [] nil)
               :recompute-sea-reachable-detection-points!-fn (fn [] nil)}]
      (with-redefs [empire.computer.threat-response.invasion-decision/invasion-armies-on-target-continent (fn [& _] 0)
                    empire.computer.threat-response.invasion-decision/armies-in-transports-to-target-continent (fn [& _] 0)
                    empire.computer.threat-response.kamikazee/refresh-army-targets! (fn [& _] nil)]
        (manager/on-round-start! ctx))
      (should= :sailing (get-in @world [0 0 :contents :transport-mission]))
      (should-be-nil (get-in @world [0 0 :contents :invasion-target]))))

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
      (should-be-nil (get-in @world [0 0 :contents :explore-path]))))

  (it "does not rebuild kamikazee routing on round start when city count is unchanged"
    (let [world (atom [[{:type :city :city-status :computer}]
                       [{:type :sea}]])
          state (atom {:active? true
                       :decision :ready
                       :target-land-set #{[0 0]}
                       :detection-points #{[0 0]}
                       :kamikazee-routing-city-count 1})
          rebuilds (atom 0)
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
               :chebyshev-distance-fn (fn [& _] 0)
               :recompute-major-invasion-target-land!-fn (fn [] nil)
               :recompute-sea-reachable-detection-points!-fn (fn [] nil)}]
      (with-redefs [empire.computer.threat-response.kamikazee/rebuild-routing-graph!
                    (fn [_] (swap! rebuilds inc))
                    empire.computer.threat-response.kamikazee/refresh-army-targets! (fn [& _] nil)
                    empire.computer.threat-response.invasion-decision/invasion-armies-on-target-continent (fn [& _] 1)
                    empire.computer.threat-response.invasion-decision/armies-in-transports-to-target-continent (fn [& _] 1)]
        (manager/on-round-start! ctx))
      (should= 0 @rebuilds)))

  (it "rebuilds kamikazee routing on round start after the computer conquers a city"
    (let [world (atom [[{:type :city :city-status :computer}]
                       [{:type :city :city-status :computer}]])
          state (atom {:active? true
                       :decision :ready
                       :target-land-set #{[0 0]}
                       :detection-points #{[0 0]}
                       :kamikazee-routing-city-count 1})
          rebuilds (atom 0)
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
               :chebyshev-distance-fn (fn [& _] 0)
               :recompute-major-invasion-target-land!-fn (fn [] nil)
               :recompute-sea-reachable-detection-points!-fn (fn [] nil)}]
      (with-redefs [empire.computer.threat-response.kamikazee/rebuild-routing-graph!
                    (fn [_] (swap! rebuilds inc))
                    empire.computer.threat-response.kamikazee/refresh-army-targets! (fn [& _] nil)
                    empire.computer.threat-response.invasion-decision/invasion-armies-on-target-continent (fn [& _] 1)
                    empire.computer.threat-response.invasion-decision/armies-in-transports-to-target-continent (fn [& _] 1)]
        (manager/on-round-start! ctx))
      (should= 1 @rebuilds)
      (should= 2 (:kamikazee-routing-city-count @state)))))

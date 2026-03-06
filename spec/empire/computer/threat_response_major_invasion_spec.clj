(ns empire.computer.threat-response-major-invasion-spec
  (:require [speclj.core :refer :all]
            [empire.computer.threat-response.major-invasion :as mi]
            [empire.computer.threat-response.invasion-state :as invasion-state]
            [empire.game-mechanics.movement.pathfinding-bfs :as pathfinding-bfs]))

(defn- update-world-fn
  [world-atom]
  (fn [f & args]
    (apply swap! world-atom f args)))

(describe "threat-response major-invasion"
  (context "connected-coastal-candidates fallback"
    (it "falls back to [target] when no radius candidates exist"
      (let [computer-map [[{:type :land}]]
            state {:target-land-set #{}}
            target [0 0]]
        (with-redefs [invasion-state/flood-fill-land (fn [& _] nil)]
          (should= [target]
                   (vec (mi/connected-coastal-candidates computer-map state target))))))

    (it "uses nearby radius candidates when flood-fill is unavailable"
      (let [computer-map [[{:type :land} {:type :land}]]
            state {:target-land-set #{[0 0] [0 1] [9 9]}}
            target [0 0]]
        (with-redefs [invasion-state/flood-fill-land (fn [& _] nil)]
          (should= #{[0 0] [0 1]}
                   (set (mi/connected-coastal-candidates computer-map state target)))))))

  (context "assignment branches"
    (it "marks fighter and ship types for major invasion"
      (let [world (atom [[{:type :land
                           :contents {:type :fighter :owner :computer}}
                          {:type :sea
                           :contents {:type :destroyer :owner :computer}}]])
            ctx {:update-game-map! (update-world-fn world)
                 :nearest-major-target (fn [_] [0 0])
                 :nearest-major-sea-target-fn (fn [_] [0 0])
                 :major-invasion-ship-types #{:destroyer}}]
        (mi/apply-major-invasion-assignment! ctx [0 0] (get-in @world [0 0 :contents]))
        (mi/apply-major-invasion-assignment! ctx [0 1] (get-in @world [0 1 :contents]))
        (should (true? (get-in @world [0 0 :contents :major-invasion])))
        (should (true? (get-in @world [0 1 :contents :major-invasion]))))))

  (context "prepare transport routing and stale clearing"
    (it "plans invasion route when target exists"
      (let [world (atom [[{:type :sea
                           :contents {:type :transport :owner :computer :transport-mission :sailing :army-count 1}}]])
            state (atom {:target-land-revision 3
                         :detection-points #{[0 0]}
                         :target-land-set #{[0 0]}})
            ctx {:load-major-invasion-state (fn [] @state)
                 :read-runtime-state (fn [k]
                                       (case k
                                         :computer-map [[{:type :sea :contents {:type :transport :owner :computer}}]]
                                         :round-number 10
                                         nil))
                 :update-major-invasion-state! (fn [f & args] (apply swap! state f args))
                 :update-game-map! (update-world-fn world)
                 :current-world (fn [] @world)
                 :nearest-major-target (fn [_] [0 0])
                 :computer-sea-unit-types #{:transport}}]
        (with-redefs [pathfinding-bfs/bfs-to-land-ho-target (fn [_ _ _] [[0 0]])]
          (mi/prepare-transport-major-invasion! ctx [0 0] (get-in @world [0 0 :contents])))
        (should= :invading (get-in @world [0 0 :contents :transport-mission]))
        (should= [0 0] (get-in @world [0 0 :contents :invasion-target]))))

    (it "clears stale invading route when no target exists"
      (let [world (atom [[{:type :sea
                           :contents {:type :transport :owner :computer
                                      :transport-mission :invading
                                      :invasion-target [1 1]
                                      :invasion-path [[1 1]]
                                      :invasion-plan-revision 1
                                      :invasion-path-origin [0 0]
                                      :army-count 1}}]])
            state (atom {:target-land-revision 1})
            ctx {:load-major-invasion-state (fn [] @state)
                 :read-runtime-state (fn [_] nil)
                 :update-major-invasion-state! (fn [f & args] (apply swap! state f args))
                 :update-game-map! (update-world-fn world)
                 :current-world (fn [] @world)
                 :nearest-major-target (fn [_] nil)
                 :computer-sea-unit-types #{:transport}}]
        (mi/prepare-transport-major-invasion! ctx [0 0] (get-in @world [0 0 :contents]))
        (should= :sailing (get-in @world [0 0 :contents :transport-mission]))
        (should-be-nil (get-in @world [0 0 :contents :invasion-target])))))

  (context "trim stale find-armies missions"
    (it "sets find-armies round when not timed out"
      (let [world (atom [[{:type :sea
                           :contents {:type :transport :owner :computer
                                      :transport-mission :find-armies-for-invasion
                                      :major-invasion-target [0 0]}}]])
            state (atom {:known-transports #{[0 0]} :target-land-revision 9})
            ctx {:load-major-invasion-state (fn [] @state)
                 :read-runtime-state (fn [_] 10)
                 :update-major-invasion-state! (fn [f & args] (apply swap! state f args))
                 :update-game-map! (update-world-fn world)
                 :current-world (fn [] @world)}]
        (mi/trim-stale-find-armies-missions! ctx)
        (should= 10 (get-in @world [0 0 :contents :major-invasion-find-armies-round]))))

    (it "times out find-armies mission and clears invasion route fields"
      (let [world (atom [[{:type :sea
                           :contents {:type :transport :owner :computer
                                      :transport-mission :find-armies-for-invasion
                                      :major-invasion-target [0 0]
                                      :major-invasion-find-armies-round 1
                                      :invasion-target [0 0]
                                      :invasion-path [[0 0]]
                                      :invasion-path-origin [0 0]
                                      :invasion-plan-revision 1}}]])
            state (atom {:known-transports #{[0 0]} :target-land-revision 9})
            ctx {:load-major-invasion-state (fn [] @state)
                 :read-runtime-state (fn [_] 20)
                 :update-major-invasion-state! (fn [f & args] (apply swap! state f args))
                 :update-game-map! (update-world-fn world)
                 :current-world (fn [] @world)}]
        (mi/trim-stale-find-armies-missions! ctx)
        (should= :sailing (get-in @world [0 0 :contents :transport-mission]))
        (should= 9 (get-in @world [0 0 :contents :major-invasion-skip-revision]))
        (should-be-nil (get-in @world [0 0 :contents :major-invasion-target]))))))

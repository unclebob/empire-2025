(ns empire.computer.threat-response-major-invasion-spec
  (:require [speclj.core :refer :all]
            [empire.computer.threat-response.major-invasion :as mi]
            [empire.computer.threat-response.major-invasion-assignment :as assignment]
            [empire.computer.threat-response.invasion-state :as invasion-state]
            [empire.game.loop.profiling :as profiling]
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

    (it "does not assign hidden player armies as kamikazee fighter targets"
      (let [world (atom [[{:type :land
                           :contents {:type :fighter :owner :computer :fuel 32}}
                          {:type :land}
                          {:type :land
                           :contents {:type :army :owner :player}}]])
            computer-map [[{:type :land
                            :contents {:type :fighter :owner :computer :fuel 32}}
                           {:type :land}
                           {:type :land}]]
            state (atom {:kamikazee-army-targets [{:pos [0 2] :seen-round 1}]})
            ctx {:update-game-map! (update-world-fn world)
                 :load-major-invasion-state (fn [] @state)
                 :read-runtime-state (fn [k]
                                       (case k
                                         :computer-map computer-map
                                         :round-number 3
                                         nil))
                 :nearest-major-target (fn [_] [9 9])
                 :major-invasion-ship-types #{:destroyer}}]
        (assignment/apply-major-invasion-assignment! ctx [0 0] (get-in @world [0 0 :contents]))
        (should= [] (get-in @world [0 0 :contents :kamikazee-targets]))))

    (it "freezes carrier bridge support in sentry mode"
      (let [world (atom [[{:type :sea
                           :contents {:type :carrier :owner :computer}}]])
            ctx {:update-game-map! (update-world-fn world)
                 :nearest-major-ship-target-fn (fn [_] [9 9])
                 :major-invasion-ship-types #{:carrier}}]
        (with-redefs [empire.computer.threat-response.kamikazee/carrier-support-target
                      (fn [_ _] [0 0])]
          (mi/apply-major-invasion-assignment! ctx [0 0] (get-in @world [0 0 :contents])))
        (should= true (get-in @world [0 0 :contents :major-invasion]))
        (should= :sentry (get-in @world [0 0 :contents :mode]))
        (should= [0 0] (get-in @world [0 0 :contents :major-invasion-target]))))

    (it "assigns non-bridge carriers toward the nearest sea target"
      (let [world (atom [[{:type :sea
                           :contents {:type :carrier :owner :computer}}]])
            ctx {:update-game-map! (update-world-fn world)
                 :nearest-major-ship-target-fn (fn [_] [9 9])
                 :major-invasion-ship-types #{:carrier}}]
        (with-redefs [empire.computer.threat-response.kamikazee/carrier-support-target
                      (fn [_ _] nil)]
          (assignment/apply-major-invasion-assignment! ctx [0 0] (get-in @world [0 0 :contents])))
        (should= true (get-in @world [0 0 :contents :major-invasion]))
        (should= [9 9] (get-in @world [0 0 :contents :major-invasion-target]))
        (should-not= :sentry (get-in @world [0 0 :contents :mode]))))

    (it "delegates transport preparation"
      (let [world (atom [[{:type :sea
                           :contents {:type :transport :owner :computer}}]])
            called (atom nil)
            ctx {:update-game-map! (update-world-fn world)
                 :prepare-transport-major-invasion!-fn (fn [pos unit]
                                                         (reset! called [pos (:type unit)]))
                 :major-invasion-ship-types #{:carrier}}]
        (assignment/apply-major-invasion-assignment! ctx [0 0] (get-in @world [0 0 :contents]))
        (should= [[0 0] :transport] @called))))

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
        (should= :sail-to-unload (get-in @world [0 0 :contents :transport-mission]))
        (should-be-nil (get-in @world [0 0 :contents :invasion-target]))))

    (it "does not rewrite a transport when the major invasion target is already current"
      (let [world (atom [[{:type :sea
                           :contents {:type :transport :owner :computer
                                      :major-invasion true
                                      :major-invasion-target [0 0]
                                      :transport-mission :invading
                                      :invasion-target [0 0]
                                      :invasion-path [[0 0]]
                                      :invasion-plan-revision 3
                                      :invasion-path-origin [0 0]
                                      :army-count 1}}]])
            state (atom {:target-land-revision 3
                         :detection-points #{[0 0]}
                         :target-land-set #{[0 0]}})
            updates (atom 0)
            syncs (atom 0)
            ctx {:load-major-invasion-state (fn [] @state)
                 :read-runtime-state (fn [k]
                                       (case k
                                         :computer-map [[{:type :sea :contents {:type :transport :owner :computer}}]]
                                         :round-number 10
                                         nil))
                 :update-major-invasion-state! (fn [f & args] (apply swap! state f args))
                 :update-game-map! (fn [f & args]
                                     (swap! updates inc)
                                     (apply swap! world f args))
                 :sync-ai-unit! (fn [_] (swap! syncs inc))
                 :current-world (fn [] @world)
                 :nearest-major-target (fn [_] [0 0])
                 :computer-sea-unit-types #{:transport}}]
        (with-redefs [pathfinding-bfs/bfs-to-land-ho-target (fn [_ _ _] (throw (ex-info "should not plan" {})))]
          (mi/prepare-transport-major-invasion! ctx [0 0] (get-in @world [0 0 :contents])))
        (should= 0 @updates)
        (should= 0 @syncs)))

    (it "does not replan an invading transport that is already crawling with the current target revision"
      (let [world (atom [[{:type :sea
                           :contents {:type :transport :owner :computer
                                      :major-invasion true
                                      :major-invasion-target [0 0]
                                      :transport-mission :invading
                                      :invasion-target [0 0]
                                      :invasion-plan-revision 3
                                      :army-count 1}}]])
            state (atom {:target-land-revision 3
                         :detection-points #{[0 0]}
                         :target-land-set #{[0 0]}})
            updates (atom 0)
            syncs (atom 0)
            ctx {:load-major-invasion-state (fn [] @state)
                 :read-runtime-state (fn [k]
                                       (case k
                                         :computer-map [[{:type :sea :contents {:type :transport :owner :computer}}]]
                                         :round-number 10
                                         nil))
                 :update-major-invasion-state! (fn [f & args] (apply swap! state f args))
                 :update-game-map! (fn [f & args]
                                     (swap! updates inc)
                                     (apply swap! world f args))
                 :sync-ai-unit! (fn [_] (swap! syncs inc))
                 :current-world (fn [] @world)
                 :nearest-major-target (fn [_] [0 0])
                 :computer-sea-unit-types #{:transport}}]
        (with-redefs [pathfinding-bfs/bfs-to-land-ho-target (fn [_ _ _] (throw (ex-info "should not plan" {})))]
          (mi/prepare-transport-major-invasion! ctx [0 0] (get-in @world [0 0 :contents])))
        (should= 0 @updates)
        (should= 0 @syncs)))

    (it "does not rewrite find-armies mission when already marked"
      (let [world (atom [[{:type :sea
                           :contents {:type :transport :owner :computer
                                      :major-invasion true
                                      :major-invasion-target [0 0]
                                      :transport-mission :find-armies-for-invasion
                                      :army-count 0}}]])
            state (atom {:target-land-revision 3
                         :detection-points #{[0 0]}
                         :target-land-set #{[0 0]}})
            updates (atom 0)
            syncs (atom 0)
            ctx {:load-major-invasion-state (fn [] @state)
                 :read-runtime-state (fn [k]
                                       (case k
                                         :computer-map [[{:type :sea :contents {:type :transport :owner :computer}}]]
                                         :round-number 10
                                         nil))
                 :update-major-invasion-state! (fn [f & args] (apply swap! state f args))
                 :update-game-map! (fn [f & args]
                                     (swap! updates inc)
                                     (apply swap! world f args))
                 :sync-ai-unit! (fn [_] (swap! syncs inc))
                 :current-world (fn [] @world)
                 :nearest-major-target (fn [_] [0 0])
                 :computer-sea-unit-types #{:transport}}]
        (mi/prepare-transport-major-invasion! ctx [0 0] (get-in @world [0 0 :contents]))
        (should= 0 @updates)
        (should= 0 @syncs)))

    (it "reuses the current invasion target before running the broader coastal search"
      (let [world (atom [[{:type :sea
                           :contents {:type :transport :owner :computer
                                      :major-invasion true
                                      :major-invasion-target [0 2]
                                      :transport-mission :sail-to-unload
                                      :invasion-target [0 2]
                                      :invasion-plan-revision 3
                                      :army-count 1}}]])
            state (atom {:target-land-revision 3
                         :detection-points #{[0 2]}
                         :target-land-set #{[0 2]}})
            ctx {:load-major-invasion-state (fn [] @state)
                 :read-runtime-state (fn [k]
                                       (case k
                                         :computer-map [[{:type :sea :contents {:type :transport :owner :computer}}
                                                         {:type :sea}
                                                         {:type :land}]]
                                         :round-number 10
                                         nil))
                 :update-major-invasion-state! (fn [f & args] (apply swap! state f args))
                 :update-game-map! (update-world-fn world)
                 :current-world (fn [] @world)
                 :nearest-major-target (fn [_] [0 2])
                 :computer-sea-unit-types #{:transport}}]
        (with-redefs [pathfinding-bfs/bfs-to-land-ho-target (fn [_ candidate _]
                                                              (if (= candidate [0 2])
                                                                [[0 1] [0 2]]
                                                                (throw (ex-info "should not broaden search" {}))))]
          (mi/prepare-transport-major-invasion! ctx [0 0] (get-in @world [0 0 :contents])))
        (should= :invading (get-in @world [0 0 :contents :transport-mission]))
        (should= [0 2] (get-in @world [0 0 :contents :invasion-target]))
        (should= [[0 1] [0 2]] (get-in @world [0 0 :contents :invasion-path]))))

    (it "updates the transport mission during transport assignment preparation"
      (let [world (atom [[{:type :sea
                           :contents {:type :transport :owner :computer
                                      :transport-mission :sailing
                                      :army-count 1}}]])
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
                 :sync-ai-unit! (fn [_] nil)
                 :nearest-major-target (fn [_] [0 0])
                 :computer-sea-unit-types #{:transport}}]
        (with-redefs [pathfinding-bfs/bfs-to-land-ho-target (fn [_ _ _] [[0 0]])]
          (mi/prepare-transport-major-invasion! ctx [0 0] (get-in @world [0 0 :contents])))
        (should= :invading
                 (get-in @world [0 0 :contents :transport-mission])))))

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
        (should= :sail-to-load (get-in @world [0 0 :contents :transport-mission]))
        (should= 9 (get-in @world [0 0 :contents :major-invasion-skip-revision]))
        (should-be-nil (get-in @world [0 0 :contents :major-invasion-target])))))

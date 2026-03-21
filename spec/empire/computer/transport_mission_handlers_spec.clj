(ns empire.computer.transport.mission-handlers-spec
  (:require [speclj.core :refer :all]
            [empire.computer.transport.mission-handlers :as mh]))

(describe "transport mission-handlers"
  (context "load-for-invasion helpers"
    (it "transitions to unloading inside the unload zone"
      (let [calls (atom [])]
        (mh/process-load-for-invasion-with-armies
         {:transition-to-sailing (fn [pos] (swap! calls conj [:sail pos]))
          :transition-to-unloading (fn [pos target] (swap! calls conj [:unload pos target]))
          :has-nearby-unloadable-land? (fn [& _] false)}
         [1 1] {:army-count 2} [4 4] true false)
        (should= [[:unload [1 1] [4 4]]] @calls)))

    (it "reverts empty invasion loading only after timeout"
      (let [calls (atom [])]
        (mh/process-load-for-invasion-empty
         (fn [& args] (swap! calls conj args))
         (fn [pos] (swap! calls conj [:loading pos]))
         [2 2]
         false)
        (should= [] @calls)
        (mh/process-load-for-invasion-empty
         (fn [& args] (swap! calls conj args))
         (fn [pos] (swap! calls conj [:loading pos]))
         [2 2]
         true)
        (should= [:loading [2 2]] (first @calls)))))

  (context "unloading movement"
    (it "continues crawl-unload for the full transport speed"
      (let [world (atom [[{:contents {:transport-mission :unloading :army-count 2}}]
                         [{:contents nil}]
                         [{:contents nil}]
                         [{:contents nil}]])
            move! (fn [from to]
                    (let [unit (get-in @world (conj from :contents))]
                      (swap! world #(-> %
                                        (assoc-in (conj to :contents) unit)
                                        (assoc-in (conj from :contents) nil)))
                      to))
            crawl-steps {[0 0] [1 0]
                         [1 0] [2 0]
                         [2 0] [3 0]}]
        (with-redefs [empire.config.units.dispatcher/speed (constantly 3)]
          (should= [3 0]
                   (mh/process-unloading-mission
                    {:read-computer-map (fn [] @world)
                     :transition-to-loading (fn [_] :loading)
                     :process-unloading-crawl (fn [pos]
                                                (when-let [next-pos (get crawl-steps pos)]
                                                  (move! pos next-pos)))
                     :try-opportunistic-unload (fn [_] false)}
                    [0 0]
                    2))))))

  (context "process-land-locked-mission coverage"
    (it "handles missing unit at position without attempting crawl"
      (let [crawl-called (atom false)
            deps {:current-world (fn [] [[{:type :sea}]])
                  :update-game-map! (fn [& _] nil)
                  :move-unit-to (fn [& _] true)
                  :retreat-step-from-shore (fn [& _] [0 1])
                  :deep-water? (fn [& _] false)
                  :process-unloading-crawl (fn [& _] (reset! crawl-called true) [0 1])
                  :try-opportunistic-unload-any-land (fn [& _] false)}]
        (should-be-nil (mh/process-land-locked-mission deps [0 0] #{}))
        (should= false @crawl-called)))

    (it "parks an empty lake transport in place when retreat movement fails"
      (let [updates (atom [])]
        (mh/park-lake-transport-if-empty
         {:current-world (fn [] {[0 0] {:contents {:army-count 0}}})
          :update-game-map! (fn [& args] (swap! updates conj args))
          :move-unit-to (fn [& _] false)
          :retreat-step-from-shore (fn [& _] [0 1])
          :deep-water? (fn [& _] false)}
         [0 0]
         #{})
        (should= 1 (count @updates))))

    (it "fixes idle missions by resetting them to sail-to-load"
      (let [calls (atom [])]
        (mh/fix-idle-mission (fn [pos mission] (swap! calls conj [pos mission])) [3 3] nil)
        (mh/fix-idle-mission (fn [pos mission] (swap! calls conj [pos mission])) [4 4] :idle)
        (should= [[[3 3] :sail-to-load]
                  [[4 4] :sail-to-load]]
                 @calls)))))

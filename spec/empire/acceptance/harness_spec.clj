(ns empire.acceptance.harness-spec
  (:require [speclj.core :refer :all]
            [empire.acceptance.harness :as h]))

(describe "acceptance harness characterization"
  (before
    (h/reset-all-atoms!))

  (it "reads values written via set-state!"
    (h/set-state! :round-number 7)
    (should= 7 (h/read-state :round-number)))

  (it "returns nil for unsupported read-state key"
    (should-be-nil (h/read-state :unsupported-key)))

  (it "updates production via update-state!"
    (h/set-state! :production {1 {:item :army :remaining-rounds 3}})
    (h/update-state! :production assoc 2 {:item :fighter :remaining-rounds 5})
    (should= {:item :fighter :remaining-rounds 5}
             (get (h/read-state :production) 2)))

  (it "writes game-map through set-state!"
    (let [world (h/build-test-map ["##"])]
      (h/set-state! :game-map world)
      (should= world (h/read-state :game-map))))

  (it "updates game-map through update-state!"
    (h/set-test-world! (h/build-test-map ["##"]))
    (h/update-state! :game-map assoc-in [0 0 :visited] true)
    (should (true? (:visited (h/cell-at [0 0])))))

  (it "sets and queries unit state through harness helpers"
    (h/set-test-world! (h/build-test-map ["A#"]))
    (h/set-unit! "A" :mode :sentry :fuel 11)
    (let [u (h/get-unit "A")]
      (should= :sentry (get-in u [:unit :mode]))
      (should= 11 (get-in u [:unit :fuel]))))

  (it "queries city and labeled cell helpers"
    (h/set-test-world! (h/build-test-map ["O=%"]))
    (let [city (h/get-city "O")
          sea-label (h/get-cell "=")
          land-label (h/get-cell "%")]
      (should= [0 0] (:pos city))
      (should= [1 0] (:pos sea-label))
      (should= [2 0] (:pos land-label))))

  (it "reads alternate map keys through cell-at"
    (h/set-state! :player-map [[:player-cell]])
    (should= :player-cell (h/cell-at :player-map [0 0])))

  (it "reads shipyard contents through shipyard-at"
    (h/set-test-world! (h/build-test-map ["O"]))
    (h/update-test-world! assoc-in [0 0 :shipyard] [{:type :destroyer :hits 2}])
    (should= [{:type :destroyer :hits 2}] (h/shipyard-at [0 0])))

  (it "counts computer armies on the map"
    (h/set-test-world! (h/build-test-map ["###" "###"]))
    (h/update-test-world! assoc-in [0 0 :contents] {:type :army :owner :computer})
    (h/update-test-world! assoc-in [2 1 :contents] {:type :army :owner :computer})
    (should= 2 (h/count-computer-armies)))

  (it "supports variadic update-test-world! operations"
    (h/set-test-world! (h/build-test-map ["##" "##"]))
    (h/update-test-world! assoc-in [1 0 :waypoint] true)
    (should (true? (:waypoint (h/cell-at [1 0])))))

  (it "drains visibility detections into threat handling"
    (let [calls (atom [])]
      (with-redefs [empire.game-mechanics.movement.visibility/update-cell-visibility (fn [pos owner unit]
                                                                                       (swap! calls conj [:update pos owner unit]))
                    empire.game-mechanics.movement.visibility/drain-detections! (fn []
                                                                                  [{:pos [1 2] :cell :first}
                                                                                   {:pos [3 4] :cell :second}])
                    empire.computer.threat-response/handle-detection! (fn [pos cell]
                                                                        (swap! calls conj [:detect pos cell]))]
        (h/update-cell-visibility! [0 0] :computer {:type :fighter})
        (should= [[:update [0 0] :computer {:type :fighter}]
                  [:detect [1 2] :first]
                  [:detect [3 4] :second]]
                 @calls))))

  (it "forwards control wrappers to their underlying modules"
    (let [calls (atom [])]
      (with-redefs [empire.ui.util.input.dispatch/handle-key (fn [k] (swap! calls conj [:handle-key k]))
                    empire.ui.util.input.dispatch/key-down (fn [k x y] (swap! calls conj [:key-down k x y]))
                    empire.game.loop.core/start-new-round (fn [] (swap! calls conj :start-round))
                    empire.game.loop.core/advance-game (fn [] (swap! calls conj :advance-game))
                    empire.game.loop.item-processing/process-player-items-batch (fn [] (swap! calls conj :player-batch))
                    empire.game.loop.core/update-player-map (fn [] (swap! calls conj :update-player-map))
                    empire.computer.production/rebuild-country-stats! (fn [] (swap! calls conj :rebuild-stats))
                    empire.computer.production/process-computer-city (fn [pos] (swap! calls conj [:computer-city pos]))
                    empire.computer.transport/process-transport (fn [pos] (swap! calls conj [:transport pos]))
                    empire.computer.fighter/process-fighter (fn [pos unit] (swap! calls conj [:fighter pos unit]))
                    empire.computer.ship/process-ship (fn [pos ship-type] (swap! calls conj [:ship pos ship-type]))]
        (h/handle-key! :a)
        (h/key-down! :b)
        (h/key-down-at! :c 7 8)
        (h/start-new-round!)
        (h/advance-game!)
        (h/process-player-items-batch!)
        (h/update-player-map!)
        (h/evaluate-computer-production! [1 2])
        (h/process-computer-transport! [3 4])
        (h/process-computer-fighter! [5 6] {:type :fighter})
        (h/process-computer-ship! [7 8] :destroyer)
        (should= [[:handle-key :a]
                  [:key-down :b 0 0]
                  [:key-down :c 7 8]
                  :start-round
                  :advance-game
                  :player-batch
                  :update-player-map
                  :rebuild-stats
                  [:computer-city [1 2]]
                  [:transport [3 4]]
                  [:fighter [5 6] {:type :fighter}]
                  [:ship [7 8] :destroyer]]
                 @calls))
      (should-be-nil (h/read-state :last-key))))

  (it "reveals a computer transport before processing it"
    (let [calls (atom [])]
      (h/set-test-world! (h/build-test-map ["t"]))
      (with-redefs [empire.game-mechanics.movement.visibility/update-cell-visibility
                    (fn [pos owner unit]
                      (swap! calls conj [:update pos owner unit]))
                    empire.game-mechanics.movement.visibility/drain-detections!
                    (fn [] [])
                    empire.computer.transport/process-transport
                    (fn [pos]
                      (swap! calls conj [:transport pos]))]
        (h/process-computer-transport! [0 0])
        (should= 2 (count @calls))
        (should= :update (ffirst @calls))
        (should= [0 0] (second (first @calls)))
        (should= :computer (nth (first @calls) 2))
        (should= :transport (get-in @calls [0 3 :type]))
        (should= :computer (get-in @calls [0 3 :owner]))
        (should= [:transport [0 0]] (second @calls)))))

  (it "reveals a computer fighter before processing it"
    (let [calls (atom [])
          unit {:type :fighter :owner :computer}]
      (with-redefs [empire.game-mechanics.movement.visibility/update-cell-visibility
                    (fn [pos owner fighter]
                      (swap! calls conj [:update pos owner fighter]))
                    empire.game-mechanics.movement.visibility/drain-detections!
                    (fn [] [])
                    empire.computer.fighter/process-fighter
                    (fn [pos fighter]
                      (swap! calls conj [:fighter pos fighter]))]
        (h/process-computer-fighter! [1 2] unit)
        (should= [[:update [1 2] :computer unit]
                  [:fighter [1 2] unit]]
                 @calls))))

  (it "reveals a computer ship before processing it"
    (let [calls (atom [])]
      (h/set-test-world! (h/build-test-map ["d"]))
      (with-redefs [empire.game-mechanics.movement.visibility/update-cell-visibility
                    (fn [pos owner unit]
                      (swap! calls conj [:update pos owner unit]))
                    empire.game-mechanics.movement.visibility/drain-detections!
                    (fn [] [])
                    empire.computer.ship/process-ship
                    (fn [pos ship-type]
                      (swap! calls conj [:ship pos ship-type]))]
        (h/process-computer-ship! [0 0] :destroyer)
        (should= 2 (count @calls))
        (should= :update (ffirst @calls))
        (should= [0 0] (second (first @calls)))
        (should= :computer (nth (first @calls) 2))
        (should= :destroyer (get-in @calls [0 3 :type]))
        (should= :computer (get-in @calls [0 3 :owner]))
        (should= [:ship [0 0] :destroyer] (second @calls)))))

  (it "does not reveal non-computer fighters before processing them"
    (let [calls (atom [])
          unit {:type :fighter :owner :player}]
      (with-redefs [empire.game-mechanics.movement.visibility/update-cell-visibility
                    (fn [& args]
                      (swap! calls conj args))
                    empire.game-mechanics.movement.visibility/drain-detections!
                    (fn [] [])
                    empire.computer.fighter/process-fighter
                    (fn [pos fighter]
                      (swap! calls conj [:fighter pos fighter]))]
        (h/process-computer-fighter! [1 2] unit)
        (should= [[:fighter [1 2] unit]]
                 @calls))))

  (it "throws on unsupported set-state! key"
    (should-throw clojure.lang.ExceptionInfo
                  (h/set-state! :unsupported-key 1)))

  (it "throws on unsupported update-state! key"
    (should-throw clojure.lang.ExceptionInfo
                  (h/update-state! :unsupported-key assoc :x 1))))

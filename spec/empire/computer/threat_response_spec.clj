(ns empire.computer.threat-response-spec
  (:require [speclj.core :refer :all]
            [empire.atoms :as atoms]
            [empire.computer.threat-response :as threat-response]
            [empire.config :as config]
            [empire.test-utils :refer [build-test-map reset-all-atoms!]]))

(defn computer-units
  [pred]
  (for [i (range (count @atoms/game-map))
        j (range (count (first @atoms/game-map)))
        :let [u (get-in @atoms/game-map [i j :contents])]
        :when (and u (= :computer (:owner u)) (pred u))]
    [[i j] u]))

(describe "threat-response"
  (before (reset-all-atoms!))

  (it "assigns up to 4 closest fighters when enemy fighter detected"
    (let [gm (build-test-map ["f~~"
                              "~~~"
                              "f~~"
                              "~~~"
                              "f~~"
                              "~~~"
                              "f~~"
                              "~~F"
                              "f~~"])]
      (reset! atoms/game-map gm)
      (reset! atoms/computer-map gm)
      (let [enemy-cell (get-in @atoms/game-map [2 7])]
        (threat-response/handle-detection! [2 7] enemy-cell))
      (let [assigned (computer-units #(= :fighter-sweep (:threat-mission %)))
            assigned-positions (set (map first assigned))]
        (should= 4 (count assigned))
        (should (not (contains? assigned-positions [0 0])))
        (doseq [[_ unit] assigned]
          (should= [2 7] (:threat-center unit))))))

  (it "assigns 2 patrol boats and 2 battleships when enemy ship detected"
    (let [gm (build-test-map ["p~~~"
                              "~b~~"
                              "p~~~"
                              "~b~~"
                              "p~~~"
                              "~b~~"
                              "~~~~"
                              "~~~~"
                              "~~~D"])]
      (reset! atoms/game-map gm)
      (reset! atoms/computer-map gm)
      (threat-response/handle-detection! [3 8] (get-in @atoms/game-map [3 8]))
      (let [assigned (computer-units #(= :sea-scout (:threat-mission %)))
            patrols (count (filter #(= :patrol-boat (:type (second %))) assigned))
            battleships (count (filter #(= :battleship (:type (second %))) assigned))]
        (should= 4 (count assigned))
        (should= 2 patrols)
        (should= 2 battleships))))

  (it "activates major invasion and assigns loaded transport to invading mission"
    (let [gm (build-test-map ["t~~~"
                              "~~~~"
                              "##O#"
                              "~~~~"])]
      (reset! atoms/game-map gm)
      (reset! atoms/computer-map gm)
      (swap! atoms/game-map assoc-in [0 0 :contents :army-count] 4)
      (swap! atoms/game-map assoc-in [0 0 :contents :transport-mission] :sailing)
      (threat-response/handle-detection! [2 2] (get-in @atoms/game-map [2 2]))
      (threat-response/refresh-major-invasion-assignments!)
      (let [transport (get-in @atoms/game-map [0 0 :contents])]
        (should (:active? @atoms/major-invasion-state))
        (should-contain [2 2] (:detection-points @atoms/major-invasion-state))
        (should= :invading (:transport-mission transport))
        (should= [2 2] (:invasion-target transport))
        (should (seq (:invasion-path transport))))
      (should (threat-response/major-invasion-target-land? [1 2]))))

  (it "ignores non-threat detections"
    (let [gm (build-test-map ["~~~"])]
      (reset! atoms/game-map gm)
      (reset! atoms/computer-map gm)
      (should-be-nil
       (threat-response/handle-detection! [0 0] (get-in @atoms/game-map [0 0])))
      (should-not (:active? @atoms/major-invasion-state)))))

(describe "fighter-step-threat"
  (before (reset-all-atoms!))

  (it "attacks adjacent enemy and consumes one fuel step"
    (with-redefs [empire.computer.fighter-movement/find-adjacent-enemy (fn [_] [1 2])
                  empire.computer.fighter-movement/attack-enemy (fn [_ _] [1 1])
                  empire.computer.fighter-movement/consume-fighter-fuel (fn [_] true)]
      (should= {:pos [1 1] :steps-used 1}
               (@#'threat-response/fighter-step-threat [1 0] {:threat-center [1 0]
                                                               :threat-radius 5}))))

  (it "lands at city when refuel is needed and city is adjacent"
    (reset! atoms/game-map (build-test-map ["#O#"]))
    (with-redefs [empire.computer.fighter-movement/find-adjacent-enemy (constantly nil)
                  empire.computer.fighter-movement/should-return-to-refuel? (fn [_ _] true)
                  empire.computer.fighter-movement/find-nearest-refueling-site (fn [_] [1 0])
                  empire.computer.fighter-movement/distance-to (fn [_ _] 1)
                  empire.computer.fighter-movement/land-at-city (fn [_ _] :landed)]
      (should-be-nil
       (@#'threat-response/fighter-step-threat [0 0] {:fuel 1 :threat-center [0 0]}))))

  (it "refuels in place when adjacent refuel site is not a city"
    (reset! atoms/game-map (build-test-map ["~~~"]))
    (swap! atoms/game-map assoc-in [0 0 :contents] {:type :fighter :owner :computer :fuel 1})
    (with-redefs [empire.computer.fighter-movement/find-adjacent-enemy (constantly nil)
                  empire.computer.fighter-movement/should-return-to-refuel? (fn [_ _] true)
                  empire.computer.fighter-movement/find-nearest-refueling-site (fn [_] [1 0])
                  empire.computer.fighter-movement/distance-to (fn [_ _] 1)]
      (should= {:pos [0 0] :steps-used 1}
               (@#'threat-response/fighter-step-threat [0 0] {:fuel 1 :threat-center [0 0]}))
      (should= config/fighter-fuel
               (get-in @atoms/game-map [0 0 :contents :fuel]))))

  (it "moves back toward threat center when outside radius"
    (with-redefs [empire.computer.fighter-movement/find-adjacent-enemy (constantly nil)
                  empire.computer.fighter-movement/should-return-to-refuel? (fn [_ _] false)
                  empire.computer.fighter-movement/hop-over-friendly (fn [_ _] [2 2])
                  empire.computer.fighter-movement/execute-hop (fn [_ _] {:pos [2 2] :hops 2})
                  empire.computer.fighter-movement/consume-fighter-fuel (fn [_] true)]
      (should= {:pos [2 2] :steps-used 2}
               (@#'threat-response/fighter-step-threat [0 0] {:threat-center [4 4]
                                                               :threat-radius 1}))))

  (it "patrols when in-radius and no higher-priority action applies"
    (with-redefs [empire.computer.fighter-movement/find-adjacent-enemy (constantly nil)
                  empire.computer.fighter-movement/should-return-to-refuel? (fn [_ _] false)
                  empire.computer.fighter-movement/do-patrol (fn [_] {:pos [0 1] :hops 1})
                  empire.computer.fighter-movement/consume-fighter-fuel (fn [_] true)]
      (should= {:pos [0 1] :steps-used 1}
               (@#'threat-response/fighter-step-threat [0 0] {:threat-center [0 0]
                                                               :threat-radius 5})))))

(describe "process-ship-threat"
  (before (reset-all-atoms!))

  (it "returns false when unit has no threat mission"
    (should-not
     (threat-response/process-ship-threat [0 0] :patrol-boat {})))

  (it "handles sea-scout by attacking adjacent enemy first"
    (with-redefs [empire.computer.ship-core/find-adjacent-enemy-ship (fn [_] [1 0])
                  empire.computer.ship-core/attack-enemy (fn [_ _] :attacked)
                  empire.computer.ship-core/move-toward (fn [_ _] (throw (ex-info "should not move" {})))
                  empire.computer.ship-core/explore-sea (fn [_ _] (throw (ex-info "should not explore" {})))]
      (should
       (threat-response/process-ship-threat
        [0 0] :patrol-boat {:threat-mission :sea-scout :threat-center [5 5] :threat-radius 1}))))

  (it "handles sea-scout by moving toward center when out of radius"
    (let [moved-target (atom nil)]
      (with-redefs [empire.computer.ship-core/find-adjacent-enemy-ship (constantly nil)
                    empire.computer.ship-core/move-toward (fn [_ target] (reset! moved-target target) :moved)
                    empire.computer.ship-core/explore-sea (fn [_ _] :explored)]
        (should
         (threat-response/process-ship-threat
          [0 0] :patrol-boat {:threat-mission :sea-scout :threat-center [4 4] :threat-radius 1}))
        (should= [4 4] @moved-target))))

  (it "handles sea-scout by exploring when in radius and no enemy"
    (let [explored? (atom false)]
      (with-redefs [empire.computer.ship-core/find-adjacent-enemy-ship (constantly nil)
                    empire.computer.ship-core/move-toward (fn [_ _] (throw (ex-info "should not move" {})))
                    empire.computer.ship-core/explore-sea (fn [_ _] (reset! explored? true) :explored)]
        (should
         (threat-response/process-ship-threat
          [0 0] :patrol-boat {:threat-mission :sea-scout :threat-center [0 0] :threat-radius 5}))
        (should @explored?))))

  (it "handles major invasion by moving toward nearest detection point"
    (reset! atoms/major-invasion-state
            {:active? true :detection-points [[3 3]] :target-land-set #{} :started-round 1})
    (let [moved-target (atom nil)]
      (with-redefs [empire.computer.ship-core/find-adjacent-enemy-ship (constantly nil)
                    empire.computer.ship-core/move-toward (fn [_ target] (reset! moved-target target) :moved)
                    empire.computer.ship-core/explore-sea (fn [_ _] :explored)]
        (should
         (threat-response/process-ship-threat
          [0 0] :destroyer {:major-invasion true}))
        (should= [3 3] @moved-target))))

  (it "handles major invasion by exploring when no target exists"
    (reset! atoms/major-invasion-state
            {:active? true :detection-points [] :target-land-set #{} :started-round 1})
    (let [explored? (atom false)]
      (with-redefs [empire.computer.ship-core/find-adjacent-enemy-ship (constantly nil)
                    empire.computer.ship-core/move-toward (fn [_ _] nil)
                    empire.computer.ship-core/explore-sea (fn [_ _] (reset! explored? true) :explored)]
        (should
         (threat-response/process-ship-threat
          [0 0] :destroyer {:major-invasion true}))
        (should @explored?)))))

(describe "process-fighter-threat"
  (before (reset-all-atoms!))

  (it "returns nil when unit is not on fighter-sweep mission"
    (should-be-nil (threat-response/process-fighter-threat [0 0] {:threat-mission :none})))

  (it "returns true and iterates while fighter-step-threat yields moves"
    (reset! atoms/game-map (build-test-map ["f"]))
    (let [calls (atom 0)]
      (with-redefs [empire.computer.threat-response/fighter-step-threat
                    (fn [_ _]
                      (swap! calls inc)
                      (if (= 1 @calls)
                        {:pos [0 0] :steps-used 2}
                        nil))]
        (should (threat-response/process-fighter-threat [0 0] {:threat-mission :fighter-sweep}))
        (should= 2 @calls)))))

(describe "prepare-transport!"
  (before (reset-all-atoms!))

  (it "returns nil when major invasion is not active"
    (reset! atoms/major-invasion-state {:active? false :detection-points [] :target-land-set #{}})
    (should-be-nil (threat-response/prepare-transport! [0 0])))

  (it "returns nil when active but cell is not a transport"
    (reset! atoms/major-invasion-state {:active? true :detection-points [] :target-land-set #{}})
    (reset! atoms/game-map (build-test-map ["a"]))
    (should-be-nil (threat-response/prepare-transport! [0 0])))

  (it "delegates to transport invasion prep and returns true"
    (reset! atoms/major-invasion-state {:active? true :detection-points [[1 1]] :target-land-set #{}})
    (reset! atoms/game-map (build-test-map ["t"]))
    (let [called (atom nil)]
      (with-redefs [empire.computer.threat-response/prepare-transport-major-invasion!
                    (fn [pos unit] (reset! called [pos (:type unit)]))]
        (should (threat-response/prepare-transport! [0 0]))
        (should= [[0 0] :transport] @called)))))

(describe "refresh-major-invasion-assignments!"
  (before (reset-all-atoms!))

  (it "does nothing when major invasion is inactive"
    (reset! atoms/major-invasion-state {:active? false :detection-points [] :target-land-set #{}})
    (let [called? (atom false)]
      (with-redefs [empire.computer.threat-response/prepare-transport-major-invasion!
                    (fn [_ _] (reset! called? true))]
        (threat-response/refresh-major-invasion-assignments!)
        (should-not @called?))))

  (it "marks fighters and major ship types; delegates transports"
    (reset! atoms/game-map (build-test-map ["fpta"]))
    (reset! atoms/major-invasion-state {:active? true :detection-points [[3 3]] :target-land-set #{}})
    (let [transport-calls (atom [])]
      (with-redefs [empire.computer.threat-response/nearest-major-target (fn [_] [3 3])
                    empire.computer.threat-response/prepare-transport-major-invasion!
                    (fn [pos unit] (swap! transport-calls conj [pos (:type unit)]))]
        (threat-response/refresh-major-invasion-assignments!)
        (should= true (get-in @atoms/game-map [0 0 :contents :major-invasion]))
        (should= [3 3] (get-in @atoms/game-map [0 0 :contents :major-invasion-target]))
        (should= true (get-in @atoms/game-map [1 0 :contents :major-invasion]))
        (should= [3 3] (get-in @atoms/game-map [1 0 :contents :major-invasion-target]))
        (should= [[[2 0] :transport]] @transport-calls)
        (should-not-contain :major-invasion (get-in @atoms/game-map [3 0 :contents]))))))

(describe "dec-threat-rounds"
  (it "returns unit unchanged when no threat timer exists"
    (let [u {:type :fighter :mode :awake}]
      (should= u (@#'threat-response/dec-threat-rounds u))))

  (it "decrements positive timer"
    (should= 1 (:threat-rounds-left
                (@#'threat-response/dec-threat-rounds {:threat-rounds-left 2 :threat-mission :fighter-sweep}))))

  (it "clears threat mission fields when timer expires"
    (let [out (@#'threat-response/dec-threat-rounds {:threat-rounds-left 1
                                                     :threat-mission :fighter-sweep
                                                     :threat-center [1 1]
                                                     :threat-radius 5})]
      (should-be-nil (:threat-rounds-left out))
      (should-be-nil (:threat-mission out))
      (should-be-nil (:threat-center out))
      (should-be-nil (:threat-radius out)))))

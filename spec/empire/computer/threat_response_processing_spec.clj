(ns empire.computer.threat-response-processing-spec
  (:require [empire.computer.core :as core]
            [empire.computer.threat-response.processing :as processing]
            [empire.computer.threat-response :as threat-response]
            [empire.computer.threat-response.processing-ship :as processing-ship]
            [empire.config.core :as config]
            [empire.config.units.config :as units-config]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-world! update-test-world!]]
            [speclj.core :refer :all]))

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
    (set-test-world! (build-test-map ["#O#"]))
    (with-redefs [empire.computer.fighter-movement/find-adjacent-enemy (constantly nil)
                  empire.computer.fighter-movement/should-return-to-refuel? (fn [_ _] true)
                  empire.computer.fighter-movement/find-nearest-refueling-site (fn [_] [1 0])
                  empire.computer.fighter-movement/distance-to (fn [_ _] 1)
                  empire.computer.fighter-movement/land-at-city (fn [_ _] :landed)]
      (should-be-nil
       (@#'threat-response/fighter-step-threat [0 0] {:fuel 1 :threat-center [0 0]}))))

  (it "refuels in place when adjacent refuel site is not a city"
    (set-test-world! (build-test-map ["~~~"]))
    (update-test-world! assoc-in [0 0 :contents] {:type :fighter :owner :computer :fuel 1})
    (with-redefs [empire.computer.fighter-movement/find-adjacent-enemy (constantly nil)
                  empire.computer.fighter-movement/should-return-to-refuel? (fn [_ _] true)
                  empire.computer.fighter-movement/find-nearest-refueling-site (fn [_] [1 0])
                  empire.computer.fighter-movement/distance-to (fn [_ _] 1)]
      (should= {:pos [0 0] :steps-used 1}
               (@#'threat-response/fighter-step-threat [0 0] {:fuel 1 :threat-center [0 0]}))
      (should= config/fighter-fuel
               (get-in (test-utils/read-test-state :game-map) [0 0 :contents :fuel]))))

  (it "moves back toward threat center when outside radius"
    (with-redefs [empire.computer.fighter-movement/find-adjacent-enemy (constantly nil)
                  empire.computer.fighter-movement/should-return-to-refuel? (fn [_ _] false)
                  empire.computer.fighter-movement/find-nearest-refueling-site (fn [_] [0 0])
                  empire.computer.fighter-movement/distance-to (fn [_ _] 1)
                  empire.computer.fighter-movement/hop-over-friendly (fn [_ _] {:dest [2 2] :hops 2})
                  empire.computer.fighter-movement/execute-hop (fn [_ _] {:pos [2 2] :hops 2})
                  empire.computer.fighter-movement/consume-fighter-fuel (fn [_] true)]
      (should= {:pos [2 2] :steps-used 2}
               (@#'threat-response/fighter-step-threat [0 0] {:threat-center [4 4]
                                                               :threat-radius 1}))))

  (it "patrols when in-radius and no higher-priority action applies"
    (with-redefs [empire.computer.fighter-movement/find-adjacent-enemy (constantly nil)
                  empire.computer.fighter-movement/should-return-to-refuel? (fn [_ _] false)
                  empire.computer.fighter-movement/find-nearest-refueling-site (fn [_] [0 0])
                  empire.computer.fighter-movement/distance-to (fn [_ _] 1)
                  empire.computer.fighter-movement/do-patrol (fn [_] {:pos [0 1] :hops 1})
                  empire.computer.fighter-movement/consume-fighter-fuel (fn [_] true)]
      (should= {:pos [0 1] :steps-used 1}
               (@#'threat-response/fighter-step-threat [0 0] {:fuel 10
                                                               :threat-center [0 0]
                                                               :threat-radius 5})))))

(describe "fighter random walk helpers"
  (before (reset-all-atoms!))

  (it "moves to a random open fighter neighbor and consumes fuel"
    (set-test-world! (build-test-map ["f~~"]))
    (with-redefs [rand-nth first
                  empire.computer.fighter-movement/get-passable-neighbors (fn [_] [[1 0] [2 0]])
                  empire.computer.fighter-movement/occupied? (constantly false)
                  empire.computer.fighter-movement/consume-fighter-fuel (fn [_] true)]
      (should= [1 0]
               (@#'processing/fighter-random-walk-step [0 0]))))

  (it "stays in place when no fighter random walk candidates exist"
    (with-redefs [empire.computer.fighter-movement/get-passable-neighbors (constantly [])
                  empire.computer.fighter-movement/occupied? (constantly false)]
      (should= [0 0]
               (@#'processing/fighter-random-walk-step [0 0]))))

  (it "decrements and restores fighter random walk state after a round"
    (set-test-world! (build-test-map ["f~"]))
    (update-test-world! assoc-in [0 0 :contents :oscillation-random-walk-rounds-left] 1)
    (update-test-world! assoc-in [0 0 :contents :threat-mission] :fighter-sweep)
    (update-test-world! assoc-in [0 0 :contents :threat-center] [0 0])
    (with-redefs [empire.computer.fighter-movement/get-passable-neighbors (constantly [])
                  empire.computer.fighter-movement/occupied? (constantly false)]
      (should
       (threat-response/process-fighter-threat [0 0]
                                               (get-in (test-utils/read-test-state :game-map) [0 0 :contents]))))
    (should= :fighter-sweep (get-in (test-utils/read-test-state :game-map) [0 0 :contents :threat-mission]))
    (should= nil (get-in (test-utils/read-test-state :game-map) [0 0 :contents :oscillation-random-walk-rounds-left]))))

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
    (test-utils/set-test-state! :major-invasion-state {:active? true :detection-points [[3 3]] :target-land-set #{} :started-round 1})
    (let [moved-target (atom nil)]
      (with-redefs [empire.computer.ship-core/find-adjacent-enemy-ship (constantly nil)
                    empire.computer.ship-core/move-toward (fn [_ target] (reset! moved-target target) :moved)
                    empire.computer.ship-core/explore-sea (fn [_ _] :explored)]
        (should
         (threat-response/process-ship-threat
          [0 0] :destroyer {:major-invasion true}))
        (should= [3 3] @moved-target))))

  (it "moves major-invasion patrol boat at patrol speed (4 steps)"
    (set-test-world! (build-test-map ["p~~~~~~~~~"]))
    (with-redefs [empire.computer.ship-core/find-adjacent-enemy-ship (constantly nil)
                  rand-nth (fn [xs] (first xs))]
      (should
       (threat-response/process-ship-threat
        [0 0] :patrol-boat {:major-invasion true :major-invasion-target [9 0]}))
      (should= :patrol-boat (get-in (test-utils/read-test-state :game-map) [4 0 :contents :type]))))

  (it "handles major invasion by exploring when no target exists"
    (test-utils/set-test-state! :major-invasion-state {:active? true :detection-points [] :target-land-set #{} :started-round 1})
    (let [explored? (atom false)]
      (with-redefs [empire.computer.ship-core/find-adjacent-enemy-ship (constantly nil)
                    empire.computer.ship-core/move-toward (fn [_ _] nil)
                    empire.computer.ship-core/explore-sea (fn [_ _] (reset! explored? true) :explored)]
        (should
         (threat-response/process-ship-threat
          [0 0] :destroyer {:major-invasion true}))
        (should @explored?))))

  (it "moves major-invasion ship toward detection point even when direct sea access is unavailable"
    (test-utils/set-test-state! :major-invasion-state {:active? true
                                                       :detection-points [[3 3]]
                                                       :sea-reachable-detection-points #{}
                                                       :target-land-set #{}
                                                       :started-round 1})
    (let [moved? (atom false)
          explored? (atom false)]
      (with-redefs [empire.computer.ship-core/find-adjacent-enemy-ship (constantly nil)
                    empire.computer.ship-core/move-toward (fn [_ _] (reset! moved? true) :moved)
                    empire.computer.ship-core/explore-sea (fn [_ _] (reset! explored? true) :explored)]
        (should
         (threat-response/process-ship-threat
          [0 0] :destroyer {:major-invasion true :major-invasion-target [3 3]}))
        (should @moved?)
        (should-not @explored?))))

  (it "starts random walk for blocked major-invasion ships when sidestep is unavailable"
    (set-test-world! (build-test-map ["d"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (should
     (threat-response/process-ship-threat
      [0 0] :destroyer {:major-invasion true :major-invasion-target [3 3]}))
    (should= 5 (get-in (test-utils/read-test-state :game-map)
                       [0 0 :contents :oscillation-random-walk-rounds-left])))

  (it "makes patrol boats yield away from nearby invading transports"
    (set-test-world! (build-test-map ["~~~"
                                      "tp~"
                                      "~~~"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (update-test-world! assoc-in [0 1 :contents :major-invasion] true)
    (update-test-world! assoc-in [0 1 :contents :transport-mission] :invading)
    (with-redefs [empire.computer.ship-core/find-adjacent-enemy-ship (constantly nil)
                  rand-nth (fn [xs] (first xs))]
      (should
       (threat-response/process-ship-threat
        [1 1] :patrol-boat {:major-invasion true :major-invasion-target [1 1]})))
    (let [patrol-pos (first (for [x (range 3)
                                  y (range 3)
                                  :when (= :patrol-boat (get-in (test-utils/read-test-state :game-map) [x y :contents :type]))]
                              [x y]))]
      (should-not= [1 1] patrol-pos)
      (should (> (core/distance patrol-pos [0 1]) 1))))

  (it "ignores hidden coastline when scoring patrol stand-off positions"
    (set-test-world! (build-test-map ["~~~"
                                      "~p~"
                                      "#~~"]))
    (set-test-computer-map! (build-test-map ["~~~"
                                             "~p~"
                                             "~~~"]))
    (let [ctx (assoc (test-utils/mission-ctx)
                     :read-runtime-state test-utils/read-test-state)]
      (should= 0
               (#'processing-ship/shore-band-score
                (test-utils/read-test-state :computer-map)
                [1 1]))
      (should= -20
               (#'processing-ship/shore-band-score
                (test-utils/read-test-state :game-map)
                [1 1]))
      (should (#'processing-ship/patrol-stand-off-step ctx [1 1] nil))))

  (it "keeps invading patrol boats within 10 cells of invasion point"
    (set-test-world! (build-test-map ["~~~~~~~p~~~~~~~"]))
    (with-redefs [empire.computer.ship-core/find-adjacent-enemy-ship (constantly nil)
                  rand-nth (fn [xs] (first xs))]
      (should
       (threat-response/process-ship-threat
        [7 0] :patrol-boat {:major-invasion true :major-invasion-target [0 0]}))
      (let [patrol-pos (first (for [x (range 15)
                                    :when (= :patrol-boat (get-in (test-utils/read-test-state :game-map) [x 0 :contents :type]))]
                                [x 0]))]
        (should (<= (core/distance patrol-pos [0 0]) 10)))))

  (it "does not run explore BFS for major-invasion patrol boats when move fails"
    (let [explored? (atom false)]
      (with-redefs [empire.computer.ship-core/find-adjacent-enemy-ship (constantly nil)
                    empire.computer.ship-core/move-toward (fn [_ _] nil)
                    empire.computer.ship-core/explore-sea (fn [& _] (reset! explored? true) :explored)]
        (should
         (threat-response/process-ship-threat
          [0 0] :patrol-boat {:major-invasion true :major-invasion-target [0 0]}))
        (should-not @explored?)))))

(describe "process-fighter-threat"
  (before (reset-all-atoms!))

  (it "returns nil when unit is not on fighter-sweep or major-invasion mission"
    (should-be-nil (threat-response/process-fighter-threat [0 0] {:threat-mission :none})))

  (it "returns true and iterates while fighter-step-threat yields moves"
    (set-test-world! (build-test-map ["f"]))
    (let [calls (atom 0)]
      (with-redefs [empire.computer.threat-response/fighter-step-threat
                    (fn [_ _]
                      (swap! calls inc)
                      (if (= 1 @calls)
                        {:pos [0 0] :steps-used 2}
                        nil))]
        (should (threat-response/process-fighter-threat [0 0] {:threat-mission :fighter-sweep}))
        (should= 2 @calls))))

  (it "returns true for major-invasion fighters"
    (set-test-world! (build-test-map ["f"]))
    (let [calls (atom 0)]
      (with-redefs [empire.computer.threat-response/fighter-step-threat
                    (fn [_ _]
                      (swap! calls inc)
                      nil)]
        (should (threat-response/process-fighter-threat
                 [0 0] {:major-invasion true :major-invasion-target [3 3]}))
        (should= 1 @calls))))

  (it "defaults a missing fighter step cost to one"
    (set-test-world! (build-test-map ["f"]))
    (let [calls (atom 0)]
      (with-redefs [empire.computer.threat-response/fighter-step-threat
                    (fn [_ _]
                      (swap! calls inc)
                      (when (= 1 @calls)
                        {:pos [0 0] :steps-used nil :hops nil}))]
        (should (threat-response/process-fighter-threat [0 0] {:threat-mission :fighter-sweep}))
        (should= 2 @calls))))

  (it "starts random walk for blocked major-invasion fighters when sidestep is unavailable"
    (set-test-world! (build-test-map ["f"]))
    (update-test-world! update-in [0 0 :contents] merge
                        {:major-invasion true
                         :major-invasion-target [3 3]
                         :threat-radius 0
                         :fuel 10})
    (with-redefs [empire.computer.fighter-movement/should-return-to-refuel? (constantly false)
                  empire.computer.fighter-movement/hop-over-friendly (constantly nil)
                  empire.computer.fighter-movement/get-passable-neighbors (constantly [])]
      (should
       (threat-response/process-fighter-threat
        [0 0] (get-in (test-utils/read-test-state :game-map) [0 0 :contents]))))
    (should= 5 (get-in (test-utils/read-test-state :game-map)
                       [0 0 :contents :oscillation-random-walk-rounds-left])))

  (it "stops iterating when fighter-step-threat returns nil immediately"
    (set-test-world! (build-test-map ["f"]))
    (with-redefs [empire.computer.threat-response/fighter-step-threat (fn [& _] nil)]
      (should (threat-response/process-fighter-threat [0 0] {:threat-mission :fighter-sweep}))))

  (it "repeats fighter threat steps until speed is exhausted"
    (set-test-world! (build-test-map ["f"]))
    (let [calls (atom 0)]
      (with-redefs [empire.computer.threat-response/fighter-step-threat
                    (fn [_ _]
                      (swap! calls inc)
                      {:pos [0 0] :steps-used 1})]
        (should (threat-response/process-fighter-threat [0 0] {:threat-mission :fighter-sweep}))
        (should= units-config/fighter-speed @calls)))))

(describe "fighter threat rounds"
  (before (reset-all-atoms!))

  (it "advances multiple fighter threat states directly in the processing loop"
    (set-test-world! (build-test-map ["f"]))
    (let [calls (atom 0)]
      (with-redefs [empire.computer.threat-response.processing/fighter-step-threat
                    (fn [_ _ _]
                      (swap! calls inc)
                      (when (< @calls 3)
                        {:pos [0 0] :steps-used 2}))]
        (@#'processing/run-fighter-threat-round (test-utils/mission-ctx) [0 0])
        (should= 3 @calls))))

  (it "returns true from the processing namespace when the unit is in fighter random walk"
    (set-test-world! (build-test-map ["f"]))
    (update-test-world! assoc-in [0 0 :contents :oscillation-random-walk-rounds-left] 1)
    (should
     (processing/process-fighter-threat (test-utils/mission-ctx)
                                        [0 0]
                                        (assoc (get-in (test-utils/read-test-state :game-map) [0 0 :contents])
                                               :threat-mission :fighter-sweep)))))

(describe "ship threat random walk"
  (before (reset-all-atoms!))

  (it "moves a ship to a random open sea neighbor and restores mode state"
    (set-test-world! (build-test-map ["d~~"]))
    (update-test-world! assoc-in [0 0 :contents :oscillation-random-walk-rounds-left] 1)
    (with-redefs [rand-nth first
                  empire.computer.ship-core/get-passable-sea-neighbors (fn [_] [[1 0]])
                  empire.computer.ship-core/find-adjacent-enemy-ship (constantly nil)]
      (should
       (@#'processing/process-ship-random-walk (test-utils/mission-ctx) [0 0])))
    (should= :destroyer (get-in (test-utils/read-test-state :game-map) [1 0 :contents :type]))
    (should= nil (get-in (test-utils/read-test-state :game-map) [1 0 :contents :oscillation-random-walk-rounds-left]))))

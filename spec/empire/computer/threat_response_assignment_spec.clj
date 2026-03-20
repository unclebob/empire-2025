(ns empire.computer.threat-response-assignment-spec
  (:require [empire.computer.threat-response-impl :as threat-response]
            [empire.computer.threat-response.core :as threat-response-core]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-world! update-test-world!]]
            [speclj.core :refer :all]))

(describe "prepare-transport!"
  (before (reset-all-atoms!))

  (it "returns nil when major invasion is not active"
    (test-utils/set-test-state! :major-invasion-state {:active? false :detection-points [] :target-land-set #{}})
    (should-be-nil (threat-response/prepare-transport! [0 0])))

  (it "returns nil when active but cell is not a transport"
    (test-utils/set-test-state! :major-invasion-state {:active? true :detection-points [] :target-land-set #{}})
    (set-test-world! (build-test-map ["a"]))
    (should-be-nil (threat-response/prepare-transport! [0 0])))

  (it "delegates to transport invasion prep and returns true"
    (test-utils/set-test-state! :major-invasion-state {:active? true :detection-points [[1 1]] :target-land-set #{}})
    (let [world (build-test-map ["t"])]
      (set-test-world! world)
      (set-test-computer-map! world))
    (let [called (atom nil)]
      (with-redefs [empire.computer.threat-response.core/prepare-transport-major-invasion!
                    (fn [pos unit] (reset! called [pos (:type unit)]))]
        (should (threat-response/prepare-transport! [0 0]))
        (should= [[0 0] :transport] @called)))))

(describe "major invasion review timing"
  (it "defaults next review round from zero when no round is recorded"
    (with-redefs [empire.state.api/read-state (constantly nil)]
      (should= empire.computer.threat-response.invasion-decision/review-interval-rounds
               (@#'threat-response-core/next-review-round)))))

(describe "prepare-transport-major-invasion!"
  (before (reset-all-atoms!))

  (it "best-invasion-target-and-path prefers closer landing over shorter path"
    (with-redefs [empire.computer.threat-response.core/load-major-invasion-state
                  (fn [] {:active? true :target-land-set #{[0 0]}})
                  empire.state.api/read-state
                  (fn [k] (case k :computer-map {} nil))
                  empire.computer.threat-response.core/connected-coastal-candidates
                  (fn [_ _ _] [[10 0] [2 0]])
                  empire.game-mechanics.movement.pathfinding-bfs/bfs-to-land-ho-target
                  (fn [_ candidate _]
                    (case candidate
                      [10 0] [[10 0]]
                      [2 0] [[1 0] [2 0]]
                      nil))]
      (let [result (@#'threat-response-core/best-invasion-target-and-path [0 0] [0 0])]
        (should= [2 0] (:target result))
        (should= [[1 0] [2 0]] (:path result)))))

  (it "reuses existing invasion plan when position and target revision are unchanged"
    (set-test-world! (build-test-map ["t~"
                                      "~O"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (test-utils/set-test-state! :major-invasion-state {:active? true
                                                       :detection-points [[1 1]]
                                                       :target-land-set #{[1 1]}
                                                       :sea-reachable-detection-points #{[1 1]}
                                                       :target-land-revision 7})
    (update-test-world! assoc-in [0 0 :contents]
                        {:type :transport :owner :computer :army-count 4
                         :transport-mission :invading
                         :invasion-target [1 1]
                         :invasion-path [[0 1]]
                         :invasion-path-origin [0 0]
                         :invasion-plan-revision 7})
    (let [called? (atom false)
          unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
      (with-redefs [empire.computer.threat-response.core/best-invasion-target-and-path
                    (fn [& _] (reset! called? true) {:target [1 1] :path [[0 1]]})]
        (@#'threat-response-core/prepare-transport-major-invasion! [0 0] unit))
      (should-not @called?)
      (should= [[0 1]] (get-in (test-utils/read-test-state :game-map) [0 0 :contents :invasion-path]))))

  (it "recomputes invasion plan when target revision changes"
    (set-test-world! (build-test-map ["t~"
                                      "~O"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (test-utils/set-test-state! :major-invasion-state {:active? true
                                                       :detection-points [[1 1]]
                                                       :target-land-set #{[1 1]}
                                                       :sea-reachable-detection-points #{[1 1]}
                                                       :target-land-revision 9})
    (update-test-world! assoc-in [0 0 :contents]
                        {:type :transport :owner :computer :army-count 4
                         :transport-mission :invading
                         :invasion-target [1 1]
                         :invasion-path [[0 1]]
                         :invasion-path-origin [0 0]
                         :invasion-plan-revision 8})
    (let [calls (atom 0)
          unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
      (with-redefs [empire.computer.threat-response.core/nearest-major-sea-target (fn [_] [1 1])
                    empire.computer.threat-response.core/best-invasion-target-and-path
                    (fn [& _]
                      (swap! calls inc)
                      {:target [1 1] :path [[1 0] [1 1]]})]
        (@#'threat-response-core/prepare-transport-major-invasion! [0 0] unit))
      (should= 1 @calls)
      (should= 9 (get-in (test-utils/read-test-state :game-map) [0 0 :contents :invasion-plan-revision]))
      (should= [0 0] (get-in (test-utils/read-test-state :game-map) [0 0 :contents :invasion-path-origin]))))

  (it "prefers sea-reachable major target for transport planning"
    (set-test-world! (build-test-map ["t~"
                                      "~O"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (test-utils/set-test-state! :major-invasion-state {:active? true
                                                       :detection-points [[1 1] [9 9]]
                                                       :target-land-set #{[1 1] [9 9]}
                                                       :sea-reachable-detection-points #{[9 9]}
                                                       :target-land-revision 2})
    (update-test-world! assoc-in [0 0 :contents]
                        {:type :transport :owner :computer :army-count 3
                         :transport-mission :sailing})
    (let [seen-target (atom nil)
          unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
      (with-redefs [empire.computer.threat-response.core/best-invasion-target-and-path
                    (fn [_ target]
                      (reset! seen-target target)
                      {:target target :path [[0 1]]})]
        (@#'threat-response-core/prepare-transport-major-invasion! [0 0] unit))
      (should= [9 9] @seen-target)
      (should= [9 9] (get-in (test-utils/read-test-state :game-map) [0 0 :contents :major-invasion-target]))))

  (it "sets empty invasion transport mission to find-armies-for-invasion"
    (set-test-world! (build-test-map ["t~"
                                      "~O"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (test-utils/set-test-state! :major-invasion-state {:active? true
                                                       :detection-points [[1 1]]
                                                       :target-land-set #{[1 1]}
                                                       :sea-reachable-detection-points #{[1 1]}
                                                       :target-land-revision 1})
    (update-test-world! assoc-in [0 0 :contents :army-count] 0)
    (update-test-world! assoc-in [0 0 :contents :transport-mission] :sailing)
    (@#'threat-response-core/prepare-transport-major-invasion! [0 0] (get-in (test-utils/read-test-state :game-map) [0 0 :contents]))
    (should= :find-armies-for-invasion (get-in (test-utils/read-test-state :game-map) [0 0 :contents :transport-mission]))))

(describe "refresh-major-invasion-assignments!"
  (before (reset-all-atoms!))

  (it "does nothing when major invasion is inactive"
    (test-utils/set-test-state! :major-invasion-state {:active? false :detection-points [] :target-land-set #{}})
    (let [called? (atom false)]
      (with-redefs [empire.computer.threat-response.core/prepare-transport-major-invasion!
                    (fn [_ _] (reset! called? true))]
        (threat-response/refresh-major-invasion-assignments!)
        (should-not @called?))))

  (it "marks fighters and major ship types; delegates transports"
    (set-test-world! (build-test-map ["fpta"]))
    (test-utils/set-test-state! :major-invasion-state {:active? true
                                                       :detection-points [[3 3]]
                                                       :sea-reachable-detection-points #{[3 3]}
                                                       :target-land-set #{}})
    (let [transport-calls (atom [])]
      (with-redefs [empire.computer.threat-response.core/nearest-major-sea-target (fn [_] [3 3])
                    empire.computer.threat-response.core/prepare-transport-major-invasion!
                    (fn [pos unit] (swap! transport-calls conj [pos (:type unit)]))]
        (threat-response/refresh-major-invasion-assignments!)
        (should= true (get-in (test-utils/read-test-state :game-map) [0 0 :contents :major-invasion]))
        (should= [3 3] (get-in (test-utils/read-test-state :game-map) [0 0 :contents :major-invasion-target]))
        (should= true (get-in (test-utils/read-test-state :game-map) [1 0 :contents :major-invasion]))
        (should= [3 3] (get-in (test-utils/read-test-state :game-map) [1 0 :contents :major-invasion-target]))
        (should= [[[2 0] :transport]] @transport-calls)
        (should-not-contain :major-invasion (get-in (test-utils/read-test-state :game-map) [3 0 :contents])))))

  (it "assigns inland armies to move-to-coast-for-invasion with a coast target"
    (set-test-world! (build-test-map ["fO~~~"
                                      "#####"
                                      "##a##"
                                      "#####"
                                      "~~~~~"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (test-utils/set-test-state! :major-invasion-state {:active? true
                                                       :detection-points [[1 0]]
                                                       :target-land-set #{[1 0]}
                                                       :sea-reachable-detection-points #{}
                                                       :target-land-revision 1})
    (threat-response/refresh-major-invasion-assignments!)
    (should= :move-to-coast-for-invasion (get-in (test-utils/read-test-state :game-map) [2 2 :contents :mode]))
    (should (vector? (get-in (test-utils/read-test-state :game-map) [2 2 :contents :coast-target])))))

(describe "dec-threat-rounds"
  (it "returns unit unchanged when no threat timer exists"
    (let [u {:type :fighter :mode :awake}]
      (should= u (@#'threat-response-core/dec-threat-rounds u))))

  (it "decrements positive timer"
    (should= 1 (:threat-rounds-left
                (@#'threat-response-core/dec-threat-rounds {:threat-rounds-left 2 :threat-mission :fighter-sweep}))))

  (it "clears threat mission fields when timer expires"
    (let [out (@#'threat-response-core/dec-threat-rounds {:threat-rounds-left 1
                                                          :threat-mission :fighter-sweep
                                                          :threat-center [1 1]
                                                          :threat-radius 5})]
      (should-be-nil (:threat-rounds-left out))
      (should-be-nil (:threat-mission out))
      (should-be-nil (:threat-center out))
      (should-be-nil (:threat-radius out)))))

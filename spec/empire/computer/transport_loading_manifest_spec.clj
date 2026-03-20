(ns empire.computer.transport-loading-manifest-spec
  (:require [empire.computer.transport :as transport]
            [empire.computer.transport.loading :as loading]
            [empire.computer.transport.mission-handlers :as mission-handlers]
            [empire.player.production :as player-prod]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [reset-all-atoms! set-test-computer-map! set-test-world!]]
            [speclj.core :refer :all]))

(describe "transport loading manifest"
  (before (reset-all-atoms!))

  (it "newly spawned computer transport starts in sail-to-load before load planning"
    (test-utils/set-test-state! :round-number 5)
    (set-test-world! [[{:type :city :city-status :computer :country-id 1}]])
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (test-utils/set-test-state! :production {[0 0] {:item :transport :remaining-rounds 1}})
    (player-prod/update-production)
    (let [unit (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))]
      (should= :transport (:type unit))
      (should= 0 (:army-count unit))
      (should= :sail-to-load (:transport-mission unit))
      (should= nil (:load-manifest unit))))

  (it "load-adjacent-armies removes boarded army ids from the manifest"
    (set-test-world! [[{:type :land :contents {:type :army :owner :computer :hits 1 :computer-unit-id 7}}
                       {:type :sea :contents {:type :transport :owner :computer
                                              :army-count 0
                                              :load-manifest [7 9]}}]])
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (should= 1 (loading/load-adjacent-armies [0 1]))
    (should= [9]
             (get-in (test-utils/read-test-state :game-map) [0 1 :contents :load-manifest])))

  (it "planned loading waits in place instead of crawling"
    (let [called (atom [])]
      (mission-handlers/process-loading-mission
       {:read-computer-map (constantly [[{:contents {:type :transport
                                                     :owner :computer
                                                     :transport-mission :loading
                                                     :army-count 1
                                                     :load-manifest [42]
                                                     :loading-since-round 10}}]])
        :load-adjacent-armies (fn [_] (swap! called conj :load))
        :should-start-sailing? (fn [& _] (swap! called conj :legacy-should-start) false)
        :start-sailing (fn [& _] (swap! called conj :start))
        :loading-crawl-move (fn [_] (swap! called conj :crawl))
        :transition-to-loading (fn [_] (swap! called conj :replan))}
       [0 0])
      (should= [:load] @called)))

  (it "loading with nil manifest does not start sailing to unload"
    (let [called (atom [])]
      (mission-handlers/process-loading-mission
       {:read-computer-map (constantly [[{:contents {:type :transport
                                                     :owner :computer
                                                     :transport-mission :loading
                                                     :army-count 0
                                                     :load-manifest nil
                                                     :loading-since-round 10}}]])
        :load-adjacent-armies (fn [_] (swap! called conj :load))
        :should-start-sailing? (fn [& _] (swap! called conj :legacy-should-start) false)
        :start-sailing (fn [& _] (swap! called conj :start))
        :loading-crawl-move (fn [_] (swap! called conj :crawl) :crawl)
        :transition-to-loading (fn [_] (swap! called conj :replan))}
       [0 0])
      (should= [:load :legacy-should-start :crawl] @called)))

  (it "sail-to-load with empty manifest enters hold-sail-to-load"
    (test-utils/set-test-state! :round-number 20)
    (set-test-world! [[{:type :sea
                        :contents {:type :transport
                                   :owner :computer
                                   :hits 1
                                   :transport-id 7
                                   :army-count 0
                                   :transport-mission :sail-to-load
                                   :load-target-cell [4 0]
                                   :load-manifest []
                                   :sail-path [[1 0] [2 0] [3 0]]}}
                      {:type :sea}
                      {:type :sea}
                      {:type :sea}
                      {:type :sea}]])
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (test-utils/set-test-state! :transport-load-reservations
                                {7 {:coastal-cell [1 0]
                                    :army-ids #{41 42}}})
    (transport/process-transport [0 0])
    (let [unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
      (should= :hold-sail-to-load (:transport-mission unit))
      (should= 20 (:hold-sail-to-load-since-round unit))
      (should= [] (:sail-path unit))
      (should-be-nil (:load-manifest unit)))
    (should= {}
             (test-utils/read-test-state :transport-load-reservations)))

  (it "sail-to-load with empty path enters hold-sail-to-load"
    (test-utils/set-test-state! :round-number 20)
    (set-test-world! [[{:type :sea
                        :contents {:type :transport
                                   :owner :computer
                                   :hits 1
                                   :transport-id 8
                                   :army-count 0
                                   :transport-mission :sail-to-load
                                   :load-target-cell [4 0]
                                   :load-manifest [41]
                                   :sail-path []}}
                      {:type :sea}
                      {:type :sea}
                      {:type :sea}
                      {:type :sea}]])
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (test-utils/set-test-state! :transport-load-reservations
                                {8 {:coastal-cell [1 0]
                                    :army-ids #{41}}})
    (transport/process-transport [0 0])
    (let [unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
      (should= :hold-sail-to-load (:transport-mission unit))
      (should= 20 (:hold-sail-to-load-since-round unit))
      (should= [] (:sail-path unit))
      (should-be-nil (:load-manifest unit)))
    (should= {}
             (test-utils/read-test-state :transport-load-reservations)))

  (it "hold-sail-to-load waits five rounds before returning to sail-to-load"
    (test-utils/set-test-state! :round-number 20)
    (set-test-world! [[{:type :sea
                        :contents {:type :transport
                                   :owner :computer
                                   :hits 1
                                   :transport-id 9
                                   :army-count 0
                                   :transport-mission :hold-sail-to-load
                                   :hold-sail-to-load-since-round 16
                                   :sail-path []}}]])
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (transport/process-transport [0 0])
    (should= :hold-sail-to-load
             (get-in (test-utils/read-test-state :game-map) [0 0 :contents :transport-mission]))
    (test-utils/set-test-state! :round-number 21)
    (transport/process-transport [0 0])
    (let [unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
      (should= :sail-to-load (:transport-mission unit))
      (should-be-nil (:hold-sail-to-load-since-round unit))
      (should= [] (:sail-path unit))))

  (it "planned loading with empty manifest replans when still empty"
    (let [called (atom nil)]
      (mission-handlers/process-loading-mission
       {:read-computer-map (constantly [[{:contents {:type :transport
                                                     :owner :computer
                                                     :transport-mission :loading
                                                     :army-count 0
                                                     :load-manifest []
                                                     :loading-since-round 10}}]])
        :load-adjacent-armies (fn [_] nil)
        :should-start-sailing? (fn [& _] false)
        :start-sailing (fn [pos transport] (reset! called [:start pos transport]))
        :loading-crawl-move (fn [_] :crawl)
        :transition-to-loading (fn [pos] (reset! called [:replan pos]))}
       [0 0])
      (should= [:replan [0 0]] @called)))

  (it "planned loading with empty manifest starts sailing to unload when partially loaded"
    (let [called (atom nil)]
      (mission-handlers/process-loading-mission
       {:read-computer-map (constantly [[{:contents {:type :transport
                                                     :owner :computer
                                                     :transport-mission :loading
                                                     :army-count 2
                                                     :load-manifest []
                                                     :loading-since-round 10}}]])
        :load-adjacent-armies (fn [_] nil)
        :should-start-sailing? (fn [& _] false)
        :start-sailing (fn [pos transport] (reset! called [:start pos transport]))
        :loading-crawl-move (fn [_] :crawl)
        :transition-to-loading (fn [pos] (reset! called [:replan pos]))}
       [0 0])
      (should= :start (first @called))))

  (it "planned loading timeout with three or fewer armies starts sailing to unload"
    (test-utils/set-test-state! :round-number 20)
    (let [called (atom nil)]
      (mission-handlers/process-loading-mission
       {:read-computer-map (constantly [[{:contents {:type :transport
                                                     :owner :computer
                                                     :transport-mission :loading
                                                     :army-count 3
                                                     :load-manifest [42]
                                                     :loading-since-round 9}}]])
        :load-adjacent-armies (fn [_] nil)
        :should-start-sailing? (fn [& _] false)
        :start-sailing (fn [pos _] (reset! called [:start pos]))
        :loading-crawl-move (fn [_] :crawl)
        :transition-to-loading (fn [pos] (reset! called [:replan pos]))}
       [0 0])
      (should= [:start [0 0]] @called)))

  (it "planned loading timeout with zero armies replans sail-to-load"
    (test-utils/set-test-state! :round-number 20)
    (let [called (atom nil)]
      (mission-handlers/process-loading-mission
       {:read-computer-map (constantly [[{:contents {:type :transport
                                                     :owner :computer
                                                     :transport-mission :loading
                                                     :army-count 0
                                                     :load-manifest [42]
                                                     :loading-since-round 9}}]])
        :load-adjacent-armies (fn [_] nil)
        :should-start-sailing? (fn [& _] false)
        :start-sailing (fn [pos _] (reset! called [:start pos]))
        :loading-crawl-move (fn [_] :crawl)
        :transition-to-loading (fn [pos] (reset! called [:replan pos]))}
       [0 0])
      (should= [:replan [0 0]] @called)))

  (it "planned loading timeout with more than three armies replans sail-to-load"
    (test-utils/set-test-state! :round-number 20)
    (let [called (atom nil)]
      (mission-handlers/process-loading-mission
       {:read-computer-map (constantly [[{:contents {:type :transport
                                                     :owner :computer
                                                     :transport-mission :loading
                                                     :army-count 4
                                                     :load-manifest [42]
                                                     :loading-since-round 9}}]])
        :load-adjacent-armies (fn [_] nil)
        :should-start-sailing? (fn [& _] false)
        :start-sailing (fn [pos _] (reset! called [:start pos]))
        :loading-crawl-move (fn [_] :crawl)
        :transition-to-loading (fn [pos] (reset! called [:replan pos]))}
       [0 0])
      (should= [:replan [0 0]] @called)))

  (it "clears the transport reservation when loading starts sailing to unload"
    (set-test-world! [[{:type :sea
                        :contents {:type :transport
                                   :owner :computer
                                   :hits 1
                                   :transport-id 7
                                   :army-count 4
                                   :transport-mission :loading
                                   :load-target-cell [1 0]
                                   :load-manifest [41 42]}}]])
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (test-utils/set-test-state! :transport-load-reservations
                                {7 {:coastal-cell [1 0]
                                    :army-ids #{41 42}}})
    (@#'transport/start-sailing [0 0]
                                (get-in (test-utils/read-test-state :computer-map)
                                        [0 0 :contents]))
    (should= {}
             (test-utils/read-test-state :transport-load-reservations))))

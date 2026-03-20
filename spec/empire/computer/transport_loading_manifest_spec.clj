(ns empire.computer.transport-loading-manifest-spec
  (:require [empire.computer.transport.loading :as loading]
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

  (it "planned loading with empty manifest starts sailing to unload"
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
        :transition-to-loading (fn [_] :replan)}
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
      (should= [:replan [0 0]] @called))))

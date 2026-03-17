(ns empire.game-loop-item-processing-computer-spec
  (:require [empire.computer.coordinator :as computer]
            [empire.computer.production :as computer-production]
            [empire.game.loop.item-processing :as ip]
            [empire.game.loop.item-processing.computer-items :as computer-items]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [reset-all-atoms! set-test-world!]]
            [speclj.core :refer :all]))

(defn- land-cell [] {:type :land})

(defn- make-land-map [n]
  (vec (repeat n (vec (repeat n (land-cell))))))

(describe "process-computer-items"
  (before (reset-all-atoms!))

  (it "does nothing when computer-items is empty"
    (test-utils/set-test-state! :computer-items [])
    (set-test-world! (make-land-map 5))
    (ip/process-computer-items)
    (should= [] (test-utils/read-test-state :computer-items)))

  (it "processes all items when fewer than 100"
    (set-test-world! (make-land-map 5))
    (test-utils/set-test-state! :computer-items [[0 0] [1 1] [2 2] [3 3] [4 4]])
    (ip/process-computer-items)
    (should= [] (test-utils/read-test-state :computer-items)))

  (it "stops after 100 items (L208, L209)"
    (let [n 5
          coords (for [c (range n) r (range n)] [c r])]
      (set-test-world! (make-land-map n))
      (test-utils/set-test-state! :computer-items (vec (apply concat (repeat 10 coords))))
      (should= 250 (count (test-utils/read-test-state :computer-items)))
      (ip/process-computer-items)
      (should= 150 (count (test-utils/read-test-state :computer-items)))))

  (it "processes computer city production (L191 city-status, L194)"
    (set-test-world! [[{:type :city :city-status :computer}]])
    (test-utils/set-test-state! :computer-items [[0 0]])
    (let [produced? (atom false)]
      (with-redefs [computer-production/process-computer-city
                    (fn [_] (reset! produced? true))]
        (ip/process-computer-items)
        (should @produced?))))

  (it "handles a single raw computer item coord pair without crashing"
    (set-test-world! [[{:type :city :city-status :computer}]])
    (test-utils/set-test-state! :computer-items [0 0])
    (let [produced? (atom false)]
      (with-redefs [computer-production/process-computer-city
                    (fn [_] (reset! produced? true))]
        (#'computer-items/process-one-computer-item)
        (should @produced?))))

  (it "normalizes a raw computer coord pair queue after a kamikazee relaunch"
    (set-test-world! [[{:type :city :city-status :computer}]])
    (test-utils/set-test-state! :computer-items [0 0])
    (with-redefs [computer-production/process-computer-city (fn [_])
                  empire.computer.threat-response/launch-kamikazee-from-airport!
                  (fn [_] [1 0])]
      (#'computer-items/process-one-computer-item)
      (should= [[1 0]] (test-utils/read-test-state :computer-items))))

  (it "requeues a computer city after launching a kamikazee fighter from its airport"
    (set-test-world! [[{:type :city :city-status :computer}]])
    (test-utils/set-test-state! :computer-items [[0 0]])
    (let [produced? (atom false)
          launched? (atom false)]
      (with-redefs [computer-production/process-computer-city
                    (fn [_] (reset! produced? true))
                    empire.computer.threat-response/launch-kamikazee-from-airport!
                    (fn [coords]
                      (reset! launched? true)
                      coords)]
        (ip/process-computer-items)
        (should @produced?)
        (should @launched?)
        (should= [[0 0]] (test-utils/read-test-state :computer-items)))))

  (it "does not process non-computer city (L191 =→not=)"
    (set-test-world! [[{:type :city :city-status :player}]])
    (test-utils/set-test-state! :computer-items [[0 0]])
    (let [produced? (atom false)]
      (with-redefs [computer-production/process-computer-city
                    (fn [_] (reset! produced? true))]
        (ip/process-computer-items)
        (should-not @produced?))))

  (it "does not process non-city as city (L191 type =→not=)"
    (set-test-world! [[{:type :land}]])
    (test-utils/set-test-state! :computer-items [[0 0]])
    (let [produced? (atom false)]
      (with-redefs [computer-production/process-computer-city
                    (fn [_] (reset! produced? true))]
        (ip/process-computer-items)
        (should-not @produced?))))

  (it "processes computer unit movement (L192, L197)"
    (set-test-world! [[{:type :land :contents {:type :army :owner :computer
                                               :mode :awake}}
                       (land-cell)]])
    (test-utils/set-test-state! :computer-items [[0 0]])
    (let [moved? (atom false)]
      (with-redefs [computer/process-computer-unit
                    (fn [_] (reset! moved? true) nil)]
        (ip/process-computer-items)
        (should @moved?))))

  (it "refreshes computer-map before processing computer units"
    (set-test-world! (test-utils/build-test-map ["~a"]))
    (test-utils/set-test-state! :computer-items [[1 0]])
    (let [seen-owner (atom nil)]
      (with-redefs [computer/process-computer-unit
                    (fn [_]
                      (reset! seen-owner
                              (get-in (test-utils/read-test-state :computer-map)
                                      [1 0 :contents :owner]))
                      nil)]
        (ip/process-computer-items)
        (should= :computer @seen-owner))))

  (it "continues processing when computer unit returns new coords (L199)"
    (set-test-world! [[{:type :land :contents {:type :army :owner :computer
                                               :mode :moving}}
                       {:type :land :contents {:type :army :owner :computer
                                               :mode :awake}}]])
    (test-utils/set-test-state! :computer-items [[0 0] [1 0]])
    (let [call-count (atom 0)]
      (with-redefs [computer/process-computer-unit
                    (fn [coords]
                      (swap! call-count inc)
                      (when (= coords [0 0]) [0 0]))]
        (ip/process-computer-items)
        (should (>= @call-count 2)))))

  (it "increments processed counter correctly (L211 inc→dec)"
    (let [n 5
          coords (for [c (range n) r (range n)] [c r])]
      (set-test-world! (make-land-map n))
      (test-utils/set-test-state! :computer-items (vec (apply concat (repeat 5 coords))))
      (should= 125 (count (test-utils/read-test-state :computer-items)))
      (ip/process-computer-items)
      (should= 25 (count (test-utils/read-test-state :computer-items))))))

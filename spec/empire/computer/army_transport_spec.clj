(ns empire.computer.army-transport-spec
  (:require [empire.computer.army.transport :as army-transport]
            [empire.test.utils :refer [reset-all-atoms! set-test-world!]]
            [speclj.core :refer :all]))

(describe "army transport boarding"
  (before (reset-all-atoms!))

  (it "boards an adjacent loading transport and updates visibility"
    (let [calls (atom [])]
      (set-test-world! [[{:contents {:type :army :owner :computer :unload-event-id 8}}]])
      (with-redefs [empire.computer.core/find-adjacent-loading-transport (fn [pos unload-id]
                                                                           (swap! calls conj [:adjacent pos unload-id])
                                                                           [1 0])
                    empire.game-mechanics.debug.logging/log-computer-event! (fn [& args] (swap! calls conj args))
                    empire.computer.core/board-transport (fn [from to] (swap! calls conj [:board from to]))
                    empire.computer.movement/update-cell-visibility! (fn [pos owner] (swap! calls conj [:visibility pos owner]))
                    empire.computer.core/find-loading-transport (fn [& _] (swap! calls conj :fallback))
                    empire.computer.army.movement/move-toward-objective (fn [& _] (swap! calls conj :move))]
        (should-be-nil (army-transport/find-and-board-transport [0 0] 1))
        (should= [[:adjacent [0 0] 8]
                  [:army-board [0 0] {:transport [1 0]}]
                  [:board [0 0] [1 0]]
                  [:visibility [0 0] :computer]]
                 @calls))))

  (it "moves toward a distant loading transport when none are adjacent"
    (set-test-world! [[{:contents {:type :army :owner :computer :unload-event-id 5}}]])
    (with-redefs [empire.computer.core/find-adjacent-loading-transport (constantly nil)
                  empire.computer.core/find-loading-transport (fn [unload-id]
                                                                (should= 5 unload-id)
                                                                [3 3])
                  empire.computer.army.movement/move-toward-objective (fn [pos target country-id]
                                                                        (should= [0 0] pos)
                                                                        (should= [3 3] target)
                                                                        (should= 9 country-id)
                                                                        :moved)]
      (should= :moved
               (army-transport/find-and-board-transport [0 0] 9))))

  (it "returns nil when no loading transport exists"
    (set-test-world! [[{:contents {:type :army :owner :computer :unload-event-id 5}}]])
    (with-redefs [empire.computer.core/find-adjacent-loading-transport (constantly nil)
                  empire.computer.core/find-loading-transport (constantly nil)]
      (should-be-nil
       (army-transport/find-and-board-transport [0 0] 9)))))

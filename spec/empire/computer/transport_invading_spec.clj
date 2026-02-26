(ns empire.computer.transport-invading-spec
  (:require [speclj.core :refer :all]
            [empire.computer.transport :as transport]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [reset-all-atoms!]]))

(defn make-map [height width cell-fn]
  (mapv (fn [r] (mapv (fn [c] (cell-fn r c)) (range width))) (range height)))

(describe "transport invading mode"
  (before (reset-all-atoms!))

  (context "with path remaining"
    (it "follows the path up to 2 steps per round"
      (let [game-map (make-map 1 5
                       (fn [_ _] {:type :sea}))]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [0 0 :contents]
               {:type :transport :owner :computer
                :transport-mission :invading
                :invasion-target [0 4]
                :invasion-path [[0 1] [0 2] [0 3]]
                :army-count 4})
        (transport/process-transport [0 0])
        ;; Should have moved 2 steps to [0 2]
        (should-be-nil (:contents (get-in @atoms/game-map [0 0])))
        (let [transport (get-in @atoms/game-map [0 2 :contents])]
          (should= :invading (:transport-mission transport))
          (should= [[0 3]] (:invasion-path transport))))))

  (context "when path is exhausted"
    (it "transitions to unloading mode"
      ;; Row 0: sea(transport), sea | Row 1: land, city
      (let [game-map (make-map 2 2
                       (fn [r c]
                         (cond
                           (and (= r 1) (= c 1)) {:type :city :city-status :free}
                           (= r 1) {:type :land}
                           :else {:type :sea})))]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [0 0 :contents]
               {:type :transport :owner :computer
                :transport-mission :invading
                :invasion-target [1 1]
                :invasion-path [[0 1]]
                :army-count 4})
        (transport/process-transport [0 0])
        ;; Should have moved to [0 1] and transitioned to unloading
        (let [transport (get-in @atoms/game-map [0 1 :contents])]
          (should= :unloading (:transport-mission transport)))))))

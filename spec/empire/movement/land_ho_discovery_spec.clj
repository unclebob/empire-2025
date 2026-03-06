(ns empire.game-mechanics.movement.land-ho-discovery-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.game-mechanics.movement.visibility :as visibility]
            [empire.test.utils :refer [reset-all-atoms! set-test-player-map! set-test-computer-map! set-test-world! update-test-world!]]))

(defn make-game-map [height width cell-fn]
  (mapv (fn [r] (mapv (fn [c] (cell-fn r c)) (range width))) (range height)))

(describe "land-ho discovery"
  (before (reset-all-atoms!))

  (context "when computer unit reveals a free city"
    (it "adds the city position to land-ho-targets"
      (let [game-map (make-game-map 5 5
                       (fn [r c]
                         (if (and (= r 2) (= c 3))
                           {:type :city :city-status :free}
                           {:type :sea})))]
        (set-test-world! game-map)
        ;; Computer-map starts with all unexplored
        (set-test-computer-map! (make-game-map 5 5 (fn [_ _] {:type :unexplored})))
        ;; Place a computer ship adjacent to the free city
        (update-test-world! assoc-in [2 2 :contents]
                            {:type :patrol-boat :owner :computer})
        ;; Update visibility for the computer ship at [2 2]
        (visibility/update-cell-visibility [2 2] :computer)
        (should-contain [2 3] (test-utils/read-test-state :land-ho-targets)))))

  (context "when player unit reveals a free city"
    (it "does not add to land-ho-targets"
      (let [game-map (make-game-map 5 5
                       (fn [r c]
                         (if (and (= r 2) (= c 3))
                           {:type :city :city-status :free}
                           {:type :sea})))]
        (set-test-world! game-map)
        (set-test-player-map! (make-game-map 5 5 (fn [_ _] {:type :unexplored})))
        (update-test-world! assoc-in [2 2 :contents]
                            {:type :patrol-boat :owner :player})
        (visibility/update-cell-visibility [2 2] :player)
        (should= [] (test-utils/read-test-state :land-ho-targets)))))

  (context "when computer unit reveals a computer-owned city"
    (it "does not add to land-ho-targets"
      (let [game-map (make-game-map 5 5
                       (fn [r c]
                         (if (and (= r 2) (= c 3))
                           {:type :city :city-status :computer}
                           {:type :sea})))]
        (set-test-world! game-map)
        (set-test-computer-map! (make-game-map 5 5 (fn [_ _] {:type :unexplored})))
        (update-test-world! assoc-in [2 2 :contents]
                            {:type :patrol-boat :owner :computer})
        (visibility/update-cell-visibility [2 2] :computer)
        (should= [] (test-utils/read-test-state :land-ho-targets)))))

  (context "when free city is already revealed on computer-map"
    (it "does not add duplicate"
      (let [game-map (make-game-map 5 5
                       (fn [r c]
                         (if (and (= r 2) (= c 3))
                           {:type :city :city-status :free}
                           {:type :sea})))]
        (set-test-world! game-map)
        ;; Computer-map already has the city revealed
        (set-test-computer-map! game-map)
        (update-test-world! assoc-in [2 2 :contents]
                            {:type :patrol-boat :owner :computer})
        (visibility/update-cell-visibility [2 2] :computer)
        (should= [] (test-utils/read-test-state :land-ho-targets)))))

  (context "when computer army reveals a free city"
    (it "does not add to land-ho-targets"
      (let [game-map (make-game-map 5 5
                       (fn [r c]
                         (cond
                           (and (= r 2) (= c 3)) {:type :city :city-status :free}
                           (and (= r 2) (= c 2)) {:type :land}
                           :else {:type :sea})))]
        (set-test-world! game-map)
        (set-test-computer-map! (make-game-map 5 5 (fn [_ _] {:type :unexplored})))
        (update-test-world! assoc-in [2 2 :contents]
                            {:type :army :owner :computer :hits 1})
        (visibility/update-cell-visibility [2 2] :computer
                                           {:type :army :owner :computer :hits 1})
        (should= [] (test-utils/read-test-state :land-ho-targets))))))

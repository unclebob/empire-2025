(ns empire.movement.land-ho-bfs-spec
  (:require [speclj.core :refer :all]
            [empire.movement.pathfinding-bfs :as bfs]
            [empire.test.utils :refer [reset-all-atoms!]]))

(defn make-map [height width cell-fn]
  (mapv (fn [r] (mapv (fn [c] (cell-fn r c)) (range width))) (range height)))

(describe "bfs-to-land-ho-target"
  (before (reset-all-atoms!))

  (context "when a sea path exists to a cell adjacent to the target city"
    (it "returns a path of sea cells ending adjacent to the city"
      ;; Map: row 0 all sea, row 1 = sea sea land city land
      ;; Transport at [0 0], city at [1 3]
      (let [m (make-map 3 5
                (fn [r c]
                  (cond
                    (= r 0) {:type :sea}
                    (and (= r 1) (= c 3)) {:type :city :city-status :free}
                    (and (= r 1) (#{2 4} c)) {:type :land}
                    :else {:type :sea})))
            path (bfs/bfs-to-land-ho-target [0 0] [1 3] m)]
        (should-not-be-nil path)
        (should (every? #(= :sea (:type (get-in m %))) path))
        ;; Last cell in path should be adjacent to [1 3] (Chebyshev distance 1)
        (let [[lr lc] (last path)]
          (should (<= (max (Math/abs (- lr 1)) (Math/abs (- lc 3))) 1))))))

  (context "when no sea path exists"
    (it "returns nil"
      ;; Land wall blocks all sea paths
      (let [m (make-map 3 5
                (fn [r c]
                  (cond
                    (and (= r 0) (= c 2)) {:type :land}
                    (and (= r 1) (= c 2)) {:type :land}
                    (and (= r 2) (= c 2)) {:type :land}
                    (and (= r 1) (= c 4)) {:type :city :city-status :free}
                    (= r 0) {:type :sea}
                    :else {:type :sea})))
            path (bfs/bfs-to-land-ho-target [0 0] [1 4] m)]
        (should-be-nil path))))

  (context "when target is adjacent to start"
    (it "returns an empty path"
      ;; Transport at [0 1], city at [1 1], land at [1 0] [1 2]
      (let [m (make-map 2 3
                (fn [r c]
                  (cond
                    (= r 0) {:type :sea}
                    (and (= r 1) (= c 1)) {:type :city :city-status :free}
                    :else {:type :land})))
            path (bfs/bfs-to-land-ho-target [0 1] [1 1] m)]
        ;; Path should be empty -- start is already adjacent
        (should= [] path)))))

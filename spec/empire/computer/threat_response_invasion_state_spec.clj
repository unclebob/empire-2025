(ns empire.computer.threat-response.invasion-state-spec
  (:require [empire.computer.threat-response.invasion-state :as invasion-state]
            [speclj.core :refer :all]))

(describe "invasion state helpers"
  (it "recognizes land and city cells only"
    (should (invasion-state/land-or-city? {:type :land}))
    (should (invasion-state/land-or-city? {:type :city}))
    (should-not (invasion-state/land-or-city? {:type :sea}))
    (should-not (invasion-state/land-or-city? nil)))

  (it "flood-fills the connected land component from a starting point"
    (with-redefs [empire.computer.shared.world-query/get-neighbors (fn [[x y]]
                                                                     [[(dec x) y] [(inc x) y] [x (dec y)] [x (inc y)]])]
      (let [world [[{:type :land} {:type :land} {:type :sea}]
                   [{:type :city} {:type :sea} {:type :land}]
                   [{:type :sea} {:type :land} {:type :land}]]]
        (should= #{[0 0] [1 0] [0 1]}
                 (invasion-state/flood-fill-land world [0 0])))))

  (it "returns nil when flood fill starts on non-land"
    (should-be-nil
     (invasion-state/flood-fill-land [[{:type :sea}]] [0 0])))

  (it "recomputes the union of target land around detection points"
    (with-redefs [empire.computer.threat-response.invasion-state/flood-fill-land
                  (fn [_world pos]
                    ({[1 1] #{[1 1] [1 2]}
                      [4 4] #{[4 4]}}
                     pos))]
      (should= #{[1 1] [1 2] [4 4]}
               (invasion-state/recompute-target-land :world [[1 1] [4 4]]))))

  (it "activates state and preserves the original started round"
    (should= {:active? true
              :detection-points [[3 4]]
              :started-round 7}
             (invasion-state/activate-state {:detection-points []} [3 4] 7))
    (should= {:active? true
              :detection-points [[1 2] [4 5]]
              :started-round 2}
             (invasion-state/activate-state {:active? false
                                             :detection-points [[1 2]]
                                             :started-round 2}
                                            [4 5]
                                            9)))

  (it "finds the nearest target and returns nil when there are none"
    (with-redefs [empire.computer.shared.grid/distance (fn [from to]
                                                         ({[[0 0] [1 1]] 3
                                                           [[0 0] [5 5]] 9}
                                                          [from to]))]
      (should= [1 1]
               (invasion-state/nearest-target {:detection-points [[5 5] [1 1]]} [0 0]))
      (should-be-nil
       (invasion-state/nearest-target {:detection-points []} [0 0])))))

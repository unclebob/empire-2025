(ns empire.computer.movement-spec
  (:require [empire.computer.movement :as movement]
            [speclj.core :refer :all]))

(describe "computer movement wrappers"
  (it "forwards visibility updates"
    (let [call (atom nil)
          unit {:type :army}]
      (with-redefs [empire.game-mechanics.movement.visibility/update-cell-visibility
                    (fn
                      ([pos owner]
                       (reset! call [pos owner])
                       :updated)
                      ([pos owner unit]
                       (reset! call [pos owner unit])
                       :updated-with-unit))]
        (should= :updated (movement/update-cell-visibility! [2 3] :computer))
        (should= [[2 3] :computer] @call)
        (should= :updated-with-unit
                 (movement/update-cell-visibility-with-unit! [2 3] :computer unit))
        (should= [[2 3] :computer unit] @call))))

  (it "forwards bfs and lake helper calls"
    (let [calls (atom [])]
      (with-redefs [empire.game-mechanics.movement.pathfinding-bfs/find-nearest-unexplored
                    (fn [pos unit-type]
                      (swap! calls conj [:unexplored pos unit-type])
                      :u)
                    empire.game-mechanics.movement.pathfinding-bfs/bfs-to-unseen-coast
                    (fn [pos computer-map claimed-targets]
                      (swap! calls conj [:unseen pos computer-map claimed-targets])
                      :c)
                    empire.game-mechanics.movement.pathfinding-bfs/bfs-to-land-ho-target
                    (fn [from target computer-map]
                      (swap! calls conj [:land-ho from target computer-map])
                      :l)
                    empire.game-mechanics.movement.pathfinding-bfs/bfs-to-coast-target
                    (fn [from computer-map army-count]
                      (swap! calls conj [:coast from computer-map army-count])
                      :t)
                    empire.game-mechanics.movement.lakes/lake-cells
                    (fn [world lake-max-cells]
                      (swap! calls conj [:lakes world lake-max-cells])
                      :lake-cells)]
        (should= :u (movement/find-nearest-unexplored [1 1] :fighter))
        (should= :c (movement/bfs-to-unseen-coast [1 2] :map #{[9 9]}))
        (should= :l (movement/bfs-to-land-ho-target [0 0] [3 3] :map))
        (should= :t (movement/bfs-to-coast-target [5 5] :map 3))
        (should= :lake-cells (movement/lake-cells :world 20))
        (should= [[:unexplored [1 1] :fighter]
                  [:unseen [1 2] :map #{[9 9]}]
                  [:land-ho [0 0] [3 3] :map]
                  [:coast [5 5] :map 3]
                  [:lakes :world 20]]
                 @calls))))

  (it "passes nil helpers for the 3-arity next-step"
    (let [call (atom nil)]
      (with-redefs [empire.game-mechanics.movement.pathfinding/next-step
                    (fn [from target unit-type passability-fn cache-key-extra]
                      (reset! call [from target unit-type passability-fn cache-key-extra])
                      :step)]
        (should= :step (movement/next-step [0 0] [1 1] :army))
        (should= [[0 0] [1 1] :army nil nil] @call))))

  (it "passes explicit helpers for the 5-arity next-step"
    (let [call (atom nil)
          passability-fn (fn [_] true)]
      (with-redefs [empire.game-mechanics.movement.pathfinding/next-step
                    (fn [from target unit-type passability cache-key-extra]
                      (reset! call [from target unit-type passability cache-key-extra])
                      :step)]
        (should= :step
                 (movement/next-step [2 2] [3 3] :fighter passability-fn :cache))
        (should= [[2 2] [3 3] :fighter passability-fn :cache]
                 @call)))))

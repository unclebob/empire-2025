(ns empire.movement.sea-lanes-spec
  (:require [speclj.core :refer :all]
            [empire.movement.sea-lanes :as sl]
            [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.test-utils :refer [build-test-map reset-all-atoms!]]))

(describe "decompose-path"
  (it "returns empty for path shorter than min-segment-length + 1"
    ;; Path of 2 cells = length 1, below min of 2
    (should= [] (sl/decompose-path [[0 0] [0 1]])))

  (it "decomposes a straight path into one segment"
    (let [path [[0 0] [0 1] [0 2] [0 3]]
          result (sl/decompose-path path)]
      (should= 1 (count result))
      (should= [0 0] (:start-pos (first result)))
      (should= [0 3] (:end-pos (first result)))
      (should= [0 1] (:direction (first result)))
      (should= path (:cells (first result)))))

  (it "decomposes an L-shaped path into two segments"
    ;; Goes right 3 then down 3
    (let [path [[0 0] [0 1] [0 2] [1 2] [2 2] [3 2]]
          result (sl/decompose-path path)]
      ;; First segment: [0,0]->[0,2] direction [0 1], length 2
      ;; Second segment: [0,2]->[3,2] direction [1 0], length 3
      (should= 2 (count result))
      (should= [0 0] (:start-pos (first result)))
      (should= [0 2] (:end-pos (first result)))
      (should= [0 2] (:start-pos (second result)))
      (should= [3 2] (:end-pos (second result)))))

  (it "discards segments shorter than min-segment-length"
    ;; Path: right 1, then down 3 -> first segment too short (length 1)
    (let [path [[0 0] [0 1] [1 1] [2 1] [3 1]]
          result (sl/decompose-path path)]
      ;; Only the down segment should be kept (length 3)
      (should= 1 (count result))
      (should= [0 1] (:start-pos (first result)))
      (should= [3 1] (:end-pos (first result)))))

  (it "returns nil for path with fewer than 2 cells"
    (should-be-nil (sl/decompose-path [[0 0]]))
    (should-be-nil (sl/decompose-path [])))

  (it "includes final segment at exact minimum length"
    ;; Right 2 then down 2 — final segment length = min-segment-length = 2
    (let [path [[0 0] [0 1] [0 2] [1 2] [2 2]]
          result (sl/decompose-path path)]
      (should= 2 (count result))
      (should= [1 0] (:direction (second result)))
      (should= [2 2] (:end-pos (second result))))))

(describe "record-path!"
  (before (reset-all-atoms!))

  (it "records a straight path as nodes and a segment"
    (sl/record-path! [[0 0] [0 1] [0 2] [0 3]])
    (let [net @atoms/sea-lane-network]
      ;; Should have 2 nodes (endpoints) and 1 segment
      (should= 2 (count (:nodes net)))
      (should= 1 (count (:segments net)))
      ;; Nodes at [0 0] and [0 3]
      (should (get-in net [:pos->node [0 0]]))
      (should (get-in net [:pos->node [0 3]]))))

  (it "creates intersection node when paths cross"
    ;; Horizontal path
    (sl/record-path! [[0 0] [0 1] [0 2] [0 3] [0 4]])
    ;; Vertical path crossing at [0 2]
    ;; [0 2] is interior of first segment -> should split
    (sl/record-path! [[-1 2] [0 2] [1 2] [2 2] [3 2]])
    (let [net @atoms/sea-lane-network]
      ;; [0 2] should now be a node (intersection)
      (should (get-in net [:pos->node [0 2]]))))

  (it "does not record duplicate segments"
    (sl/record-path! [[0 0] [0 1] [0 2] [0 3]])
    (sl/record-path! [[0 0] [0 1] [0 2] [0 3]])
    (let [net @atoms/sea-lane-network]
      (should= 1 (count (:segments net)))))

  (it "respects size limits"
    ;; Record many paths to approach limits
    (let [original-max-nodes config/max-sea-lane-nodes]
      ;; Set very low limits for testing
      (with-redefs [config/max-sea-lane-nodes 4
                    config/max-sea-lane-segments 2]
        (reset! atoms/sea-lane-network sl/empty-network)
        (sl/record-path! [[0 0] [0 1] [0 2] [0 3]])
        (sl/record-path! [[1 0] [1 1] [1 2] [1 3]])
        ;; Third path should be rejected (at capacity)
        (sl/record-path! [[2 0] [2 1] [2 2] [2 3]])
        (let [net @atoms/sea-lane-network]
          (should (<= (count (:segments net)) 2))))))

  (it "does not record paths shorter than 3 cells"
    (sl/record-path! [[0 0] [0 1]])
    (let [net @atoms/sea-lane-network]
      (should= 0 (count (:nodes net)))
      (should= 0 (count (:segments net)))))

  (it "records path with exactly 3 cells"
    (sl/record-path! [[0 0] [0 1] [0 2]])
    (let [net @atoms/sea-lane-network]
      (should= 2 (count (:nodes net)))
      (should= 1 (count (:segments net)))))

  (it "records segment with correct length"
    (sl/record-path! [[0 0] [0 1] [0 2] [0 3]])
    (let [net @atoms/sea-lane-network
          seg (first (vals (:segments net)))]
      (should= 3 (:length seg))))

  (it "indexes interior cells but not endpoints in pos->seg"
    (sl/record-path! [[0 0] [0 1] [0 2] [0 3]])
    (let [net @atoms/sea-lane-network]
      (should (get-in net [:pos->seg [0 1]]))
      (should (get-in net [:pos->seg [0 2]]))
      (should-be-nil (get-in net [:pos->seg [0 0]]))
      (should-be-nil (get-in net [:pos->seg [0 3]])))))

(describe "dijkstra"
  (it "finds path through single segment"
    (let [net (-> sl/empty-network
                  (assoc-in [:nodes 1] {:id 1 :pos [0 0] :segment-ids #{1}})
                  (assoc-in [:nodes 2] {:id 2 :pos [0 3] :segment-ids #{1}})
                  (assoc-in [:segments 1] {:id 1 :node-a-id 1 :node-b-id 2
                                           :direction [0 1] :cells [[0 0] [0 1] [0 2] [0 3]]
                                           :length 3}))
          result (sl/dijkstra net 1 2)]
      (should= [1] result)))

  (it "finds path through multiple segments"
    (let [net (-> sl/empty-network
                  (assoc-in [:nodes 1] {:id 1 :pos [0 0] :segment-ids #{1}})
                  (assoc-in [:nodes 2] {:id 2 :pos [0 3] :segment-ids #{1 2}})
                  (assoc-in [:nodes 3] {:id 3 :pos [3 3] :segment-ids #{2}})
                  (assoc-in [:segments 1] {:id 1 :node-a-id 1 :node-b-id 2
                                           :direction [0 1] :cells [[0 0] [0 1] [0 2] [0 3]]
                                           :length 3})
                  (assoc-in [:segments 2] {:id 2 :node-a-id 2 :node-b-id 3
                                           :direction [1 0] :cells [[0 3] [1 3] [2 3] [3 3]]
                                           :length 3}))
          result (sl/dijkstra net 1 3)]
      (should= [1 2] result)))

  (it "returns empty vector when entry equals exit"
    (let [net (assoc-in sl/empty-network [:nodes 1] {:id 1 :pos [0 0] :segment-ids #{}})]
      (should= [] (sl/dijkstra net 1 1))))

  (it "returns nil when no path exists"
    (let [net (-> sl/empty-network
                  (assoc-in [:nodes 1] {:id 1 :pos [0 0] :segment-ids #{}})
                  (assoc-in [:nodes 2] {:id 2 :pos [5 5] :segment-ids #{}}))]
      (should-be-nil (sl/dijkstra net 1 2))))

  (it "chooses shorter path among alternatives"
    (let [net (-> sl/empty-network
                  (assoc-in [:nodes 1] {:id 1 :pos [0 0] :segment-ids #{1 3}})
                  (assoc-in [:nodes 2] {:id 2 :pos [0 3] :segment-ids #{1 2}})
                  (assoc-in [:nodes 3] {:id 3 :pos [3 3] :segment-ids #{2 3}})
                  (assoc-in [:segments 1] {:id 1 :node-a-id 1 :node-b-id 2
                                           :length 3
                                           :cells [[0 0] [0 1] [0 2] [0 3]]
                                           :direction [0 1]})
                  (assoc-in [:segments 2] {:id 2 :node-a-id 2 :node-b-id 3
                                           :length 3
                                           :cells [[0 3] [1 3] [2 3] [3 3]]
                                           :direction [1 0]})
                  (assoc-in [:segments 3] {:id 3 :node-a-id 1 :node-b-id 3
                                           :length 10
                                           :cells (vec (concat [[0 0]]
                                                               (for [i (range 1 10)] [i i])
                                                               [[3 3]]))
                                           :direction [1 1]}))
          result (sl/dijkstra net 1 3)]
      ;; Shorter path via node 2 (total 6) beats direct (10)
      (should= [1 2] result))))

(describe "find-nearest-node"
  (it "finds node within local radius"
    (let [net (-> sl/empty-network
                  (assoc-in [:nodes 1] {:id 1 :pos [10 10] :segment-ids #{}})
                  (assoc-in [:pos->node [10 10]] 1))]
      (should= 1 (sl/find-nearest-node net [10 12] 5))))

  (it "returns nil when no node within radius"
    (let [net (-> sl/empty-network
                  (assoc-in [:nodes 1] {:id 1 :pos [10 10] :segment-ids #{}})
                  (assoc-in [:pos->node [10 10]] 1))]
      (should-be-nil (sl/find-nearest-node net [30 30] 5))))

  (it "returns nearest among multiple candidates"
    (let [net (-> sl/empty-network
                  (assoc-in [:nodes 1] {:id 1 :pos [10 10] :segment-ids #{}})
                  (assoc-in [:nodes 2] {:id 2 :pos [10 11] :segment-ids #{}})
                  (assoc-in [:pos->node [10 10]] 1)
                  (assoc-in [:pos->node [10 11]] 2))]
      (should= 2 (sl/find-nearest-node net [10 12] 5))))

  (it "includes node at exact radius boundary"
    (let [net (-> sl/empty-network
                  (assoc-in [:nodes 1] {:id 1 :pos [10 10] :segment-ids #{}})
                  (assoc-in [:pos->node [10 10]] 1))]
      ;; Chebyshev dist from [10 10] to [10 15] = max(0,5) = 5 = radius
      (should= 1 (sl/find-nearest-node net [10 15] 5))))

  (it "uses Chebyshev distance not Manhattan"
    (let [net (-> sl/empty-network
                  (assoc-in [:nodes 1] {:id 1 :pos [14 12] :segment-ids #{}})
                  (assoc-in [:pos->node [14 12]] 1))]
      ;; Chebyshev from [10 10] to [14 12] = max(4,2) = 4, outside radius 3
      ;; Manhattan = 6, min = 2 — both differ from Chebyshev
      (should-be-nil (sl/find-nearest-node net [10 10] 3)))))

(describe "assemble-path"
  (it "assembles path from single segment forward"
    (let [net (-> sl/empty-network
                  (assoc-in [:nodes 1] {:id 1 :pos [0 0] :segment-ids #{1}})
                  (assoc-in [:nodes 2] {:id 2 :pos [0 3] :segment-ids #{1}})
                  (assoc-in [:segments 1] {:id 1 :node-a-id 1 :node-b-id 2
                                           :direction [0 1]
                                           :cells [[0 0] [0 1] [0 2] [0 3]]
                                           :length 3}))
          result (sl/assemble-path net [1] 1)]
      (should= [[0 0] [0 1] [0 2] [0 3]] result)))

  (it "assembles path from single segment reversed"
    (let [net (-> sl/empty-network
                  (assoc-in [:nodes 1] {:id 1 :pos [0 0] :segment-ids #{1}})
                  (assoc-in [:nodes 2] {:id 2 :pos [0 3] :segment-ids #{1}})
                  (assoc-in [:segments 1] {:id 1 :node-a-id 1 :node-b-id 2
                                           :direction [0 1]
                                           :cells [[0 0] [0 1] [0 2] [0 3]]
                                           :length 3}))
          result (sl/assemble-path net [1] 2)]
      (should= [[0 3] [0 2] [0 1] [0 0]] result)))

  (it "joins multi-segment path without duplicates"
    (let [net (-> sl/empty-network
                  (assoc-in [:nodes 1] {:id 1 :pos [0 0] :segment-ids #{1}})
                  (assoc-in [:nodes 2] {:id 2 :pos [0 3] :segment-ids #{1 2}})
                  (assoc-in [:nodes 3] {:id 3 :pos [3 3] :segment-ids #{2}})
                  (assoc-in [:segments 1] {:id 1 :node-a-id 1 :node-b-id 2
                                           :direction [0 1]
                                           :cells [[0 0] [0 1] [0 2] [0 3]]
                                           :length 3})
                  (assoc-in [:segments 2] {:id 2 :node-a-id 2 :node-b-id 3
                                           :direction [1 0]
                                           :cells [[0 3] [1 3] [2 3] [3 3]]
                                           :length 3}))
          result (sl/assemble-path net [1 2] 1)]
      (should= [[0 0] [0 1] [0 2] [0 3] [1 3] [2 3] [3 3]] result)))

  (it "returns single node position for empty segment list"
    (let [net (-> sl/empty-network
                  (assoc-in [:nodes 1] {:id 1 :pos [5 5] :segment-ids #{}}))]
      (should= [[5 5]] (sl/assemble-path net [] 1)))))

(describe "route-through-network"
  (before (reset-all-atoms!))

  (it "returns nil when network has too few nodes"
    (let [net sl/empty-network
          fake-bounded-a* (fn [_ _ _ _] nil)]
      (should-be-nil (sl/route-through-network net [0 0] [5 5] :transport {} fake-bounded-a*))))

  (it "routes through a simple network"
    ;; Build a network with 4+ nodes
    (let [net (-> sl/empty-network
                  (assoc-in [:nodes 1] {:id 1 :pos [0 0] :segment-ids #{1}})
                  (assoc-in [:nodes 2] {:id 2 :pos [0 5] :segment-ids #{1 2}})
                  (assoc-in [:nodes 3] {:id 3 :pos [5 5] :segment-ids #{2}})
                  (assoc-in [:nodes 4] {:id 4 :pos [5 0] :segment-ids #{}})
                  (assoc-in [:pos->node [0 0]] 1)
                  (assoc-in [:pos->node [0 5]] 2)
                  (assoc-in [:pos->node [5 5]] 3)
                  (assoc-in [:pos->node [5 0]] 4)
                  (assoc-in [:segments 1] {:id 1 :node-a-id 1 :node-b-id 2
                                           :direction [0 1]
                                           :cells [[0 0] [0 1] [0 2] [0 3] [0 4] [0 5]]
                                           :length 5})
                  (assoc-in [:segments 2] {:id 2 :node-a-id 2 :node-b-id 3
                                           :direction [1 0]
                                           :cells [[0 5] [1 5] [2 5] [3 5] [4 5] [5 5]]
                                           :length 5}))
          ;; bounded-a* just returns direct paths for simplicity
          fake-bounded-a* (fn [start goal _unit-type _game-map]
                            (if (= start goal) [start] [start goal]))]
      (let [result (sl/route-through-network net [0 0] [5 5] :transport {} fake-bounded-a*)]
        (should-not-be-nil result)
        (should= [0 0] (first result))
        (should= [5 5] (last result)))))

  (it "uses extended radius when local radius fails"
    (with-redefs [config/sea-lane-local-radius 2
                  config/sea-lane-extended-radius 10
                  config/sea-lane-min-network-nodes 2]
      (let [net (-> sl/empty-network
                    (assoc-in [:nodes 1] {:id 1 :pos [5 0] :segment-ids #{1}})
                    (assoc-in [:nodes 2] {:id 2 :pos [5 10] :segment-ids #{1}})
                    (assoc-in [:pos->node [5 0]] 1)
                    (assoc-in [:pos->node [5 10]] 2)
                    (assoc-in [:segments 1] {:id 1 :node-a-id 1 :node-b-id 2
                                             :direction [0 1]
                                             :cells (mapv #(vector 5 %) (range 11))
                                             :length 10}))
            ;; Start [0 0] to [5 0]: Chebyshev=5, local=2 miss, extended=10 hit
            fake-a* (fn [s g _ _] [s g])]
        (should-not-be-nil
          (sl/route-through-network net [0 0] [10 10] :transport {} fake-a*)))))

  (it "returns nil when bounded A* fails for a leg"
    (with-redefs [config/sea-lane-min-network-nodes 2]
      (let [net (-> sl/empty-network
                    (assoc-in [:nodes 1] {:id 1 :pos [0 5] :segment-ids #{1}})
                    (assoc-in [:nodes 2] {:id 2 :pos [0 10] :segment-ids #{1}})
                    (assoc-in [:pos->node [0 5]] 1)
                    (assoc-in [:pos->node [0 10]] 2)
                    (assoc-in [:segments 1] {:id 1 :node-a-id 1 :node-b-id 2
                                             :direction [0 1]
                                             :cells [[0 5] [0 6] [0 7] [0 8] [0 9] [0 10]]
                                             :length 5}))
            ;; First leg: start [0 0] != entry [0 5], calls A* → nil
            ;; Last leg: exit [0 10] == goal, returns [goal] (no A* call)
            fail-a* (fn [_ _ _ _] nil)]
        (should-be-nil
          (sl/route-through-network net [0 0] [0 10] :transport {} fail-a*)))))

  (it "does not duplicate cells at leg/segment joins"
    (with-redefs [config/sea-lane-min-network-nodes 2]
      (let [net (-> sl/empty-network
                    (assoc-in [:nodes 1] {:id 1 :pos [0 0] :segment-ids #{1}})
                    (assoc-in [:nodes 2] {:id 2 :pos [0 5] :segment-ids #{1}})
                    (assoc-in [:pos->node [0 0]] 1)
                    (assoc-in [:pos->node [0 5]] 2)
                    (assoc-in [:segments 1] {:id 1 :node-a-id 1 :node-b-id 2
                                             :direction [0 1]
                                             :cells [[0 0] [0 1] [0 2] [0 3] [0 4] [0 5]]
                                             :length 5}))
            ;; start==entry, exit==goal — no A* calls needed
            fake-a* (fn [_ _ _ _] nil)]
        (let [result (sl/route-through-network net [0 0] [0 5] :transport {} fake-a*)]
          (should= (count result) (count (distinct result))))))))

(describe "integration: record and route"
  (before (reset-all-atoms!))

  (it "records paths and routes through the network"
    ;; Record several paths to build the network
    (sl/record-path! [[0 0] [0 1] [0 2] [0 3] [0 4] [0 5]])
    (sl/record-path! [[0 5] [1 5] [2 5] [3 5] [4 5] [5 5]])
    ;; Add a couple more paths to get 4+ nodes
    (sl/record-path! [[5 5] [5 4] [5 3] [5 2] [5 1] [5 0]])
    (let [net @atoms/sea-lane-network]
      (should (>= (count (:nodes net)) 4))
      (should (>= (count (:segments net)) 3)))))

(describe "duplicate-segment?"
  (it "detects duplicate segment between same nodes with same length"
    (let [net (-> sl/empty-network
                  (assoc-in [:nodes 1] {:id 1 :pos [0 0] :segment-ids #{1}})
                  (assoc-in [:nodes 2] {:id 2 :pos [0 3] :segment-ids #{1}})
                  (assoc-in [:segments 1] {:id 1 :node-a-id 1 :node-b-id 2
                                            :length 3}))]
      (should (#'empire.movement.sea-lanes/duplicate-segment? net 1 2 3))))

  (it "detects duplicate when node order is reversed"
    (let [net (-> sl/empty-network
                  (assoc-in [:nodes 1] {:id 1 :pos [0 0] :segment-ids #{1}})
                  (assoc-in [:nodes 2] {:id 2 :pos [0 3] :segment-ids #{1}})
                  (assoc-in [:segments 1] {:id 1 :node-a-id 1 :node-b-id 2
                                            :length 3}))]
      (should (#'empire.movement.sea-lanes/duplicate-segment? net 2 1 3))))

  (it "returns falsy for different length"
    (let [net (-> sl/empty-network
                  (assoc-in [:nodes 1] {:id 1 :pos [0 0] :segment-ids #{1}})
                  (assoc-in [:nodes 2] {:id 2 :pos [0 3] :segment-ids #{1}})
                  (assoc-in [:segments 1] {:id 1 :node-a-id 1 :node-b-id 2
                                            :length 3}))]
      (should-not (#'empire.movement.sea-lanes/duplicate-segment? net 1 2 5))))

  (it "returns falsy for different nodes"
    (let [net (-> sl/empty-network
                  (assoc-in [:nodes 1] {:id 1 :pos [0 0] :segment-ids #{1}})
                  (assoc-in [:nodes 2] {:id 2 :pos [0 3] :segment-ids #{1}})
                  (assoc-in [:nodes 3] {:id 3 :pos [0 5] :segment-ids #{}})
                  (assoc-in [:segments 1] {:id 1 :node-a-id 1 :node-b-id 2
                                            :length 3}))]
      (should-not (#'empire.movement.sea-lanes/duplicate-segment? net 1 3 3))))

  (it "returns falsy for node with no segments"
    (let [net (assoc-in sl/empty-network [:nodes 1] {:id 1 :pos [0 0] :segment-ids #{}})]
      (should-not (#'empire.movement.sea-lanes/duplicate-segment? net 1 2 3)))))

(describe "split-cells-at"
  (it "splits cells at interior position"
    (let [[left right] (#'empire.movement.sea-lanes/split-cells-at
                          [[0 0] [0 1] [0 2] [0 3]] [0 2])]
      (should= [[0 0] [0 1] [0 2]] left)
      (should= [[0 2] [0 3]] right)))

  (it "splits at first position"
    (let [[left right] (#'empire.movement.sea-lanes/split-cells-at
                          [[0 0] [0 1] [0 2]] [0 0])]
      (should= [[0 0]] left)
      (should= [[0 0] [0 1] [0 2]] right)))

  (it "splits at last position"
    (let [[left right] (#'empire.movement.sea-lanes/split-cells-at
                          [[0 0] [0 1] [0 2]] [0 2])]
      (should= [[0 0] [0 1] [0 2]] left)
      (should= [[0 2]] right))))

(describe "network-at-capacity?"
  (it "returns false for empty network"
    (should-not (#'empire.movement.sea-lanes/network-at-capacity? sl/empty-network)))

  (it "returns true when nodes at max"
    (let [net (assoc sl/empty-network :nodes
                     (into {} (for [i (range config/max-sea-lane-nodes)]
                                [i {:id i}])))]
      (should (#'empire.movement.sea-lanes/network-at-capacity? net))))

  (it "returns true when segments at max"
    (let [net (assoc sl/empty-network :segments
                     (into {} (for [i (range config/max-sea-lane-segments)]
                                [i {:id i}])))]
      (should (#'empire.movement.sea-lanes/network-at-capacity? net))))

  (it "returns false when below both limits"
    (let [net (-> sl/empty-network
                  (assoc :nodes {1 {:id 1} 2 {:id 2}})
                  (assoc :segments {1 {:id 1}}))]
      (should-not (#'empire.movement.sea-lanes/network-at-capacity? net)))))

(describe "remove-segment"
  (it "removes segment-id from both endpoint nodes"
    (let [net (-> sl/empty-network
                  (assoc-in [:nodes 1] {:id 1 :pos [0 0] :segment-ids #{1}})
                  (assoc-in [:nodes 2] {:id 2 :pos [0 3] :segment-ids #{1}})
                  (assoc-in [:segments 1] {:id 1 :node-a-id 1 :node-b-id 2
                                           :cells [[0 0] [0 1] [0 2] [0 3]]
                                           :length 3})
                  (assoc-in [:pos->seg [0 1]] 1)
                  (assoc-in [:pos->seg [0 2]] 1))
          result (#'empire.movement.sea-lanes/remove-segment net 1)]
      (should= #{} (get-in result [:nodes 1 :segment-ids]))
      (should= #{} (get-in result [:nodes 2 :segment-ids]))
      (should-be-nil (get-in result [:segments 1]))
      (should-be-nil (get-in result [:pos->seg [0 1]]))
      (should-be-nil (get-in result [:pos->seg [0 2]])))))

(describe "split-segment-at"
  (before (reset-all-atoms!))

  (it "splits segment creating new node at intersection"
    (let [;; Build a network with one segment from node 1 to node 2
          net (-> sl/empty-network
                  (assoc-in [:nodes 1] {:id 1 :pos [0 0] :segment-ids #{1}})
                  (assoc-in [:nodes 2] {:id 2 :pos [0 4] :segment-ids #{1}})
                  (assoc-in [:pos->node [0 0]] 1)
                  (assoc-in [:pos->node [0 4]] 2)
                  (assoc-in [:segments 1] {:id 1 :node-a-id 1 :node-b-id 2
                                            :direction [0 1]
                                            :cells [[0 0] [0 1] [0 2] [0 3] [0 4]]
                                            :length 4})
                  (assoc-in [:pos->seg [0 1]] 1)
                  (assoc-in [:pos->seg [0 2]] 1)
                  (assoc-in [:pos->seg [0 3]] 1)
                  (assoc :next-node-id 3 :next-segment-id 2))
          result (#'empire.movement.sea-lanes/split-segment-at net 1 [0 2])]
      ;; Original segment 1 should be removed
      (should-not (get-in result [:segments 1]))
      ;; New node at [0 2] should exist
      (should (get-in result [:pos->node [0 2]]))
      ;; Should have at least 2 segments (left and right of split)
      (should (>= (count (:segments result)) 2)))))

(run-specs)

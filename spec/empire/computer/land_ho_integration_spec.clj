(ns empire.computer.land-ho-integration-spec
  (:require [speclj.core :refer :all]
            [empire.movement.visibility :as visibility]
            [empire.computer.land-ho :as land-ho]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [reset-all-atoms!]]))

(defn make-map [height width cell-fn]
  (mapv (fn [r] (mapv (fn [c] (cell-fn r c)) (range width))) (range height)))

(describe "land-ho full flow"
  (before (reset-all-atoms!))

  (it "patrol boat discovers city -> assignment -> transport invades"
    ;; Row 0: sea(patrol@0,0) sea sea(transport@0,3) sea
    ;; Row 1: sea            sea sea                 sea
    ;; Row 2: land           city(free)  land        sea
    (let [game-map (make-map 3 4
                     (fn [r c]
                       (cond
                         (and (= r 2) (= c 1)) {:type :city :city-status :free}
                         (and (= r 2) (#{0 2} c)) {:type :land}
                         :else {:type :sea})))]
      (reset! atoms/game-map game-map)
      ;; Computer-map: only row 0 explored, rest unexplored
      (reset! atoms/computer-map
              (make-map 3 4
                (fn [r c]
                  (if (= r 0) (get-in game-map [r c]) {:type :unexplored}))))
      ;; Patrol boat at [0 0]
      (swap! atoms/game-map assoc-in [0 0 :contents]
             {:type :patrol-boat :owner :computer})
      ;; Transport at [0 3] with 4 armies, sailing
      (swap! atoms/game-map assoc-in [0 3 :contents]
             {:type :transport :owner :computer
              :transport-mission :sailing :army-count 4})

      ;; Step 1: Patrol boat moves to [1 1] and discovers the free city
      ;; Simulate by updating visibility from [1 1]
      (swap! atoms/computer-map assoc-in [1 1] (get-in game-map [1 1]))
      (swap! atoms/game-map assoc-in [1 1 :contents]
             {:type :patrol-boat :owner :computer})
      (visibility/update-cell-visibility [1 1] :computer)

      ;; City should be in land-ho-targets
      (should-contain [2 1] @atoms/land-ho-targets)

      ;; Step 2: Round-start assignment
      (land-ho/assign-land-ho-invasion)

      ;; Target consumed, transport assigned
      (should= [] @atoms/land-ho-targets)
      (let [t (get-in @atoms/game-map [0 3 :contents])]
        (should= :invading (:transport-mission t))
        (should= [2 1] (:invasion-target t))))))

(ns empire.computer.land-ho-spec
  (:require [speclj.core :refer :all]
            [empire.computer.land-ho :as land-ho]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [reset-all-atoms! set-test-player-map! set-test-computer-map! set-test-world! update-test-world!]]))

(defn make-map [height width cell-fn]
  (mapv (fn [r] (mapv (fn [c] (cell-fn r c)) (range width))) (range height)))

(describe "assign-land-ho-invasion"
  (before (reset-all-atoms!))

  (context "with a target and a qualifying transport"
    (it "assigns the transport to invading mode with path"
      ;; Row 0: sea (transport at [0,0]), sea, sea
      ;; Row 1: sea, sea, sea
      ;; Row 2: land, city(free), land
      (let [game-map (make-map 3 3
                       (fn [r c]
                         (cond
                           (and (= r 2) (= c 1)) {:type :city :city-status :free}
                           (= r 2) {:type :land}
                           :else {:type :sea})))]
        (set-test-world! game-map)
        (set-test-computer-map! game-map)
        ;; Place a sailing transport with 4 armies at [0 0]
        (update-test-world! assoc-in [0 0 :contents]
                            {:type :transport :owner :computer
                             :transport-mission :sailing :army-count 4})
        ;; Add target city
        (reset! atoms/land-ho-targets [[2 1]])
        (land-ho/assign-land-ho-invasion)
        ;; Transport should be in invading mode
        (let [transport (get-in @atoms/game-map [0 0 :contents])]
          (should= :invading (:transport-mission transport))
          (should= [2 1] (:invasion-target transport))
          (should-not-be-nil (:invasion-path transport)))
        ;; Target should be consumed
        (should= [] @atoms/land-ho-targets))))

  (context "with no qualifying transports"
    (it "leaves the target at the front of the queue"
      (let [game-map (make-map 3 3
                       (fn [r c]
                         (if (and (= r 2) (= c 1))
                           {:type :city :city-status :free}
                           {:type :sea})))]
        (set-test-world! game-map)
        (set-test-computer-map! game-map)
        (reset! atoms/land-ho-targets [[2 1]])
        (land-ho/assign-land-ho-invasion)
        (should= [[2 1]] @atoms/land-ho-targets))))

  (context "with an unreachable target"
    (it "moves the target to the end of the queue"
      (let [game-map (make-map 3 5
                       (fn [r c]
                         (cond
                           ;; Land wall at col 2
                           (= c 2) {:type :land}
                           (and (= r 2) (= c 4)) {:type :city :city-status :free}
                           (and (= r 1) (= c 0)) {:type :city :city-status :free}
                           :else {:type :sea})))]
        (set-test-world! game-map)
        (set-test-computer-map! game-map)
        (update-test-world! assoc-in [0 0 :contents]
                            {:type :transport :owner :computer
                             :transport-mission :sailing :army-count 4})
        ;; Unreachable target first, reachable target second
        (reset! atoms/land-ho-targets [[2 4] [1 0]])
        (land-ho/assign-land-ho-invasion)
        ;; Unreachable target moves to end
        (should= [[1 0] [2 4]] @atoms/land-ho-targets))))

  (context "with empty target list"
    (it "does nothing"
      (reset! atoms/land-ho-targets [])
      (land-ho/assign-land-ho-invasion)
      (should= [] @atoms/land-ho-targets)))

  (context "with multiple transports"
    (it "picks the nearest transport to the first target"
      (let [game-map (make-map 5 5
                       (fn [r c]
                         (cond
                           (and (= r 4) (= c 2)) {:type :city :city-status :free}
                           (= r 4) {:type :land}
                           :else {:type :sea})))]
        (set-test-world! game-map)
        (set-test-computer-map! game-map)
        ;; Far transport at [0 0]
        (update-test-world! assoc-in [0 0 :contents]
                            {:type :transport :owner :computer
                             :transport-mission :sailing :army-count 4})
        ;; Near transport at [3 2]
        (update-test-world! assoc-in [3 2 :contents]
                            {:type :transport :owner :computer
                             :transport-mission :sailing :army-count 4})
        (reset! atoms/land-ho-targets [[4 2]])
        (land-ho/assign-land-ho-invasion)
        ;; Near transport should be assigned
        (let [near (get-in @atoms/game-map [3 2 :contents])
              far (get-in @atoms/game-map [0 0 :contents])]
          (should= :invading (:transport-mission near))
          (should= :sailing (:transport-mission far)))))))

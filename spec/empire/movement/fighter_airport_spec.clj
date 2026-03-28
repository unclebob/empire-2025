(ns empire.game-mechanics.movement.fighter-airport-spec
  (:require [empire.test.utils :as test-utils]
            [empire.game-mechanics.containers.ops :as container-ops]
            [empire.game.loop.core :as game-loop]
            [empire.game-mechanics.movement.api :refer :all]
            [empire.game-mechanics.movement.wake-conditions :as wake]
            [empire.test.utils :refer [build-test-map get-test-unit get-test-city set-test-unit reset-all-atoms! set-test-player-map! make-initial-test-map set-test-world! update-test-world!]]
            [speclj.core :refer :all]))

(describe "fighter shot down by city"
  (before (reset-all-atoms!))
  (it "fighter is destroyed when flying into hostile city"
    (set-test-world! (build-test-map ["-FX"]))
    (let [fighter-coords (:pos (get-test-unit (test-utils/game-map-atom) "F"))
          city-coords (:pos (get-test-city (test-utils/game-map-atom) "X"))]
      (set-test-unit (test-utils/game-map-atom) "F" :mode :moving :target city-coords :fuel 10 :steps-remaining 1 :hits 1)
      (set-test-player-map! (make-initial-test-map 1 3 nil))
      (test-utils/set-test-state! :error-message "")
      (let [cell (get-in (test-utils/read-test-state :game-map) fighter-coords)
            unit (:contents cell)
            result (wake/wake-after-move unit fighter-coords city-coords (test-utils/game-map-atom))]
        (should= 0 (:hits result))))))

(describe "fighter landing at city"
  (before (reset-all-atoms!))
  (it "fighter lands at city and increments fighter-count"
    (set-test-world! (build-test-map ["-FO"]))
    (let [fighter-coords (:pos (get-test-unit (test-utils/game-map-atom) "F"))
          city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))]
      (set-test-unit (test-utils/game-map-atom) "F" :mode :moving :target city-coords :fuel 10 :steps-remaining 1 :hits 1)
      (update-test-world! assoc-in (conj city-coords :fighter-count) 0)
      (set-test-player-map! (make-initial-test-map 1 3 nil))
      (game-loop/move-current-unit fighter-coords)
      (let [city (get-in (test-utils/read-test-state :game-map) city-coords)]
        (should= 1 (:fighter-count city))
        (should-be-nil (:contents city)))))

  (it "fighter lands at city with army on it"
    (set-test-world! (build-test-map ["-FO"]))
    (let [fighter-coords (:pos (get-test-unit (test-utils/game-map-atom) "F"))
          city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))]
      (set-test-unit (test-utils/game-map-atom) "F" :mode :moving :target city-coords :fuel 10 :steps-remaining 1 :hits 1)
      (update-test-world! assoc-in (conj city-coords :fighter-count) 0)
      (update-test-world! assoc-in (conj city-coords :contents)
                         {:type :army :mode :moving :target [2 0] :hits 1 :owner :player})
      (set-test-player-map! (make-initial-test-map 1 3 nil))
      (game-loop/move-current-unit fighter-coords)
      (let [city (get-in (test-utils/read-test-state :game-map) city-coords)]
        (should= 1 (:fighter-count city))
        (should= :army (:type (:contents city)))))))

(describe "get-active-unit airport fighter"
  (before (reset-all-atoms!))
  (it "returns synthetic fighter when city has awake airport fighters"
    (let [cell {:type :city :city-status :player :fighter-count 2 :awake-fighters 1}]
      (let [active (get-active-unit cell)]
        (should= :fighter (:type active))
        (should= :awake (:mode active))
        (should= true (:from-airport active)))))

  (it "creates synthetic fighter when fighter-count > 0 and awake-fighters > 0"
    (let [cell {:type :city :city-status :player :fighter-count 2 :awake-fighters 2}
          active (get-active-unit cell)]
      (should= :fighter (:type active))
      (should= true (:from-airport active))))

  (it "is-fighter-from-airport? returns true for synthetic airport fighter"
    (let [fighter {:type :fighter :mode :awake :owner :player :from-airport true}]
      (should= true (is-fighter-from-airport? fighter))))

  (it "is-fighter-from-airport? returns falsy for regular fighter"
    (let [fighter {:type :fighter :mode :awake :owner :player :hits 1}]
      (should-not (is-fighter-from-airport? fighter)))))

(describe "launch-fighter-from-airport"
  (before (reset-all-atoms!))
  (it "removes fighter from airport and places it moving"
    (set-test-world! (build-test-map ["-O#-"]))
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))
          target-coords [(+ (first city-coords) 2) (second city-coords)]]
      (update-test-world! assoc-in (conj city-coords :fighter-count) 2)
      (update-test-world! assoc-in (conj city-coords :awake-fighters) 2)
      (set-test-player-map! (make-initial-test-map 1 4 nil))
      (container-ops/launch-fighter-from-airport city-coords target-coords)
      (let [world (test-utils/read-test-state :game-map)
            city (get-in world city-coords)
            fighter (get-in world [2 0 :contents])]
        (should= 1 (:fighter-count city))
        (should= 2 (:awake-fighters city))
        (should= :fighter (:type fighter))
        (should= :moving (:mode fighter))
        (should= target-coords (:target fighter)))))

  (it "launches fighter from airport with zero awake-fighters"
    (set-test-world! (build-test-map ["-O#-"]))
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))
          target-coords [(+ (first city-coords) 2) (second city-coords)]]
      (update-test-world! assoc-in city-coords
                          {:type :city :city-status :player :fighter-count 2 :awake-fighters 0})
      (set-test-player-map! (make-initial-test-map 1 4 nil))
      (container-ops/launch-fighter-from-airport city-coords target-coords)
      (let [cell (get-in (test-utils/read-test-state :game-map) city-coords)]
        (should= 1 (:fighter-count cell))
        (should= 0 (:awake-fighters cell))))))

(describe "fighter landing via do-move"
  (before (reset-all-atoms!))

  (it "fighter lands at player city and is added to airport"
    (set-test-world! (build-test-map ["-O"
                                      "-#"]))
    (update-test-world! assoc-in [0 0 :contents]
                        {:type :fighter :owner :player :hits 1 :fuel 10 :mode :moving :target [1 0]})
    (set-test-player-map! (make-initial-test-map 2 2 nil))
    (let [from [0 0]
          cell (get-in (test-utils/read-test-state :game-map) from)
          unit (:contents cell)]
      (do-move from [1 0] cell unit)
      (let [city (get-in (test-utils/read-test-state :game-map) [1 0])]
        (should= 1 (:fighter-count city))
        (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))))))

  (it "fighter lands on friendly carrier and is added to carrier"
    (set-test-world! (build-test-map ["~~"
                                      "~~"]))
    (update-test-world! assoc-in [0 0 :contents]
                        {:type :fighter :owner :player :hits 1 :fuel 10 :mode :moving :target [1 0]})
    (update-test-world! assoc-in [1 0 :contents]
                        {:type :carrier :owner :player :hits 3 :fighter-count 0
                         :awake-fighters 0 :mode :sentry})
    (set-test-player-map! (make-initial-test-map 2 2 nil))
    (let [from [0 0]
          cell (get-in (test-utils/read-test-state :game-map) from)
          unit (:contents cell)]
      (do-move from [1 0] cell unit)
      (should= 1 (get-in (test-utils/read-test-state :game-map) [1 0 :contents :fighter-count]))
      (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))))))

(describe "wake-fighters-on-airport"
  (before (reset-all-atoms!))
  (it "wakes all fighters on airport"
    (set-test-world! (build-test-map ["-O"]))
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))]
      (update-test-world! assoc-in city-coords
                          {:type :city :city-status :player :fighter-count 3 :awake-fighters 0})
      (set-test-player-map! (make-initial-test-map 2 1 nil))
      (container-ops/wake-fighters-on-airport city-coords)
      (let [cell (get-in (test-utils/read-test-state :game-map) city-coords)]
        (should= 3 (:awake-fighters cell))
        (should= 3 (:fighter-count cell))))))

(describe "sleep-fighters-on-airport"
  (before (reset-all-atoms!))
  (it "sleeps all fighters on airport"
    (set-test-world! (build-test-map ["-O"]))
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))]
      (update-test-world! assoc-in city-coords
                          {:type :city :city-status :player :fighter-count 3 :awake-fighters 2})
      (set-test-player-map! (make-initial-test-map 2 1 nil))
      (container-ops/sleep-fighters-on-airport city-coords)
      (let [cell (get-in (test-utils/read-test-state :game-map) city-coords)]
        (should= 0 (:awake-fighters cell))
        (should= 3 (:fighter-count cell))))))

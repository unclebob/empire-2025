(ns empire.game-mechanics.movement.fighter-fuel-spec
  (:require [empire.test.utils :as test-utils]
            [empire.game.loop.core :as game-loop]
            [empire.game-mechanics.movement.api :refer :all]
            [empire.test.utils :refer [build-test-map get-test-unit get-test-city set-test-unit reset-all-atoms! set-test-player-map! make-initial-test-map set-test-world!]]
            [speclj.core :refer :all]))

(describe "fighter fuel"
  (before (reset-all-atoms!))
  (it "moves fighter and decrements fuel"
    (set-test-world! (build-test-map ["-F#"]))
    (let [fighter-coords (:pos (get-test-unit (test-utils/game-map-atom) "F"))
          target-coords [(inc (first fighter-coords)) (second fighter-coords)]]
      (set-test-unit (test-utils/game-map-atom) "F" :mode :moving :target target-coords :fuel 10 :steps-remaining 1)
      (set-test-player-map! (make-initial-test-map 1 3 nil))
      (game-loop/move-current-unit fighter-coords)
      (should= {:type :land} (get-in (test-utils/read-test-state :game-map) fighter-coords))
      (should= {:type :land :contents {:type :fighter :owner :player :hits 1 :steps-remaining 0 :mode :awake :fuel 9}} (get-in (test-utils/read-test-state :game-map) target-coords))))

  (it "fighter wakes when fuel reaches 0"
    (set-test-world! (build-test-map ["-F#"]))
    (let [fighter-coords (:pos (get-test-unit (test-utils/game-map-atom) "F"))
          target-coords [(inc (first fighter-coords)) (second fighter-coords)]]
      (set-test-unit (test-utils/game-map-atom) "F" :mode :moving :target target-coords :fuel 1 :steps-remaining 1)
      (set-test-player-map! (make-initial-test-map 1 3 nil))
      (game-loop/move-current-unit fighter-coords)
      (should= {:type :land} (get-in (test-utils/read-test-state :game-map) fighter-coords))
      (should= {:type :land :contents {:type :fighter :owner :player :hits 1 :steps-remaining 0 :mode :awake :fuel 0 :reason :fighter-out-of-fuel}} (get-in (test-utils/read-test-state :game-map) target-coords))))

  (it "fighter crashes when trying to move with 0 fuel"
    (set-test-world! (build-test-map ["-F#"]))
    (let [fighter-coords (:pos (get-test-unit (test-utils/game-map-atom) "F"))
          target-coords [(inc (first fighter-coords)) (second fighter-coords)]]
      (set-test-unit (test-utils/game-map-atom) "F" :mode :moving :target target-coords :fuel 0 :steps-remaining 1)
      (set-test-player-map! (make-initial-test-map 1 3 nil))
      (game-loop/move-current-unit fighter-coords)
      (should= {:type :land} (get-in (test-utils/read-test-state :game-map) fighter-coords))
      (should= {:type :land} (get-in (test-utils/read-test-state :game-map) target-coords))
      (should= 2 (count (filter (complement nil?) (flatten (test-utils/read-test-state :game-map)))))))

  (it "fighter lands in city, refuels, and awakens"
    (set-test-world! (build-test-map ["-FO"]))
    (let [fighter-coords (:pos (get-test-unit (test-utils/game-map-atom) "F"))
          city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))]
      (set-test-unit (test-utils/game-map-atom) "F" :mode :moving :target city-coords :fuel 5 :steps-remaining 1)
      (set-test-player-map! (make-initial-test-map 1 3 nil))
      (game-loop/move-current-unit fighter-coords)
      (should= {:type :land} (get-in (test-utils/read-test-state :game-map) fighter-coords))
      (let [city-cell (get-in (test-utils/read-test-state :game-map) city-coords)]
        (should= :city (:type city-cell))
        (should= :player (:city-status city-cell))
        (should= 1 (:fighter-count city-cell))
        (should= 0 (:awake-fighters city-cell 0)))))

  (it "fighter safely lands at friendly city"
    (set-test-world! (build-test-map ["-FO"]))
    (let [fighter-coords (:pos (get-test-unit (test-utils/game-map-atom) "F"))
          city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))]
      (set-test-unit (test-utils/game-map-atom) "F" :mode :moving :target city-coords :fuel 10 :steps-remaining 1)
      (set-test-player-map! (make-initial-test-map 1 3 nil))
      (test-utils/set-test-state! :error-message "")
      (game-loop/move-current-unit fighter-coords)
      (let [city-cell (get-in (test-utils/read-test-state :game-map) city-coords)]
        (should= 1 (:fighter-count city-cell))
        (should= 0 (:awake-fighters city-cell 0)))
      (should= "" (test-utils/read-test-state :error-message))))

  (it "fighter wakes before flying over free city"
    (set-test-world! (build-test-map ["F+#"]))
    (let [fighter-coords (:pos (get-test-unit (test-utils/game-map-atom) "F"))
          city-coords (:pos (get-test-city (test-utils/game-map-atom) "+"))
          target-coords [(+ (first fighter-coords) 2) (second fighter-coords)]]
      (set-test-unit (test-utils/game-map-atom) "F" :mode :moving :target target-coords :fuel 10 :steps-remaining 1)
      (set-test-player-map! (make-initial-test-map 1 3 nil))
      (game-loop/move-current-unit fighter-coords)
      (let [fighter (:contents (get-in (test-utils/read-test-state :game-map) fighter-coords))]
        (should= :fighter (:type fighter))
        (should= :awake (:mode fighter))
        (should= :fighter-over-defended-city (:reason fighter)))
      (should= nil (:contents (get-in (test-utils/read-test-state :game-map) city-coords)))))

  (it "fighter wakes before flying over computer city"
    (set-test-world! (build-test-map ["FX#"]))
    (let [fighter-coords (:pos (get-test-unit (test-utils/game-map-atom) "F"))
          city-coords (:pos (get-test-city (test-utils/game-map-atom) "X"))
          target-coords [(+ (first fighter-coords) 2) (second fighter-coords)]]
      (set-test-unit (test-utils/game-map-atom) "F" :mode :moving :target target-coords :fuel 10 :steps-remaining 1)
      (set-test-player-map! (make-initial-test-map 1 3 nil))
      (game-loop/move-current-unit fighter-coords)
      (let [fighter (:contents (get-in (test-utils/read-test-state :game-map) fighter-coords))]
        (should= :fighter (:type fighter))
        (should= :awake (:mode fighter))
        (should= :fighter-over-defended-city (:reason fighter)))
      (should= nil (:contents (get-in (test-utils/read-test-state :game-map) city-coords)))))

  (it "fighter wakes with bingo warning when fuel at 25% and friendly city in range"
    (set-test-world! (build-test-map ["O--F#-#"]))
    (let [fighter-coords (:pos (get-test-unit (test-utils/game-map-atom) "F"))
          target-coords [6 (second fighter-coords)]
          dest-coords [(inc (first fighter-coords)) (second fighter-coords)]]
      (set-test-unit (test-utils/game-map-atom) "F" :mode :moving :target target-coords :fuel 8 :steps-remaining 1)
      (set-test-player-map! (make-initial-test-map 1 7 nil))
      (game-loop/move-current-unit fighter-coords)
      (let [fighter (:contents (get-in (test-utils/read-test-state :game-map) dest-coords))]
        (should= :fighter (:type fighter))
        (should= :awake (:mode fighter))
        (should= :fighter-bingo (:reason fighter)))))

  (it "fighter does not wake with bingo warning when no friendly city in range"
    (set-test-world! (build-test-map ["O--------"
                                      "---------"
                                      "---------"
                                      "---------"
                                      "----F#---"]))
    (let [fighter-coords (:pos (get-test-unit (test-utils/game-map-atom) "F"))
          target-coords [(inc (first fighter-coords)) (second fighter-coords)]]
      (set-test-unit (test-utils/game-map-atom) "F" :mode :moving :target target-coords :fuel 3 :steps-remaining 1)
      (set-test-player-map! (make-initial-test-map 5 9 nil))
      (game-loop/move-current-unit fighter-coords)
      (let [fighter (:contents (get-in (test-utils/read-test-state :game-map) target-coords))]
        (should= :fighter (:type fighter))
        (should= :awake (:mode fighter))
        (should= nil (:reason fighter)))))

  (it "fighter does not wake with bingo warning when fuel above 25%"
    (set-test-world! (build-test-map ["O--F#"]))
    (let [fighter-coords (:pos (get-test-unit (test-utils/game-map-atom) "F"))
          target-coords [(inc (first fighter-coords)) (second fighter-coords)]]
      (set-test-unit (test-utils/game-map-atom) "F" :mode :moving :target target-coords :fuel 10 :steps-remaining 1)
      (set-test-player-map! (make-initial-test-map 1 5 nil))
      (game-loop/move-current-unit fighter-coords)
      (let [fighter (:contents (get-in (test-utils/read-test-state :game-map) target-coords))]
        (should= :fighter (:type fighter))
        (should= :awake (:mode fighter))
        (should= nil (:reason fighter)))))

  (it "fighter does not wake with bingo when target is a reachable friendly city"
    (set-test-world! (build-test-map ["F#-O"]))
    (let [fighter-coords (:pos (get-test-unit (test-utils/game-map-atom) "F"))
          city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))
          dest-coords [(inc (first fighter-coords)) (second fighter-coords)]]
      (set-test-unit (test-utils/game-map-atom) "F" :mode :moving :target city-coords :fuel 8 :steps-remaining 1)
      (set-test-player-map! (make-initial-test-map 1 4 nil))
      (game-loop/move-current-unit fighter-coords)
      (let [fighter (:contents (get-in (test-utils/read-test-state :game-map) dest-coords))]
        (should= :fighter (:type fighter))
        (should= :moving (:mode fighter))
        (should= nil (:reason fighter)))))

  (it "fighter does not wake with bingo when target is a reachable friendly carrier"
    (set-test-world! (build-test-map ["O---"
                                      "F~C-"]))
    (let [fighter-coords (:pos (get-test-unit (test-utils/game-map-atom) "F"))
          carrier-coords (:pos (get-test-unit (test-utils/game-map-atom) "C"))
          dest-coords [(inc (first fighter-coords)) (second fighter-coords)]]
      (set-test-unit (test-utils/game-map-atom) "F" :mode :moving :target carrier-coords :fuel 8 :steps-remaining 1)
      (set-test-unit (test-utils/game-map-atom) "C" :mode :sentry)
      (set-test-player-map! (make-initial-test-map 2 4 nil))
      (game-loop/move-current-unit fighter-coords)
      (let [fighter (:contents (get-in (test-utils/read-test-state :game-map) dest-coords))]
        (should= :fighter (:type fighter))
        (should= :moving (:mode fighter))
        (should= nil (:reason fighter)))))

  (it "fighter does not wake with bingo when a friendly city is on its current path"
    (set-test-world! (build-test-map ["F---O-"]))
    (let [fighter-coords (:pos (get-test-unit (test-utils/game-map-atom) "F"))
          dest-coords [(inc (first fighter-coords)) (second fighter-coords)]
          target-coords [5 0]]
      (set-test-unit (test-utils/game-map-atom) "F" :mode :moving :target target-coords :fuel 9 :steps-remaining 1)
      (set-test-player-map! (make-initial-test-map 1 6 nil))
      (game-loop/move-current-unit fighter-coords)
      (let [fighter (:contents (get-in (test-utils/read-test-state :game-map) dest-coords))]
        (should= :fighter (:type fighter))
        (should= :moving (:mode fighter))
        (should= nil (:reason fighter)))))

  (it "fighter wakes with bingo when carrier is too far to reach"
    (set-test-world! (build-test-map ["O---------"
                                      "F~------C-"]))
    (let [fighter-coords (:pos (get-test-unit (test-utils/game-map-atom) "F"))
          carrier-coords (:pos (get-test-unit (test-utils/game-map-atom) "C"))
          dest-coords [(inc (first fighter-coords)) (second fighter-coords)]]
      (set-test-unit (test-utils/game-map-atom) "F" :mode :moving :target carrier-coords :fuel 6 :steps-remaining 1)
      (set-test-unit (test-utils/game-map-atom) "C" :mode :sentry)
      (set-test-player-map! (make-initial-test-map 2 10 nil))
      (game-loop/move-current-unit fighter-coords)
      (let [fighter (:contents (get-in (test-utils/read-test-state :game-map) dest-coords))]
        (should= :fighter (:type fighter))
        (should= :awake (:mode fighter))
        (should= :fighter-bingo (:reason fighter))))))

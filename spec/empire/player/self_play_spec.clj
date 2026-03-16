(ns empire.player.self-play-spec
  (:require [empire.player.self-play :as self-play]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-player-map! set-test-state! set-test-unit set-test-world!]]
            [speclj.core :refer :all]))

(describe "player self-play"
  (before (reset-all-atoms!))

  (it "processes player items with computer logic without leaving attention state behind"
    (set-test-world! (build-test-map ["A"]))
    (set-test-player-map! (build-test-map ["#"]))
    (set-test-computer-map! (build-test-map ["#"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :awake :owner :player)
    (set-test-state! :player-items [[0 0]])
    (set-test-state! :computer-items [[9 9]])
    (set-test-state! :waiting-for-input true)
    (set-test-state! :cells-needing-attention [[0 0]])
    (self-play/process-player-items-batch!)
    (should-not (test-utils/read-test-state :waiting-for-input))
    (should= [] (test-utils/read-test-state :cells-needing-attention))
    (should= [] (vec (test-utils/read-test-state :player-items)))
    (should= [[9 9]] (vec (test-utils/read-test-state :computer-items)))
    (should= :player (get-in (test-utils/read-test-world) [0 0 :contents :owner]))))

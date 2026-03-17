(ns empire.player.self-play-spec
  (:require [empire.player.self-play :as self-play]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-player-map! set-test-state! set-test-unit set-test-world!]]
            [speclj.core :refer :all]))

(describe "player self-play"
  (before (reset-all-atoms!))

  (it "does nothing when there are no player items"
    (set-test-state! :player-items [])
    (set-test-state! :waiting-for-input true)
    (self-play/process-player-items-batch!)
    (should (test-utils/read-test-state :waiting-for-input)))

  (it "mirrors owners and city status across the world grid"
    (let [grid [[{:type :city
                  :city-status :player
                  :contents {:type :army :owner :player}}
                 {:type :city
                  :city-status :computer
                  :contents {:type :fighter :owner :computer}}]]]
      (should= [[{:type :city
                  :city-status :computer
                  :contents {:type :army :owner :computer}}
                 {:type :city
                  :city-status :player
                  :contents {:type :fighter :owner :player}}]]
               (#'self-play/mirror-grid grid))))

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
    (should= :player (get-in (test-utils/read-test-world) [0 0 :contents :owner])))

  (it "restores the world and scratch state if mirrored processing throws"
    (set-test-world! (build-test-map ["A"]))
    (set-test-player-map! (build-test-map ["#"]))
    (set-test-computer-map! (build-test-map ["#"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :awake :owner :player)
    (set-test-state! :player-items [[0 0]])
    (set-test-state! :computer-items [[9 9]])
    (set-test-state! :claimed-objectives {:before true})
    (with-redefs [empire.computer.production/rebuild-country-stats! (fn [] nil)
                  empire.computer.army/assign-city-attacks (fn [] nil)
                  empire.game.loop.item-processing.computer-items/process-computer-items
                  (fn []
                    (set-test-state! :claimed-objectives {:during true})
                    (throw (ex-info "boom" {})))]
      (should-throw clojure.lang.ExceptionInfo
        (self-play/process-player-items-batch!)))
    (should= :player (get-in (test-utils/read-test-world) [0 0 :contents :owner]))
    (should= {:before true} (test-utils/read-test-state :claimed-objectives))
    (should= [[0 0]] (vec (test-utils/read-test-state :player-items)))))

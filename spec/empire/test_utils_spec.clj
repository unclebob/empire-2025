(ns empire.test-utils-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]))

(describe "test utils map updates"
  (before (test-utils/reset-all-atoms!))

  (it "updates the game map when the source is :game-map"
    (test-utils/set-test-world! (test-utils/build-test-map ["~"]))
    (@#'test-utils/update-map-atom! :game-map assoc-in [0 0 :marker] :ok)
    (should= :ok (get-in (test-utils/read-test-state :game-map) [0 0 :marker])))

  (it "updates the player map when the source is :player-map"
    (test-utils/set-test-player-map! (test-utils/build-test-map ["#"]))
    (@#'test-utils/update-map-atom! :player-map assoc-in [0 0 :marker] :ok)
    (should= :ok (get-in (test-utils/read-test-state :player-map) [0 0 :marker])))

  (it "updates the computer map when the source is :computer-map"
    (test-utils/set-test-computer-map! (test-utils/build-test-map ["#"]))
    (@#'test-utils/update-map-atom! :computer-map assoc-in [0 0 :marker] :ok)
    (should= :ok (get-in (test-utils/read-test-state :computer-map) [0 0 :marker])))

  (it "updates a raw atom source through swap!"
    (let [m (atom {:n 1})]
      (@#'test-utils/update-map-atom! m update :n inc)
      (should= 2 (:n @m))))

  (it "treats the game-map accessor token like the world map"
    (test-utils/set-test-world! (test-utils/build-test-map ["~"]))
    (@#'test-utils/update-map-atom! (test-utils/game-map-atom) assoc-in [0 0 :marker] :ok)
    (should= :ok (get-in (test-utils/read-test-state :game-map) [0 0 :marker])))

  (it "treats the player-map accessor token like the player map"
    (test-utils/set-test-player-map! (test-utils/build-test-map ["#"]))
    (@#'test-utils/update-map-atom! (test-utils/player-map-atom) assoc-in [0 0 :marker] :ok)
    (should= :ok (get-in (test-utils/read-test-state :player-map) [0 0 :marker])))

  (it "treats the computer-map accessor token like the computer map"
    (test-utils/set-test-computer-map! (test-utils/build-test-map ["#"]))
    (@#'test-utils/update-map-atom! (test-utils/computer-map-atom) assoc-in [0 0 :marker] :ok)
    (should= :ok (get-in (test-utils/read-test-state :computer-map) [0 0 :marker]))))

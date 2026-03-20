(ns empire.game-mechanics.movement.add-unit-spec
  (:require [empire.test.utils :as test-utils]
    [empire.config.core :as config]
    [empire.game.loop.core :as game-loop]
    [empire.game-mechanics.movement.explore :as explore]
    [empire.game-mechanics.movement.api :refer :all]
    [empire.game-mechanics.visibility :as visibility]
    [empire.game-mechanics.movement.wake-conditions :as wake]
    [empire.test.utils :refer [build-test-map get-test-unit set-test-unit reset-all-atoms! set-test-player-map! set-test-world! update-test-world!]]
    [speclj.core :refer :all]))
(describe "add-unit-at"
  (before
    (reset-all-atoms!)
    (set-test-world! (build-test-map ["###" "###" "###"])))

  (it "adds army unit at empty cell"
    (add-unit-at [1 1] :army)
    (let [contents (get-in (test-utils/read-test-state :game-map) [1 1 :contents])]
      (should= :army (:type contents))
      (should= :player (:owner contents))
      (should= :awake (:mode contents))
      (should= (config/item-hits :army) (:hits contents))))

  (it "adds fighter with fuel"
    (add-unit-at [1 1] :fighter)
    (let [contents (get-in (test-utils/read-test-state :game-map) [1 1 :contents])]
      (should= :fighter (:type contents))
      (should= config/fighter-fuel (:fuel contents))))

  (it "does not add unit if cell has contents"
    (update-test-world! assoc-in [1 1 :contents] {:type :army :owner :computer})
    (add-unit-at [1 1] :carrier)
    (should= :army (get-in (test-utils/read-test-state :game-map) [1 1 :contents :type])))

  (it "adds computer-owned army when owner is :computer"
    (add-unit-at [1 1] :army :computer)
    (let [contents (get-in (test-utils/read-test-state :game-map) [1 1 :contents])]
      (should= :army (:type contents))
      (should= :computer (:owner contents))
      (should= :awake (:mode contents))))

  (it "adds computer-owned destroyer when owner is :computer"
    (add-unit-at [2 2] :destroyer :computer)
    (let [contents (get-in (test-utils/read-test-state :game-map) [2 2 :contents])]
      (should= :destroyer (:type contents))
      (should= :computer (:owner contents))))

  (it "defaults to player owner when not specified"
    (add-unit-at [0 0] :transport)
    (should= :player (get-in (test-utils/read-test-state :game-map) [0 0 :contents :owner]))))

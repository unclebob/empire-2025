(ns empire.game-loop.round-setup-satellites-spec
  (:require [empire.game-mechanics.movement.satellite :as satellite]
            [empire.game-mechanics.movement.visibility :as visibility]
            [empire.game-mechanics.movement.wake-conditions :as wake]
            [empire.game.loop.round-setup :as setup]
            [empire.game.loop.round-setup.lakes :as lakes]
            [empire.game.loop.round-setup.waking :as waking]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-unit set-test-world! update-test-world!]]
            [speclj.core :refer :all]))
(describe "move-satellites"
  (before (reset-all-atoms!))

  (it "moves satellite with turns-remaining"
    (let [game-map (build-test-map ["V###########"])]
      (set-test-world! game-map)
      (set-test-unit (test-utils/game-map-atom) "V" :turns-remaining 10 :target [0 11])
      (let [move-count (atom 0)]
        (with-redefs [satellite/move-satellite
                      (fn [coords]
                        (swap! move-count inc)
                        (let [[r c] coords
                              cell (get-in (test-utils/read-test-state :game-map) coords)
                              sat (:contents cell)
                              new-c (inc c)
                              new-coords [r new-c]]
                          (update-test-world! assoc-in [r c :contents] nil)
                          (update-test-world! assoc-in (conj new-coords :contents) sat)
                          new-coords))
                      visibility/update-cell-visibility (fn [_ _])]
          (setup/move-satellites)
          (should= 10 @move-count)))))

  (it "removes expired satellite with turns-remaining=0"
    (let [game-map (build-test-map ["V"])]
      (set-test-world! game-map)
      (set-test-unit (test-utils/game-map-atom) "V" :turns-remaining 0)
      (with-redefs [visibility/update-cell-visibility (fn [_ _])]
        (setup/move-satellites))
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [0 0 :contents]))))

  (it "decrements turns-remaining after all steps"
    (let [game-map (build-test-map ["V###########"])]
      (set-test-world! game-map)
      (set-test-unit (test-utils/game-map-atom) "V" :turns-remaining 5 :target [0 11])
      (with-redefs [satellite/move-satellite
                    (fn [coords]
                      (let [[r c] coords
                            cell (get-in (test-utils/read-test-state :game-map) coords)
                            sat (:contents cell)
                            new-c (inc c)
                            new-coords [r new-c]]
                        (update-test-world! assoc-in [r c :contents] nil)
                        (update-test-world! assoc-in (conj new-coords :contents) sat)
                        new-coords))
                    visibility/update-cell-visibility (fn [_ _])]
        (setup/move-satellites)
        (let [sat-pos [0 10]
              sat (get-in (test-utils/read-test-state :game-map) (conj sat-pos :contents))]
          (should= 4 (:turns-remaining sat))))))

  (it "removes satellite when turns-remaining reaches 0 after steps"
    (let [game-map (build-test-map ["V###########"])]
      (set-test-world! game-map)
      (set-test-unit (test-utils/game-map-atom) "V" :turns-remaining 1 :target [0 11])
      (with-redefs [satellite/move-satellite
                    (fn [coords]
                      (let [[r c] coords
                            cell (get-in (test-utils/read-test-state :game-map) coords)
                            sat (:contents cell)
                            new-c (inc c)
                            new-coords [r new-c]]
                        (update-test-world! assoc-in [r c :contents] nil)
                        (update-test-world! assoc-in (conj new-coords :contents) sat)
                        new-coords))
                    visibility/update-cell-visibility (fn [_ _])]
        (setup/move-satellites)
        (should-be-nil (get-in (test-utils/read-test-state :game-map) [0 10 :contents])))))

  (it "does not move satellite with turns-remaining=0"
    (let [game-map (build-test-map ["V##"])]
      (set-test-world! game-map)
      (set-test-unit (test-utils/game-map-atom) "V" :turns-remaining 0)
      (let [move-count (atom 0)]
        (with-redefs [satellite/move-satellite
                      (fn [coords] (swap! move-count inc) coords)
                      visibility/update-cell-visibility (fn [_ _])]
          (setup/move-satellites)
          (should= 0 @move-count)))))

  (it "satellite with turns-remaining=1 still moves before expiring"
    (let [game-map (build-test-map ["V###########"])]
      (set-test-world! game-map)
      (set-test-unit (test-utils/game-map-atom) "V" :turns-remaining 1 :target [0 11])
      (let [move-count (atom 0)]
        (with-redefs [satellite/move-satellite
                      (fn [coords]
                        (swap! move-count inc)
                        (let [[r c] coords
                              cell (get-in (test-utils/read-test-state :game-map) coords)
                              sat (:contents cell)
                              new-coords [r (inc c)]]
                          (update-test-world! assoc-in [r c :contents] nil)
                          (update-test-world! assoc-in (conj new-coords :contents) sat)
                          new-coords))
                      visibility/update-cell-visibility (fn [_ _])]
          (setup/move-satellites)
          (should= 10 @move-count)))))

  (it "satellite with turns-remaining=2 survives with 1 turn left"
    (let [game-map (build-test-map ["V###########"])]
      (set-test-world! game-map)
      (set-test-unit (test-utils/game-map-atom) "V" :turns-remaining 2 :target [0 11])
      (with-redefs [satellite/move-satellite
                    (fn [coords]
                      (let [[r c] coords
                            cell (get-in (test-utils/read-test-state :game-map) coords)
                            sat (:contents cell)
                            new-coords [r (inc c)]]
                        (update-test-world! assoc-in [r c :contents] nil)
                        (update-test-world! assoc-in (conj new-coords :contents) sat)
                        new-coords))
                    visibility/update-cell-visibility (fn [_ _])]
        (setup/move-satellites)
        (let [sat (get-in (test-utils/read-test-state :game-map) [0 10 :contents])]
          (should-not-be-nil sat)
          (should= 1 (:turns-remaining sat)))))))


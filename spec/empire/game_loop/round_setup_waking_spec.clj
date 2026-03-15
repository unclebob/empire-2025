(ns empire.game-loop-round-setup-waking-spec
  (:require [empire.game-mechanics.movement.satellite :as satellite]
            [empire.game-mechanics.movement.visibility :as visibility]
            [empire.game-mechanics.movement.wake-conditions :as wake]
            [empire.game.loop.round-setup :as setup]
            [empire.game.loop.round-setup.lakes :as lakes]
            [empire.game.loop.round-setup.waking :as waking]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-unit set-test-world! update-test-world!]]
            [speclj.core :refer :all]))

(describe "wake-airport-fighters"
  (before (reset-all-atoms!))

  (it "wakes fighters in player city airport"
    (let [game-map (build-test-map ["O"])]
      (set-test-world! game-map)
      (update-test-world! assoc-in [0 0 :fighter-count] 3)
      (waking/wake-airport-fighters)
      (should= 3 (get-in (test-utils/read-test-state :game-map) [0 0 :awake-fighters]))))

  (it "does NOT wake fighters in computer city"
    (let [game-map (build-test-map ["X"])]
      (set-test-world! game-map)
      (update-test-world! assoc-in [0 0 :fighter-count] 2)
      (waking/wake-airport-fighters)
      (should= 0 (get-in (test-utils/read-test-state :game-map) [0 0 :awake-fighters] 0))))

  (it "does nothing when city has no fighters"
    (let [game-map (build-test-map ["O"])]
      (set-test-world! game-map)
      (waking/wake-airport-fighters)
      (should= 0 (get-in (test-utils/read-test-state :game-map) [0 0 :awake-fighters] 0)))))

(describe "wake-carrier-fighters"
  (before (reset-all-atoms!))

  (it "wakes fighters on player carrier"
    (let [game-map (build-test-map ["C"])]
      (set-test-world! game-map)
      (update-test-world! assoc-in [0 0 :contents :fighter-count] 4)
      (waking/wake-carrier-fighters)
      (should= 4 (get-in (test-utils/read-test-state :game-map) [0 0 :contents :awake-fighters]))))

  (it "does NOT wake fighters on computer carrier"
    (let [game-map (build-test-map ["c"])]
      (set-test-world! game-map)
      (update-test-world! assoc-in [0 0 :contents :fighter-count] 3)
      (waking/wake-carrier-fighters)
      (should= 0 (get-in (test-utils/read-test-state :game-map) [0 0 :contents :awake-fighters] 0))))

  (it "does nothing when carrier has no fighters"
    (let [game-map (build-test-map ["C"])]
      (set-test-world! game-map)
      (waking/wake-carrier-fighters)
      (should= 0 (get-in (test-utils/read-test-state :game-map) [0 0 :contents :awake-fighters] 0)))))

(describe "wake-sentries-seeing-enemy"
  (before (reset-all-atoms!))

  (it "wakes player sentry that sees enemy"
    (let [game-map (build-test-map ["Da"])]
      (set-test-world! game-map)
      (set-test-unit (test-utils/game-map-atom) "D" :mode :sentry)
      (with-redefs [wake/enemy-unit-visible? (fn [_ _ _] true)]
        (waking/wake-sentries-seeing-enemy))
      (should= :awake (get-in (test-utils/read-test-state :game-map) [0 0 :contents :mode]))
      (should= :enemy-spotted (get-in (test-utils/read-test-state :game-map) [0 0 :contents :reason]))))

  (it "does NOT wake sentry that sees no enemy"
    (let [game-map (build-test-map ["D~"])]
      (set-test-world! game-map)
      (set-test-unit (test-utils/game-map-atom) "D" :mode :sentry)
      (with-redefs [wake/enemy-unit-visible? (fn [_ _ _] false)]
        (waking/wake-sentries-seeing-enemy))
      (should= :sentry (get-in (test-utils/read-test-state :game-map) [0 0 :contents :mode]))))

  (it "does NOT wake computer sentries"
    (let [game-map (build-test-map ["dA"])]
      (set-test-world! game-map)
      (set-test-unit (test-utils/game-map-atom) "d" :mode :sentry)
      (with-redefs [wake/enemy-unit-visible? (fn [_ _ _] true)]
        (waking/wake-sentries-seeing-enemy))
      (should= :sentry (get-in (test-utils/read-test-state :game-map) [0 0 :contents :mode])))))

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

(describe "evacuate-lake-patrol-boats"
  (before (reset-all-atoms!))

  (it "moves patrol boat from computer lake-shore city to adjacent sea"
    (let [world (build-test-map ["~~~"
                                 "~X~"
                                 "~~~"])
          computer-map (build-test-map ["~~~"
                                        "~X~"
                                        "~~~"])]
      (set-test-world! world)
      (set-test-computer-map! computer-map)
      (test-utils/set-test-state! :lake-max-cells 10)
      (update-test-world! assoc-in [1 1 :contents]
                         {:type :patrol-boat :owner :computer :hits 1 :mode :awake})
      (lakes/evacuate-lake-patrol-boats)
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [1 1 :contents]))
      (let [sea-neighbors (for [pos [[0 0] [0 1] [0 2] [1 0] [1 2] [2 0] [2 1] [2 2]]
                                :let [u (get-in (test-utils/read-test-state :game-map) (conj pos :contents))]
                                :when (= :patrol-boat (:type u))]
                            pos)]
        (should= 1 (count sea-neighbors)))))

  (it "leaves patrol boat in city when no adjacent sea is empty"
    (let [world (build-test-map ["~~~"
                                 "~X~"
                                 "~~~"])
          computer-map (build-test-map ["~~~"
                                        "~X~"
                                        "~~~"])]
      (set-test-world! world)
      (set-test-computer-map! computer-map)
      (test-utils/set-test-state! :lake-max-cells 10)
      (update-test-world! assoc-in [1 1 :contents]
                         {:type :patrol-boat :owner :computer :hits 1 :mode :awake})
      (doseq [pos [[0 0] [0 1] [0 2] [1 0] [1 2] [2 0] [2 1] [2 2]]]
        (update-test-world! assoc-in (conj pos :contents)
                           {:type :transport :owner :computer :hits 1 :mode :awake}))
      (lakes/evacuate-lake-patrol-boats)
      (should= :patrol-boat (get-in (test-utils/read-test-state :game-map) [1 1 :contents :type]))
      (should= :computer (get-in (test-utils/read-test-state :game-map) [1 1 :contents :owner]))))

  (it "moves transport out of computer lake-shore city and preserves cargo state"
    (let [world (build-test-map ["~~~"
                                 "~X~"
                                 "~~~"])
          computer-map (build-test-map ["~~~"
                                        "~X~"
                                        "~~~"])]
      (set-test-world! world)
      (set-test-computer-map! computer-map)
      (test-utils/set-test-state! :lake-max-cells 10)
      (update-test-world! assoc-in [1 1 :contents]
                         {:type :transport
                          :owner :computer
                          :hits 1
                          :mode :awake
                          :army-count 3
                          :transport-mission :invading})
      (lakes/evacuate-lake-patrol-boats)
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [1 1 :contents]))
      (let [moved (first (for [pos [[0 0] [0 1] [0 2] [1 0] [1 2] [2 0] [2 1] [2 2]]
                               :let [u (get-in (test-utils/read-test-state :game-map) (conj pos :contents))]
                               :when (= :transport (:type u))]
                           u))]
        (should-not-be-nil moved)
        (should= 3 (:army-count moved))
        (should= :land-locked (:transport-mission moved))
        (should= true (:never-reload? moved)))))

  (it "prefers ocean sea over lake sea when both are adjacent"
    (let [world [[{:type :sea}
                  {:type :city :city-status :computer
                   :contents {:type :patrol-boat :owner :computer :hits 1}}
                  {:type :sea}]]
          computer-map world]
      (set-test-world! world)
      (set-test-computer-map! computer-map)
      (with-redefs [empire.game-mechanics.movement.lakes/lake-cells (fn [_ _] #{[0 0]})
                    visibility/update-cell-visibility (fn [_ _] nil)]
        (lakes/evacuate-lake-patrol-boats))
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [0 1 :contents]))
      (should= :patrol-boat (get-in (test-utils/read-test-state :game-map) [0 2 :contents :type])))))

(describe "lake discovery army retask"
  (before (reset-all-atoms!))

  (it "wakes and retasks all computer armies on lake-adjacent landmass"
    (let [world (build-test-map ["~~~~~"
                                 "#####"
                                 "##~##"
                                 "#####"
                                 "#####"])]
      (set-test-world! world)
      (set-test-computer-map! world)
      (test-utils/set-test-state! :lake-max-cells 2)
      (test-utils/set-test-state! :known-lake-cells #{})
      (update-test-world! assoc-in [0 4 :country-id] 1)
      (update-test-world! assoc-in [4 4 :country-id] 1)
      (update-test-world! assoc-in [0 4 :contents]
                         {:type :army :owner :computer :hits 1 :mode :sentry :country-id 1})
      (update-test-world! assoc-in [4 4 :contents]
                         {:type :army :owner :computer :hits 1 :mode :awake :country-id 1})
      (lakes/mark-lake-locked-ships)
      (should= :move-to-coast-for-invasion (get-in (test-utils/read-test-state :game-map) [0 4 :contents :mode]))
      (should= :move-to-coast-for-invasion (get-in (test-utils/read-test-state :game-map) [4 4 :contents :mode]))
      (should (vector? (get-in (test-utils/read-test-state :game-map) [0 4 :contents :coast-target])))
      (should (vector? (get-in (test-utils/read-test-state :game-map) [4 4 :contents :coast-target])))
      (should= #{[2 2]} (test-utils/read-test-state :known-lake-cells))))

  (it "does not retask again when no newly discovered lake cells"
    (let [world (build-test-map ["###"
                                 "#~#"
                                 "###"])]
      (set-test-world! world)
      (set-test-computer-map! world)
      (test-utils/set-test-state! :lake-max-cells 10)
      (test-utils/set-test-state! :known-lake-cells #{[1 1]})
      (update-test-world! assoc-in [0 0 :country-id] 1)
      (update-test-world! assoc-in [0 0 :contents]
                         {:type :army :owner :computer :hits 1 :mode :sentry :country-id 1})
      (lakes/mark-lake-locked-ships)
      (should= :sentry (get-in (test-utils/read-test-state :game-map) [0 0 :contents :mode]))))

  (it "removes :lake-locked? from ships no longer in a lake"
    (let [world (build-test-map ["d~"])]
      (set-test-world! world)
      (set-test-computer-map! world)
      (test-utils/set-test-state! :lake-max-cells 1)
      (test-utils/set-test-state! :known-lake-cells #{})
      (update-test-world! assoc-in [0 0 :contents :lake-locked?] true)
      (lakes/mark-lake-locked-ships)
      (should= false (contains? (get-in (test-utils/read-test-state :game-map) [0 0 :contents]) :lake-locked?))))

  (it "marks empty transport in lake as never-reload without forcing mission"
    (let [world (build-test-map ["~t~"])]
      (set-test-world! world)
      (set-test-computer-map! world)
      (test-utils/set-test-state! :lake-max-cells 10)
      (test-utils/set-test-state! :known-lake-cells #{})
      (update-test-world! assoc-in [1 0 :contents :army-count] 0)
      (lakes/mark-lake-locked-ships)
      (should= true (get-in (test-utils/read-test-state :game-map) [1 0 :contents :lake-locked?]))
      (should= true (get-in (test-utils/read-test-state :game-map) [1 0 :contents :never-reload?]))
      (should-not= :land-locked (get-in (test-utils/read-test-state :game-map) [1 0 :contents :transport-mission])))))

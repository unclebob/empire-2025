(ns empire.game-loop.round-setup-lake-discovery-spec
  (:require [empire.game-mechanics.movement.satellite :as satellite]
            [empire.game-mechanics.movement.visibility :as visibility]
            [empire.game-mechanics.movement.wake-conditions :as wake]
            [empire.game.loop.round-setup :as setup]
            [empire.game.loop.round-setup.lakes :as lakes]
            [empire.game.loop.round-setup.waking :as waking]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-unit set-test-world! update-test-world!]]
            [speclj.core :refer :all]))
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

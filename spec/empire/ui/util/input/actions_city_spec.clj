(ns empire.ui.util.input.actions-city-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.ui.util.input.actions :as actions]
            [empire.ui.util.input.actions.movement :as actions-movement]
            [empire.ui.util.input.dispatch :as dispatch]
            [empire.config.core :as config]
            [empire.game.loop.core :as game-loop]
            [empire.game-mechanics.services.combat :as combat]
            [empire.player.orders :as orders]
            [empire.player.production :as production]
            [empire.test.utils :refer [build-test-map get-test-city get-test-unit set-test-unit reset-all-atoms!
                                       set-test-world! update-test-world!]]))
(describe "handle-key production on city"
  (before (reset-all-atoms!))

  (it "sets army production on player city"
    (set-test-world! (build-test-map ["O"]))
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))]
      (test-utils/set-test-state! :cells-needing-attention [city-coords])
      (test-utils/set-test-state! :player-items [city-coords])
      (test-utils/set-test-state! :waiting-for-input true)
      (actions/handle-key :a)
      (should= :army (:item (get (test-utils/read-test-state :production) city-coords)))))

  (it "rejects naval production on non-coastal city"
    (set-test-world! (build-test-map ["###"
                                             "#O#"
                                             "###"]))
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))]
      (test-utils/set-test-state! :cells-needing-attention [city-coords])
      (test-utils/set-test-state! :player-items [city-coords])
      (test-utils/set-test-state! :waiting-for-input true)
      (test-utils/set-test-state! :warning-message "")
      (actions/handle-key :d)
      ;; Should show error message about coastal city
      (should-contain "coastal" (test-utils/read-test-state :warning-message))
      ;; Should NOT set production
      (should-be-nil (get (test-utils/read-test-state :production) city-coords))))

  (it "allows naval production on coastal city"
    (set-test-world! (build-test-map ["~O#"]))
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))]
      (test-utils/set-test-state! :cells-needing-attention [city-coords])
      (test-utils/set-test-state! :player-items [city-coords])
      (test-utils/set-test-state! :waiting-for-input true)
      (actions/handle-key :d)
      (should= :destroyer (:item (get (test-utils/read-test-state :production) city-coords)))))

  (it "clears production with :x key"
    (set-test-world! (build-test-map ["O"]))
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))]
      ;; Set initial production
      (production/set-city-production city-coords :army)
      (should= :army (:item (get (test-utils/read-test-state :production) city-coords)))
      ;; Now press :x to clear
      (test-utils/set-test-state! :cells-needing-attention [city-coords])
      (test-utils/set-test-state! :player-items [city-coords])
      (test-utils/set-test-state! :waiting-for-input true)
      (actions/handle-key :x)
      (should= :none (get (test-utils/read-test-state :production) city-coords)))))

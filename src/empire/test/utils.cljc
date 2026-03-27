(ns empire.test.utils
  (:require [empire.test.state :as state]
            [empire.test.builders :as builders]))

;; --- state re-exports ---

(def read-test-state state/read-test-state)
(def set-test-state! state/set-test-state!)
(def update-test-state! state/update-test-state!)
(def read-test-world state/read-test-world)
(def game-map-atom state/game-map-atom)
(def player-map-atom state/player-map-atom)
(def computer-map-atom state/computer-map-atom)
(def set-test-world! state/set-test-world!)
(def set-test-cell! state/set-test-cell!)
(def set-test-contents! state/set-test-contents!)
(def clear-test-contents! state/clear-test-contents!)
(def update-test-world! state/update-test-world!)
(def set-test-player-map! state/set-test-player-map!)
(def update-test-player-map! state/update-test-player-map!)
(def set-test-computer-map! state/set-test-computer-map!)
(def update-test-computer-map! state/update-test-computer-map!)
(def mission-ctx state/mission-ctx)
(def set-major-invasion-state! state/set-major-invasion-state!)
(def set-kamikazee-fighter! state/set-kamikazee-fighter!)
(def seed-airport-kamikazees! state/seed-airport-kamikazees!)
(def reset-all-atoms! state/reset-all-atoms!)

;; --- builders re-exports ---

(def char->cell builders/char->cell)
(def build-test-map builders/build-test-map)
(def set-test-world-with-country! builders/set-test-world-with-country!)
(def visibility-mask builders/visibility-mask)
(def territory-mask builders/territory-mask)
(def build-territory-expected builders/build-territory-expected)
(def build-sparse-test-map builders/build-sparse-test-map)
(def set-test-unit builders/set-test-unit)
(def get-test-unit builders/get-test-unit)
(def get-test-city builders/get-test-city)
(def get-test-cell builders/get-test-cell)
(def make-initial-test-map builders/make-initial-test-map)
(def message-matches? builders/message-matches?)

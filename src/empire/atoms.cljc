(ns empire.atoms)

(def map-size (atom [0 0]))

(def last-key (atom nil))

(def test-mode (atom false))

(def map-screen-dimensions (atom [0 0]))

(def text-area-dimensions (atom [0 0 0 0]))

(def map-to-display (atom :player-map))

(def round-number (atom 0))

(def last-clicked-cell (atom nil))

(def menu-state (atom {:visible false :x 0 :y 0 :items []}))
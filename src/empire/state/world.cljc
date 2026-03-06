(ns empire.state.world)

(def defaults
  {:random-seed nil
   :map-size [0 0]
   :map-size-constants {}
   :round-number 0
   :production {}
   :game-map nil
   :player-map {}
   :computer-map {}
   :continent-groups {}
   :next-country-id 1
   :game-over-check-enabled true})

(def state (atom defaults))

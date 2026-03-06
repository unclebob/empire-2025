(ns empire.state.player)

(def defaults
  {:player-items []
   :cells-needing-attention []
   :waiting-for-input false
   :destination nil
   :paused false
   :pause-requested false})

(def state (atom defaults))

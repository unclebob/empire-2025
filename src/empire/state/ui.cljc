(ns empire.state.ui)

(def defaults
  {:last-key nil
   :backtick-pressed false
   :last-clicked-cell nil
   :map-screen-dimensions [0 0]
   :text-area-dimensions [0 0 0 0]
   :map-to-display :player-map
   :text-font nil
   :production-char-font nil
   :attention-message ""
   :turn-message ""
   :turn-message-until 0
   :hover-message ""
   :error-message ""
   :error-until 0
   :production-status ""
   :debug-drag-start nil
   :debug-drag-current nil
   :debug-message ""
   :action-log []
   :player-movement-log []
   :load-menu-open false
   :load-menu-files []
   :load-menu-hovered nil})

(def state (atom defaults))

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
   :hover-message ""
   :hover-cell nil
   :warning-message ""
   :command-message ""
   :production-status ""
   :debug-drag-start nil
   :debug-drag-current nil
   :debug-message ""
   :action-log []
   :player-movement-log []
   :load-menu-open false
   :load-menu-files []
   :load-menu-hovered nil
   :save-menu-open false
   :save-menu-input ""
   :save-menu-default-active false
   :window-focused? false
   :refocus-click-armed? false
   :refocus-click-saw-press? false
   :computer-unit-log-file nil
   :debug-dump-on-exit? false
   :debug-dump-written? false
   :computer-production-limits {}
   :headless-mode? false
   :headless-stop-on-major-invasion? false
   :major-invasion-probe-hit? false})

(def state (atom defaults))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T02:35:05.071442-05:00", :module-hash "-2077500062", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "254930533"} {:id "def/defaults", :kind "def", :line 3, :end-line 37, :hash "251635609"} {:id "def/state", :kind "def", :line 39, :end-line 39, :hash "-1274501582"}]}
;; clj-mutate-manifest-end

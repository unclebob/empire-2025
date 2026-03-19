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
   :load-menu-hovered nil
   :save-menu-open false
   :save-menu-input ""
   :save-menu-default-active false
   :computer-unit-log-file nil
   :headless-mode? false
   :headless-stop-on-major-invasion? false
   :major-invasion-probe-hit? false})

(def state (atom defaults))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T12:02:52.392128-05:00", :module-hash "-452244108", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "254930533"} {:id "def/defaults", :kind "def", :line 3, :end-line 29, :hash "794861183"} {:id "def/state", :kind "def", :line 31, :end-line 31, :hash "-1274501582"}]}
;; clj-mutate-manifest-end

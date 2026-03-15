(ns empire.state.world)

(def defaults
  {:random-seed nil
   :map-size [0 0]
   :map-size-constants {}
   :round-number 0
   :handicap-rounds-remaining 0
   :handicap-display-rounds nil
   :production {}
   :game-map nil
   :player-map {}
   :computer-map {}
   :continent-groups {}
   :next-country-id 1
   :integrity-check-enabled true
   :game-over-check-enabled true})

(def state (atom defaults))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T12:02:54.810086-05:00", :module-hash "1737867117", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "1874570204"} {:id "def/defaults", :kind "def", :line 3, :end-line 14, :hash "-286726466"} {:id "def/state", :kind "def", :line 16, :end-line 16, :hash "-1274501582"}]}
;; clj-mutate-manifest-end

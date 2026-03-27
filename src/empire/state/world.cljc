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
;; {:version 1, :tested-at "2026-03-27T02:36:59.420274-05:00", :module-hash "1462789888", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "1874570204"} {:id "def/defaults", :kind "def", :line 3, :end-line 17, :hash "-34247758"} {:id "def/state", :kind "def", :line 19, :end-line 19, :hash "-1274501582"}]}
;; clj-mutate-manifest-end

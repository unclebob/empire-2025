(ns empire.state.player)

(def defaults
  {:player-items []
   :cells-needing-attention []
   :waiting-for-input false
   :destination nil
   :paused false
   :pause-requested false})

(def state (atom defaults))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T02:33:49.653615-05:00", :module-hash "-53765020", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "-1470436628"} {:id "def/defaults", :kind "def", :line 3, :end-line 9, :hash "1639740884"} {:id "def/state", :kind "def", :line 11, :end-line 11, :hash "-1274501582"}]}
;; clj-mutate-manifest-end

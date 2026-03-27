(ns empire.config.units.ships)

(def ^:private configs
  {:patrol-boat {:speed 4 :cost 15 :hits 1 :strength 1 :display-char "P" :visibility-radius 1}
   :destroyer   {:speed 2 :cost 20 :hits 3 :strength 1 :display-char "D" :visibility-radius 1}
   :submarine   {:speed 2 :cost 20 :hits 2 :strength 3 :display-char "S" :visibility-radius 1}
   :battleship  {:speed 2 :cost 40 :hits 10 :strength 2 :display-char "B" :visibility-radius 1}})

(defn config [ship-type key]
  (get-in configs [ship-type key]))

(defn initial-state
  []
  {})

(defn can-move-to? [cell]
  (and cell (= (:type cell) :sea)))

(defn needs-attention? [unit]
  (= (:mode unit) :awake))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T00:47:50.438658-05:00", :module-hash "1927536432", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "-838414538"} {:id "def/configs", :kind "def", :line 3, :end-line 7, :hash "-1960861086"} {:id "defn/config", :kind "defn", :line 9, :end-line 10, :hash "-1916618753"} {:id "defn/initial-state", :kind "defn", :line 12, :end-line 14, :hash "-142005869"} {:id "defn/can-move-to?", :kind "defn", :line 16, :end-line 17, :hash "-927721079"} {:id "defn/needs-attention?", :kind "defn", :line 19, :end-line 20, :hash "335118728"}]}
;; clj-mutate-manifest-end

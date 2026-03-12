(ns empire.config.units.fighter
  (:require [empire.config.units.config :as units-config]))

(defn initial-state
  []
  {:fuel units-config/fighter-fuel})

(defn can-move-to?
  [_cell]
  true)

(defn needs-attention?
  [unit]
  (= (:mode unit) :awake))

(defn consume-fuel
  [unit]
  (let [current-fuel (:fuel unit units-config/fighter-fuel)
        new-fuel (dec current-fuel)]
    (if (<= new-fuel -1)
      nil
      (assoc unit :fuel new-fuel))))

(defn refuel
  [unit]
  (assoc unit :fuel units-config/fighter-fuel))

(defn bingo?
  [unit]
  (<= (:fuel unit units-config/fighter-fuel) units-config/fighter-bingo-threshold))

(defn out-of-fuel?
  [unit]
  (<= (:fuel unit units-config/fighter-fuel) 1))

(defn can-land-at-city?
  [cell]
  (and (= (:type cell) :city)
       (= (:city-status cell) :player)))

(defn can-land-on-carrier?
  [cell owner carrier-capacity]
  (let [contents (:contents cell)]
    (and contents
         (= (:type contents) :carrier)
         (= (:owner contents) owner)
         (< (:fighter-count contents 0) carrier-capacity))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T11:59:50.557598-05:00", :module-hash "771038633", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 2, :hash "403190634"} {:id "defn/initial-state", :kind "defn", :line 4, :end-line 6, :hash "-1215377802"} {:id "defn/can-move-to?", :kind "defn", :line 8, :end-line 10, :hash "1923566926"} {:id "defn/needs-attention?", :kind "defn", :line 12, :end-line 14, :hash "335118728"} {:id "defn/consume-fuel", :kind "defn", :line 16, :end-line 22, :hash "-513741186"} {:id "defn/refuel", :kind "defn", :line 24, :end-line 26, :hash "1919714893"} {:id "defn/bingo?", :kind "defn", :line 28, :end-line 30, :hash "1010170834"} {:id "defn/out-of-fuel?", :kind "defn", :line 32, :end-line 34, :hash "-1087618279"} {:id "defn/can-land-at-city?", :kind "defn", :line 36, :end-line 39, :hash "-638640865"} {:id "defn/can-land-on-carrier?", :kind "defn", :line 41, :end-line 47, :hash "172540537"}]}
;; clj-mutate-manifest-end

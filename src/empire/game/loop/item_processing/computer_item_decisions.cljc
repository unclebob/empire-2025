(ns empire.game.loop.item-processing.computer-item-decisions)

(defn coord-pair?
  [x]
  (and (vector? x)
       (= 2 (count x))
       (every? integer? x)))

(defn normalize-computer-items
  [items]
  (cond
    (coord-pair? items) [items]
    (sequential? items) items
    :else []))

(defn computer-item-action
  [{:keys [cell launched-pos new-coords should-requeue-city?]}]
  (let [is-computer-city? (and (= (:type cell) :city) (= (:city-status cell) :computer))
        has-computer-unit? (= (:owner (:contents cell)) :computer)]
    (cond
      launched-pos
      {:action :launch
       :requeue-city? should-requeue-city?
       :launched-pos launched-pos}

      has-computer-unit?
      (if new-coords
        {:action :unit-continue :new-coords new-coords}
        {:action :unit-done})

      is-computer-city?
      {:action :city-done}

      :else
      {:action :drop})))

(defn next-computer-items
  [items action]
  (let [remaining (vec (rest (normalize-computer-items items)))]
    (case (:action action)
      :launch
      (cond-> (vec (cons (:launched-pos action) remaining))
        (:requeue-city? action) (#(vec (cons (first (normalize-computer-items items)) %))))

      :unit-continue
      (vec (cons (:new-coords action) remaining))

      :unit-done
      remaining

      :city-done
      remaining

      :drop
      remaining

      remaining)))

(defn computer-item-state
  [{:keys [items action]}]
  {:computer-items (next-computer-items items action)
   :result (if (#{:launch :unit-continue} (:action action))
             :continue
             :done)})

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T01:54:03.544264-05:00", :module-hash "-1960337722", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "178836077"} {:id "defn/coord-pair?", :kind "defn", :line 3, :end-line 7, :hash "-447259980"} {:id "defn/normalize-computer-items", :kind "defn", :line 9, :end-line 14, :hash "925652102"} {:id "defn/computer-item-action", :kind "defn", :line 16, :end-line 35, :hash "566864413"} {:id "defn/next-computer-items", :kind "defn", :line 37, :end-line 57, :hash "-2053847841"} {:id "defn/computer-item-state", :kind "defn", :line 59, :end-line 64, :hash "977622679"}]}
;; clj-mutate-manifest-end

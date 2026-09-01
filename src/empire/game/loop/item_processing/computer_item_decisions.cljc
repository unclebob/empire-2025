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

(defn- computer-unit-item-action
  [new-coords]
  (if new-coords
    {:action :unit-continue :new-coords new-coords}
    {:action :unit-done}))

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
      (computer-unit-item-action new-coords)

      is-computer-city?
      {:action :city-done}

      :else
      {:action :drop})))

(defn- apply-launch-items
  [items action remaining]
  (cond-> (vec (cons (:launched-pos action) remaining))
    (:requeue-city? action) (#(vec (cons (first (normalize-computer-items items)) %)))))

(defn next-computer-items
  [items action]
  (let [remaining (vec (rest (normalize-computer-items items)))]
    (case (:action action)
      :launch (apply-launch-items items action remaining)
      :unit-continue (vec (cons (:new-coords action) remaining))
      remaining)))

(defn computer-item-state
  [{:keys [items action]}]
  {:computer-items (next-computer-items items action)
   :result (if (#{:launch :unit-continue} (:action action))
             :continue
             :done)})

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T16:06:59.291295-05:00", :module-hash "1486108250", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "178836077"} {:id "defn/coord-pair?", :kind "defn", :line 3, :end-line nil, :hash "-447259980"} {:id "defn/normalize-computer-items", :kind "defn", :line 9, :end-line nil, :hash "925652102"} {:id "defn-/computer-unit-item-action", :kind "defn-", :line 16, :end-line nil, :hash "444864396"} {:id "defn/computer-item-action", :kind "defn", :line 22, :end-line nil, :hash "1888886229"} {:id "defn-/apply-launch-items", :kind "defn-", :line 41, :end-line nil, :hash "-960308561"} {:id "defn/next-computer-items", :kind "defn", :line 46, :end-line nil, :hash "-1265744206"} {:id "defn/computer-item-state", :kind "defn", :line 54, :end-line nil, :hash "977622679"}]}
;; clj-mutate-manifest-end

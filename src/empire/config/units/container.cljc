(ns empire.config.units.container)

(defn sea-can-move-to?
  [cell]
  (and cell
       (= (:type cell) :sea)))

(defn needs-attention?
  [unit awake-key]
  (or (= (:mode unit) :awake)
      (pos? (get unit awake-key 0))))

(defn full?
  [unit count-key capacity]
  (>= (get unit count-key 0) capacity))

(defn has-contained?
  [unit count-key]
  (pos? (get unit count-key 0)))

(defn add-contained
  [unit count-key]
  (update unit count-key (fnil inc 0)))

(defn remove-contained
  [unit count-key]
  (update unit count-key (fnil dec 0)))

(defn wake-contained
  [unit count-key awake-key]
  (assoc unit awake-key (get unit count-key 0)))

(defn sleep-contained
  [unit awake-key]
  (assoc unit awake-key 0))

(defn remove-awake-contained
  [unit count-key awake-key]
  (-> unit
      (update count-key (fnil dec 0))
      (update awake-key (fnil dec 0))))

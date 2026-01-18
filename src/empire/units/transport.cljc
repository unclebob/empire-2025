(ns empire.units.transport)

;; Configuration
(def speed 2)
(def cost 30)
(def hits 1)
(def strength 1)
(def display-char "T")
(def capacity 6)
(def visibility-radius 1)

(defn initial-state
  "Returns initial state fields for a new transport."
  []
  {:army-count 0
   :awake-armies 0
   :been-to-sea true})

(defn can-move-to?
  "Transports can only move on sea."
  [cell]
  (and cell
       (= (:type cell) :sea)))

(defn needs-attention?
  "Transports need attention when awake or when they have awake armies aboard."
  [unit]
  (or (= (:mode unit) :awake)
      (pos? (:awake-armies unit 0))))

(defn full?
  "Returns true if transport is at capacity."
  [unit]
  (>= (:army-count unit 0) capacity))

(defn has-armies?
  "Returns true if transport has any armies aboard."
  [unit]
  (pos? (:army-count unit 0)))

(defn has-awake-armies?
  "Returns true if transport has awake armies aboard."
  [unit]
  (pos? (:awake-armies unit 0)))

(defn add-army
  "Adds an army to the transport. Returns updated transport."
  [unit]
  (update unit :army-count (fnil inc 0)))

(defn remove-army
  "Removes an army from the transport. Returns updated transport."
  [unit]
  (update unit :army-count (fnil dec 0)))

(defn wake-armies
  "Wakes all sleeping armies aboard the transport."
  [unit]
  (assoc unit :awake-armies (:army-count unit 0)))

(defn sleep-armies
  "Puts all armies aboard back to sleep."
  [unit]
  (assoc unit :awake-armies 0))

(defn remove-awake-army
  "Removes one awake army from the transport. Returns updated transport."
  [unit]
  (-> unit
      (update :army-count (fnil dec 0))
      (update :awake-armies (fnil dec 0))))

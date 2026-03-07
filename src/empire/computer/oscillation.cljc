;; mutation-tested: no
(ns empire.computer.oscillation
  "Helpers for oscillation detection and temporary random-walk escape.")

(def ^:private default-history-limit 10)
(def ^:private patrol-boat-history-limit 16)
(def escape-rounds 5)
(def ^:private missing ::missing)

(defn- history-limit-for-unit
  [unit]
  (if (= :patrol-boat (:type unit))
    patrol-boat-history-limit
    default-history-limit))

(defn append-position
  [unit pos]
  (let [history-limit (history-limit-for-unit unit)]
    (update unit :oscillation-history
            (fn [history]
              (->> (conj (vec (or history [])) pos)
                   (take-last history-limit)
                   vec)))))

(defn- oscillation-period?
  [history period history-limit]
  (let [n (count history)]
    (and (>= n history-limit)
         (every? (fn [i]
                   (= (nth history i)
                      (nth history (- i period))))
                 (range period n)))))

(defn oscillating?
  [unit]
  (let [history-limit (history-limit-for-unit unit)
        history (vec (or (:oscillation-history unit) []))]
    (and (>= (count history) history-limit)
         (boolean (some #(oscillation-period? history % history-limit)
                        (range 2 6))))))

(defn in-random-walk?
  [unit]
  (pos? (:oscillation-random-walk-rounds-left unit 0)))

(defn start-random-walk
  [unit restore-keys]
  (if (in-random-walk? unit)
    unit
    (let [snapshot (into {}
                         (for [k restore-keys]
                           [k (if (contains? unit k) (get unit k) missing)]))]
      (-> unit
          (assoc :oscillation-random-walk-rounds-left escape-rounds)
          (assoc :oscillation-restore snapshot)))))

(defn maybe-enter-random-walk
  ([unit restore-keys]
   (maybe-enter-random-walk unit restore-keys nil))
  ([unit restore-keys _context]
  (if (or (in-random-walk? unit)
          (not (oscillating? unit)))
    unit
    (start-random-walk unit restore-keys))))

(defn dec-random-walk
  [unit]
  (if (in-random-walk? unit)
    (update unit :oscillation-random-walk-rounds-left dec)
    unit))

(defn maybe-restore
  [unit]
  (if (in-random-walk? unit)
    unit
    (if-let [snapshot (:oscillation-restore unit)]
      (let [without-meta (dissoc unit :oscillation-restore :oscillation-random-walk-rounds-left)]
        (reduce (fn [acc [k v]]
                  (if (= missing v)
                    (dissoc acc k)
                    (assoc acc k v)))
                without-meta
                snapshot))
      (dissoc unit :oscillation-random-walk-rounds-left))))

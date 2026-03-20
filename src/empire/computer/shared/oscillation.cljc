;; mutation-tested: no
(ns empire.computer.shared.oscillation
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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T11:58:02.945762-05:00", :module-hash "-1245599918", :forms [{:id "form/0/ns", :kind "ns", :line 2, :end-line 3, :hash "1663568167"} {:id "def/default-history-limit", :kind "def", :line 5, :end-line 5, :hash "183882387"} {:id "def/patrol-boat-history-limit", :kind "def", :line 6, :end-line 6, :hash "1938788207"} {:id "def/escape-rounds", :kind "def", :line 7, :end-line 7, :hash "-1420890481"} {:id "def/missing", :kind "def", :line 8, :end-line 8, :hash "1923231318"} {:id "defn-/history-limit-for-unit", :kind "defn-", :line 10, :end-line 14, :hash "-1645874476"} {:id "defn/append-position", :kind "defn", :line 16, :end-line 23, :hash "-451428230"} {:id "defn-/oscillation-period?", :kind "defn-", :line 25, :end-line 32, :hash "2002281526"} {:id "defn/oscillating?", :kind "defn", :line 34, :end-line 40, :hash "603756188"} {:id "defn/in-random-walk?", :kind "defn", :line 42, :end-line 44, :hash "-1464565000"} {:id "defn/start-random-walk", :kind "defn", :line 46, :end-line 55, :hash "-581970112"} {:id "defn/maybe-enter-random-walk", :kind "defn", :line 57, :end-line 64, :hash "-1313270760"} {:id "defn/dec-random-walk", :kind "defn", :line 66, :end-line 70, :hash "245997758"} {:id "defn/maybe-restore", :kind "defn", :line 72, :end-line 84, :hash "1755917827"}]}
;; clj-mutate-manifest-end

;; mutation-tested: 2026-03-02
(ns empire.computer.army.coastal-invasion
  "Invasion embarkation target selection and coastal movement helpers."
  (:require [empire.computer.core :as core]
            [empire.computer.army.movement :as movement]))

 (defn- coastal-cell?
   [ctx pos country-id]
   (let [cell (get-in ((:current-world ctx)) pos)]
     (and (movement/sovereign-passable? country-id cell)
          ((:adjacent-to-ocean? ctx) pos))))

(defn empty-coastal-cell?
  [ctx pos country-id]
   (let [cell (get-in ((:current-world ctx)) pos)]
     (and (coastal-cell? ctx pos country-id)
          (nil? (:contents cell)))))

 (defn- bfs-land-distances
   [start country-id get-passable-neighbors]
   (loop [queue (conj clojure.lang.PersistentQueue/EMPTY start)
          visited #{start}
          distances {start 0}]
     (if (empty? queue)
       distances
       (let [current (peek queue)
             depth (get distances current 0)
             nexts (remove visited (get-passable-neighbors current country-id))]
         (recur (into (pop queue) nexts)
                (into visited nexts)
                (reduce (fn [m p] (assoc m p (inc depth))) distances nexts))))))

 (defn- closest-staging-cell
   [ctx distances country-id]
   (let [visited (keys distances)
         coastal-seeds (filter #(coastal-cell? ctx % country-id) visited)]
     (when (seq coastal-seeds)
       (let [staging (filter (fn [p]
                               (let [cell (get-in ((:current-world ctx)) p)]
                                 (and (movement/sovereign-passable? country-id cell)
                                      (or (nil? (:contents cell))
                                          (= p (first coastal-seeds))))))
                             visited)]
         (first (sort-by (fn [p]
                           (let [coast-dist (apply min (map #(core/distance p %) coastal-seeds))]
                             [coast-dist (get distances p 9999) p]))
                         staging))))))

 (defn find-coast-target-once
   "One-time land-only BFS target selection for invasion embarkation.
    Prefers nearest empty coastal cell; if none reachable, picks nearest staging cell."
   [ctx start country-id]
   (let [distances (bfs-land-distances start country-id movement/get-passable-neighbors)
         reachable (keys distances)
        empty-coastal (filter #(empty-coastal-cell? ctx % country-id) reachable)]
     (if (seq empty-coastal)
       (first (sort-by (fn [p] [(get distances p 9999) p]) empty-coastal))
       (closest-staging-cell ctx distances country-id))))

(defn local-empty-coast-target
  [ctx pos country-id]
   (loop [queue (conj clojure.lang.PersistentQueue/EMPTY [pos 0])
          visited #{pos}]
     (if (empty? queue)
       nil
       (let [[current depth] (peek queue)]
         (cond
           (and (not= current pos) (empty-coastal-cell? ctx current country-id)) current
           (>= depth 2) (recur (pop queue) visited)
           :else
           (let [nexts (->> (movement/get-passable-neighbors current country-id)
                            (remove visited))]
             (recur (reduce #(conj %1 [%2 (inc depth)]) (pop queue) nexts)
                    (into visited nexts))))))))

(def ^:private local-coast-repath-interval-rounds 3)

 (defn- settle-at-coast-target!
   [update-game-map! pos]
   (update-game-map! update-in (conj pos :contents)
                     #(-> %
                          (assoc :mode :sentry)
                          (dissoc :coast-target :coast-repath-after-round :lake-retask?))))

 (defn- step-toward-target-cheap
   [pos target country-id]
   (let [current-dist (core/distance pos target)
         candidates (->> (movement/get-empty-passable-neighbors pos country-id)
                         (filter #(> current-dist (core/distance % target)))
                         (sort-by #(core/distance % target)))]
     (when-let [best (first candidates)]
       (movement/try-move pos best))))

(defn- set-coast-target! [ctx pos target]
  ((:update-game-map! ctx) assoc-in (conj pos :contents :coast-target) target))

(defn- resolve-coast-target [ctx unit pos country-id]
  (or (:coast-target unit)
      ((:find-coast-target-once ctx) pos country-id)))

(defn- retry-repath-now? [ctx unit]
  (let [now (or ((:read-runtime-state ctx) :round-number) 0)
        retry-at (:coast-repath-after-round unit)]
    (when (or (nil? retry-at) (<= retry-at now))
      ((:update-game-map! ctx) assoc-in (conj (:pos unit) :contents :coast-repath-after-round)
       (+ now local-coast-repath-interval-rounds))
      now)))

(defn- maybe-repath-local-target
  [ctx pos country-id unit]
  (when (retry-repath-now? ctx (assoc unit :pos pos))
    (when-let [local-target (local-empty-coast-target ctx pos country-id)]
      (set-coast-target! ctx pos local-target)
      (movement/move-toward-objective pos local-target country-id))))

(defn- move-toward-coast-target
  [ctx pos country-id unit target]
  (or (movement/move-toward-objective pos target country-id)
      (maybe-repath-local-target ctx pos country-id unit)))

(defn- execute-coast-target-step
  [ctx pos country-id unit target]
  (cond
    (= pos target)
    (do (settle-at-coast-target! (:update-game-map! ctx) pos) pos)

    (:lake-retask? unit)
    (or (step-toward-target-cheap pos target country-id)
        (do (settle-at-coast-target! (:update-game-map! ctx) pos) pos))

    :else
    (move-toward-coast-target ctx pos country-id unit target)))

(defn process-move-to-coast-for-invasion
  "Move an army toward its cached coast target for pickup."
  [ctx pos country-id]
  (if ((:should-sentry-on-coast? ctx) pos country-id)
    (do
      ((:update-game-map! ctx) update-in (conj pos :contents)
       #(-> %
            (assoc :mode :sentry)
            (dissoc :coast-target :coast-repath-after-round :lake-retask?)))
      pos)
    (let [unit (get-in ((:current-world ctx)) (conj pos :contents))]
      (when-let [target (resolve-coast-target ctx unit pos country-id)]
        (set-coast-target! ctx pos target)
        (execute-coast-target-step ctx pos country-id unit target)))))

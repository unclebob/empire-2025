(ns empire.computer.army.coastal-invasion
  "Invasion embarkation target selection and coastal movement helpers."
  (:require [empire.computer.army.coastal-invasion-decisions :as decisions]
            [empire.computer.shared.grid :as grid]
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
         ;; LCOV currently omits this nested recur/reduce update line even when exercised,
         ;; so clj-mutate may report the inc site here as uncovered.
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
                           (let [coast-dist (apply min (map #(grid/distance p %) coastal-seeds))]
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

(defn- set-sentry-mode-if-unit!
  [ctx pos]
  (when (get-in ((:current-world ctx)) (conj pos :contents))
    ((:update-game-map! ctx) update-in (conj pos :contents)
     #(-> %
          (assoc :mode :sentry)
          (dissoc :coast-target :coast-repath-after-round :lake-retask?)))
    ((:sync-ai-unit! ctx) pos)))

(defn- settle-at-coast-target!
  [ctx pos]
  (set-sentry-mode-if-unit! ctx pos))

 (defn- step-toward-target-cheap
   [pos target country-id]
   (let [current-dist (grid/distance pos target)
         candidates (->> (movement/get-empty-passable-neighbors pos country-id)
                         (filter #(> current-dist (grid/distance % target)))
                         (sort-by #(grid/distance % target)))]
     (when-let [best (first candidates)]
       (movement/try-move pos best))))

(defn- set-coast-target! [ctx pos target]
  (when-not (= target (get-in ((:current-world ctx)) (conj pos :contents :coast-target)))
    ((:update-game-map! ctx) assoc-in (conj pos :contents :coast-target) target)
    ((:sync-ai-unit! ctx) pos)))

(defn- resolve-coast-target [ctx unit pos country-id]
  (let [cached-target (:coast-target unit)]
    (decisions/resolve-coast-target cached-target
                                    (when-not cached-target
                                      ((:find-coast-target-once ctx) pos country-id)))))

(defn- retry-repath-now? [ctx unit]
  (let [now (or ((:read-runtime-state ctx) :round-number) 0)
        retry-at (:coast-repath-after-round unit)]
    (when (decisions/retry-repath-now? now retry-at)
      ((:update-game-map! ctx) assoc-in (conj (:pos unit) :contents :coast-repath-after-round)
       (+ now local-coast-repath-interval-rounds))
      ((:sync-ai-unit! ctx) (:pos unit))
      now)))

(defn- maybe-repath-local-target
  [ctx pos country-id unit]
  (when (retry-repath-now? ctx (assoc unit :pos pos))
    (when-let [local-target (local-empty-coast-target ctx pos country-id)]
      (set-coast-target! ctx pos local-target)
      (movement/move-toward-objective pos local-target country-id))))

(defn- plan-coast-target-step
  [ctx pos country-id unit target]
  (let [action (if (= pos target)
                 (decisions/coast-step-action {:pos pos
                                               :target target
                                               :lake-retask? (:lake-retask? unit)
                                               :cheap-step? nil
                                               :local-step? nil
                                               :move-step? nil
                                               :repath-step? nil})
                 (let [cheap-step (when (:lake-retask? unit)
                                    (step-toward-target-cheap pos target country-id))
                       local-step (when-not (or (:lake-retask? unit) cheap-step)
                                    (movement/local-step-toward-objective pos target country-id))
                       move-step (when-not (:lake-retask? unit)
                                    (when-not local-step
                                      (movement/move-toward-objective pos target country-id)))
                       repath-step (when-not (or (:lake-retask? unit) local-step move-step)
                                     (maybe-repath-local-target ctx pos country-id unit))]
                   (decisions/coast-step-action {:pos pos
                                                 :target target
                                                 :lake-retask? (:lake-retask? unit)
                                                 :cheap-step? cheap-step
                                                 :local-step? local-step
                                                 :move-step? move-step
                                                 :repath-step? repath-step})))]
    action))

(defn- execute-coast-target-step
  [ctx pos country-id unit target]
  (let [action (plan-coast-target-step ctx pos country-id unit target)]
    (case (:action action)
      :settle (do (settle-at-coast-target! ctx pos) pos)
      :cheap-step (:target action)
      :local-step (:target action)
      :move (:target action)
      :repath (:target action)
      nil)))

(defn process-move-to-coast-for-invasion
  "Move an army toward its cached coast target for pickup."
  [ctx pos country-id]
  (if ((:should-sentry-on-coast? ctx) pos country-id)
    (do
      (set-sentry-mode-if-unit! ctx pos)
      pos)
    (let [unit (get-in ((:current-world ctx)) (conj pos :contents))]
      (when-let [target (resolve-coast-target ctx unit pos country-id)]
        (set-coast-target! ctx pos target)
        (execute-coast-target-step ctx pos country-id unit target)))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T22:23:03.156838-05:00", :module-hash "-442141931", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 5, :hash "1658039413"} {:id "defn-/coastal-cell?", :kind "defn-", :line 7, :end-line 11, :hash "-308990903"} {:id "defn/empty-coastal-cell?", :kind "defn", :line 13, :end-line 17, :hash "-935405407"} {:id "defn-/bfs-land-distances", :kind "defn-", :line 19, :end-line 33, :hash "-702595219"} {:id "defn-/closest-staging-cell", :kind "defn-", :line 35, :end-line 49, :hash "444394976"} {:id "defn/find-coast-target-once", :kind "defn", :line 51, :end-line 60, :hash "1909238643"} {:id "defn/local-empty-coast-target", :kind "defn", :line 62, :end-line 76, :hash "-447623782"} {:id "def/local-coast-repath-interval-rounds", :kind "def", :line 78, :end-line 78, :hash "1825849599"} {:id "defn-/set-sentry-mode-if-unit!", :kind "defn-", :line 80, :end-line 87, :hash "-140379044"} {:id "defn-/settle-at-coast-target!", :kind "defn-", :line 89, :end-line 91, :hash "967657846"} {:id "defn-/step-toward-target-cheap", :kind "defn-", :line 93, :end-line 100, :hash "877161758"} {:id "defn-/set-coast-target!", :kind "defn-", :line 102, :end-line 105, :hash "1725405936"} {:id "defn-/resolve-coast-target", :kind "defn-", :line 107, :end-line 111, :hash "973356450"} {:id "defn-/retry-repath-now?", :kind "defn-", :line 113, :end-line 120, :hash "166270117"} {:id "defn-/maybe-repath-local-target", :kind "defn-", :line 122, :end-line 127, :hash "918333870"} {:id "defn-/plan-coast-target-step", :kind "defn-", :line 129, :end-line 155, :hash "-687639062"} {:id "defn-/execute-coast-target-step", :kind "defn-", :line 157, :end-line 166, :hash "-706581146"} {:id "defn/process-move-to-coast-for-invasion", :kind "defn", :line 168, :end-line 178, :hash "-778620575"}]}
;; clj-mutate-manifest-end

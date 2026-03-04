(ns empire.architecture.dependency-checker.core-graph
  (:require [empire.architecture.dependency-checker.core-base-config :as cfg]
            [empire.architecture.dependency-checker.core-base-parse :as parse]))

(defn- tarjan-state
  []
  {:index (atom 0)
   :stack (atom [])
   :on-stack (atom #{})
   :indices (atom {})
   :lowlinks (atom {})
   :sccs (atom [])})

(defn- tarjan-push!
  [state v]
  (swap! (:indices state) assoc v @(:index state))
  (swap! (:lowlinks state) assoc v @(:index state))
  (swap! (:index state) inc)
  (swap! (:stack state) conj v)
  (swap! (:on-stack state) conj v))

(defn- tarjan-pop-component!
  [state v]
  (loop [component []]
    (let [w (peek @(:stack state))]
      (swap! (:stack state) pop)
      (swap! (:on-stack state) disj w)
      (let [updated (conj component w)]
        (if (= w v)
          updated
          (recur updated))))))

(declare tarjan-strongconnect!)

(defn- tarjan-process-neighbor!
  [state adj v w]
  (let [indices @(:indices state)]
    (cond
      (not (contains? indices w))
      (do
        (tarjan-strongconnect! state adj w)
        (swap! (:lowlinks state) update v min (get @(:lowlinks state) w)))

      (contains? @(:on-stack state) w)
      (swap! (:lowlinks state) update v min (get indices w))

      :else nil)))

(defn- tarjan-root?
  [state v]
  (= (get @(:lowlinks state) v)
     (get @(:indices state) v)))

(defn- tarjan-strongconnect!
  [state adj v]
  (tarjan-push! state v)
  (doseq [w (get adj v)]
    (tarjan-process-neighbor! state adj v w))
  (when (tarjan-root? state v)
    (swap! (:sccs state) conj (tarjan-pop-component! state v))))

(defn strongly-connected-components
  [nodes edges]
  (let [adj (reduce (fn [m [a b]] (update m a (fnil conj []) b))
                    (zipmap nodes (repeat []))
                    edges)
        state (tarjan-state)]
    (doseq [v nodes]
      (when-not (contains? @(:indices state) v)
        (tarjan-strongconnect! state adj v)))
    @(:sccs state)))

(defn aggregate-warnings
  [parsed]
  (->> parsed
       (mapcat (fn [{:keys [file namespace warnings]}]
                 (for [{:keys [kind callee targets]} warnings]
                   {:kind kind
                    :file file
                    :namespace (str namespace)
                    :callee callee
                    :targets targets})))
       distinct
       (sort-by (juxt :namespace :callee :targets))
       vec))

(defn build-ns-edges
  [parsed ns->entry component-rules]
  (->> parsed
       (mapcat (fn [{:keys [namespace component requires]}]
                 (for [dep requires
                       :let [dep-entry (get ns->entry dep)
                             dep-component (or (:component dep-entry)
                                               (cfg/component-for-ns component-rules dep))]
                       :when (and component dep-component)]
                   {:from-ns (str namespace)
                    :to-ns (str dep)
                    :from-component component
                    :to-component dep-component})))
       vec))

(defn neighbor-maps
  [component-set component-edges]
  {:outgoing (reduce (fn [m [a b]]
                       (if (= a b) m (update m a (fnil conj #{}) b)))
                     (zipmap component-set (repeat #{}))
                     component-edges)
   :incoming (reduce (fn [m [a b]]
                       (if (= a b) m (update m b (fnil conj #{}) a)))
                     (zipmap component-set (repeat #{}))
                     component-edges)})

(defn component-stat
  [component parsed incoming outgoing]
  (let [ns-in-component (filter #(= component (:component %)) parsed)
        public-count (reduce + (map :public-count ns-in-component))
        abstract-count (reduce + (map :abstract-count ns-in-component))
        fan-in (count (get incoming component #{}))
        fan-out (count (get outgoing component #{}))
        denom (+ fan-in fan-out)
        instability (if (zero? denom) 0 (/ fan-out denom))
        abstractness (if (zero? public-count) 0 (/ abstract-count public-count))
        distance (cfg/abs-num (- (+ abstractness instability) 1))]
    {:fan-in fan-in
     :fan-out fan-out
     :instability instability
     :abstractness abstractness
     :distance distance
     :public-vars public-count
     :abstract-vars abstract-count}))

(defn component-stats-map
  [component-set parsed incoming outgoing]
  (->> component-set
       (map (fn [component]
              [component (component-stat component parsed incoming outgoing)]))
       (sort-by (comp str first))
       (into {})))

(defn find-violations
  [ns-edges forbidden-rules exceptions]
  (->> ns-edges
       (mapcat (fn [edge]
                 (for [{:keys [from to] :as rule} forbidden-rules
                       :when (and (= from (:from-component edge))
                                  (= to (:to-component edge)))
                       :when (not (some #(cfg/exception-matches? % edge) exceptions))]
                   (assoc edge :rule rule))))
       vec))

(defn analyze-project
  [config]
  (let [merged-config (merge cfg/default-config config)
        component-rules (cfg/compile-component-rules (:component-rules merged-config))
        files (cfg/source-files (:source-paths merged-config) (:include-exts merged-config))
        parsed (->> files (map #(parse/parse-source-entry % component-rules)) (filter some?) vec)
        warnings (aggregate-warnings parsed)
        ns->entry (into {} (map (juxt :namespace identity) parsed))
        component-set (->> parsed (map :component) (filter some?) set)
        ns-edges (build-ns-edges parsed ns->entry component-rules)
        component-edges (->> ns-edges
                             (map (juxt :from-component :to-component))
                             set)
        {:keys [incoming outgoing]} (neighbor-maps component-set component-edges)
        component-stats (component-stats-map component-set parsed incoming outgoing)
        exceptions (mapv cfg/compile-exception (:allowed-exceptions merged-config))
        forbidden-rules (mapv cfg/normalize-forbidden-rule (:forbidden-dependencies merged-config))
        violations (find-violations ns-edges forbidden-rules exceptions)
        sccs (strongly-connected-components component-set (remove (fn [[a b]] (= a b)) component-edges))
        cycles (->> sccs (filter #(> (count %) 1)) vec)]
    {:config merged-config
     :namespaces parsed
     :component-edges (sort component-edges)
     :component-stats component-stats
     :warnings warnings
     :violations violations
     :cycles cycles}))

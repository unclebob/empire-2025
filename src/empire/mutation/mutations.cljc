(ns empire.mutation.mutations)

(def rules
  [{:original '+   :mutant '-   :category :arithmetic :position :head}
   {:original '-   :mutant '+   :category :arithmetic :position :head}
   {:original '*   :mutant '/   :category :arithmetic :position :head}
   {:original 'inc :mutant 'dec :category :arithmetic :position :head}
   {:original 'dec :mutant 'inc :category :arithmetic :position :head}
   {:original '>   :mutant '>=  :category :comparison :position :head}
   {:original '>=  :mutant '>   :category :comparison :position :head}
   {:original '<   :mutant '<=  :category :comparison :position :head}
   {:original '<=  :mutant '<   :category :comparison :position :head}
   {:original '=   :mutant 'not= :category :equality :position :head}
   {:original 'not= :mutant '= :category :equality :position :head}
   {:original true  :mutant false :category :boolean :position :any}
   {:original false :mutant true  :category :boolean :position :any}
   {:original 'if      :mutant 'if-not   :category :conditional :position :head}
   {:original 'if-not  :mutant 'if       :category :conditional :position :head}
   {:original 'when    :mutant 'when-not :category :conditional :position :head}
   {:original 'when-not :mutant 'when    :category :conditional :position :head}
   {:original 0 :mutant 1 :category :constant :position :any}
   {:original 1 :mutant 0 :category :constant :position :any}])

(defn matches-rule?
  "True if rule matches node. For :head rules, node must be
   a list/seq and the symbol must be its first element.
   parent-form is the enclosing list (or nil at top level)."
  [rule parent-form node]
  (and (= (:original rule) node)
       (or (= :any (:position rule))
           (and (= :head (:position rule))
                (seq? parent-form)
                (= node (first parent-form))))))

(defn- first-matching-rule [parent node]
  (first (filter #(matches-rule? % parent node) rules)))

(defn- walk-children
  "Recurse into child nodes of any collection type."
  [walk-fn parent node]
  (cond
    (seq? node) (doseq [child node] (walk-fn node child))
    (vector? node) (doseq [child node] (walk-fn parent child))
    (map? node) (doseq [[k v] node] (walk-fn parent k) (walk-fn parent v))
    (set? node) (doseq [child node] (walk-fn parent child))))

(defn find-mutations
  "Walk form tree, return vector of mutation sites.
   Each site: {:index N :original form :mutant form :description \"...\"}."
  [form]
  (let [counter (atom 0)
        sites (atom [])]
    (letfn [(walk [parent node]
              (when-let [rule (first-matching-rule parent node)]
                (swap! sites conj {:index @counter
                                   :original (:original rule)
                                   :mutant (:mutant rule)
                                   :category (:category rule)
                                   :description (str (:original rule) " -> " (:mutant rule))})
                (swap! counter inc))
              (walk-children walk parent node))]
      (walk nil form))
    @sites))

(defn- rebuild-coll
  "Rebuild a collection after walking its children."
  [walk-fn parent node]
  (cond
    (seq? node) (apply list (map #(walk-fn node %) node))
    (vector? node) (mapv #(walk-fn parent %) node)
    (map? node) (into {} (map (fn [[k v]] [(walk-fn parent k) (walk-fn parent v)]) node))
    (set? node) (into #{} (map #(walk-fn parent %) node))
    :else node))

(defn apply-mutation
  "Walk form tree, apply the mutation at the given index.
   Returns the mutated form."
  [form target-index]
  (let [counter (atom 0)]
    (letfn [(walk [parent node]
              (if-let [rule (first-matching-rule parent node)]
                (let [idx @counter]
                  (swap! counter inc)
                  (if (= idx target-index)
                    (if (seq? node)
                      (let [mutant (:mutant rule)
                            new-parent (cons mutant (rest node))]
                        (apply list mutant (map #(walk new-parent %) (rest node))))
                      (:mutant rule))
                    (rebuild-coll walk parent node)))
                (rebuild-coll walk parent node)))]
      (walk nil form))))

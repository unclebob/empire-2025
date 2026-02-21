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

(defn find-mutations
  "Walk form tree, return vector of mutation sites.
   Each site: {:index N :original form :mutant form :description \"...\"}."
  [form]
  (let [counter (atom 0)
        sites (atom [])]
    (letfn [(walk [parent node]
              (doseq [rule rules]
                (when (matches-rule? rule parent node)
                  (swap! sites conj {:index @counter
                                     :original (:original rule)
                                     :mutant (:mutant rule)
                                     :category (:category rule)
                                     :description (str (:original rule) " -> " (:mutant rule))})
                  (swap! counter inc)))
              (when (seq? node)
                (doseq [child node]
                  (walk node child))))]
      (walk nil form))
    @sites))

(defn- first-matching-rule [parent node]
  (first (filter #(matches-rule? % parent node) rules)))

(defn apply-mutation
  "Walk form tree, apply the mutation at the given index.
   Returns the mutated form."
  [form target-index]
  (let [counter (atom 0)]
    (letfn [(walk [parent node]
              (let [rule (first-matching-rule parent node)]
                (if rule
                  (let [idx @counter
                        _ (swap! counter inc)]
                    (if (= idx target-index)
                      (if (seq? node)
                        (let [mutant (:mutant rule)
                              new-parent (cons mutant (rest node))]
                          (apply list mutant (map #(walk new-parent %) (rest node))))
                        (:mutant rule))
                      (if (seq? node)
                        (apply list (map #(walk node %) node))
                        node)))
                  (if (seq? node)
                    (apply list (map #(walk node %) node))
                    node))))]
      (walk nil form))))

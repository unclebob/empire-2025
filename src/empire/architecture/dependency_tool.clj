(ns empire.architecture.dependency-tool
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint]
            [clojure.set :as set]
            [clojure.string :as str]))

(def default-config
  {:source-paths ["src"]
   :include-exts #{".clj" ".cljc" ".cljs"}
   :component-rules []
   :forbidden-dependencies []
   :allowed-exceptions []
   :fail-on-cycles true
   :fail-on-violations true})

(defn- glob->regex
  [pattern]
  (re-pattern
   (str "^"
        (-> pattern
            (str/replace "." "\\.")
            (str/replace "*" ".*"))
        "$")))

(defn- pattern->matcher
  [pattern]
  (cond
    (instance? java.util.regex.Pattern pattern)
    (fn [s] (boolean (re-find pattern s)))

    (keyword? pattern)
    (let [exact (name pattern)]
      (fn [s] (= exact s)))

    (string? pattern)
    (let [rx (if (or (str/includes? pattern "*")
                     (str/starts-with? pattern "^"))
               (if (str/starts-with? pattern "^")
                 (re-pattern pattern)
                 (glob->regex pattern))
               (re-pattern (str "^" (java.util.regex.Pattern/quote pattern) "$")))]
      (fn [s] (boolean (re-find rx s))))

    :else
    (fn [_] false)))

(defn- normalize-component-rule
  [rule]
  (cond
    (and (map? rule) (contains? rule :component))
    (let [raw-matches (or (:match rule) (:matches rule) (:pattern rule))
          patterns (cond
                     (nil? raw-matches) []
                     (sequential? raw-matches) raw-matches
                     :else [raw-matches])
          matchers (mapv pattern->matcher patterns)]
      {:component (:component rule)
       :matches? (fn [ns-name]
                   (boolean (some #(% ns-name) matchers)))})

    (and (vector? rule) (= 2 (count rule)))
    (normalize-component-rule {:component (first rule) :match (second rule)})

    :else
    (throw (ex-info "Invalid component rule" {:rule rule}))))

(defn- compile-component-rules
  [rules]
  (mapv normalize-component-rule rules))

(defn- component-for-ns
  [compiled-rules ns-sym]
  (let [ns-name (str ns-sym)]
    (some (fn [{:keys [component matches?]}]
            (when (matches? ns-name) component))
          compiled-rules)))

(defn- source-file?
  [^java.io.File f include-exts]
  (and (.isFile f)
       (some #(str/ends-with? (.getName f) %) include-exts)))

(defn- source-files
  [paths include-exts]
  (->> paths
       (map io/file)
       (filter #(.exists ^java.io.File %))
       (mapcat file-seq)
       (filter #(source-file? % include-exts))))

(defn- read-forms
  [file]
  (with-open [r (java.io.PushbackReader. (io/reader file))]
    (binding [*read-eval* false]
      (loop [acc []]
        (let [form (try
                     (read {:eof ::eof :read-cond :allow :features #{:clj}} r)
                     (catch Exception _
                       ::read-error))]
          (cond
            (= ::eof form) acc
            (= ::read-error form) acc
            :else (recur (conj acc form))))))))

(defn- ns-form?
  [form]
  (and (seq? form) (= 'ns (first form))))

(defn- require-target
  [entry]
  (cond
    (symbol? entry) entry
    (vector? entry) (when (symbol? (first entry)) (first entry))
    :else nil))

(defn- ns-clause-entries
  [ns-decl clause]
  (->> (drop 2 ns-decl)
       (filter seq?)
       (filter #(keyword? (first %)))
       (filter #(= clause (first %)))
       (mapcat rest)))

(defn- quote-unwrapped
  [form]
  (if (and (seq? form) (= 'quote (first form)))
    (second form)
    form))

(defn- dependency-symbol->namespace
  [sym]
  (if (qualified-symbol? sym)
    (symbol (namespace sym))
    sym))

(defn- require-arg-targets
  [arg]
  (let [arg* (quote-unwrapped arg)]
    (cond
      (symbol? arg*) [arg*]
      (vector? arg*) (if-let [target (require-target arg*)] [target] [])
      :else [])))

(defn- extract-ns-clause-deps
  [ns-decl clause]
  (->> (ns-clause-entries ns-decl clause)
       (mapcat require-arg-targets)
       (map dependency-symbol->namespace)
       (filter symbol?)
       set))

(defn- walk-forms
  [forms]
  (tree-seq coll? seq forms))

(defn- extract-direct-requires
  [forms]
  (->> (walk-forms forms)
       (filter #(and (seq? %) (= 'require (first %))))
       (mapcat rest)
       (mapcat require-arg-targets)
       (map dependency-symbol->namespace)
       (filter symbol?)
       set))

(defn- extract-requiring-resolves
  [forms]
  (->> (walk-forms forms)
       (filter #(and (seq? %) (= 'requiring-resolve (first %))))
       (keep (fn [form]
               (some-> (second form)
                       quote-unwrapped
                       dependency-symbol->namespace)))
       (filter symbol?)
       set))

(defn- extract-dependencies
  [forms ns-decl]
  (set/union (extract-ns-clause-deps ns-decl :require)
             (extract-ns-clause-deps ns-decl :use)
             (extract-ns-clause-deps ns-decl :import)
             (extract-direct-requires forms)
             (extract-requiring-resolves forms)))

(def ^:private def-ops
  #{"def" "defonce" "defmacro" "defn" "defn-" "defmulti" "defprotocol"})

(defn- var-symbol
  [form]
  (when (and (seq? form) (<= 2 (count form)))
    (let [sym (second form)]
      (when (symbol? sym) sym))))

(defn- private-var?
  [op-name sym]
  (or (= op-name "defn-")
      (:private (meta sym))
      (str/starts-with? (name sym) "-")))

(defn- abstract-var?
  [op-name]
  (#{"defprotocol" "defmulti"} op-name))

(defn- var-stats
  [forms]
  (reduce
   (fn [{:keys [public-count abstract-count] :as acc} form]
     (if (seq? form)
       (let [op (first form)
             op-name (when (symbol? op) (name op))
             sym (var-symbol form)]
         (if (and op-name (def-ops op-name) sym (not (private-var? op-name sym)))
           (-> acc
               (assoc :public-count (inc public-count))
               (update :abstract-count + (if (abstract-var? op-name) 1 0)))
           acc))
       acc))
   {:public-count 0 :abstract-count 0}
   forms))

(defn- normalize-forbidden-rule
  [rule]
  (cond
    (map? rule) rule
    (and (vector? rule) (= 2 (count rule))) {:from (first rule) :to (second rule)}
    :else (throw (ex-info "Invalid forbidden dependency rule" {:rule rule}))))

(defn- compile-exception
  [ex]
  (let [from-ns-m (when-let [p (:from-ns ex)] (pattern->matcher p))
        to-ns-m (when-let [p (:to-ns ex)] (pattern->matcher p))]
    (assoc ex
           :from-ns-match? from-ns-m
           :to-ns-match? to-ns-m)))

(defn- exception-matches?
  [ex {:keys [from-component to-component from-ns to-ns]}]
  (and (if (contains? ex :from-component) (= (:from-component ex) from-component) true)
       (if (contains? ex :to-component) (= (:to-component ex) to-component) true)
       (if-let [m (:from-ns-match? ex)] (m from-ns) true)
       (if-let [m (:to-ns-match? ex)] (m to-ns) true)))

(defn- strongly-connected-components
  [nodes edges]
  (let [adj (reduce (fn [m [a b]] (update m a (fnil conj []) b)) (zipmap nodes (repeat [])) edges)
        index (atom 0)
        stack (atom [])
        on-stack (atom #{})
        indices (atom {})
        lowlinks (atom {})
        sccs (atom [])]
    (letfn [(strongconnect [v]
              (swap! indices assoc v @index)
              (swap! lowlinks assoc v @index)
              (swap! index inc)
              (swap! stack conj v)
              (swap! on-stack conj v)
              (doseq [w (get adj v)]
                (cond
                  (not (contains? @indices w))
                  (do
                    (strongconnect w)
                    (swap! lowlinks update v min (get @lowlinks w)))

                  (contains? @on-stack w)
                  (swap! lowlinks update v min (get @indices w))))
              (when (= (get @lowlinks v) (get @indices v))
                (loop [component []]
                  (let [w (peek @stack)]
                    (swap! stack pop)
                    (swap! on-stack disj w)
                    (let [updated (conj component w)]
                      (if (= w v)
                        (swap! sccs conj updated)
                        (recur updated)))))))]
      (doseq [v nodes]
        (when-not (contains? @indices v)
          (strongconnect v)))
      @sccs)))

(defn analyze-project
  [config]
  (let [cfg (merge default-config config)
        component-rules (compile-component-rules (:component-rules cfg))
        files (source-files (:source-paths cfg) (:include-exts cfg))
        parsed (->> files
                    (map (fn [f]
                           (let [forms (read-forms f)
                                 ns-decl (first (filter ns-form? forms))]
                             (when ns-decl
                               (let [ns-name (second ns-decl)
                                     component (component-for-ns component-rules ns-name)
                                     requires (extract-dependencies forms ns-decl)
                                     stats (var-stats forms)]
                                 {:file (.getPath f)
                                  :namespace ns-name
                                  :component component
                                  :requires requires
                                  :public-count (:public-count stats)
                                  :abstract-count (:abstract-count stats)})))))
                    (filter some?)
                    vec)
        ns->entry (into {} (map (juxt :namespace identity) parsed))
        component-set (->> parsed (map :component) (filter some?) set)
        ns-edges (->> parsed
                      (mapcat (fn [{:keys [namespace component requires]}]
                                (for [dep requires
                                      :let [dep-entry (get ns->entry dep)
                                            dep-component (or (:component dep-entry)
                                                              (component-for-ns component-rules dep))]
                                      :when (and component dep-component)]
                                  {:from-ns (str namespace)
                                   :to-ns (str dep)
                                   :from-component component
                                   :to-component dep-component})))
                      vec)
        component-edges (->> ns-edges
                             (map (juxt :from-component :to-component))
                             set)
        outgoing (reduce (fn [m [a b]]
                           (if (= a b) m (update m a (fnil conj #{}) b)))
                         (zipmap component-set (repeat #{}))
                         component-edges)
        incoming (reduce (fn [m [a b]]
                           (if (= a b) m (update m b (fnil conj #{}) a)))
                         (zipmap component-set (repeat #{}))
                         component-edges)
        component-stats (->> component-set
                             (map (fn [component]
                                    (let [ns-in-component (filter #(= component (:component %)) parsed)
                                          public-count (reduce + (map :public-count ns-in-component))
                                          abstract-count (reduce + (map :abstract-count ns-in-component))
                                          fan-in (count (get incoming component #{}))
                                          fan-out (count (get outgoing component #{}))
                                          denom (+ fan-in fan-out)
                                          instability (if (zero? denom) 0.0 (/ (double fan-out) (double denom)))
                                          abstractness (if (zero? public-count) 0.0 (/ (double abstract-count) (double public-count)))
                                          distance (Math/abs (double (- (+ abstractness instability) 1.0)))]
                                      [component {:fan-in fan-in
                                                  :fan-out fan-out
                                                  :instability instability
                                                  :abstractness abstractness
                                                  :distance distance
                                                  :public-vars public-count
                                                  :abstract-vars abstract-count}])))
                             (sort-by (comp str first))
                             (into {}))
        exceptions (mapv compile-exception (:allowed-exceptions cfg))
        forbidden-rules (mapv normalize-forbidden-rule (:forbidden-dependencies cfg))
        violations (->> ns-edges
                        (mapcat (fn [edge]
                                  (for [{:keys [from to] :as rule} forbidden-rules
                                        :when (and (= from (:from-component edge))
                                                   (= to (:to-component edge)))
                                        :when (not (some #(exception-matches? % edge) exceptions))]
                                    (assoc edge :rule rule))))
                        vec)
        sccs (strongly-connected-components component-set (remove (fn [[a b]] (= a b)) component-edges))
        cycles (->> sccs (filter #(> (count %) 1)) vec)]
    {:config cfg
     :namespaces parsed
     :component-edges (sort component-edges)
     :component-stats component-stats
     :violations violations
     :cycles cycles}))

(defn- source-ns-records
  [source-paths include-exts]
  (->> (source-files source-paths include-exts)
       (map (fn [f]
              (let [forms (read-forms f)
                    ns-decl (first (filter ns-form? forms))]
                (when ns-decl
                  (let [ns-name (second ns-decl)
                        stats (var-stats forms)]
                    {:namespace (str ns-name)
                     :requires (set (map str (extract-dependencies forms ns-decl)))
                     :public-count (:public-count stats)
                     :abstract-count (:abstract-count stats)})))))
       (filter some?)
       vec))

(defn- ns-prefixes
  [ns-name]
  (let [parts (str/split ns-name #"\.")]
    (map #(str/join "." (take % parts))
         (range 1 (inc (count parts))))))

(defn- parent-prefix
  [prefix]
  (let [parts (str/split prefix #"\.")]
    (when (> (count parts) 1)
      (str/join "." (butlast parts)))))

(defn- in-prefix?
  [prefix ns-name]
  (or (= prefix ns-name)
      (str/starts-with? ns-name (str prefix "."))))

(defn- module-abstract?
  [{:keys [public-count abstract-count]}]
  (and (pos? public-count) (= public-count abstract-count)))

(defn- infer-abstract-prefixes
  [records]
  (let [module-abstract (into {} (map (juxt :namespace module-abstract?) records))
        all-ns (set (keys module-abstract))
        prefixes (->> all-ns (mapcat ns-prefixes) set)
        prefix-abstract? (fn [prefix]
                           (let [desc (filter #(in-prefix? prefix %) all-ns)]
                             (and (seq desc)
                                  (every? #(true? (get module-abstract %)) desc))))
        abstract-prefixes (set (filter prefix-abstract? prefixes))]
    (->> abstract-prefixes
         (filter (fn [prefix]
                   (let [p (parent-prefix prefix)]
                     (or (nil? p) (not (contains? abstract-prefixes p))))))
         set)))

(defn- best-abstract-prefix
  [abstract-prefixes ns-name]
  (->> abstract-prefixes
       (filter #(in-prefix? % ns-name))
       (sort-by count >)
       first))

(defn- infer-concrete-prefixes
  [records abstract-prefixes]
  (let [all-ns (set (map :namespace records))
        deps-by-ns (into {} (map (juxt :namespace :requires) records))
        module-abstract (into {} (map (juxt :namespace module-abstract?) records))
        abs-for-dep (fn [dep] (best-abstract-prefix abstract-prefixes dep))
        candidate-target (fn [ns-name]
                           (when-not (get module-abstract ns-name)
                             (let [deps (filter all-ns (get deps-by-ns ns-name))
                                   abs-deps (set (keep abs-for-dep deps))]
                               (when (= 1 (count abs-deps))
                                 (first abs-deps)))))
        candidates-by-abs (reduce (fn [acc ns-name]
                                    (if-let [target (candidate-target ns-name)]
                                      (update acc target (fnil conj #{}) ns-name)
                                      acc))
                                  {}
                                  all-ns)
        qualified-prefixes (fn [target nss]
                             (let [prefixes (->> nss (mapcat ns-prefixes) set)
                                   qualifies? (fn [prefix]
                                                (let [desc (filter #(in-prefix? prefix %) all-ns)
                                                      desc-candidates (filter nss desc)]
                                                  (and (seq desc)
                                                       (= (set desc) (set desc-candidates))
                                                       (every?
                                                        (fn [ns-name]
                                                          (let [deps (filter all-ns (get deps-by-ns ns-name))]
                                                            (every? #(in-prefix? target %) deps)))
                                                        desc))))]
                               (->> prefixes
                                    (filter qualifies?)
                                    (remove #(= % target))
                                    set)))]
    (->> candidates-by-abs
         (mapcat (fn [[target nss]]
                   (let [prefixes (qualified-prefixes target nss)]
                     (->> prefixes
                          (filter (fn [prefix]
                                    (let [p (parent-prefix prefix)]
                                      (or (nil? p) (not (contains? prefixes p))))))
                          vec))))
         set)))

(defn- prefix->component
  [root prefix]
  (let [suffix (if (str/starts-with? prefix (str root "."))
                 (subs prefix (inc (count root)))
                 prefix)]
    (keyword (str/replace suffix "." "-"))))

(defn- prefix->rule
  [root prefix]
  {:component (prefix->component root prefix)
   :match (if (re-find #"\." prefix)
            (str prefix "*")
            (str prefix ".*"))})

(defn- fallback-prefixes
  [root nss covered]
  (let [remaining (remove covered nss)]
    (->> remaining
         (keep (fn [ns-name]
                 (let [parts (str/split ns-name #"\.")
                       seg1 (second parts)]
                   (when seg1 (str root "." seg1)))))
         set)))

(defn- default-forbidden-deps
  [components]
  (let [present? (set components)
        pairs [[:application :atoms]
               [:application :ui]
               [:application :game-loop]
               [:application :test-utils]
               [:application :acceptance-parser]
               [:application :acceptance-generator]
               [:domain :atoms]
               [:domain :ui]
               [:domain :game-loop]
               [:domain :test-utils]
               [:domain :application]
               [:domain :acceptance-parser]
               [:domain :acceptance-generator]
               [:adapters :acceptance-parser]
               [:adapters :acceptance-generator]]]
    (->> pairs
         (filter (fn [[a b]] (and (present? a) (present? b))))
         vec)))

(defn- generate-starter-config
  ([]
   (generate-starter-config (:source-paths default-config)))
  ([source-paths]
   (let [records (source-ns-records source-paths (:include-exts default-config))
         nss (set (map :namespace records))
         roots (->> nss (map #(first (str/split % #"\."))) frequencies)
         root (or (first (first (sort-by (comp - val) roots))) "app")
         abstract-prefixes (infer-abstract-prefixes records)
         concrete-prefixes (infer-concrete-prefixes records abstract-prefixes)
         covered (fn [ns-name]
                   (or (some #(in-prefix? % ns-name) abstract-prefixes)
                       (some #(in-prefix? % ns-name) concrete-prefixes)))
         fallback (fallback-prefixes root nss covered)
         prefixes (->> (concat abstract-prefixes concrete-prefixes fallback)
                       set)
         rules (->> prefixes
                    (sort-by count >)
                    (map #(prefix->rule root %))
                    (reduce (fn [acc {:keys [component] :as rule}]
                              (if (contains? acc component)
                                acc
                                (assoc acc component rule)))
                            {})
                    vals
                    vec)
         components (map :component rules)]
     {:source-paths (vec source-paths)
      :component-rules rules
      :forbidden-dependencies (default-forbidden-deps components)
      :fail-on-cycles false
      :fail-on-violations true})))

(defn- write-config!
  [path cfg]
  (spit path (str (with-out-str (clojure.pprint/pprint cfg)))))

(defn- fmt-double [d]
  (format "%.3f" (double d)))

(defn- report-text
  [{:keys [component-stats component-edges violations cycles]}
   {:keys [max-distance distance-violations]}]
  (let [components (keys component-stats)]
    (println "Dependency Analysis")
    (println "===================")
    (println)
    (println (format "Components: %d" (count components)))
    (println (format "Component edges: %d" (count component-edges)))
    (println (format "Violations: %d" (count violations)))
    (println (format "Cycles: %d" (count cycles)))
    (println (format "Distance limit: %.3f" (double max-distance)))
    (println (format "Distance violations: %d" (count distance-violations)))
    (println)
    (println "Component Metrics")
    (println "-----------------")
    (println (format "%-18s %6s %7s %11s %11s %9s" "Component" "FanIn" "FanOut" "Instability" "Abstract" "Distance"))
    (doseq [[component {:keys [fan-in fan-out instability abstractness distance]}] component-stats]
      (println (format "%-18s %6d %7d %11s %11s %9s"
                       (str component)
                       fan-in
                       fan-out
                       (fmt-double instability)
                       (fmt-double abstractness)
                       (fmt-double distance))))
    (when (seq component-edges)
      (println)
      (println "Component Dependencies")
      (println "----------------------")
      (doseq [[from to] component-edges]
        (println (format "%s -> %s" from to))))
    (when (seq violations)
      (println)
      (println "Boundary Violations")
      (println "-------------------")
      (doseq [{:keys [from-component to-component from-ns to-ns]} violations]
        (println (format "%s -> %s  (%s -> %s)"
                         from-component to-component from-ns to-ns))))
    (when (seq cycles)
      (println)
      (println "Cycles")
      (println "------")
      (doseq [cycle cycles]
        (println (str/join " -> " (map str cycle)))))
    (when (seq distance-violations)
      (println)
      (println "Distance Violations")
      (println "-------------------")
      (doseq [[component distance] distance-violations]
        (println (format "%s distance=%s exceeds limit=%s"
                         component
                         (fmt-double distance)
                         (fmt-double max-distance)))))))

(defn- load-config
  [path]
  (if (and path (.exists (io/file path)))
    (edn/read-string (slurp path))
    {}))

(defn- usage!
  []
  (binding [*out* *err*]
    (println "Usage: clj -M:check-dependencies [config.edn] [--format text|edn] [--max-distance N] [--init|--force-init]"))
  2)

(defn -main
  [& args]
  (let [[config-path args*] (if (and (seq args) (not (str/starts-with? (first args) "--")))
                              [(first args) (rest args)]
                              ["dependency-tool.edn" args])]
    (loop [remaining args*
           fmt :text
           init? false
           force-init? false
           max-distance 0.0]
      (if (empty? remaining)
        (let [config-file (io/file config-path)]
          (cond
            (and init? force-init?)
            (System/exit (usage!))

            (or force-init? (and init? (not (.exists config-file))) (not (.exists config-file)))
            (let [cfg (generate-starter-config)
                  reason (cond
                           force-init? "Recreated"
                           init? "Created"
                           :else "Created")]
              (write-config! config-path cfg)
              (println (format "%s starter dependency config at %s" reason config-path))
              (println "Review the generated component rules and boundary restrictions, then rerun.")
              (System/exit 0))

            init?
            (do
              (println (format "Config already exists at %s (not overwritten)." config-path))
              (System/exit 0))

            :else
            (let [result (analyze-project (load-config config-path))
                  distance-violations (->> (:component-stats result)
                                           (filter (fn [[_ {:keys [distance]}]]
                                                     (> (double distance) (double max-distance))))
                                           (mapv (fn [[component {:keys [distance]}]]
                                                   [component distance])))
                  has-violations (seq (:violations result))
                  has-cycles (seq (:cycles result))
                  has-distance-violations (seq distance-violations)
                  fail? (or (and has-violations (get-in result [:config :fail-on-violations] true))
                            (and has-cycles (get-in result [:config :fail-on-cycles] true))
                            has-distance-violations)]
              (case fmt
                :edn (prn result)
                :text (report-text result {:max-distance max-distance
                                           :distance-violations distance-violations})
                (do
                  (binding [*out* *err*]
                    (println "Unsupported format:" fmt))
                  (System/exit 2)))
              (when fail?
                (System/exit 1)))))
        (let [[arg & more] remaining]
          (cond
            (= arg "--init")
            (recur more fmt true force-init? max-distance)

            (= arg "--force-init")
            (recur more fmt init? true max-distance)

            (= arg "--format")
            (if-let [format-arg (first more)]
              (recur (rest more) (keyword format-arg) init? force-init? max-distance)
              (System/exit (usage!)))

            (= arg "--max-distance")
            (if-let [raw (first more)]
              (let [parsed (try
                             (Double/parseDouble raw)
                             (catch Exception _
                               ::invalid-max-distance))]
                (if (= ::invalid-max-distance parsed)
                  (do
                    (binding [*out* *err*]
                      (println "Invalid value for --max-distance:" raw))
                    (System/exit 2))
                  (recur (rest more) fmt init? force-init? parsed)))
              (System/exit (usage!)))

            :else
            (System/exit (usage!))))))))

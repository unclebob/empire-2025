(ns empire.architecture.dependency-tool
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint]
            [clojure.string :as str]))

(def default-config
  {:source-paths ["src"]
   :include-exts #{".clj" ".cljc" ".cljs"}
   :component-rules []
   :forbidden-dependencies []
   :allowed-exceptions []
   :abstract-patterns []
   :fail-on-cycles true
   :fail-on-violations true})

(def ^:private preferred-component-order
  ["application" "domain" "adapters" "atoms" "ui" "game-loop" "computer" "player"
   "movement" "units" "containers" "combat" "acceptance-parser" "acceptance-generator"
   "debug" "test-utils"])

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

(defn- extract-requires
  [ns-decl]
  (->> (drop 2 ns-decl)
       (filter seq?)
       (filter #(keyword? (first %)))
       (filter #(= :require (first %)))
       (mapcat rest)
       (map require-target)
       (filter symbol?)
       set))

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

(defn- compile-abstract-matchers
  [patterns]
  (mapv pattern->matcher patterns))

(defn- abstract-var?
  [op-name sym ns-name abstract-matchers]
  (or (#{"defprotocol" "defmulti"} op-name)
      (:abstract (meta sym))
      (some #(% (str ns-name "/" (name sym))) abstract-matchers)))

(defn- var-stats
  [forms ns-name abstract-matchers]
  (reduce
   (fn [{:keys [public-count abstract-count] :as acc} form]
     (if (seq? form)
       (let [op (first form)
             op-name (when (symbol? op) (name op))
             sym (var-symbol form)]
         (if (and op-name (def-ops op-name) sym (not (private-var? op-name sym)))
           (-> acc
               (assoc :public-count (inc public-count))
               (update :abstract-count + (if (abstract-var? op-name sym ns-name abstract-matchers) 1 0)))
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
        abstract-matchers (compile-abstract-matchers (:abstract-patterns cfg))
        files (source-files (:source-paths cfg) (:include-exts cfg))
        parsed (->> files
                    (map (fn [f]
                           (let [forms (read-forms f)
                                 ns-decl (first (filter ns-form? forms))]
                             (when ns-decl
                               (let [ns-name (second ns-decl)
                                     component (component-for-ns component-rules ns-name)
                                     requires (extract-requires ns-decl)
                                     stats (var-stats forms (str ns-name) abstract-matchers)]
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

(defn- file-namespaces
  [source-paths include-exts]
  (->> (source-files source-paths include-exts)
       (map (fn [f]
              (let [forms (read-forms f)
                    ns-decl (first (filter ns-form? forms))]
                (when ns-decl (second ns-decl)))))
       (filter symbol?)
       set))

(defn- infer-component-rule
  [root ns-sym]
  (let [parts (str/split (str ns-sym) #"\.")
        seg1 (second parts)
        seg2 (nth parts 2 nil)]
    (cond
      (nil? seg1) nil
      (and (= "acceptance" seg1) (#{"parser" "generator"} seg2))
      {:component (keyword (str "acceptance-" seg2))
       :match (str root ".acceptance." seg2 "*")}
      (= "test-utils" seg1)
      {:component :test-utils :match (str root ".test-utils")}
      :else
      {:component (keyword seg1)
       :match (str root "." seg1 "*")})))

(defn- compare-component-name
  [a b]
  (let [ia (.indexOf preferred-component-order a)
        ib (.indexOf preferred-component-order b)
        ra (if (neg? ia) 999 ia)
        rb (if (neg? ib) 999 ib)]
    (if (= ra rb)
      (compare a b)
      (compare ra rb))))

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
   (let [nss (file-namespaces source-paths (:include-exts default-config))
         roots (->> nss (map #(first (str/split (str %) #"\."))) frequencies)
         root (or (first (first (sort-by (comp - val) roots))) "app")
         rules (->> nss
                    (map #(infer-component-rule root %))
                    (filter some?)
                    (sort-by (fn [{:keys [component]}] (name component)) (fn [a b] (compare-component-name a b)))
                    (reduce (fn [acc {:keys [component] :as rule}]
                              (if (contains? acc component) acc (assoc acc component rule)))
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
  [{:keys [component-stats component-edges violations cycles]}]
  (let [components (keys component-stats)]
    (println "Dependency Analysis")
    (println "===================")
    (println)
    (println (format "Components: %d" (count components)))
    (println (format "Component edges: %d" (count component-edges)))
    (println (format "Violations: %d" (count violations)))
    (println (format "Cycles: %d" (count cycles)))
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
        (println (str/join " -> " (map str cycle)))))))

(defn- load-config
  [path]
  (if (and path (.exists (io/file path)))
    (edn/read-string (slurp path))
    {}))

(defn- usage!
  []
  (binding [*out* *err*]
    (println "Usage: clj -M:dependency-tool [config.edn] [--format text|edn] [--init|--force-init]"))
  2)

(defn -main
  [& args]
  (let [[config-path args*] (if (and (seq args) (not (str/starts-with? (first args) "--")))
                              [(first args) (rest args)]
                              ["dependency-tool.edn" args])]
    (loop [remaining args*
           fmt :text
           init? false
           force-init? false]
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
                  has-violations (seq (:violations result))
                  has-cycles (seq (:cycles result))
                  fail? (or (and has-violations (get-in result [:config :fail-on-violations] true))
                            (and has-cycles (get-in result [:config :fail-on-cycles] true)))]
              (case fmt
                :edn (prn result)
                :text (report-text result)
                (do
                  (binding [*out* *err*]
                    (println "Unsupported format:" fmt))
                  (System/exit 2)))
              (when fail?
                (System/exit 1)))))
        (let [[arg & more] remaining]
          (cond
            (= arg "--init")
            (recur more fmt true force-init?)

            (= arg "--force-init")
            (recur more fmt init? true)

            (= arg "--format")
            (if-let [format-arg (first more)]
              (recur (rest more) (keyword format-arg) init? force-init?)
              (System/exit (usage!)))

            :else
            (System/exit (usage!))))))))

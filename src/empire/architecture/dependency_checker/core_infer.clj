;; mutation-tested: 2026-03-04
(ns empire.architecture.dependency-checker.core-infer
  (:require [clojure.pprint]
            [clojure.string :as str]
            [empire.architecture.dependency-checker.core-base-config :as cfg]
            [empire.architecture.dependency-checker.core-base-dependencies :as deps]
            [empire.architecture.dependency-checker.core-base-reader :as reader]
            [empire.architecture.dependency-checker.core-base-stats :as stats]))

(defn source-ns-records
  [source-paths include-exts]
  (->> (cfg/source-files source-paths include-exts)
       (map (fn [f]
              (let [forms (reader/read-forms f)
                    ns-decl (first (filter reader/ns-form? forms))]
                (when ns-decl
                  (let [ns-name (second ns-decl)
                        var-stats (stats/var-stats forms)]
                    {:namespace (str ns-name)
                     :requires (set (map str (deps/extract-dependencies forms ns-decl)))
                     :public-count (:public-count var-stats)
                     :abstract-count (:abstract-count var-stats)})))))
       (filter some?)
       vec))

(defn ns-prefixes
  [ns-name]
  (let [parts (str/split ns-name #"\.")]
    (map #(str/join "." (take % parts))
         (range 1 (inc (count parts))))))

(defn parent-prefix
  [prefix]
  (let [parts (str/split prefix #"\.")]
    (when (> (count parts) 1)
      (str/join "." (butlast parts)))))

(defn in-prefix?
  [prefix ns-name]
  (or (= prefix ns-name)
      (str/starts-with? ns-name (str prefix "."))))

(defn module-abstract?
  [{:keys [public-count abstract-count]}]
  (and (pos? public-count) (= public-count abstract-count)))

(defn infer-abstract-prefixes
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

(defn best-abstract-prefix
  [abstract-prefixes ns-name]
  (->> abstract-prefixes
       (filter #(in-prefix? % ns-name))
       (sort-by count >)
       first))

(defn infer-concrete-prefixes
  [records abstract-prefixes]
  (let [all-ns (set (map :namespace records))
        deps-by-ns (into {} (map (juxt :namespace :requires) records))
        module-abstract (into {} (map (juxt :namespace module-abstract?) records))
        abs-for-dep (fn [dep] (best-abstract-prefix abstract-prefixes dep))
        candidate-target (fn [ns-name]
                           (when-not (get module-abstract ns-name)
                             (let [dep-namespaces (filter all-ns (get deps-by-ns ns-name))
                                   abs-deps (set (keep abs-for-dep dep-namespaces))]
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
                                                          (let [dep-namespaces (filter all-ns (get deps-by-ns ns-name))]
                                                            (every? #(in-prefix? target %) dep-namespaces)))
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

(defn prefix->component
  [root prefix]
  (let [suffix (if (str/starts-with? prefix (str root "."))
                 (subs prefix (inc (count root)))
                 prefix)]
    (keyword (str/replace suffix "." "-"))))

(defn prefix->rule
  [root prefix]
  {:component (prefix->component root prefix)
   :match (if (re-find #"\." prefix)
            (str prefix "*")
            (str prefix ".*"))})

(defn fallback-prefixes
  [root nss covered]
  (let [remaining (remove covered nss)]
    (->> remaining
         (keep (fn [ns-name]
                 (let [parts (str/split ns-name #"\.")
                       seg1 (second parts)]
                   (when seg1 (str root "." seg1)))))
         set)))

(defn default-forbidden-deps
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

(defn generate-starter-config
  ([]
   (generate-starter-config (:source-paths cfg/default-config)))
  ([source-paths]
   (let [records (source-ns-records source-paths (:include-exts cfg/default-config))
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
      :utility-components []
      :forbidden-dependencies (default-forbidden-deps components)
      :fail-on-cycles false
      :fail-on-violations true})))

(defn write-config!
  [path cfg]
  (spit path (str (with-out-str (clojure.pprint/pprint cfg)))))

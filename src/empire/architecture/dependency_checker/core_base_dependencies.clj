(ns empire.architecture.dependency-checker.core-base-dependencies
  (:require [clojure.set :as set]))

(defn require-target
  [entry]
  (cond
    (symbol? entry) entry
    (vector? entry) (when (symbol? (first entry)) (first entry))
    :else nil))

(defn ns-clause-entries
  [ns-decl clause]
  (->> (drop 2 ns-decl)
       (filter seq?)
       (filter #(keyword? (first %)))
       (filter #(= clause (first %)))
       (mapcat rest)))

(defn quote-unwrapped
  [form]
  (if (and (seq? form) (= 'quote (first form)))
    (second form)
    form))

(defn dependency-symbol->namespace
  [sym]
  (if (qualified-symbol? sym)
    (symbol (namespace sym))
    sym))

(defn require-arg-targets
  [arg]
  (let [arg* (quote-unwrapped arg)]
    (cond
      (symbol? arg*) [arg*]
      (vector? arg*) (if-let [target (require-target arg*)] [target] [])
      :else [])))

(defn extract-ns-clause-deps
  [ns-decl clause]
  (->> (ns-clause-entries ns-decl clause)
       (mapcat require-arg-targets)
       (map dependency-symbol->namespace)
       (filter symbol?)
       set))

(defn walk-forms
  [forms]
  (tree-seq coll? seq forms))

(defn call-name
  [form]
  (when (seq? form)
    (let [op (first form)]
      (when (symbol? op)
        (name op)))))

(defn called?
  [form callee]
  (= callee (call-name form)))

(defn extract-direct-requires
  [forms]
  (->> (walk-forms forms)
       (filter #(called? % "require"))
       (mapcat rest)
       (mapcat require-arg-targets)
       (map dependency-symbol->namespace)
       (filter symbol?)
       set))

(defn ns-target
  [arg]
  (let [arg* (quote-unwrapped arg)]
    (cond
      (symbol? arg*) arg*
      (string? arg*) (symbol arg*)
      :else nil)))

(defn symbol-target-namespace
  [arg]
  (some-> arg
          quote-unwrapped
          dependency-symbol->namespace))

(defn qualified-symbol-namespace
  [arg]
  (let [sym (quote-unwrapped arg)]
    (when (qualified-symbol? sym)
      (dependency-symbol->namespace sym))))

(def ^:private dynamic-lookup-callees
  #{"requiring-resolve" "resolve" "ns-resolve" "find-ns" "the-ns"})

(def ^:private dynamic-lookup-extractors
  {"requiring-resolve" (fn [arg1 _] [(symbol-target-namespace arg1)])
   "resolve" (fn [arg1 _] [(qualified-symbol-namespace arg1)])
   "ns-resolve" (fn [arg1 arg2] [(ns-target arg1)
                                 (qualified-symbol-namespace arg2)])
   "find-ns" (fn [arg1 _] [(ns-target arg1)])
   "the-ns" (fn [arg1 _] [(ns-target arg1)])})

(defn dynamic-lookup-targets
  [form]
  (let [[_ arg1 arg2] form
        callee (call-name form)]
    (if-let [extractor (get dynamic-lookup-extractors callee)]
      (extractor arg1 arg2)
      [])))

(defn extract-dynamic-namespace-lookups
  [forms]
  (->> (walk-forms forms)
       (filter seq?)
       (mapcat dynamic-lookup-targets)
       (filter symbol?)
       set))

(defn extract-dynamic-lookup-warnings
  [forms]
  (->> (walk-forms forms)
       (filter seq?)
       (keep (fn [form]
               (let [callee (call-name form)]
                 (when (contains? dynamic-lookup-callees callee)
                   {:kind :dynamic-namespace-lookup
                    :callee callee
                    :targets (->> (dynamic-lookup-targets form)
                                  (filter symbol?)
                                  (map str)
                                  set
                                  sort
                                  vec)}))))
       vec))

(defn extract-dependencies
  [forms ns-decl]
  (set/union (extract-ns-clause-deps ns-decl :require)
             (extract-ns-clause-deps ns-decl :use)
             (extract-ns-clause-deps ns-decl :import)
             (extract-direct-requires forms)
             (extract-dynamic-namespace-lookups forms)))

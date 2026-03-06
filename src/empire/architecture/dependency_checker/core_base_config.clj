;; mutation-tested: 2026-03-04
(ns empire.architecture.dependency-checker.core-base-config
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(def default-config
  {:source-paths ["src"]
   :include-exts #{".clj" ".cljc" ".cljs"}
   :component-rules []
   :allowed-dependencies {}
   :allowed-exceptions []
   :fail-on-cycles true
   :fail-on-violations true})

(defn glob->regex
  [pattern]
  (re-pattern
   (str "^"
        (-> pattern
            (str/replace "." "\\.")
            (str/replace "*" ".*"))
        "$")))

(defn exact-regex
  [pattern]
  (re-pattern (str "^" (java.util.regex.Pattern/quote pattern) "$")))

(defn wildcard-or-regex?
  [pattern]
  (or (str/includes? pattern "*")
      (str/starts-with? pattern "^")))

(defn string-pattern->regex
  [pattern]
  (if (wildcard-or-regex? pattern)
    (if (str/starts-with? pattern "^")
      (re-pattern pattern)
      (glob->regex pattern))
    (exact-regex pattern)))

(defn pattern->matcher
  [pattern]
  (cond
    (instance? java.util.regex.Pattern pattern)
    (fn [s] (boolean (re-find pattern s)))

    (keyword? pattern)
    (let [exact (name pattern)]
      (fn [s] (= exact s)))

    (string? pattern)
    (let [rx (string-pattern->regex pattern)]
      (fn [s] (boolean (re-find rx s))))

    :else
    (fn [_] false)))

(defn normalize-component-rule
  [rule]
  (cond
    (and (vector? rule) (= 2 (count rule)))
    {:component (first rule) :match (second rule)}

    (and (map? rule) (contains? rule :component))
    rule

    :else
    (throw (ex-info "Invalid component rule" {:rule rule}))))

(defn match-patterns
  [rule]
  (let [raw-matches (or (:match rule) (:matches rule) (:pattern rule))]
    (cond
      (nil? raw-matches) []
      (sequential? raw-matches) raw-matches
      :else [raw-matches])))

(defn compile-normalized-rule
  [rule]
  (let [matchers (mapv pattern->matcher (match-patterns rule))]
    {:component (:component rule)
     :matches? (fn [ns-name]
                 (boolean (some #(% ns-name) matchers)))}))

(defn compile-component-rules
  [rules]
  (->> rules
       (map normalize-component-rule)
       (map compile-normalized-rule)
       vec))

(defn component-for-ns
  [compiled-rules ns-sym]
  (let [ns-name (str ns-sym)]
    (some (fn [{:keys [component matches?]}]
            (when (matches? ns-name) component))
          compiled-rules)))

(defn source-file?
  [^java.io.File f include-exts]
  (and (.isFile f)
       (some #(str/ends-with? (.getName f) %) include-exts)))

(defn source-files
  [paths include-exts]
  (->> paths
       (map io/file)
       (filter #(.exists ^java.io.File %))
       (mapcat file-seq)
       (filter #(source-file? % include-exts))))

(defn abs-num
  [n]
  (if (neg? n) (- n) n))

(defn normalize-forbidden-rule
  [rule]
  (cond
    (map? rule) rule
    (and (vector? rule) (= 2 (count rule))) {:from (first rule) :to (second rule)}
    :else (throw (ex-info "Invalid forbidden dependency rule" {:rule rule}))))

(defn compile-exception
  [ex]
  (let [from-ns-m (when-let [p (:from-ns ex)] (pattern->matcher p))
        to-ns-m (when-let [p (:to-ns ex)] (pattern->matcher p))]
    (assoc ex
           :from-ns-match? from-ns-m
           :to-ns-match? to-ns-m)))

(defn exception-matches?
  [ex {:keys [from-component to-component from-ns to-ns]}]
  (and (if (contains? ex :from-component) (= (:from-component ex) from-component) true)
       (if (contains? ex :to-component) (= (:to-component ex) to-component) true)
       (if-let [m (:from-ns-match? ex)] (m from-ns) true)
       (if-let [m (:to-ns-match? ex)] (m to-ns) true)))

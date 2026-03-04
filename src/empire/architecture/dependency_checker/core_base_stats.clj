(ns empire.architecture.dependency-checker.core-base-stats
  (:require [clojure.string :as str]))

(def ^:private def-ops
  #{"def" "defonce" "defmacro" "defn" "defn-" "defmulti" "defprotocol"})

(defn var-symbol
  [form]
  (when (and (seq? form) (<= 2 (count form)))
    (let [sym (second form)]
      (when (symbol? sym) sym))))

(defn private-var?
  [op-name sym]
  (or (= op-name "defn-")
      (:private (meta sym))
      (str/starts-with? (name sym) "-")))

(defn abstract-var?
  [op-name]
  (#{"defprotocol" "defmulti"} op-name))

(defn var-stats
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

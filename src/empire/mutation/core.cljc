(ns empire.mutation.core
  (:require [clojure.tools.reader :as reader]
            [clojure.tools.reader.reader-types :as reader-types]
            [empire.mutation.mutations :as mutations]))

(defn read-source-forms
  "Parse a source string into a vector of top-level forms.
   Handles .cljc reader conditionals."
  [source-str]
  (let [rdr (reader-types/source-logging-push-back-reader source-str)
        opts {:read-cond :allow :features #{:clj} :eof ::eof}]
    (loop [forms []]
      (let [form (reader/read opts rdr)]
        (if (= ::eof form)
          forms
          (recur (conj forms form)))))))

(defn discover-all-mutations
  "Find all mutation sites across a vector of top-level forms.
   Returns a flat vector of mutation sites with :form-index added."
  [forms]
  (vec (mapcat
         (fn [idx form]
           (map #(assoc % :form-index idx)
                (mutations/find-mutations form)))
         (range) forms)))

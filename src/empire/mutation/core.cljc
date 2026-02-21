(ns empire.mutation.core
  (:require [clojure.string :as str]
            [clojure.tools.reader :as reader]
            [clojure.tools.reader.reader-types :as reader-types]
            [empire.mutation.mutations :as mutations]
            [empire.mutation.runner :as runner]))

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

(defn- serialize-forms
  "Serialize a vector of forms to a string."
  [forms]
  (str/join "\n\n" (map pr-str forms)))

(defn mutate-and-test
  "Apply one mutation, write file, run spec, restore original.
   Returns {:site site :result :killed/:survived}."
  [source-path original-content forms site spec-path]
  (let [mutated-forms (update forms (:form-index site)
                              #(mutations/apply-mutation % (:index site)))]
    (try
      (spit source-path (serialize-forms mutated-forms))
      {:site site :result (runner/run-spec spec-path)}
      (finally
        (spit source-path original-content)))))

(defn- result-label [r]
  (if (= :killed (:result r)) "KILLED" "SURVIVED"))

(defn- format-line [i total r]
  (format "[%3d/%d] %-8s  %s%n"
          (inc i) total (result-label r) (:description (:site r))))

(defn- format-survivor [r]
  (format "  #%d  %s%n" (inc (or (:index (:site r)) 0)) (:description (:site r))))

(defn format-report
  "Format mutation testing results as a console report string."
  [source-path spec-path results]
  (let [total (count results)
        killed (count (filter #(= :killed (:result %)) results))
        pct (if (zero? total) 0.0 (* 100.0 (/ killed total)))
        survivors (filter #(= :survived (:result %)) results)]
    (str
      (format "=== Mutation Testing: %s ===%n" source-path)
      (format "Spec: %s%n" spec-path)
      (format "Found %d mutation sites.%n%n" total)
      (apply str (map-indexed #(format-line %1 total %2) results))
      (format "%n=== Summary ===%n")
      (format "%d/%d mutants killed (%.1f%%)%n" killed total pct)
      (when (seq survivors)
        (str "Survivors:\n"
             (apply str (map format-survivor survivors)))))))

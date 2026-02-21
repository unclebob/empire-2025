(ns empire.mutation.core
  (:require [clojure.string :as str]
            [clojure.tools.reader :as reader]
            [clojure.tools.reader.reader-types :as reader-types]
            [empire.mutation.coverage :as coverage]
            [empire.mutation.mutations :as mutations]
            [empire.mutation.runner :as runner])
  (:import [java.io File]))

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

(defn partition-by-coverage
  "Split sites into [covered uncovered] based on covered-lines set.
   Sites with nil :line are treated as covered. If covered-lines is nil,
   all sites are treated as covered."
  [sites covered-lines]
  (if (nil? covered-lines)
    [sites []]
    (let [covered? #(or (nil? (:line %)) (contains? covered-lines (:line %)))
          grouped (group-by covered? sites)]
      [(vec (get grouped true [])) (vec (get grouped false []))])))

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
  [source-path spec-path results uncovered-count]
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
      (when (pos? uncovered-count)
        (format "%d uncovered mutations skipped%n" uncovered-count))
      (when (seq survivors)
        (str "Survivors:\n"
             (apply str (map format-survivor survivors)))))))

(defn validate-args
  "Validate command-line arguments. Returns {:source-path ... :spec-path ...}
   or {:error \"message\"}."
  [args]
  (cond
    (empty? args)
    {:error "Usage: clj -M:mutate <source-file.cljc>"}

    (not (.exists (File. (first args))))
    {:error (str "Source file not found: " (first args))}

    :else
    (let [spec-path (runner/source->spec-path (first args))]
      (if (runner/spec-exists? spec-path)
        {:source-path (first args) :spec-path spec-path}
        {:error (str "No spec found at: " spec-path)}))))

(defn- print-progress [i total result site]
  (println (format "[%3d/%d] %-8s  %s"
                   (inc i) total
                   (result-label result)
                   (:description site)))
  (flush))

(defn- print-uncovered [uncovered]
  (when (seq uncovered)
    (println (format "\n=== Coverage Gaps (%d mutations on uncovered lines) ==="
                     (count uncovered)))
    (doseq [site uncovered]
      (println (format "  line %d: %s" (:line site) (:description site))))))

(defn- print-summary [killed total pct survivors uncovered-count]
  (println (format "\n=== Summary ==="))
  (println (format "%d/%d mutants killed (%.1f%%)" killed total pct))
  (when (pos? uncovered-count)
    (println (format "%d uncovered mutations skipped" uncovered-count)))
  (when (seq survivors)
    (println "Survivors:")
    (doseq [r survivors]
      (println (format "  #%d  %s"
                       (inc (:index (:site r)))
                       (:description (:site r)))))))

(defn run-mutation-testing
  "Run mutation testing on a single source file."
  [source-path spec-path]
  (let [original-content (slurp source-path)
        forms (read-source-forms original-content)
        all-sites (discover-all-mutations forms)
        covered-lines (coverage/load-coverage source-path)
        [sites uncovered] (partition-by-coverage all-sites covered-lines)]
    (println (format "=== Mutation Testing: %s ===" source-path))
    (println (format "Spec: %s" spec-path))
    (println (format "Found %d mutation sites (%d covered, %d uncovered).\n"
                     (count all-sites) (count sites) (count uncovered)))
    (print-uncovered uncovered)
    (let [results (doall
                    (map-indexed
                      (fn [i site]
                        (let [result (mutate-and-test source-path original-content
                                                      forms site spec-path)]
                          (print-progress i (count sites) result site)
                          result))
                      sites))
          killed (count (filter #(= :killed (:result %)) results))
          total (count results)
          pct (if (zero? total) 0.0 (* 100.0 (/ killed total)))
          survivors (filter #(= :survived (:result %)) results)]
      (print-summary killed total pct survivors (count uncovered)))))

(defn -main [& args]
  (let [validated (validate-args (vec args))]
    (if (:error validated)
      (do (println (:error validated))
          (System/exit 1))
      (run-mutation-testing (:source-path validated) (:spec-path validated)))))

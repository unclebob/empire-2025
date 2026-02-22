(ns empire.mutation.core
  (:require [clojure.string :as str]
            [clojure.tools.reader :as reader]
            [clojure.tools.reader.reader-types :as reader-types]
            [empire.mutation.coverage :as coverage]
            [empire.mutation.mutations :as mutations]
            [empire.mutation.runner :as runner])
  (:import [java.io File]))

(def ^:private mutation-comment-re #"^;; mutation-tested: (\d{4}-\d{2}-\d{2})")

(defn extract-mutation-date
  "Extract the mutation test date from a source file's content.
   Returns the date string or nil."
  [content]
  (when-let [m (re-find mutation-comment-re content)]
    (second m)))

(defn stamp-mutation-date
  "Add or replace the mutation-tested comment at the top of source content."
  [content date-str]
  (let [comment-line (str ";; mutation-tested: " date-str)]
    (if (re-find mutation-comment-re content)
      (str/replace content mutation-comment-re comment-line)
      (str comment-line "\n" content))))

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
  (format "[%3d/%d] %-8s  L%-4d %s%n"
          (inc i) total (result-label r) (or (:line (:site r)) 0) (:description (:site r))))

(defn- format-survivor [r]
  (format "  #%d  L%-4d %s%n" (inc (or (:index (:site r)) 0)) (or (:line (:site r)) 0) (:description (:site r))))

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

(defn- parse-lines-arg
  "Parse --lines L1,L2,... into a set of integers, or nil if not present."
  [args]
  (when-let [idx (some #(when (= "--lines" (nth args %)) %) (range (count args)))]
    (when (< (inc idx) (count args))
      (set (map #(parse-long (str/trim %))
                (str/split (nth args (inc idx)) #","))))))

(defn validate-args
  "Validate command-line arguments. Returns {:source-path ... :spec-path ... :lines ...}
   or {:error \"message\"}."
  [args]
  (cond
    (empty? args)
    {:error "Usage: clj -M:mutate <source-file.cljc> [--lines L1,L2,...]"}

    (not (.exists (File. (first args))))
    {:error (str "Source file not found: " (first args))}

    :else
    (let [spec-path (runner/source->spec-path (first args))
          lines (parse-lines-arg args)]
      (if (runner/spec-exists? spec-path)
        {:source-path (first args) :spec-path spec-path :lines lines}
        {:error (str "No spec found at: " spec-path)}))))

(defn- print-progress [i total result site]
  (println (format "[%3d/%d] %-8s  L%-4d %s"
                   (inc i) total
                   (result-label result)
                   (or (:line site) 0)
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
      (println (format "  #%d  L%-4d %s"
                       (inc (:index (:site r)))
                       (or (:line (:site r)) 0)
                       (:description (:site r)))))))

(defn- today-str []
  (.format (java.time.LocalDate/now)
           (java.time.format.DateTimeFormatter/ISO_LOCAL_DATE)))

(defn- filter-by-lines
  "Filter mutation sites to only those on the specified lines."
  [sites lines]
  (if lines
    (vec (filter #(contains? lines (:line %)) sites))
    sites))

(defn run-mutation-testing
  "Run mutation testing on a single source file.
   Optional lines arg: set of line numbers to restrict testing to."
  ([source-path spec-path] (run-mutation-testing source-path spec-path nil))
  ([source-path spec-path lines]
   (let [original-content (slurp source-path)
         prev-date (extract-mutation-date original-content)
         forms (read-source-forms original-content)
         all-sites (discover-all-mutations forms)
         covered-lines (coverage/load-coverage source-path)
         [covered-sites uncovered] (partition-by-coverage all-sites covered-lines)
         sites (filter-by-lines covered-sites lines)]
     (println (format "=== Mutation Testing: %s ===" source-path))
     (println (format "Spec: %s" spec-path))
     (when prev-date
       (println (format "Previous mutation test: %s" prev-date)))
     (println (format "Found %d mutation sites (%d covered, %d uncovered)."
                      (count all-sites) (count covered-sites) (count uncovered)))
     (when lines
       (println (format "Filtering to lines: %s → %d mutations to test."
                        (str/join "," (sort lines)) (count sites))))
     (println)
     (when-not lines (print-uncovered uncovered))
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
       (print-summary killed total pct survivors (if lines 0 (count uncovered)))
       (when-not lines
         (spit source-path (stamp-mutation-date original-content (today-str))))))))

(defn -main [& args]
  (let [validated (validate-args (vec args))]
    (if (:error validated)
      (do (println (:error validated))
          (System/exit 1))
      (run-mutation-testing (:source-path validated)
                            (:spec-path validated)
                            (:lines validated)))))

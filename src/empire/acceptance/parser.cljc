(ns empire.acceptance.parser
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [empire.config :as config]
            [empire.acceptance.parser.helpers :as h]
            [empire.acceptance.parser.given :as given]
            [empire.acceptance.parser.when :as when-parser]
            [empire.acceptance.parser.then :as then-parser]))

;; --- Test splitting ---

(defn- classify-directive [trimmed]
  (cond
    (str/starts-with? trimmed "GIVEN") :given
    (str/starts-with? trimmed "WHEN") :when
    (or (str/starts-with? trimmed "THEN")
        (re-matches #"^and\s+.*" trimmed)) :then
    (str/starts-with? trimmed "WHERE") :where
    :else nil))

(defn- classify-line [trimmed in-header?]
  (cond
    (h/separator-line? trimmed) :separator
    (and in-header? (str/starts-with? trimmed ";")) :header-comment
    (h/blank-or-comment? trimmed) :blank
    :else (or (classify-directive trimmed) :content)))

(def ^:private section-keys
  {:given :given-lines :when :when-lines :then :then-lines :where :where-lines})

(defn- handle-separator [{:keys [in-header current-test] :as state}]
  (if in-header
    (assoc state :in-header false)
    (cond-> (assoc state :in-header true :header-desc nil :current-test nil :section nil)
      current-test (update :tests conj current-test))))

(defn- ensure-test-started [state line-num]
  (if (:current-test state)
    state
    (assoc state :current-test {:line line-num
                                :description (or (:header-desc state) "")
                                :given-lines [] :when-lines [] :then-lines [] :where-lines []})))

(defn- add-to-section [state section trimmed]
  (cond-> (assoc state :section section)
    (:current-test state)
    (update-in [:current-test (section-keys section)] conj trimmed)))

(defn- add-content-line [state trimmed]
  (cond-> state
    (and (:current-test state) (:section state))
    (update-in [:current-test (section-keys (:section state))] conj trimmed)))

(defn- process-test-line [state [line-num line]]
  (let [trimmed (str/trim line)]
    (case (classify-line trimmed (:in-header state))
      :separator (handle-separator state)
      :header-comment (assoc state :header-desc (str/trim (subs trimmed 1)))
      :given (-> state (ensure-test-started line-num) (add-to-section :given trimmed))
      :when (add-to-section state :when trimmed)
      :then (add-to-section state :then trimmed)
      :where (assoc state :section :where)
      :blank state
      :content (add-content-line state trimmed))))

(defn split-into-tests
  "Split lines (with 1-based line numbers) into test groups.
   Returns [{:line N :description \"...\" :given-lines [...] :when-lines [...] :then-lines [...]}]"
  [lines]
  (let [indexed (map-indexed (fn [i l] [(inc i) l]) lines)
        final (reduce process-test-line
                      {:tests [] :current-test nil :section nil :in-header false :header-desc nil}
                      indexed)]
    (cond-> (:tests final)
      (:current-test final) (conj (:current-test final)))))

;; --- WHERE table expansion ---

(defn- parse-where-header [line]
  (mapv str/trim (str/split line #"\|")))

(defn- parse-where-row [line]
  (mapv str/trim (str/split line #"\|")))

(defn- substitute-vars [line bindings]
  (reduce (fn [s [var-name value]]
            (str/replace s (str "<" var-name ">") value))
          line bindings))

(defn- expand-one-where
  [{:keys [description line given-lines when-lines then-lines where-lines]}]
  (let [header (parse-where-header (first where-lines))
        rows (->> (rest where-lines)
                  (map parse-where-row)
                  (remove #(every? str/blank? %)))]
    (mapv (fn [row]
            (let [bindings (zipmap header row)
                  sub (fn [lines] (mapv #(substitute-vars % bindings) lines))
                  label (str/join ", " (map #(str %1 "=" %2) header row))]
              {:line line
               :description (str description " (" label ")")
               :given-lines (sub given-lines)
               :when-lines (sub when-lines)
               :then-lines (sub then-lines)
               :where-lines []}))
          rows)))

(defn expand-where-tables [test-groups]
  (vec (mapcat (fn [group]
                 (if (seq (:where-lines group))
                   (expand-one-where group)
                   [group]))
               test-groups)))

;; --- Context building ---

(defn- unit-chars-from-map [rows]
  (into #{}
        (comp (mapcat seq) (map str)
              (filter #(or (contains? h/player-unit-chars %)
                           (contains? h/computer-unit-chars %))))
        rows))

(defn- unit-type-from-given [g]
  (case (:type g)
    :map (unit-chars-from-map (:rows g))
    :unit-props #{(:unit g)}
    :waiting-for-input #{(:unit g)}
    #{}))

(defn- extract-unit-types-from-givens [givens]
  (into #{} (mapcat unit-type-from-given) givens))

(defn- has-waiting-for-input? [givens]
  (some #(= :waiting-for-input (:type %)) givens))

;; --- Top-level parsing ---

(defn parse-test
  "Parse a single test group into IR."
  [{:keys [line description given-lines when-lines then-lines]}]
  (let [{:keys [givens context]} (given/parse-given given-lines {})
        unit-types (extract-unit-types-from-givens givens)
        wfi (has-waiting-for-input? givens)
        when-ctx {:has-waiting-for-input wfi
                  :unit-types unit-types
                  :units-with-mode (or (:units-with-mode context) #{})}
        {:keys [whens]} (when-parser/parse-when when-lines when-ctx)
        {:keys [thens]} (then-parser/parse-then then-lines {})]
    {:line line
     :description description
     :givens givens
     :whens whens
     :thens thens}))

(defn parse-file
  "Parse a .txt acceptance test file into structured EDN IR."
  [path]
  (let [content (slurp path)
        lines (str/split-lines content)
        source (last (str/split path #"/"))
        raw-tests (split-into-tests lines)
        tests (mapv parse-test raw-tests)]
    {:source source
     :tests tests}))

;; --- Config key validation ---

(defn validate-config-keys
  "Print warnings for config keys referenced in thens that don't exist in config/messages."
  [source-name tests]
  (doseq [{:keys [line thens]} tests]
    (doseq [{:keys [config-key]} thens]
      (when (and config-key (not (contains? config/messages config-key)))
        (println (str "WARNING: " source-name ":" line " - config key :" (name config-key) " not found in config/messages"))))))

;; --- CLI entry point ---

(defn- write-edn [path data]
  (spit path (pr-str data)))

(defn -main [& args]
  (let [dir (or (first args) "acceptanceTests")
        edn-dir (str dir "/edn")
        files (->> (io/file dir)
                   .listFiles
                   (filter #(str/ends-with? (.getName %) ".txt"))
                   (sort-by #(.getName %)))]
    (io/make-parents (io/file edn-dir "dummy"))
    (doseq [f files]
      (let [txt-path (.getPath f)
            base-name (str/replace (.getName f) #"\.txt$" ".edn")
            edn-path (str edn-dir "/" base-name)]
        (println (str "Parsing " txt-path " -> " edn-path))
        (let [result (parse-file txt-path)]
          (validate-config-keys (.getName f) (:tests result))
          (write-edn edn-path result)
          (println (str "  " (count (:tests result)) " tests parsed")))))))

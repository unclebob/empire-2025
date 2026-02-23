(ns empire.acceptance.parser
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [empire.config :as config]
            [empire.acceptance.parser.helpers :as h]
            [empire.acceptance.parser.given :as given]
            [empire.acceptance.parser.when :as when-parser]
            [empire.acceptance.parser.then :as then-parser]))

;; --- Test splitting ---

(defn split-into-tests
  "Split lines (with 1-based line numbers) into test groups.
   Returns [{:line N :description \"...\" :given-lines [...] :when-lines [...] :then-lines [...]}]"
  [lines]
  (let [indexed (map-indexed (fn [i l] [(inc i) l]) lines)
        tests (atom [])
        current-test (atom nil)
        current-section (atom nil)
        in-header (atom false)
        header-desc (atom nil)]
    (doseq [[line-num line] indexed]
      (let [trimmed (str/trim line)]
        (cond
          ;; Separator line
          (h/separator-line? trimmed)
          (if @in-header
            ;; End of header
            (do (reset! in-header false))
            ;; Start of header — save any current test
            (do
              (when @current-test
                (swap! tests conj @current-test))
              (reset! in-header true)
              (reset! header-desc nil)
              (reset! current-test nil)
              (reset! current-section nil)))

          ;; Comment line inside header → description
          (and @in-header (str/starts-with? trimmed ";"))
          (reset! header-desc (str/trim (subs trimmed 1)))

          ;; GIVEN line
          (str/starts-with? trimmed "GIVEN")
          (do
            (when (nil? @current-test)
              (reset! current-test {:line line-num
                                    :description (or @header-desc "")
                                    :given-lines []
                                    :when-lines []
                                    :then-lines []}))
            (reset! current-section :given)
            (swap! current-test update :given-lines conj trimmed))

          ;; WHEN line
          (str/starts-with? trimmed "WHEN")
          (do
            (reset! current-section :when)
            (when @current-test
              (swap! current-test update :when-lines conj trimmed)))

          ;; THEN line or and-continuation
          (or (str/starts-with? trimmed "THEN")
              (re-matches #"^and\s+.*" trimmed))
          (do
            (reset! current-section :then)
            (when @current-test
              (swap! current-test update :then-lines conj trimmed)))

          ;; Blank or comment — ignore
          (h/blank-or-comment? trimmed)
          nil

          ;; Content line — add to current section
          :else
          (when @current-test
            (case @current-section
              :given (swap! current-test update :given-lines conj trimmed)
              :when (swap! current-test update :when-lines conj trimmed)
              :then (swap! current-test update :then-lines conj trimmed)
              nil)))))
    (when @current-test
      (swap! tests conj @current-test))
    @tests))

;; --- Context building ---

(defn- extract-unit-types-from-givens [givens]
  (let [types (atom #{})]
    (doseq [g givens]
      (case (:type g)
        :map (doseq [row (:rows g)]
               (doseq [ch (seq row)]
                 (let [s (str ch)]
                   (when (or (contains? h/player-unit-chars s)
                             (contains? h/computer-unit-chars s))
                     (swap! types conj s)))))
        :unit-props (swap! types conj (:unit g))
        :waiting-for-input (swap! types conj (:unit g))
        nil))
    @types))

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

(ns empire.acceptance.generator
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.spec.alpha :as s]
            [empire.acceptance.parser.ir-contracts :as contracts]
            [empire.acceptance.generator.given :as gen-given]
            [empire.acceptance.generator.when :as gen-when]
            [empire.acceptance.generator.then :as gen-then]))

;; --- Needs determination ---

(defn- node-types
  "Collect all :type values from a sequence of IR nodes."
  [nodes]
  (set (map :type nodes)))

(defn- build-need-context
  "Pre-compute aggregated data from a single test for need-rules."
  [test]
  (let [givens (:givens test)
        whens (:whens test)
        thens (:thens test)
        all-nodes (concat givens whens thens)
        types (node-types all-nodes)]
    {:givens givens :whens whens :thens thens
     :types types}))

(def ^:private need-rules
  [{:need :config
    :pred (fn [{:keys [thens whens]}]
            (some :config-key (concat thens whens)))}
   {:need :game-loop
    :pred (fn [{:keys [types whens thens]}]
            (or (some #{:start-new-round :rounds-complete :advance-game :advance-game-batch :visibility-update} types)
                (some #{:unit-at-next-round :unit-after-moves :unit-after-steps :message-for-unit} types)
                (some #(= :battle (:type %)) whens)
                (some :at-next-round thens)
                (some :at-next-step thens)))}
   {:need :make-initial-test-map
    :pred (fn [{:keys [givens whens]}]
            (or (some #(= :waiting-for-input (:type %)) (concat givens whens))
                (some #(= :visible-to-computer (:type %)) givens)))}
   {:need :advance-until-waiting-helper
    :needs-also #{:game-loop}
    :pred (fn [{:keys [whens thens]}]
            (or (some #(= :advance-until-waiting (:type %)) whens)
                (some #(= :unit-waiting-for-input (:type %)) thens)))}
   {:need :advance-helper
    :pred (fn [{:keys [types thens]}]
            (or (some #{:unit-eventually-at :unit-after-steps} types)
                (some :at-next-round thens)
                (some #(and (= :unit-at-next-round (:type %))
                            (not (:at-next-step %))) thens)))}
   {:need :computer-production
    :pred (fn [{:keys [whens]}]
            (some #(= :evaluate-production (:type %)) whens))}
   {:need :computer-transport
    :pred (fn [{:keys [whens]}]
            (some #(= :process-computer-transport (:type %)) whens))}
   {:need :computer-fighter
    :pred (fn [{:keys [whens]}]
            (some #(= :process-computer-fighter (:type %)) whens))}
   {:need :computer-rounds
    :needs-also #{:game-loop}
    :pred (fn [{:keys [whens]}]
            (some #(= :computer-rounds (:type %)) whens))}
   {:need :visibility-mask
    :pred (fn [{:keys [types]}]
            (some #{:player-map-visibility} types))}
   {:need :computer-ship
    :pred (fn [{:keys [whens]}]
            (some #(= :process-computer-ship (:type %)) whens))}
   {:need :visibility
    :pred (fn [{:keys [types]}]
            (some #{:cell-visibility-update} types))}
   {:need :territory-mask
    :pred (fn [{:keys [types]}]
            (some #{:territory-map} types))}])

(defn determine-needs
  "Scan all IR nodes across all tests. Returns a set of keywords
   indicating which requires/helpers are needed."
  [tests]
  (let [all-contexts (map build-need-context tests)]
    (reduce (fn [needs {:keys [need pred] :as rule}]
              (if (some pred all-contexts)
                (apply conj needs need (seq (or (:needs-also rule) [])))
                needs))
            #{}
            need-rules)))

;; --- NS form generation ---

(def ^:private optional-refers
  [[:config          ["message-matches?"]]
   [:make-initial-test-map ["make-initial-test-map"]]
   [:visibility-mask ["visibility-mask"]]
   [:territory-mask  ["territory-mask" "build-territory-expected"]]])

(def ^:private optional-requires
  [[:config              "[empire.config.core :as config]"]])

(defn- collect-harness-refers [needs]
  (into ["build-test-map" "set-test-world!" "update-test-world!"
         "reset-all-atoms!"]
        (mapcat (fn [[need refs]] (when (contains? needs need) refs))
                optional-refers)))

(defn- collect-requires [needs]
  (into []
        (keep (fn [[need req]] (when (contains? needs need) req))
              optional-requires)))

(defn generate-ns-form
  "Generate the ns declaration string."
  [source-name needs]
  (let [base-name (str/replace source-name #"\.txt$" "")
        ns-name (str "acceptance." base-name "-spec")
        harness-refers (collect-harness-refers needs)
        requires (collect-requires needs)]
    (str "(ns " ns-name "\n"
         "  (:require [speclj.core :refer :all]\n"
         "            [empire.acceptance.harness :as h :refer [" (str/join " " harness-refers) "]]\n"
         (str/join "\n" (map #(str "            " %) requires))
         "))")))

;; --- Helper functions ---

(defn generate-helper-fns
  "Generate helper function definitions if needed."
  [needs]
  (let [parts (atom [])]
    (when (contains? needs :advance-helper)
      (swap! parts conj
             (str "(defn- advance-until-next-round []\n"
                  "  (let [start-round (h/read-state :round-number)]\n"
                  "    (loop [n 100]\n"
                  "      (cond\n"
                  "        (not= start-round (h/read-state :round-number))\n"
                  "        (do (h/advance-game!) :ok)\n"
                  "\n"
                  "        (zero? n) :timeout\n"
                  "\n"
                  "        :else\n"
                  "        (do (h/advance-game!)\n"
                  "            (recur (dec n)))))))")))
    (when (contains? needs :advance-until-waiting-helper)
      (swap! parts conj
             (str "(defn- advance-until-unit-waiting [unit-label]\n"
                  "  (loop [n 100]\n"
                  "    (cond\n"
                  "      (and (h/read-state :waiting-for-input)\n"
                  "           (let [u (h/get-unit unit-label)]\n"
                  "             (and u (= (:pos u) (first (h/read-state :cells-needing-attention))))))\n"
                  "      :ok\n"
                  "\n"
                  "      (zero? n) :timeout\n"
                  "\n"
                  "      (h/read-state :waiting-for-input)\n"
                  "      (let [coords (first (h/read-state :cells-needing-attention))\n"
                  "            cell (get-in (h/read-state :game-map) coords)\n"
                  "            k (if (= :city (:type cell)) :x :space)]\n"
                  "        (h/key-down! k)\n"
                  "        (h/advance-game!)\n"
                  "        (recur (dec n)))\n"
                  "\n"
                  "      :else\n"
                  "      (do (h/advance-game!)\n"
                  "          (recur (dec n))))))")))
    (if (seq @parts)
      (str "\n\n" (str/join "\n\n" @parts))
      "")))

;; --- Phase dispatchers (public API) ---

(defn generate-given
  "Generate code string for a single GIVEN IR node."
  ([given] (gen-given/generate-given given))
  ([given givens] (gen-given/generate-given given givens)))

(defn generate-when
  "Generate code string for a single WHEN IR node."
  ([when-ir] (gen-when/generate-when when-ir))
  ([when-ir givens] (gen-when/generate-when when-ir givens)))

(defn generate-then
  "Generate code string for a single THEN IR node."
  [then-ir givens]
  (gen-then/generate-then then-ir givens))

;; --- Test generation ---

(defn- wrap-with-redefs [bindings code-str]
  (let [binding-pairs (str/join "\n                  "
                        (map #(str (:var %) " " (:value %)) bindings))
        lines (str/split-lines code-str)
        re-indented (str/join "\n" (map #(str "  " %) lines))]
    (str "    (with-redefs [" binding-pairs "]\n" re-indented ")")))

(defn generate-test
  "Generate a single (it ...) block from test IR."
  [test-ir source-name]
  (let [{:keys [line description givens whens thens]} test-ir
        clean-desc (if (str/ends-with? description ".")
                     (subs description 0 (dec (count description)))
                     description)
        it-name (str source-name ":" line " - " clean-desc)
        stub-givens (filter #(= :stub (:type %)) givens)
        regular-givens (remove #(= :stub (:type %)) givens)
        all-bindings (mapcat :bindings stub-givens)
        given-code (str/join "\n" (map #(gen-given/generate-given % givens) regular-givens))
        raw-when-code (str/join "\n" (map #(gen-when/generate-when % givens) whens))
        when-code (if (seq all-bindings)
                    (wrap-with-redefs all-bindings raw-when-code)
                    raw-when-code)
        then-code (str/join "\n" (map #(gen-then/generate-then % givens) thens))
        body-parts (remove str/blank?
                           [(str "    (reset-all-atoms!)")
                            given-code
                            when-code
                            then-code])]
    (str "  (it \"" it-name "\"\n"
         (str/join "\n" body-parts) ")")))

;; --- Top-level generation ---

(defn- validate-test-ir!
  "Validate parser IR contracts for all test phases. Throws ex-info on contract violations."
  [source {:keys [line description givens whens thens]}]
  (let [phase-data [[:givens :empire.acceptance.parser.ir-contracts/givens givens]
                    [:whens :empire.acceptance.parser.ir-contracts/whens whens]
                    [:thens :empire.acceptance.parser.ir-contracts/thens thens]]]
    (doseq [[phase spec-key value] phase-data]
      (when-not (s/valid? spec-key value)
        (throw (ex-info (str "IR contract validation failed for " source
                             ":" line " (" phase ") - " description)
                        {:source source
                         :line line
                         :phase phase
                         :description description
                         :problems (s/explain-data spec-key value)
                         :explain (s/explain-str spec-key value)}))))))

(defn generate-spec
  "Generate a complete Speclj spec file string from parsed EDN data."
  [edn-data]
  (let [{:keys [source tests]} edn-data
        _ (doseq [t tests] (validate-test-ir! source t))
        needs (determine-needs tests)
        ns-form (generate-ns-form source needs)
        helpers (generate-helper-fns needs)
        describe-name source
        test-blocks (map #(generate-test % source) tests)
        test-str (str/join "\n\n" test-blocks)]
    (str ns-form
         helpers
         "\n\n(describe \"" describe-name "\"\n\n"
         test-str ")\n")))

;; --- CLI entry point ---

(defn -main [& args]
  (let [edn-dir (or (first args) "acceptanceTests/edn")
        out-dir (or (second args) "generated-acceptance-specs/acceptance")
        edn-files (->> (io/file edn-dir)
                       .listFiles
                       (filter #(str/ends-with? (.getName %) ".edn"))
                       (sort-by #(.getName %)))]
    (doseq [f edn-files]
      (let [edn-path (.getPath f)
            data (edn/read-string (slurp edn-path))
            base-name (-> (.getName f)
                          (str/replace #"\.edn$" "")
                          (str/replace #"-" "_"))
            out-path (str out-dir "/" base-name "_spec.clj")
            spec-str (generate-spec data)]
        (println (str "Generating " out-path " from " edn-path))
        (io/make-parents (io/file out-path))
        (spit out-path spec-str)
        (println (str "  " (count (:tests data)) " tests generated"))))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T12:04:10.426694-05:00", :module-hash "1252199109", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 9, :hash "-16631093"} {:id "defn-/node-types", :kind "defn-", :line 13, :end-line 16, :hash "-209752400"} {:id "defn-/build-need-context", :kind "defn-", :line 18, :end-line 27, :hash "1180670715"} {:id "def/need-rules", :kind "def", :line 29, :end-line 79, :hash "269152176"} {:id "defn/determine-needs", :kind "defn", :line 81, :end-line 91, :hash "161833285"} {:id "def/optional-refers", :kind "def", :line 95, :end-line 99, :hash "-2066643811"} {:id "def/optional-requires", :kind "def", :line 101, :end-line 102, :hash "-2098069097"} {:id "defn-/collect-harness-refers", :kind "defn-", :line 104, :end-line 108, :hash "1885883674"} {:id "defn-/collect-requires", :kind "defn-", :line 110, :end-line 113, :hash "-306610198"} {:id "defn/generate-ns-form", :kind "defn", :line 115, :end-line 126, :hash "1338040456"} {:id "defn/generate-helper-fns", :kind "defn", :line 130, :end-line 173, :hash "-973297317"} {:id "defn/generate-given", :kind "defn", :line 177, :end-line 180, :hash "-371571042"} {:id "defn/generate-when", :kind "defn", :line 182, :end-line 185, :hash "-1592427725"} {:id "defn/generate-then", :kind "defn", :line 187, :end-line 190, :hash "472725419"} {:id "defn-/wrap-with-redefs", :kind "defn-", :line 194, :end-line 199, :hash "-1438905868"} {:id "defn/generate-test", :kind "defn", :line 201, :end-line 224, :hash "-1832111092"} {:id "defn-/validate-test-ir!", :kind "defn-", :line 228, :end-line 243, :hash "739720756"} {:id "defn/generate-spec", :kind "defn", :line 245, :end-line 259, :hash "1991289022"} {:id "defn/-main", :kind "defn", :line 263, :end-line 281, :hash "-1629665199"}]}
;; clj-mutate-manifest-end

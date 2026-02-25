(ns empire.acceptance.generator
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [empire.acceptance.generator.utils :as utils]
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
        types (node-types all-nodes)
        then-targets (keep :target thens)
        given-pi-targets (mapcat :items (filter #(= :player-items (:type %)) givens))
        given-ut-targets (keep :target (filter #(= :unit-target (:type %)) givens))
        given-prod-cities (keep :city (filter #(= :production (:type %)) givens))
        given-vtc-refs (keep :ref (filter #(= :visible-to-computer (:type %)) givens))
        all-targets (concat then-targets given-pi-targets given-ut-targets given-prod-cities given-vtc-refs)
        map-rows (:rows (first (filter #(= :map (:type %)) givens)))
        wfi-units (keep #(when (= :waiting-for-input (:type %)) (:unit %)) givens)]
    {:givens givens :whens whens :thens thens
     :types types :all-targets all-targets
     :map-rows map-rows :wfi-units wfi-units}))

(def ^:private need-rules
  [{:need :config
    :pred (fn [{:keys [thens whens]}]
            (some :config-key (concat thens whens)))}
   {:need :game-loop
    :pred (fn [{:keys [types whens thens]}]
            (or (some #{:start-new-round :advance-game :advance-game-batch :visibility-update} types)
                (some #{:unit-at-next-round :unit-after-moves :unit-after-steps :message-for-unit} types)
                (some #(= :battle (:type %)) whens)
                (some :at-next-round thens)
                (some :at-next-step thens)))}
   {:need :item-processing
    :pred (fn [{:keys [givens whens types]}]
            (or (some #(= :waiting-for-input (:type %)) (concat givens whens))
                (some #{:process-player-items} types)))}
   {:need :get-test-cell
    :pred (fn [{:keys [all-targets]}]
            (some #(or (= "=" %) (= "%" %)) all-targets))}
   {:need :get-test-city
    :pred (fn [{:keys [all-targets thens givens map-rows wfi-units]}]
            (or (some utils/city-spec? all-targets)
                (some utils/city-spec? (keep :target-unit thens))
                (some #(and (= :container-prop (:type %)) (= :city (:lookup %))) thens)
                (some #(and (= :container-state (:type %))
                            (utils/city-spec? (:target %))) givens)
                (some #(and (= :production (:type %))
                            (utils/city-spec? (:city %))) givens)
                (some #(= :city-prop (:type %)) givens)
                (some #(#{:shipyard-state :city-unit} (:type %)) givens)
                (some #(#{:shipyard-has-ship :shipyard-empty} (:type %)) thens)
                (some #(and (= :waiting-for-input (:type %))
                            (utils/city-spec? (:unit %))) givens)
                (some #(and (= :unit-props (:type %))
                            (utils/city-spec? (:unit %))) givens)
                (some (fn [u] (and (not (utils/city-spec? u))
                                   map-rows
                                   (not (some #(str/includes? % u) map-rows)))) wfi-units)))}
   {:need :make-initial-test-map
    :pred (fn [{:keys [givens whens]}]
            (or (some #(= :waiting-for-input (:type %)) (concat givens whens))
                (some #(= :visible-to-computer (:type %)) givens)))}
   {:need :advance-until-waiting-helper
    :needs-also #{:quil :game-loop}
    :pred (fn [{:keys [whens thens]}]
            (or (some #(= :advance-until-waiting (:type %)) whens)
                (some #(= :unit-waiting-for-input (:type %)) thens)))}
   {:need :quil
    :pred (fn [{:keys [whens]}]
            (or (some #(and (= :key-press (:type %)) (= :key-down (:input-fn %))) whens)
                (some #(= :backtick (:type %)) whens)
                (some #(= :mouse-at-key (:type %)) whens)))}
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
  [[:get-test-cell   ["get-test-cell"]]
   [:get-test-city   ["get-test-city"]]
   [:config          ["message-matches?"]]
   [:make-initial-test-map ["make-initial-test-map"]]
   [:visibility-mask ["visibility-mask"]]
   [:territory-mask  ["territory-mask" "build-territory-expected"]]])

(def ^:private optional-requires
  [[:config              "[empire.config :as config]"]
   [:game-loop           "[empire.game-loop :as game-loop]"]
   [:item-processing     "[empire.game-loop.item-processing :as item-processing]"]
   [:quil                "[quil.core :as q]"]
   [:computer-production "[empire.computer.production :as computer-production]"]
   [:computer-transport  "[empire.computer.transport :as computer-transport]"]
   [:computer-fighter    "[empire.computer.fighter :as computer-fighter]"]
   [:visibility          "[empire.movement.visibility :as visibility]"]])

(defn- collect-refers [needs]
  (into ["build-test-map" "set-test-unit" "get-test-unit" "reset-all-atoms!"]
        (mapcat (fn [[need refs]] (when (contains? needs need) refs))
                optional-refers)))

(defn- collect-requires [needs]
  (into ["[empire.atoms :as atoms]" "[empire.ui.input :as input]"]
        (keep (fn [[need req]] (when (contains? needs need) req))
              optional-requires)))

(defn generate-ns-form
  "Generate the ns declaration string."
  [source-name needs]
  (let [base-name (str/replace source-name #"\.txt$" "")
        ns-name (str "acceptance." base-name "-spec")
        refers (collect-refers needs)
        requires (collect-requires needs)]
    (str "(ns " ns-name "\n"
         "  (:require [speclj.core :refer :all]\n"
         "            [empire.test-utils :refer [" (str/join " " refers) "]]\n"
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
                  "  (let [start-round @atoms/round-number]\n"
                  "    (loop [n 100]\n"
                  "      (cond\n"
                  "        (not= start-round @atoms/round-number)\n"
                  "        (do (game-loop/advance-game) :ok)\n"
                  "\n"
                  "        (zero? n) :timeout\n"
                  "\n"
                  "        :else\n"
                  "        (do (game-loop/advance-game)\n"
                  "            (recur (dec n)))))))")))
    (when (contains? needs :advance-until-waiting-helper)
      (swap! parts conj
             (str "(defn- advance-until-unit-waiting [unit-label]\n"
                  "  (loop [n 100]\n"
                  "    (cond\n"
                  "      (and @atoms/waiting-for-input\n"
                  "           (let [u (get-test-unit atoms/game-map unit-label)]\n"
                  "             (and u (= (:pos u) (first @atoms/cells-needing-attention)))))\n"
                  "      :ok\n"
                  "\n"
                  "      (zero? n) :timeout\n"
                  "\n"
                  "      @atoms/waiting-for-input\n"
                  "      (let [coords (first @atoms/cells-needing-attention)\n"
                  "            cell (get-in @atoms/game-map coords)\n"
                  "            k (if (= :city (:type cell)) :x :space)]\n"
                  "        (with-redefs [q/mouse-x (constantly 0)\n"
                  "                      q/mouse-y (constantly 0)]\n"
                  "          (reset! atoms/last-key nil)\n"
                  "          (input/key-down k))\n"
                  "        (game-loop/advance-game)\n"
                  "        (recur (dec n)))\n"
                  "\n"
                  "      :else\n"
                  "      (do (game-loop/advance-game)\n"
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

(defn generate-spec
  "Generate a complete Speclj spec file string from parsed EDN data."
  [edn-data]
  (let [{:keys [source tests]} edn-data
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

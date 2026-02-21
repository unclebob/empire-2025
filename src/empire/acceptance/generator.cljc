(ns empire.acceptance.generator
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.edn :as edn]))

;; --- Target resolution ---

(def ^:private city-chars #{"O" "X" "+"})
(def ^:private cell-label-chars #{"=" "%"})

(defn- city-spec?
  "Returns true if spec refers to a city (starts with O, X, or +)."
  [spec]
  (contains? city-chars (str (first spec))))

(defn- target-pos-expr [target]
  (cond
    (city-spec? target)
    (str "(:pos (get-test-city atoms/game-map \"" target "\"))")

    (contains? cell-label-chars target)
    (str "(:pos (get-test-cell atoms/game-map \"" target "\"))")

    :else
    (str "(:pos (get-test-unit atoms/game-map \"" target "\"))")))

;; --- Message area → atom mapping ---

(defn- area->atom [area]
  (case area
    :attention "atoms/attention-message"
    :turn "atoms/turn-message"
    :error "atoms/error-message"))

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
        all-targets (concat then-targets given-pi-targets given-ut-targets given-prod-cities)
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
            (or (some city-spec? all-targets)
                (some city-spec? (keep :target-unit thens))
                (some #(and (= :container-prop (:type %)) (= :city (:lookup %))) thens)
                (some #(and (= :container-state (:type %))
                            (city-spec? (:target %))) givens)
                (some #(and (= :production (:type %))
                            (city-spec? (:city %))) givens)
                (some #(= :city-prop (:type %)) givens)
                (some #(#{:shipyard-state :city-unit} (:type %)) givens)
                (some #(#{:shipyard-has-ship :shipyard-empty} (:type %)) thens)
                (some #(and (= :waiting-for-input (:type %))
                            (city-spec? (:unit %))) givens)
                (some #(and (= :unit-props (:type %))
                            (city-spec? (:unit %))) givens)
                (some (fn [u] (and (not (city-spec? u))
                                   map-rows
                                   (not (some #(str/includes? % u) map-rows)))) wfi-units)))}
   {:need :make-initial-test-map
    :pred (fn [{:keys [givens whens]}]
            (some #(= :waiting-for-input (:type %)) (concat givens whens)))}
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

(defn generate-ns-form
  "Generate the ns declaration string."
  [source-name needs]
  (let [base-name (str/replace source-name #"\.txt$" "")
        ns-name (str "acceptance." base-name "-spec")
        ;; Build test-utils refers
        refers (atom ["build-test-map" "set-test-unit" "get-test-unit"])
        requires (atom [])]
    (when (contains? needs :get-test-cell)
      (swap! refers conj "get-test-cell"))
    (when (contains? needs :get-test-city)
      (swap! refers conj "get-test-city"))
    (swap! refers conj "reset-all-atoms!")
    (when (contains? needs :config)
      (swap! refers conj "message-matches?"))
    (when (contains? needs :make-initial-test-map)
      (swap! refers conj "make-initial-test-map"))
    (when (contains? needs :visibility-mask)
      (swap! refers conj "visibility-mask"))
    (when (contains? needs :territory-mask)
      (swap! refers conj "territory-mask")
      (swap! refers conj "build-territory-expected"))

    ;; Always need atoms
    (swap! requires conj "[empire.atoms :as atoms]")
    (when (contains? needs :config)
      (swap! requires conj "[empire.config :as config]"))
    (when (contains? needs :game-loop)
      (swap! requires conj "[empire.game-loop :as game-loop]"))
    (when (contains? needs :item-processing)
      (swap! requires conj "[empire.game-loop.item-processing :as item-processing]"))
    ;; Always need input (key-down/handle-key)
    (swap! requires conj "[empire.ui.input :as input]")
    (when (contains? needs :quil)
      (swap! requires conj "[quil.core :as q]"))
    (when (contains? needs :computer-production)
      (swap! requires conj "[empire.computer.production :as computer-production]"))
    (when (contains? needs :computer-transport)
      (swap! requires conj "[empire.computer.transport :as computer-transport]"))
    (when (contains? needs :visibility)
      (swap! requires conj "[empire.movement.visibility :as visibility]"))

    (str "(ns " ns-name "\n"
         "  (:require [speclj.core :refer :all]\n"
         "            [empire.test-utils :refer [" (str/join " " @refers) "]]\n"
         (str/join "\n" (map #(str "            " %) @requires))
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

;; --- GIVEN generation ---

(defn- generate-map-given [{:keys [rows target]}]
  (let [row-strs (str/join " " (map #(str "\"" % "\"") rows))
        atom-str (case target
                   :player-map "atoms/player-map"
                   :computer-map "atoms/computer-map"
                   "atoms/game-map")]
    (str "    (reset! " atom-str " (build-test-map [" row-strs "]))")))

(defn- generate-unit-props-given [{:keys [unit props]}]
  (if (city-spec? unit)
    ;; For cities, use get-test-city and update the cell directly
    (let [kvs (mapv (fn [[k v]]
                      (str ":" (name k) " " (pr-str v)))
                    props)]
      (str "    (let [city-pos (:pos (get-test-city atoms/game-map \"" unit "\"))]\n"
           "      (swap! atoms/game-map update-in city-pos assoc " (str/join " " kvs) "))"))
    ;; For units, use set-test-unit
    (let [kvs (mapv (fn [[k v]]
                      (str ":" (name k) " " (pr-str v)))
                    props)]
      (str "    (set-test-unit atoms/game-map \"" unit "\" " (str/join " " kvs) ")"))))

(defn- find-container-city
  "When a unit label doesn't appear in the map rows, find the city
   that contains it by looking at container-state givens with awake-fighters."
  [givens]
  (let [map-rows (:rows (first (filter #(= :map (:type %)) givens)))]
    (when map-rows
      (->> givens
           (filter #(and (= :container-state (:type %))
                         (city-spec? (:target %))
                         (pos? (get-in % [:props :awake-fighters] 0))))
           first
           :target))))

(defn- generate-waiting-for-input-given
  ([given] (generate-waiting-for-input-given given []))
  ([{:keys [unit set-mode]} givens]
   (let [lines (atom [])
         is-city (city-spec? unit)
         map-given (first (filter #(= :map (:type %)) givens))
         unit-on-map? (or is-city
                          (nil? map-given)
                          (some #(str/includes? % unit) (:rows map-given)))
         container-city (when (not unit-on-map?) (find-container-city givens))
         effective-unit (or container-city unit)
         effective-is-city (or (some? container-city) is-city)
         pos-lookup (if effective-is-city
                      (str "(:pos (get-test-city atoms/game-map \"" effective-unit "\"))")
                      (str "(:pos (get-test-unit atoms/game-map \"" effective-unit "\"))"))]
     (when (and set-mode unit-on-map? (not is-city))
       (swap! lines conj (str "    (set-test-unit atoms/game-map \"" unit "\" :mode :awake)")))
     (when container-city
       (swap! lines conj (str "    (swap! atoms/production assoc " pos-lookup " :none)")))
     (swap! lines conj
            (str "    (let [cols (count @atoms/game-map)\n"
                 "          rows (count (first @atoms/game-map))\n"
                 "          pos " pos-lookup "]\n"
                 "      (reset! atoms/player-map (make-initial-test-map rows cols nil))\n"
                 "      (reset! atoms/player-items [pos])\n"
                 "      (item-processing/process-player-items-batch))"))
     (str/join "\n" @lines))))

(defn- infer-container-counts
  "When awake-fighters or awake-armies are set but the corresponding
   count key is missing, infer the count from the awake value."
  [props]
  (cond-> props
    (and (contains? props :awake-fighters) (not (contains? props :fighter-count)))
    (assoc :fighter-count (:awake-fighters props))
    (and (contains? props :awake-armies) (not (contains? props :army-count)))
    (assoc :army-count (:awake-armies props))))

(defn- generate-container-state-given [{:keys [target props]}]
  (let [props (infer-container-counts props)]
    (if (city-spec? target)
      ;; City container — props go on the cell
      (let [prop-map (str/join " " (mapcat (fn [[k v]] [(str ":" (name k)) (pr-str v)]) props))]
        (str "    (let [" (str/lower-case target) "-pos (:pos (get-test-city atoms/game-map \"" target "\"))]\n"
             "      (swap! atoms/game-map update-in " (str/lower-case target) "-pos merge {" prop-map "}))"))
      ;; Unit container — props go on :contents
      (let [kvs (mapv (fn [[k v]] (str ":" (name k) " " (pr-str v))) props)]
        (str "    (set-test-unit atoms/game-map \"" target "\" " (str/join " " kvs) ")")))))

(defn- generate-production-given [{:keys [city item remaining-rounds]}]
  (let [pos-expr (target-pos-expr city)]
    (if remaining-rounds
      (str "    (let [" (str/lower-case city) "-pos " pos-expr "]\n"
           "      (swap! atoms/production assoc " (str/lower-case city) "-pos {:item :" (name item) " :remaining-rounds " remaining-rounds "}))")
      (str "    (let [" (str/lower-case city) "-pos " pos-expr "]\n"
           "      (swap! atoms/production assoc " (str/lower-case city) "-pos {:item :" (name item) "}))"))))

(defn- generate-city-prop-given [{:keys [city prop value]}]
  (let [pos-expr (target-pos-expr city)]
    (str "    (let [" (str/lower-case city) "-pos " pos-expr "]\n"
         "      (swap! atoms/game-map update-in " (str/lower-case city) "-pos assoc :" (name prop) " " value "))")))

(defn- generate-unit-target-given [{:keys [unit target]}]
  (let [target-expr (target-pos-expr target)]
    (str "    (set-test-unit atoms/game-map \"" unit "\" :mode :moving :target " target-expr ")")))

(defn- generate-round-given [{:keys [value]}]
  (str "    (reset! atoms/round-number " value ")"))

(defn- generate-destination-given [{:keys [coords]}]
  (str "    (reset! atoms/destination " (pr-str coords) ")"))

(defn- generate-cell-props-given [{:keys [coords props]}]
  (let [prop-map (str/join " " (mapcat (fn [[k v]] [(str ":" (name k)) (pr-str v)]) props))]
    (str "    (swap! atoms/game-map update-in " (pr-str coords) " merge {" prop-map "})")))

(defn- generate-player-items-given [{:keys [items]}]
  (let [exprs (mapv target-pos-expr items)]
    (str "    (reset! atoms/player-items [" (str/join " " exprs) "])")))

(defn- generate-waiting-for-input-state-given [_]
  "    (reset! atoms/waiting-for-input true)")

(defn- generate-no-production-given [_]
  "    (reset! atoms/production {})")

(defn- generate-city-unit-given [{:keys [city unit-type owner]}]
  (let [pos-expr (target-pos-expr city)]
    (str "    (let [" (str/lower-case city) "-pos " pos-expr "]\n"
         "      (swap! atoms/game-map assoc-in (conj " (str/lower-case city) "-pos :contents)\n"
         "        {:type :" (name unit-type) " :owner :" (name owner) " :mode :sentry :hits 1}))")))

(defn- generate-shipyard-state-given [{:keys [city ship-type hits]}]
  (let [pos-expr (target-pos-expr city)]
    (str "    (let [" (str/lower-case city) "-pos " pos-expr "]\n"
         "      (swap! atoms/game-map update-in " (str/lower-case city) "-pos\n"
         "        update :shipyard (fnil conj []) {:type :" (name ship-type) " :hits " hits "}))")))

(defn generate-given
  "Generate code string for a single GIVEN IR node."
  ([given] (generate-given given []))
  ([given givens]
   (case (:type given)
     :map (generate-map-given given)
     :unit-props (generate-unit-props-given given)
     :waiting-for-input (generate-waiting-for-input-given given givens)
     :container-state (generate-container-state-given given)
     :production (generate-production-given given)
     :unit-target (generate-unit-target-given given)
     :round (generate-round-given given)
     :destination (generate-destination-given given)
     :cell-props (generate-cell-props-given given)
     :city-prop (generate-city-prop-given given)
     :player-items (generate-player-items-given given)
     :waiting-for-input-state (generate-waiting-for-input-state-given given)
     :no-production (generate-no-production-given given)
     :shipyard-state (generate-shipyard-state-given given)
     :city-unit (generate-city-unit-given given)
     :stub ""
     :unrecognized (str "    (pending \"Unrecognized: " (:text given) "\")")
     (str "    ;; Unknown given type: " (:type given)))))

;; --- WHEN generation ---

(defn- generate-key-press-when [{:keys [key input-fn]}]
  (if (= input-fn :key-down)
    (str "    (with-redefs [q/mouse-x (constantly 0)\n"
         "                  q/mouse-y (constantly 0)]\n"
         "      (reset! atoms/last-key nil)\n"
         "      (input/key-down :" (name key) "))")
    (str "    (input/handle-key :" (name key) ")")))

(defn- generate-battle-when [{:keys [key outcome combat-type]}]
  (let [rand-val (if (= outcome :win) "0.0" "1.0")]
    (if (= combat-type :ship)
      (str "    (with-redefs [rand (constantly " rand-val ")]\n"
           "      (input/handle-key :" (name key) ")\n"
           "      (game-loop/advance-game))")
      (str "    (with-redefs [rand (constantly " rand-val ")]\n"
           "      (input/handle-key :" (name key) "))"))))

(defn- generate-backtick-when [{:keys [key mouse-cell]}]
  (let [[x y] mouse-cell]
    (str "    (reset! atoms/map-screen-dimensions [22 16])\n"
         "    (with-redefs [q/mouse-x (constantly " x ")\n"
         "                  q/mouse-y (constantly " y ")]\n"
         "      (input/key-down (keyword \"`\"))\n"
         "      (input/key-down :" (name key) "))")))

(defn- mouse-at-key-expr [key]
  (case key
    :period "(keyword \".\")"
    (str ":" (name key))))

(defn- generate-mouse-at-key-when [{:keys [coords key]}]
  (let [[cx cy] coords
        key-expr (mouse-at-key-expr key)]
    (str "    (let [map-w 22 map-h 16\n"
         "          cols (count @atoms/game-map)\n"
         "          rows (count (first @atoms/game-map))\n"
         "          cell-w (/ map-w cols) cell-h (/ map-h rows)\n"
         "          px (int (+ (* " cx " cell-w) (/ cell-w 2)))\n"
         "          py (int (+ (* " cy " cell-h) (/ cell-h 2)))]\n"
         "      (reset! atoms/map-screen-dimensions [map-w map-h])\n"
         "      (with-redefs [q/mouse-x (constantly px)\n"
         "                    q/mouse-y (constantly py)]\n"
         "        (input/key-down " key-expr ")))")))

(defn- generate-visibility-update-when [_]
  "    (game-loop/update-player-map)")

(defn- generate-cell-visibility-update-when [{:keys [unit]}]
  (let [pos-expr (target-pos-expr unit)]
    (str "    (let [pos " pos-expr "\n"
         "          cell (get-in @atoms/game-map pos)]\n"
         "      (visibility/update-cell-visibility pos (:owner (:contents cell)) (:contents cell)))")))

(defn- generate-evaluate-production-when [{:keys [city]}]
  (let [pos-expr (target-pos-expr city)]
    (str "    (computer-production/process-computer-city " pos-expr ")")))

(defn- generate-process-computer-transport-when [{:keys [unit]}]
  (let [pos-expr (target-pos-expr unit)]
    (str "    (computer-transport/process-transport " pos-expr ")")))

(defn- generate-computer-rounds-when [{:keys [count]}]
  (str "    (dotimes [_ " count "]\n"
       "      ;; Process all computer transports\n"
       "      (doseq [i (range (count @atoms/game-map))\n"
       "              j (range (count (first @atoms/game-map)))\n"
       "              :let [cell (get-in @atoms/game-map [i j])\n"
       "                    unit (:contents cell)]\n"
       "              :when (and unit\n"
       "                         (= :transport (:type unit))\n"
       "                         (= :computer (:owner unit)))]\n"
       "        (computer-transport/process-transport [i j])))"))

(defn- generate-start-new-round-when [_]
  (str "    (game-loop/start-new-round)\n"
       "    (game-loop/advance-game)"))

(defn- generate-advance-game-when [_]
  "    (game-loop/advance-game)")

(defn- generate-process-player-items-when [_]
  "    (item-processing/process-player-items-batch)")

(defn- generate-advance-until-waiting-when [{:keys [unit]}]
  (str "    (should= :ok (advance-until-unit-waiting \"" unit "\"))"))

(defn generate-when
  "Generate code string for a single WHEN IR node."
  ([when-ir] (generate-when when-ir []))
  ([when-ir givens]
   (case (:type when-ir)
     :key-press (generate-key-press-when when-ir)
     :battle (generate-battle-when when-ir)
     :backtick (generate-backtick-when when-ir)
     :mouse-at-key (generate-mouse-at-key-when when-ir)
     :visibility-update (generate-visibility-update-when when-ir)
     :cell-visibility-update (generate-cell-visibility-update-when when-ir)
     :start-new-round (generate-start-new-round-when when-ir)
     :advance-game (generate-advance-game-when when-ir)
     :advance-game-batch (generate-advance-game-when when-ir)
     :process-player-items (generate-process-player-items-when when-ir)
     :advance-until-waiting (generate-advance-until-waiting-when when-ir)
     :evaluate-production (generate-evaluate-production-when when-ir)
     :process-computer-transport (generate-process-computer-transport-when when-ir)
     :computer-rounds (generate-computer-rounds-when when-ir)
     :waiting-for-input (generate-waiting-for-input-given when-ir givens)
     :unrecognized (str "    (pending \"Unrecognized: " (:text when-ir) "\")")
     (str "    ;; Unknown when type: " (:type when-ir)))))

;; --- THEN generation ---

(defn- generate-unit-prop-then [{:keys [unit property expected]}]
  (str "    (should= " (pr-str expected) " (:" (name property) " (:unit (get-test-unit atoms/game-map \"" unit "\"))))"))

(defn- generate-unit-absent-then [{:keys [unit]}]
  (str "    (should-be-nil (get-test-unit atoms/game-map \"" unit "\"))"))

(defn- generate-unit-present-then [{:keys [unit coords target]}]
  (if target
    ;; Unit present at named target
    (let [target-expr (target-pos-expr target)]
      (str "    (let [{:keys [pos]} (get-test-unit atoms/game-map \"" unit "\")]\n"
           "      (should-not-be-nil pos)\n"
           "      (should= " target-expr " pos))"))
    ;; Unit present at explicit coords
    (str "    (let [{:keys [pos]} (get-test-unit atoms/game-map \"" unit "\")]\n"
         "      (should= " (pr-str coords) " pos))")))

(defn- generate-unit-at-then [{:keys [unit coords target]}]
  (if target
    (let [target-expr (target-pos-expr target)]
      (str "    (let [{:keys [pos]} (get-test-unit atoms/game-map \"" unit "\")]\n"
           "      (should= " target-expr " pos))"))
    (str "    (let [{:keys [pos]} (get-test-unit atoms/game-map \"" unit "\")]\n"
         "      (should= " (pr-str coords) " pos))")))

(defn- generate-unit-at-next-round-then [{:keys [unit target at-next-step]}]
  (let [target-expr (target-pos-expr target)
        advance (if at-next-step
                  "    (game-loop/advance-game)\n"
                  "    (should= :ok (advance-until-next-round))\n")]
    (str advance
         "    (let [{:keys [pos]} (get-test-unit atoms/game-map \"" unit "\")\n"
         "          target-pos " target-expr "]\n"
         "      (should= target-pos pos))")))

(defn- generate-unit-after-moves-then [{:keys [unit moves target]}]
  (let [target-expr (target-pos-expr target)]
    (str "    (dotimes [_ " moves "] (game-loop/advance-game))\n"
         "    (let [{:keys [pos]} (get-test-unit atoms/game-map \"" unit "\")\n"
         "          target-pos " target-expr "]\n"
         "      (should= target-pos pos))")))

(defn- generate-unit-after-steps-then [{:keys [unit steps target coords]}]
  (let [pos-expr (if target
                   (target-pos-expr target)
                   (pr-str coords))]
    (str "    (dotimes [_ " steps "] (game-loop/advance-game))\n"
         "    (let [target-pos " pos-expr "\n"
         "          f-result (get-test-unit atoms/game-map \"" unit "\")]\n"
         "      (should-not-be-nil f-result)\n"
         "      (should= target-pos (:pos f-result)))")))

(defn- generate-unit-eventually-at-then [{:keys [unit target]}]
  (let [target-expr (target-pos-expr target)]
    (str "    (let [target-pos " target-expr "]\n"
         "      (loop [n 0]\n"
         "        (when (< n 20)\n"
         "          (let [{:keys [pos]} (get-test-unit atoms/game-map \"" unit "\")]\n"
         "            (when (not= target-pos pos)\n"
         "              (game-loop/advance-game)\n"
         "              (recur (inc n))))))\n"
         "      (should= target-pos (:pos (get-test-unit atoms/game-map \"" unit "\"))))")))

(defn- find-unit-initial-pos
  "Given a map rows and a unit spec, find the [col row] position."
  [rows unit-spec]
  (let [ch (first unit-spec)]
    (first (for [r (range (count rows))
                 c (range (count (nth rows r)))
                 :when (= ch (nth (nth rows r) c))]
             [c r]))))

(defn- generate-unit-occupies-cell-then
  "Unit occupies the cell where target-unit was originally placed."
  [{:keys [unit target-unit]} givens]
  ;; Find the original position of target-unit from the map rows
  (let [map-given (first (filter #(= :map (:type %)) givens))
        rows (:rows map-given)
        target-pos (find-unit-initial-pos rows target-unit)]
    (str "    (let [{:keys [pos]} (get-test-unit atoms/game-map \"" unit "\")]\n"
         "      (should= " (pr-str target-pos) " pos))")))

(defn- generate-unit-unmoved-then
  "Assert unit hasn't moved from its original position."
  [{:keys [unit]} givens]
  (let [map-given (first (filter #(= :map (:type %)) givens))
        rows (:rows map-given)
        orig-pos (find-unit-initial-pos rows unit)]
    (str "    (let [{:keys [pos]} (get-test-unit atoms/game-map \"" unit "\")]\n"
         "      (should= " (pr-str orig-pos) " pos))")))

(defn- generate-unit-waiting-for-input-then [{:keys [unit]}]
  (str "    (should= :ok (advance-until-unit-waiting \"" unit "\"))"))

(defn- generate-message-contains-then [{:keys [area config-key text at-next-round at-next-step]}]
  (let [atom-str (area->atom area)
        advance (cond
                  at-next-round "    (should= :ok (advance-until-next-round))\n"
                  at-next-step  "    (game-loop/advance-game)\n"
                  :else         "")]
    (if config-key
      (str advance
           "    (should-not-be-nil (:" (name config-key) " config/messages))\n"
           "    (should (message-matches? (:" (name config-key) " config/messages) @" atom-str "))")
      (str advance "    (should-contain \"" text "\" @" atom-str ")"))))

(defn- generate-message-for-unit-then [{:keys [area unit config-key]}]
  (let [atom-str (area->atom area)]
    (str "    (should= :ok\n"
         "      (loop [n 100]\n"
         "        (let [u (get-test-unit atoms/game-map \"" unit "\")]\n"
         "          (cond\n"
         "            (and u (= :awake (:mode (:unit u))) @atoms/waiting-for-input) :ok\n"
         "            (zero? n) :timeout\n"
         "            :else (do (game-loop/advance-game) (recur (dec n)))))))\n"
         "    (should-not-be-nil (:" (name config-key) " config/messages))\n"
         "    (should (message-matches? (:" (name config-key) " config/messages) @" atom-str "))")))

(defn- generate-message-is-then [{:keys [area config-key format]}]
  (let [atom-str (area->atom area)]
    (if config-key
      (str "    (should= (:" (name config-key) " config/messages) @" atom-str ")")
      ;; format case
      (let [{:keys [key args]} format
            args-str (str/join " " (map pr-str args))]
        (str "    (should= (format (:" (name key) " config/messages) " args-str ") @" atom-str ")")))))

(defn- generate-no-message-then [{:keys [area]}]
  (let [atom-str (area->atom area)]
    (str "    (should= \"\" @" atom-str ")")))

(defn- generate-cell-prop-then [{:keys [coords property expected target]}]
  (let [map-atom (if (= target :computer-map) "atoms/computer-map" "atoms/game-map")]
    (str "    (should= " (pr-str expected) " (:" (name property) " (get-in @" map-atom " " (pr-str coords) ")))")))

(defn- generate-cell-type-then [{:keys [coords expected]}]
  (str "    (should= " (pr-str expected) " (:type (get-in @atoms/game-map " (pr-str coords) ")))"))

(defn- generate-waiting-for-input-then [{:keys [expected]}]
  (if expected
    "    (should @atoms/waiting-for-input)"
    "    (should-not @atoms/waiting-for-input)"))

(defn- generate-container-prop-then [{:keys [target property expected lookup at-next-round at-next-step]}]
  (let [advance (cond
                  at-next-round "    (should= :ok (advance-until-next-round))\n"
                  at-next-step  "    (game-loop/advance-game)\n"
                  :else         "")]
    (if (= lookup :city)
      (str advance
           "    (let [" (str/lower-case target) "-pos (:pos (get-test-city atoms/game-map \"" target "\"))\n"
           "          cell (get-in @atoms/game-map " (str/lower-case target) "-pos)]\n"
           "      (should= " (pr-str expected) " (:" (name property) " cell)))")
      ;; :unit lookup — container props are in :contents
      (str advance
           "    (should= " (pr-str expected) " (:" (name property) " (:unit (get-test-unit atoms/game-map \"" target "\"))))"))))


(defn- generate-round-then [{:keys [expected]}]
  (str "    (should= " expected " @atoms/round-number)"))

(defn- generate-destination-then [{:keys [expected]}]
  (str "    (should= " (pr-str expected) " @atoms/destination)"))

(defn- generate-production-then [{:keys [city expected]}]
  (let [pos-expr (target-pos-expr city)]
    (str "    (should= " (pr-str expected) " (:item (get @atoms/production " pos-expr ")))")))

(defn- generate-no-production-then [{:keys [city]}]
  (let [pos-expr (target-pos-expr city)]
    (str "    (let [prod (get @atoms/production " pos-expr ")]\n"
         "      (should (or (nil? prod) (= :none prod))))")))

(defn- generate-production-with-rounds-then [{:keys [city expected remaining-rounds]}]
  (let [pos-expr (target-pos-expr city)]
    (str "    (let [prod (get @atoms/production " pos-expr ")]\n"
         "      (should= " (pr-str expected) " (:item prod))\n"
         "      (should= " remaining-rounds " (:remaining-rounds prod)))")))

(defn- generate-production-not-then [{:keys [city excluded]}]
  (let [pos-expr (target-pos-expr city)]
    (str "    (should-not= " (pr-str excluded) " (:item (get @atoms/production " pos-expr ")))")))

(defn- generate-game-paused-then [_]
  "    (should @atoms/paused)")

(defn- generate-player-map-cell-not-nil-then [{:keys [coords]}]
  (let [[x y] coords]
    (str "    (should-not-be-nil (get-in @atoms/player-map [" x " " y "]))")))

(defn- generate-player-map-cell-nil-then [{:keys [coords]}]
  (let [[x y] coords]
    (str "    (should-be-nil (get-in @atoms/player-map [" x " " y "]))")))

(defn- generate-territory-map-then [{:keys [rows]}]
  (let [row-strs (str/join " " (map #(str "\"" % "\"") rows))]
    (str "    (should= (build-territory-expected [" row-strs "])"
         "\n             (territory-mask @atoms/game-map))")))

(defn- generate-player-map-visibility-then [{:keys [rows]}]
  (let [row-strs (str/join " " (map #(str "\"" % "\"") rows))]
    (str "    (should= (visibility-mask (build-test-map [" row-strs "]))"
         "\n             (visibility-mask @atoms/player-map))")))

(defn- generate-no-unit-at-then [{:keys [coords]}]
  (str "    (should-be-nil (:contents (get-in @atoms/game-map " (pr-str coords) ")))"))

(defn- generate-unit-prop-absent-then [{:keys [unit property]}]
  (str "    (should-be-nil (:" (name property) " (:unit (get-test-unit atoms/game-map \"" unit "\"))))"))

(defn- generate-computer-army-count-then [{:keys [expected]}]
  (str "    (let [count (count (for [i (range (count @atoms/game-map))\n"
       "                             j (range (count (first @atoms/game-map)))\n"
       "                             :let [cell (get-in @atoms/game-map [i j])]\n"
       "                             :when (and (:contents cell)\n"
       "                                        (= :army (:type (:contents cell)))\n"
       "                                        (= :computer (:owner (:contents cell))))]\n"
       "                         true))]\n"
       "      (should= " expected " count))"))

(defn- generate-shipyard-has-ship-then [{:keys [city ship-type hits]}]
  (let [pos-expr (target-pos-expr city)]
    (str "    (let [cell (get-in @atoms/game-map " pos-expr ")\n"
         "          shipyard (:shipyard cell [])]\n"
         "      (should (some #(and (= :" (name ship-type) " (:type %)) (= " hits " (:hits %))) shipyard)))")))

(defn- generate-shipyard-empty-then [{:keys [city]}]
  (let [pos-expr (target-pos-expr city)]
    (str "    (let [cell (get-in @atoms/game-map " pos-expr ")\n"
         "          shipyard (:shipyard cell [])]\n"
         "      (should= [] shipyard))")))

(defn- generate-map-is-then [{:keys [expected]}]
  (str "    (let [expected (build-test-map [\"" expected "\"])\n"
       "          actual @atoms/game-map]\n"
       "      (doseq [col (range (count expected))\n"
       "              row (range (count (first expected)))\n"
       "              :let [exp-cell (get-in expected [col row])\n"
       "                    act-cell (get-in actual [col row])]\n"
       "              :when exp-cell]\n"
       "        (should= (:type exp-cell) (:type act-cell))\n"
       "        (when (:city-status exp-cell)\n"
       "          (should= (:city-status exp-cell) (:city-status act-cell)))\n"
       "        (if (:contents exp-cell)\n"
       "          (do (should-not-be-nil (:contents act-cell))\n"
       "              (should= (:type (:contents exp-cell)) (:type (:contents act-cell)))\n"
       "              (should= (:owner (:contents exp-cell)) (:owner (:contents act-cell))))\n"
       "          (should-be-nil (:contents act-cell)))))"))

(defn- generate-refueling-position-near-then [{:keys [unit target]}]
  (let [target-expr (target-pos-expr target)]
    (str "    (let [unit-data (:unit (get-test-unit atoms/game-map \"" unit "\"))\n"
         "          carrier-target (:carrier-target unit-data)\n"
         "          expected-pos " target-expr "\n"
         "          distance (+ (Math/abs (- (first carrier-target) (first expected-pos)))\n"
         "                      (Math/abs (- (second carrier-target) (second expected-pos))))]\n"
         "      (should= :position (:refueling unit-data))\n"
         "      (should (<= distance 1)))")))

(defn generate-then
  "Generate code string for a single THEN IR node."
  [then-ir givens]
  (case (:type then-ir)
    :unit-prop (generate-unit-prop-then then-ir)
    :unit-absent (generate-unit-absent-then then-ir)
    :unit-present (generate-unit-present-then then-ir)
    :unit-at (generate-unit-at-then then-ir)
    :unit-at-next-round (generate-unit-at-next-round-then then-ir)
    :unit-after-moves (generate-unit-after-moves-then then-ir)
    :unit-after-steps (generate-unit-after-steps-then then-ir)
    :unit-eventually-at (generate-unit-eventually-at-then then-ir)
    :unit-occupies-cell (generate-unit-occupies-cell-then then-ir givens)
    :unit-unmoved (generate-unit-unmoved-then then-ir givens)
    :unit-waiting-for-input (generate-unit-waiting-for-input-then then-ir)
    :message-contains (generate-message-contains-then then-ir)
    :message-for-unit (generate-message-for-unit-then then-ir)
    :message-is (generate-message-is-then then-ir)
    :no-message (generate-no-message-then then-ir)
    :cell-prop (generate-cell-prop-then then-ir)
    :cell-type (generate-cell-type-then then-ir)
    :waiting-for-input (generate-waiting-for-input-then then-ir)
    :container-prop (generate-container-prop-then then-ir)
    :round (generate-round-then then-ir)
    :destination (generate-destination-then then-ir)
    :production (generate-production-then then-ir)
    :production-with-rounds (generate-production-with-rounds-then then-ir)
    :no-production (generate-no-production-then then-ir)
    :production-not (generate-production-not-then then-ir)
    :game-paused (generate-game-paused-then then-ir)
    :player-map-cell-not-nil (generate-player-map-cell-not-nil-then then-ir)
    :player-map-cell-nil (generate-player-map-cell-nil-then then-ir)
    :player-map-visibility (generate-player-map-visibility-then then-ir)
    :territory-map (generate-territory-map-then then-ir)
    :no-unit-at (generate-no-unit-at-then then-ir)
    :unit-prop-absent (generate-unit-prop-absent-then then-ir)
    :computer-army-count (generate-computer-army-count-then then-ir)
    :refueling-position-near (generate-refueling-position-near-then then-ir)
    :shipyard-has-ship (generate-shipyard-has-ship-then then-ir)
    :shipyard-empty (generate-shipyard-empty-then then-ir)
    :map-is (generate-map-is-then then-ir)
    :unrecognized (str "    (pending \"Unrecognized: " (:text then-ir) "\")")
    (str "    ;; Unknown then type: " (:type then-ir))))

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
        given-code (str/join "\n" (map #(generate-given % givens) regular-givens))
        raw-when-code (str/join "\n" (map #(generate-when % givens) whens))
        when-code (if (seq all-bindings)
                    (wrap-with-redefs all-bindings raw-when-code)
                    raw-when-code)
        then-code (str/join "\n" (map #(generate-then % givens) thens))
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

(ns empire.acceptance.parser
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [empire.config :as config]))

;; --- Helpers ---

(defn- strip-trailing-period [s]
  (if (str/ends-with? s ".")
    (subs s 0 (dec (count s)))
    s))

(defn- strip-keyword-prefix [line]
  (-> line
      (str/replace #"^(?:GIVEN|WHEN|THEN)\s+" "")
      str/trim))

(defn- blank-or-comment? [line]
  (or (str/blank? line)
      (str/starts-with? (str/trim line) ";")))

(defn- separator-line? [line]
  (re-matches #"\s*;=+\s*" line))

(defn- map-row? [line]
  (let [trimmed (str/trim line)]
    (and (not (str/blank? trimmed))
         (not (str/starts-with? trimmed ";"))
         (not (re-matches #"(?i)^(GIVEN|WHEN|THEN)\b.*" trimmed))
         (not (re-matches #"^[A-Za-z].*\s+(is|has|are|with)\b.*" trimmed))
         (re-matches #"^[~#.=% +XOAFTDCSBPVatdfcsbpv\-]+$" trimmed))))

(defn- territory-map-row? [line]
  (let [trimmed (str/trim line)]
    (and (not (str/blank? trimmed))
         (re-matches #"^[0-9~.]+$" trimmed))))

(def direction-keys #{"q" "w" "e" "a" "d" "z" "x" "c"})

(defn- lowercase-direction? [k]
  (contains? direction-keys (name k)))

(defn- uppercase-direction? [k]
  (let [n (name k)]
    (and (= 1 (count n))
         (contains? direction-keys (str/lower-case n))
         (Character/isUpperCase ^char (first n)))))

(defn- parse-coords [s]
  (when-let [[_ x y] (re-find #"\[(\d+)\s+(\d+)\]" s)]
    [(Integer/parseInt x) (Integer/parseInt y)]))

(defn- parse-number [s]
  (try (Integer/parseInt s) (catch Exception _ nil)))

(def unit-name->char
  {"army" "A" "fighter" "F" "transport" "T" "destroyer" "D"
   "carrier" "C" "submarine" "S" "battleship" "B" "patrol-boat" "P"
   "satellite" "V"})

(defn- normalize-unit-ref [s]
  (get unit-name->char (str/lower-case s) s))

(def player-unit-chars #{"A" "F" "T" "D" "C" "S" "B" "P" "V"})
(def computer-unit-chars #{"a" "f" "t" "d" "c" "s" "b" "p" "v"})
(def city-chars #{"O" "X" "+"})
(def ship-unit-chars #{"T" "D" "C" "S" "B" "P" "t" "d" "c" "s" "b" "p"})

(defn- unit-char? [s]
  (or (contains? player-unit-chars s)
      (contains? computer-unit-chars s)
      (re-matches #"[A-Za-z]\d+" s)))

(defn- city-or-unit-char? [s]
  (or (unit-char? s) (contains? city-chars s)))

(def ^:private word->number
  {"no" 0 "one" 1 "two" 2 "three" 3 "four" 4 "five" 5
   "six" 6 "seven" 7 "eight" 8 "nine" 9 "ten" 10})

(defn- parse-count [s]
  (or (get word->number (str/lower-case s))
      (parse-number s)))

(def ^:private cell-prop-aliases
  {"spawn-orders" :marching-orders
   "flight-orders" :flight-path})

(defn- resolve-cell-prop [k]
  (or (get cell-prop-aliases k) (keyword k)))

;; --- Pattern table dispatch ---

(defn- first-matching-pattern
  "Scan patterns for the first whose :regex matches text.
   Returns (handler match) or nil."
  [patterns text]
  (loop [entries patterns]
    (when-let [{:keys [regex handler]} (first entries)]
      (if-let [match (re-find regex text)]
        (handler match)
        (recur (rest entries))))))

(defn- first-matching-pattern-with-context
  "Like first-matching-pattern, but passes (match, context) to handler."
  [patterns text context]
  (loop [entries patterns]
    (when-let [{:keys [regex handler]} (first entries)]
      (if-let [match (re-find regex text)]
        (handler match context)
        (recur (rest entries))))))

;; --- GIVEN parsing ---

(def ^:private unit-prop-extractors
  [{:regex #"(?:is|has mode|mode)\s+([\w-]+)"
    :extract-fn (fn [[_ mode]] {:props {:mode (keyword mode)}})}
   {:regex #"(?:has\s+)?fuel\s+(\d+)"
    :extract-fn (fn [[_ n]] {:props {:fuel (Integer/parseInt n)}})}
   {:regex #"with\s+fuel\s+(\d+)"
    :extract-fn (fn [[_ n]] {:props {:fuel (Integer/parseInt n)}})}
   {:regex #"army-count\s+(\d+)"
    :extract-fn (fn [[_ n]] {:props {:army-count (Integer/parseInt n)}})}
   {:regex #"(\w+)\s+(?:army|armies)"
    :extract-fn (fn [[_ n]]
                  (when-let [cnt (parse-count n)]
                    {:props {:army-count cnt}}))}
   {:regex #"hits\s+(\d+)"
    :extract-fn (fn [[_ n]] {:props {:hits (Integer/parseInt n)}})}
   {:regex #"fighter-count\s+(\d+)"
    :extract-fn (fn [[_ n]] {:container-props {:fighter-count (Integer/parseInt n)}})}
   {:regex #"awake-fighters\s+(\d+)"
    :extract-fn (fn [[_ n]] {:container-props {:awake-fighters (Integer/parseInt n)}})}
   {:regex #"(\w+)\s+fighters?\b(?!\s+in)"
    :extract-fn (fn [[_ n]]
                  (when-let [cnt (parse-count n)]
                    {:container-props {:fighter-count cnt}}))}
   {:regex #"no\s+awake\s+fighters?"
    :extract-fn (fn [_] {:container-props {:awake-fighters 0}})}
   {:regex #"(\w+)\s+awake\s+fighters?"
    :extract-fn (fn [[_ n]]
                  (when (not= n "no")
                    (when-let [cnt (parse-count n)]
                      {:container-props {:awake-fighters cnt}})))}
   ;; "has mission <value>" for transport-mission
   {:regex #"(?:with|has)\s+mission\s+(\w+)"
    :extract-fn (fn [[_ v]]
                  {:props {:transport-mission (keyword v)}})}
   ;; "has escort destroyer" - natural language for escort-destroyer-id
   {:regex #"(?:with|has)\s+(?:an?\s+)?escort\s+destroyer"
    :extract-fn (fn [_] {:props {:escort-destroyer-id 1}})}
   ;; Catch-all: "has <hyphenated-property> <value>" for unit properties like country-id, patrol-country-id, been-to-sea
   {:regex #"(?:with|has)\s+([\w]+-[\w-]+)\s+(\S+)"
    :extract-fn (fn [[_ k v]]
                  (when-not (#{"army-count" "fighter-count" "awake-fighters"} k)
                    {:props {(keyword k) (or (parse-number v)
                                             (case v "true" true "false" false nil)
                                             (keyword v))}}))}])

(defn- parse-unit-props-line [line]
  (let [clean (strip-trailing-period (str/trim line))
        clean (strip-keyword-prefix clean)]
    (when-let [[_ unit rest-str] (re-matches #"(\w+)\s+(.*)" clean)]
      (when (city-or-unit-char? unit)
        (let [{:keys [props container-props]}
              (reduce (fn [acc {:keys [regex extract-fn]}]
                        (if-let [match (re-find regex rest-str)]
                          (if-let [extracted (extract-fn match)]
                            (merge-with merge acc extracted)
                            acc)
                          acc))
                      {:props {} :container-props {}}
                      unit-prop-extractors)
              result {:type :unit-props :unit unit :props props}]
          (when (or (seq props) (seq container-props))
            (if (seq container-props)
              (assoc result :container-props container-props)
              result)))))))

(defn- parse-container-state-line [line]
  (let [clean (strip-trailing-period (str/trim line))
        clean (strip-keyword-prefix clean)]
    (cond
      ;; "O has one fighter in its airport"
      (re-find #"(\w+)\s+has\s+one\s+fighter\s+in\s+its\s+airport" clean)
      (let [[_ target] (re-find #"(\w+)\s+has\s+one\s+fighter" clean)]
        {:type :container-state :target target :props {:fighter-count 1 :awake-fighters 1}})

      ;; "C has no fighters"
      (re-find #"(\w+)\s+has\s+no\s+fighters" clean)
      (let [[_ target] (re-find #"(\w+)\s+has\s+no\s+fighters" clean)]
        {:type :container-state :target target :props {:fighter-count 0}})

      ;; "C has three fighters" (natural language count)
      (re-find #"(\w+)\s+has\s+(\w+)\s+fighters?" clean)
      (let [[_ target n] (re-find #"(\w+)\s+has\s+(\w+)\s+fighters?" clean)]
        (when-let [count (parse-count n)]
          {:type :container-state :target target :props {:fighter-count count}}))

      :else nil)))

;; --- GIVEN parsing: handler functions ---

(defn- given-handle-game-map [_] {:directive :map-start :target :game-map})
(defn- given-handle-game-map-explicit [_] {:directive :map-start :target :game-map})
(defn- given-handle-player-map [_] {:directive :map-start :target :player-map})
(defn- given-handle-computer-map [_] {:directive :map-start :target :computer-map})

(defn- given-handle-waiting-for-input [[_ unit] ctx]
  (let [mode-already-set (contains? (:units-with-mode ctx) unit)]
    {:directive :waiting-for-input
     :ir {:type :waiting-for-input :unit unit :set-mode (not mode-already-set)}}))

(defn- given-handle-production-with-rounds [[_ city item n] _ctx]
  {:directive :production
   :ir {:type :production :city city :item (keyword item) :remaining-rounds (Integer/parseInt n)}})

(defn- given-handle-production [[_ city item] _ctx]
  {:directive :production
   :ir {:type :production :city city :item (keyword item)}})

(defn- given-handle-no-production [_ _ctx]
  {:directive :no-production :ir {:type :no-production}})

(defn- given-handle-round [[_ n] _ctx]
  {:directive :round :ir {:type :round :value (Integer/parseInt n)}})

(defn- given-handle-destination [[_ x y] _ctx]
  {:directive :destination
   :ir {:type :destination :coords [(Integer/parseInt x) (Integer/parseInt y)]}})

(defn- given-handle-cell-props [[_ x y rest-str] _ctx]
  (let [pairs (str/split rest-str #"\s+and\s+")
        props (into {}
                    (for [pair pairs
                          :let [[_ k v] (re-find #"(\S+)\s+(.*\S)" (str/trim pair))]
                          :when k]
                      [(resolve-cell-prop k) (or (parse-number v)
                                       (parse-coords v)
                                       (keyword v))]))]
    {:directive :cell-props
     :ir {:type :cell-props :coords [(Integer/parseInt x) (Integer/parseInt y)] :props props}}))

(defn- given-handle-player-items-multi [[_ items-str] _ctx]
  (let [items (mapv str/trim (str/split items-str #",\s*"))]
    {:directive :player-items :ir {:type :player-items :items items}}))

(defn- given-handle-player-items-single [[_ item] _ctx]
  {:directive :player-items :ir {:type :player-items :items [item]}})

(defn- given-handle-waiting-for-input-bare [_ _ctx]
  {:directive :waiting-for-input-bare :ir {:type :waiting-for-input-state}})

(defn- given-handle-unit-target [[_ unit target] _ctx]
  {:directive :unit-target :ir {:type :unit-target :unit unit :target target}})

(defn- given-handle-city-unit [[_ city owner unit-type] _ctx]
  {:directive :city-unit
   :ir {:type :city-unit :city city :unit-type (keyword unit-type) :owner (keyword owner)}})

(defn- given-handle-shipyard-state [[_ city ship-type hits] _ctx]
  {:directive :shipyard-state
   :ir {:type :shipyard-state :city city :ship-type (keyword ship-type) :hits (Integer/parseInt hits)}})

;; --- GIVEN parsing: pattern tables ---

(def ^:private given-map-patterns
  [{:regex #"(?i)^(?:GIVEN\s+)?(?:game\s+)?map\s*$"
    :handler given-handle-game-map}
   {:regex #"(?i)^(?:GIVEN\s+)?game\s+map"
    :handler given-handle-game-map-explicit}
   {:regex #"(?i)^(?:GIVEN\s+)?player\s+map"
    :handler given-handle-player-map}
   {:regex #"(?i)^(?:GIVEN\s+)?computer\s+map"
    :handler given-handle-computer-map}])

(def ^:private given-directive-patterns
  [{:regex #"(?:the\s+)?game\s+is\s+waiting\s+for\s+input"
    :handler (fn [_ _ctx]
               {:directive :waiting-for-input-bare :ir {:type :waiting-for-input-state}})}
   {:regex #"(\w+)\s+is\s+waiting\s+for\s+input"
    :handler given-handle-waiting-for-input}
   {:regex #"production\s+at\s+(\w+)\s+is\s+(\w+)\s+with\s+(\d+)\s+rounds?\s+remaining"
    :handler given-handle-production-with-rounds}
   {:regex #"production\s+at\s+(\w+)\s+is\s+(\w+)"
    :handler given-handle-production}
   {:regex #"no\s+production"
    :handler given-handle-no-production}
   {:regex #"round\s+(\d+)"
    :handler given-handle-round}
   {:regex #"destination\s+\[(\d+)\s+(\d+)\]"
    :handler given-handle-destination}
   {:regex #"cell\s+\[(\d+)\s+(\d+)\]\s+has\s+(.*)"
    :handler given-handle-cell-props}
   {:regex #"player-items\s+are\s+(.*)"
    :handler given-handle-player-items-multi}
   {:regex #"player-items\s+(\w+)"
    :handler given-handle-player-items-single}
   {:regex #"player\s+units?\s+are\s+(.*)"
    :handler given-handle-player-items-multi}
   {:regex #"player\s+units?\s+(\w+)"
    :handler given-handle-player-items-single}
   {:regex #"^waiting-for-input$"
    :handler given-handle-waiting-for-input-bare}
   {:regex #"(\w+)'s\s+target\s+is\s+(\S+)"
    :handler given-handle-unit-target}
   {:regex #"(\w+)\s+has\s+(?:a|an)\s+(player|computer)\s+(\w+)"
    :handler given-handle-city-unit}
   {:regex #"(\w+)\s+has\s+(?:a|an)\s+(\w+)\s+with\s+(\d+)\s+hits?\s+in\s+its\s+shipyard"
    :handler given-handle-shipyard-state}
   {:regex #"(?:the\s+)?computer\s+controls?\s+(\d+)\s+cit(?:y|ies)"
    :handler (fn [[_ n] _ctx]
               {:directive :stub
                :ir {:type :stub
                     :bindings [{:var "empire.computer.production/count-computer-cities"
                                 :value (str "(constantly " n ")")}]}})}
   {:regex #"(?:a\s+)?valid\s+carrier\s+position\s+exists"
    :handler (fn [_ _ctx]
               {:directive :stub
                :ir {:type :stub
                     :bindings [{:var "empire.computer.ship/find-carrier-position"
                                 :value "(constantly [0 0])"}]}})}
   {:regex #"(\w+)\s+belongs\s+to\s+country\s+(\d+)"
    :handler (fn [[_ ref n] _ctx]
               (let [country-id (Integer/parseInt n)]
                 (if (contains? city-chars ref)
                   {:directive :city-prop
                    :ir {:type :city-prop :city ref :prop :country-id :value country-id}}
                   {:directive :unit-props
                    :ir {:type :unit-props :unit ref :props {:country-id country-id}}})))}
   {:regex #"(\w+)\s+patrols\s+(?:for\s+)?country\s+(\d+)"
    :handler (fn [[_ ref n] _ctx]
               {:directive :unit-props
                :ir {:type :unit-props :unit ref :props {:patrol-country-id (Integer/parseInt n)}}})}])

(defn- parse-given-line [line context]
  (let [clean (str/trim line)
        stripped (strip-trailing-period clean)
        given-text (strip-keyword-prefix stripped)]
    (or (first-matching-pattern given-map-patterns stripped)
        (first-matching-pattern-with-context given-directive-patterns given-text context)
        (when-let [ir (parse-container-state-line line)]
          {:directive :container-state :ir ir})
        (when-let [ir (parse-unit-props-line line)]
          {:directive :unit-props :ir ir})
        {:directive :unrecognized
         :ir {:type :unrecognized :text clean}})))

(defn parse-given
  "Parse GIVEN lines into IR. Returns {:givens [...] :context updated-context}"
  [lines context]
  (let [context (atom (merge {:units-with-mode #{}} context))
        givens (atom [])
        i (atom 0)]
    (while (< @i (count lines))
      (let [line (nth lines @i)
            trimmed (str/trim line)]
        (if (blank-or-comment? line)
          (swap! i inc)
          (let [parsed (parse-given-line trimmed @context)]
            (case (:directive parsed)
              :map-start
              (let [target (:target parsed)
                    _ (swap! i inc)
                    rows (atom [])]
                (while (and (< @i (count lines))
                            (map-row? (nth lines @i)))
                  (swap! rows conj (str/trim (nth lines @i)))
                  (swap! i inc))
                (swap! givens conj {:type :map :target target :rows @rows}))

              :waiting-for-input
              (do
                (swap! givens conj (:ir parsed))
                (swap! i inc))

              :unit-props
              (let [ir (:ir parsed)]
                (when (:mode (:props ir))
                  (swap! context update :units-with-mode conj (:unit ir)))
                (when (seq (:props ir))
                  (swap! givens conj (dissoc ir :container-props)))
                (when-let [cp (:container-props ir)]
                  (swap! givens conj {:type :container-state :target (:unit ir) :props cp}))
                (swap! i inc))

              :container-state
              (do (swap! givens conj (:ir parsed))
                  (swap! i inc))

              (:production :no-production :round :destination :cell-props
               :player-items :waiting-for-input-bare :unit-target :city-prop :stub
               :shipyard-state :city-unit)
              (do (swap! givens conj (:ir parsed))
                  (swap! i inc))

              :unrecognized
              (do (swap! givens conj (:ir parsed))
                  (swap! i inc))

              ;; default
              (swap! i inc))))))
    {:givens @givens :context @context}))

;; --- WHEN parsing ---

(defn- determine-key-type [key-str context]
  (let [k (keyword key-str)]
    (cond
      (= key-str "space") {:key :space :input-fn :key-down}
      (uppercase-direction? k) {:key k :input-fn :key-down}
      (and (lowercase-direction? k) (:has-waiting-for-input context)) {:key k :input-fn :handle-key}
      :else {:key k :input-fn :key-down})))

(defn- determine-combat-type [context]
  (let [unit-types (or (:unit-types context) #{})]
    (if (some ship-unit-chars unit-types)
      :ship
      :army)))

;; --- WHEN parsing: handler functions ---
;; Handlers return a vector of IR nodes (compound patterns emit multiple).

(defn- when-handle-backtick [[_ x y k] _ctx]
  [{:type :backtick
    :key (keyword k)
    :mouse-cell [(Integer/parseInt x) (Integer/parseInt y)]}])

(defn- when-handle-mouse-at-key [[_ x y k] _ctx]
  [{:type :mouse-at-key
    :coords [(Integer/parseInt x) (Integer/parseInt y)]
    :key (keyword k)}])

(defn- when-handle-waiting-and-key [[_ unit k] ctx]
  (let [key-info (determine-key-type k ctx)]
    [{:type :waiting-for-input :unit unit :set-mode true}
     (merge {:type :key-press} key-info)]))

(defn- when-handle-battle [[_ k outcome] ctx]
  (let [outcome-kw (case outcome "wins" :win "loses" :lose (keyword outcome))]
    [{:type :battle
      :key (keyword k)
      :outcome outcome-kw
      :combat-type (determine-combat-type ctx)}]))

(defn- when-handle-key-and-advance [[_ k unit] ctx]
  (let [key-info (determine-key-type k ctx)]
    [(merge {:type :key-press} key-info)
     {:type :advance-until-waiting :unit unit}]))

(defn- when-handle-key-press [[_ k] ctx]
  (let [key-info (determine-key-type k ctx)
        when-text (:when-text ctx)]
    (when (and when-text (re-find #"presses\s+\w+\s+and\s+" when-text))
      (println (str "WARNING: unconsumed trailing text in WHEN: " when-text)))
    [(merge {:type :key-press} key-info)]))

(defn- when-handle-types-keys [[_ keys-str] ctx]
  (let [keys (str/split (str/trim keys-str) #"\s+")]
    (mapv (fn [k] (merge {:type :key-press} (determine-key-type k ctx))) keys)))

(defn- when-handle-new-round-and-waiting [[_ unit] _ctx]
  [{:type :start-new-round}
   {:type :advance-until-waiting :unit unit}])

(defn- when-handle-new-round [_match ctx]
  (let [when-text (:when-text ctx)]
    (when (and when-text (re-find #"starts\s+and\s+" when-text))
      (println (str "WARNING: unconsumed trailing text in WHEN: " (:clean-text ctx))))
    [{:type :start-new-round}]))

(defn- when-handle-advance-game-batch [_ _ctx]
  [{:type :advance-game-batch}])

(defn- when-handle-advance-game [_ _ctx]
  [{:type :advance-game}])

(defn- when-handle-process-player-items [_ _ctx]
  [{:type :process-player-items}])

(defn- when-handle-visibility-update [_ _ctx]
  [{:type :visibility-update}])

(defn- when-handle-standalone-waiting [[_ unit] _ctx]
  [{:type :waiting-for-input :unit unit :set-mode true}])

(defn- when-handle-evaluate-production [[_ city] _ctx]
  [{:type :evaluate-production :city city}])

(defn- when-handle-process-computer-transport [[_ unit] _ctx]
  [{:type :process-computer-transport :unit unit}])

(defn- when-handle-computer-rounds [[_ n] _ctx]
  [{:type :computer-rounds :count (parse-count n)}])

;; --- WHEN parsing: pattern table ---

(def ^:private when-patterns
  [{:regex #"mouse\s+is\s+at\s+cell\s+\[(\d+)\s+(\d+)\]\s+and.*backtick\s+then\s+(\w)"
    :handler when-handle-backtick}
   {:regex #"mouse\s+is\s+at\s+cell\s+\[(\d+)\s+(\d+)\]\s+and.*presses\s+(\w+)"
    :handler when-handle-mouse-at-key}
   {:regex #"(\w+)\s+is\s+waiting\s+for\s+input\s+and\s+the\s+player\s+presses\s+(\w+)"
    :handler when-handle-waiting-and-key}
   {:regex #"player\s+presses\s+(\w+)\s+and\s+(wins|loses)\s+the\s+battle"
    :handler when-handle-battle}
   {:regex #"player\s+presses\s+(\w+)\s+and\s+(?:the\s+game\s+advances\s+until\s+)?(\w+)\s+is\s+waiting\s+for\s+input"
    :handler when-handle-key-and-advance}
   {:regex #"player\s+presses\s+(\w+)"
    :handler when-handle-key-press}
   {:regex #"player\s+types\s+(.*)"
    :handler when-handle-types-keys}
   {:regex #"new\s+round\s+starts\s+and\s+(\w+)\s+is\s+waiting\s+for\s+input"
    :handler when-handle-new-round-and-waiting}
   {:regex #"(?:new\s+round\s+starts|next\s+round\s+begins)"
    :handler when-handle-new-round}
   {:regex #"game\s+advances\s+one\s+batch"
    :handler when-handle-advance-game-batch}
   {:regex #"game\s+advances"
    :handler when-handle-advance-game}
   {:regex #"player\s+items\s+are\s+processed"
    :handler when-handle-process-player-items}
   {:regex #"cell\s+visibility\s+updates\s+for\s+(\w+)"
    :handler (fn [[_ unit] _ctx]
               [{:type :cell-visibility-update :unit unit}])}
   {:regex #"visibility\s+updates"
    :handler when-handle-visibility-update}
   {:regex #"production\s+for\s+(\w+)\s+is\s+evaluated"
    :handler when-handle-evaluate-production}
   {:regex #"computer\s+chooses\s+production\s+at\s+(\w+)"
    :handler when-handle-evaluate-production}
   {:regex #"computer\s+transport\s+(\w+)\s+is\s+processed"
    :handler when-handle-process-computer-transport}
   {:regex #"(\w+)\s+computer\s+rounds?\s+pass"
    :handler when-handle-computer-rounds}
   {:regex #"(\w+)\s+is\s+waiting\s+for\s+input"
    :handler when-handle-standalone-waiting}])

(defn parse-when
  "Parse WHEN lines into IR. Returns {:whens [...]}"
  [lines context]
  (let [whens (atom [])]
    (doseq [line lines]
      (let [clean (str/trim line)
            stripped (strip-trailing-period clean)
            when-text (strip-keyword-prefix stripped)]
        (when-not (blank-or-comment? clean)
          (let [line-ctx (assoc context :when-text when-text :clean-text clean)
                result (or (first-matching-pattern-with-context when-patterns when-text line-ctx)
                           [{:type :unrecognized :text clean}])]
            (doseq [ir result]
              (swap! whens conj ir))))))
    {:whens @whens}))

;; --- THEN parsing: handler functions ---

(defn- then-handle-after-moves [[_ n unit target]]
  {:type :unit-after-moves :unit unit :moves (parse-count n) :target target})

(defn- then-handle-after-steps-coords [[_ n unit x y]]
  {:type :unit-after-steps :unit unit :steps (parse-count n)
   :coords [(Integer/parseInt x) (Integer/parseInt y)]})

(defn- then-handle-after-steps-target [[_ n unit target]]
  {:type :unit-after-steps :unit unit :steps (parse-count n) :target target})

(defn- then-handle-unit-waiting-for-input [[_ unit]]
  {:type :unit-waiting-for-input :unit unit})

(defn- then-handle-unit-at-position-with-mode [[_ unit x y mode]]
  [{:type :unit-at :unit unit :coords [(Integer/parseInt x) (Integer/parseInt y)]}
   {:type :unit-prop :unit unit :property :mode :expected (keyword mode)}])

(defn- then-handle-unit-at-coords [[_ unit x y]]
  {:type :unit-at :unit unit :coords [(Integer/parseInt x) (Integer/parseInt y)]})

(defn- then-handle-unit-at-target [[_ unit target]]
  {:type :unit-at :unit unit :target target})

(defn- then-handle-eventually-at [[_ unit target]]
  {:type :unit-eventually-at :unit unit :target target})

(defn- then-handle-unit-absent-on-map [[_ unit]]
  {:type :unit-absent :unit (normalize-unit-ref unit)})

(defn- then-handle-no-message [[_ area]]
  {:type :no-message :area (keyword area)})

(defn- then-handle-message-for-unit [[_ area unit key-str]]
  {:type :message-for-unit :area (keyword area) :unit unit
   :config-key (keyword (strip-trailing-period key-str))})

(defn- then-handle-message-contains-literal [[_ area text]]
  {:type :message-contains :area (keyword area) :text text})

(defn- then-handle-message-contains-key [[_ area key-str]]
  {:type :message-contains :area (keyword area)
   :config-key (keyword (strip-trailing-period key-str))})

(defn- then-handle-message-is-key [[_ area key-str]]
  {:type :message-is :area (keyword area)
   :config-key (keyword (strip-trailing-period key-str))})

(defn- then-handle-message-is-format [[_ area key-str args-str]]
  (let [args (mapv #(let [s (str/trim %)]
                      (or (parse-number s) s))
                   (str/split args-str #"\s+"))]
    {:type :message-is :area (keyword area) :format {:key (keyword key-str) :args args}}))

(defn- then-handle-bare-message-literal [[_ text]]
  {:type :message-contains :area :attention :text text})

(defn- then-handle-bare-message-key [[_ key-str]]
  {:type :message-contains :area :attention
   :config-key (keyword (strip-trailing-period key-str))})

(defn- then-handle-out-of-fuel [_]
  {:type :message-contains :area :attention :config-key :fighter-out-of-fuel})

(defn- then-handle-player-map-not-nil [[_ x y]]
  {:type :player-map-cell-not-nil :coords [(Integer/parseInt x) (Integer/parseInt y)]})

(defn- then-handle-player-map-nil [[_ x y]]
  {:type :player-map-cell-nil :coords [(Integer/parseInt x) (Integer/parseInt y)]})

(defn- then-handle-cell-prop [[_ x y prop val]]
  {:type :cell-prop :coords [(Integer/parseInt x) (Integer/parseInt y)]
   :property (resolve-cell-prop prop) :expected (keyword val)})

(defn- then-handle-cell-type [[_ x y t]]
  {:type :cell-type :coords [(Integer/parseInt x) (Integer/parseInt y)]
   :expected (keyword t)})

(defn- then-handle-waiting-for-input [_]
  {:type :waiting-for-input :expected true})

(defn- then-handle-not-waiting-for-input [_]
  {:type :waiting-for-input :expected false})

(defn- then-handle-game-paused [_]
  {:type :game-paused :expected true})

(defn- then-handle-round [[_ n]]
  {:type :round :expected (Integer/parseInt n)})

(defn- then-handle-destination [[_ x y]]
  {:type :destination :expected [(Integer/parseInt x) (Integer/parseInt y)]})

(defn- then-handle-production-with-rounds [[_ city item n]]
  {:type :production-with-rounds :city city :expected (keyword item)
   :remaining-rounds (Integer/parseInt n)})

(defn- then-handle-production [[_ city item]]
  {:type :production :city city :expected (keyword item)})

(defn- then-handle-no-production [[_ city]]
  {:type :no-production :city city})

(defn- then-handle-production-not [[_ city item]]
  {:type :production-not :city city :excluded (keyword item)})

(defn- then-handle-no-unit-at [[_ x y]]
  {:type :no-unit-at :coords [(Integer/parseInt x) (Integer/parseInt y)]})

(defn- then-handle-unit-has-mission [[_ unit val]]
  (when (city-or-unit-char? unit)
    {:type :unit-prop :unit unit :property :transport-mission :expected (keyword val)}))

(defn- then-handle-unit-has-prop [[_ unit prop val]]
  (let [val (str/trim val)]
    (when (city-or-unit-char? unit)
      {:type :unit-prop :unit unit
       :property (keyword prop)
       :expected (or (parse-number val) (parse-coords val) (keyword val))})))

(defn- then-handle-unit-is-mode [[_ unit val]]
  (when (city-or-unit-char? unit)
    {:type :unit-prop :unit unit :property :mode :expected (keyword val)}))

(defn- then-handle-unit-prop-absent [[_ unit prop]]
  {:type :unit-prop-absent :unit unit :property (keyword prop)})

;; --- THEN parsing: timed-pattern handlers ---

(defn- then-handle-will-be-at [[_ unit target]]
  (if-let [coords (parse-coords (str "[" (str/replace target #"[\[\]]" "") "]"))]
    {:type :unit-at-next-round :unit unit :coords coords}
    {:type :unit-at-next-round :unit unit :target target}))

(defn- then-handle-occupies-cell [[_ unit target]]
  {:type :unit-occupies-cell :unit unit :target-unit target})

(defn- then-handle-remains-unmoved [[_ unit]]
  {:type :unit-unmoved :unit unit})

(defn- then-handle-airport-fighter [[_ target]]
  {:type :container-prop :target target :property :fighter-count :expected 1 :lookup :city})

(defn- then-handle-fighter-aboard [[_ target]]
  {:type :container-prop :target target :property :fighter-count :expected 1 :lookup :unit})

(defn- then-handle-no-fighters [[_ target]]
  {:type :container-prop :target target :property :fighter-count :expected 0
   :lookup (if (contains? city-chars target) :city :unit)})

(defn- then-handle-awake-fighters [[_ target n]]
  {:type :container-prop :target target :property :awake-fighters
   :expected (parse-count n)
   :lookup (if (contains? city-chars target) :city :unit)})

(defn- then-handle-unit-absent-short [[_ unit]]
  {:type :unit-absent :unit (normalize-unit-ref unit)})

(defn- then-handle-refueling-position-near [[_ unit target]]
  {:type :refueling-position-near :unit unit :target target})

(defn- then-handle-unit-present-coords [[_ unit x y]]
  {:type :unit-present :unit unit :coords [(Integer/parseInt x) (Integer/parseInt y)]})

(defn- then-handle-unit-present-target [[_ unit target]]
  {:type :unit-present :unit unit :target target})

;; --- THEN parsing: pattern tables ---

(def ^:private then-bare-patterns
  [{:regex #"^after\s+(\w+)\s+moves?\s+(\w+)\s+will\s+be\s+at\s+(\S+)"
    :handler then-handle-after-moves}
   {:regex #"^after\s+(\w+)\s+steps?\s+there\s+is\s+an?\s+(\w+)\s+at\s+\[(\d+)\s+(\d+)\]"
    :handler then-handle-after-steps-coords}
   {:regex #"^after\s+(\w+)\s+steps?\s+there\s+is\s+an?\s+(\w+)\s+at\s+(\S+)"
    :handler then-handle-after-steps-target}
   {:regex #"^(\w+)\s+is\s+waiting\s+for\s+input$"
    :handler then-handle-unit-waiting-for-input}
   {:regex #"^(\w+)\s+wakes\s+up\s+and\s+asks\s+for\s+input"
    :handler then-handle-unit-waiting-for-input}
   {:regex #"^(\w+)\s+is\s+at\s+\[(\d+)\s+(\d+)\]\s+in\s+mode\s+([\w-]+)"
    :handler then-handle-unit-at-position-with-mode}
   {:regex #"^(\w+)\s+is\s+at\s+\[(\d+)\s+(\d+)\]"
    :handler then-handle-unit-at-coords}
   {:regex #"^(\w+)\s+is\s+at\s+(\S+)$"
    :handler then-handle-unit-at-target}
   {:regex #"eventually\s+(\w+)\s+will\s+be\s+at\s+(\S+)"
    :handler then-handle-eventually-at}
   {:regex #"there\s+is\s+no\s+(\w+)\s+on\s+the\s+map"
    :handler then-handle-unit-absent-on-map}
   {:regex #"there\s+is\s+no\s+(attention|turn|error)\s+message"
    :handler then-handle-no-message}
   {:regex #"(?:the\s+)?(attention|turn|error)\s+message\s+for\s+(\w+)\s+contains\s+:(\S+)"
    :handler then-handle-message-for-unit}
   {:regex #"(?:the\s+)?(attention|turn|error)\s+message\s+contains\s+\"([^\"]+)\""
    :handler then-handle-message-contains-literal}
   {:regex #"(?:the\s+)?(attention|turn|error)\s+message\s+contains\s+:(\S+)"
    :handler then-handle-message-contains-key}
   {:regex #"(?:the\s+)?(attention|turn|error)\s+message\s+is\s+\(fmt\s+:(\S+)\s+(.*)\)"
    :handler then-handle-message-is-format}
   {:regex #"(?:the\s+)?(attention|turn|error)\s+message\s+is\s+:(\S+)"
    :handler then-handle-message-is-key}
   {:regex #"(?:the\s+)?message\s+contains\s+\"([^\"]+)\""
    :handler then-handle-bare-message-literal}
   {:regex #"(?:the\s+)?message\s+contains\s+:(\S+)"
    :handler then-handle-bare-message-key}
   {:regex #"out-of-fuel\s+message\s+is\s+displayed"
    :handler then-handle-out-of-fuel}
   {:regex #"player-map\s+cell\s+\[(\d+)\s+(\d+)\]\s+is\s+not\s+nil"
    :handler then-handle-player-map-not-nil}
   {:regex #"player-map\s+cell\s+\[(\d+)\s+(\d+)\]\s+is\s+nil"
    :handler then-handle-player-map-nil}
   {:regex #"(?:the\s+)?player\s+can\s+see\s+\[(\d+)\s+(\d+)\]"
    :handler then-handle-player-map-not-nil}
   {:regex #"(?:the\s+)?player\s+cannot\s+see\s+\[(\d+)\s+(\d+)\]"
    :handler then-handle-player-map-nil}
   {:regex #"cell\s+\[(\d+)\s+(\d+)\]\s+has\s+(\S+)\s+(\S+)"
    :handler then-handle-cell-prop}
   {:regex #"on\s+(?:the\s+)?computer-map\s+cell\s+\[(\d+)\s+(\d+)\]\s+is\s+a\s+(player|computer)\s+city"
    :handler (fn [[_ x y status]]
               {:type :cell-prop :coords [(Integer/parseInt x) (Integer/parseInt y)]
                :property :city-status :expected (keyword status) :target :computer-map})}
   {:regex #"cell\s+\[(\d+)\s+(\d+)\]\s+is\s+a\s+(player|computer)\s+city"
    :handler (fn [[_ x y status]]
               {:type :cell-prop :coords [(Integer/parseInt x) (Integer/parseInt y)]
                :property :city-status :expected (keyword status)})}
   {:regex #"cell\s+\[(\d+)\s+(\d+)\]\s+is\s+(?:a\s+)?(\w+)"
    :handler then-handle-cell-type}
   {:regex #"^waiting-for-input$"
    :handler then-handle-waiting-for-input}
   {:regex #"^not\s+waiting-for-input$"
    :handler then-handle-not-waiting-for-input}
   {:regex #"(?:the\s+)?game\s+is\s+waiting\s+for\s+input"
    :handler then-handle-waiting-for-input}
   {:regex #"(?:the\s+)?game\s+is\s+not\s+waiting\s+for\s+input"
    :handler then-handle-not-waiting-for-input}
   {:regex #"game\s+is\s+paused"
    :handler then-handle-game-paused}
   {:regex #"round\s+is\s+(\d+)"
    :handler then-handle-round}
   {:regex #"destination\s+is\s+\[(\d+)\s+(\d+)\]"
    :handler then-handle-destination}
   {:regex #"production\s+at\s+(\w+)\s+is\s+([\w-]+)\s+with\s+(\d+)\s+rounds?\s+remaining"
    :handler then-handle-production-with-rounds}
   {:regex #"production\s+at\s+(\w+)\s+is\s+not\s+([\w-]+)"
    :handler then-handle-production-not}
   {:regex #"production\s+at\s+(\w+)\s+is\s+([\w-]+)"
    :handler then-handle-production}
   {:regex #"(?:there\s+is\s+)?no\s+production\s+at\s+(\w+)"
    :handler then-handle-no-production}
   {:regex #"no\s+unit\s+at\s+\[(\d+)\s+(\d+)\]"
    :handler then-handle-no-unit-at}
   {:regex #"there\s+are\s+(\d+)\s+computer\s+armies\s+on\s+the\s+map"
    :handler (fn [[_ n]]
               {:type :computer-army-count :expected (Integer/parseInt n)})}])

(def ^:private then-timed-patterns
  [{:regex #"^(\w+)\s+will\s+be\s+at\s+(\S+)$"
    :handler then-handle-will-be-at}
   {:regex #"^(\w+)\s+occupies\s+the\s+(\w+)\s+cell"
    :handler then-handle-occupies-cell}
   {:regex #"^(\w+)\s+remains\s+unmoved"
    :handler then-handle-remains-unmoved}
   {:regex #"^(\w+)\s+has\s+one\s+fighter\s+in\s+its\s+airport"
    :handler then-handle-airport-fighter}
   {:regex #"^(\w+)\s+has\s+one\s+fighter\s+aboard"
    :handler then-handle-fighter-aboard}
   {:regex #"^(\w+)\s+has\s+no\s+fighters"
    :handler then-handle-no-fighters}
   {:regex #"^(\w+)\s+has\s+(\w+)\s+awake\s+fighters?"
    :handler then-handle-awake-fighters}
   {:regex #"there\s+is\s+no\s+(\w+)$"
    :handler then-handle-unit-absent-short}
   {:regex #"there\s+is\s+an?\s+(\w+)\s+at\s+\[(\d+)\s+(\d+)\]"
    :handler then-handle-unit-present-coords}
   {:regex #"there\s+is\s+an?\s+(\w+)\s+at\s+(\S+)"
    :handler then-handle-unit-present-target}
   {:regex #"^(\w+)\s+has\s+no\s+mission$"
    :handler (fn [[_ unit]]
               {:type :unit-prop-absent :unit unit :property :transport-mission})}
   {:regex #"^(\w+)\s+has\s+(\w+)\s+(?:army|armies)$"
    :handler (fn [[_ unit n]]
               (when-let [cnt (parse-count n)]
                 {:type :unit-prop :unit unit :property :army-count :expected cnt}))}
   {:regex #"^(\w+)\s+has\s+(\d+)\s+turns?\s+remaining$"
    :handler (fn [[_ unit n]]
               {:type :unit-prop :unit unit :property :turns-remaining :expected (Integer/parseInt n)})}
   {:regex #"^(\w+)\s+has\s+mission\s+(\w+)$"
    :handler then-handle-unit-has-mission}
   {:regex #"^(\w+)\s+has\s+refueling\s+position\s+near\s+(\S+)$"
    :handler then-handle-refueling-position-near}
   {:regex #"^(\w+)\s+has\s+no\s+([\w-]+)$"
    :handler then-handle-unit-prop-absent}
   {:regex #"^(\w+)\s+has\s+(\w[\w-]*)\s+(.+)$"
    :handler then-handle-unit-has-prop}
   {:regex #"^(\w+)\s+(?:has\s+mode|is)\s+([\w-]+)$"
    :handler then-handle-unit-is-mode}
   {:regex #"^(\w+)\s+does\s+not\s+have\s+(\S+)"
    :handler then-handle-unit-prop-absent}])

;; --- THEN parsing ---

(defn- strip-then-preamble
  "Strip THEN/and prefix and timing prefix, returning
   {:bare-text text :timed-text text :timing-key key-or-nil}"
  [clause]
  (let [clean (str/trim clause)
        stripped (strip-trailing-period clean)
        bare-text (-> stripped
                      (str/replace #"^(?:THEN|and)\s+" "")
                      str/trim)
        timing-match (re-find #"^[Aa]t\s+(?:the\s+)?next\s+(round|step|move)\s+" bare-text)
        timing-word (when timing-match (nth timing-match 1))
        timed-text (if timing-match
                     (str/trim (str/replace-first bare-text (first timing-match) ""))
                     bare-text)
        timing-key (case timing-word
                     "round" :at-next-round
                     ("step" "move") :at-next-step
                     nil)]
    {:bare-text bare-text :timed-text timed-text :timing-key timing-key :clean clean}))

(defn- tag-timing [timing-key result]
  (if timing-key
    (if (map? result)
      (assoc result timing-key true)
      (update result 0 assoc timing-key true))
    result))

(defn- parse-single-then-clause [clause]
  (let [{:keys [bare-text timed-text timing-key clean]} (strip-then-preamble clause)]
    (tag-timing timing-key
      (or (first-matching-pattern then-bare-patterns bare-text)
          (first-matching-pattern then-timed-patterns timed-text)
          {:type :unrecognized :text clean}))))

(defn- split-then-continuations [lines]
  (let [result (atom [])
        current (atom nil)]
    (doseq [line lines]
      (let [trimmed (str/trim line)]
        (cond
          (blank-or-comment? trimmed)
          nil

          (str/starts-with? (str/upper-case trimmed) "THEN ")
          (do
            (when @current (swap! result conj @current))
            (reset! current trimmed))

          (re-matches #"^and\s+.*" trimmed)
          (if @current
            (do (swap! result conj @current)
                (reset! current trimmed))
            (reset! current trimmed))

          ;; Continuation of previous line (e.g., multi-line THEN)
          @current
          (swap! current str " " trimmed)

          :else
          (reset! current trimmed))))
    (when @current (swap! result conj @current))
    @result))

(defn- split-compound-then [clause]
  (let [clean (str/trim clause)
        ;; Split on " and " but be careful not to split inside phrases like "wakes up and asks"
        ;; We split on " and " that follows a complete assertion pattern
        ;; Strategy: split on " and " then check if subparts are valid
        ;; Simple approach: check for known compound patterns
        ]
    ;; Check for "X has one fighter in its airport and there is no fighter on the map"
    (if-let [[_ part1 part2] (re-find #"(.*?\b(?:airport|aboard))\s+and\s+(there\s+is\s+.*)" clean)]
      [part1 part2]
      ;; Check for "X occupies the Y cell and there is no Y"
      (if-let [[_ part1 part2] (re-find #"(.*?cell)\s+and\s+(there\s+is\s+.*)" clean)]
        [part1 part2]
        ;; Check for "s remains unmoved" followed by "and there is no D"
        ;; Already handled by split-then-continuations
        [clean]))))

(defn- extract-then-map-blocks
  "Pre-process THEN lines: extract map blocks after 'THEN player map'.
   Returns {:remaining-lines [...] :map-thens [...]}"
  [lines]
  (let [remaining (atom [])
        map-thens (atom [])
        i (atom 0)]
    (while (< @i (count lines))
      (let [line (nth lines @i)
            trimmed (str/trim line)]
        (cond
          (re-matches #"(?i)^THEN\s+player\s+map\s*\.?\s*$" trimmed)
          (let [_ (swap! i inc)
                rows (atom [])]
            (while (and (< @i (count lines))
                        (map-row? (nth lines @i)))
              (swap! rows conj (str/trim (nth lines @i)))
              (swap! i inc))
            (swap! map-thens conj {:type :player-map-visibility :rows @rows}))

          (re-matches #"(?i)^THEN\s+territory\s+map\s*\.?\s*$" trimmed)
          (let [_ (swap! i inc)
                rows (atom [])]
            (while (and (< @i (count lines))
                        (territory-map-row? (nth lines @i)))
              (swap! rows conj (str/trim (nth lines @i)))
              (swap! i inc))
            (swap! map-thens conj {:type :territory-map :rows @rows}))

          :else
          (do
            (swap! remaining conj line)
            (swap! i inc)))))
    {:remaining-lines @remaining :map-thens @map-thens}))

(defn parse-then
  "Parse THEN lines into IR. Returns {:thens [...]}"
  [lines context]
  (let [{:keys [remaining-lines map-thens]} (extract-then-map-blocks lines)
        clauses (split-then-continuations remaining-lines)
        thens (atom (vec map-thens))]
    (doseq [clause clauses]
      (let [parts (split-compound-then clause)]
        (doseq [part parts]
          (let [parsed (parse-single-then-clause part)]
            (if (vector? parsed)
              (doseq [p parsed]
                (when p (swap! thens conj p)))
              (when parsed (swap! thens conj parsed)))))))
    {:thens @thens}))

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
          (separator-line? trimmed)
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
          (blank-or-comment? trimmed)
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
                   (when (or (contains? player-unit-chars s)
                             (contains? computer-unit-chars s))
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
  (let [{:keys [givens context]} (parse-given given-lines {})
        unit-types (extract-unit-types-from-givens givens)
        wfi (has-waiting-for-input? givens)
        when-ctx {:has-waiting-for-input wfi
                  :unit-types unit-types
                  :units-with-mode (or (:units-with-mode context) #{})}
        {:keys [whens]} (parse-when when-lines when-ctx)
        {:keys [thens]} (parse-then then-lines {})]
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

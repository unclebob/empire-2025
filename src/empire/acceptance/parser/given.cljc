(ns empire.acceptance.parser.given
  (:require [clojure.string :as str]
            [clojure.edn :as edn]
            [empire.acceptance.parser.helpers :as h]))

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
                  (when-let [cnt (h/parse-count n)]
                    {:props {:army-count cnt}}))}
   {:regex #"hits\s+(\d+)"
    :extract-fn (fn [[_ n]] {:props {:hits (Integer/parseInt n)}})}
   {:regex #"fighter-count\s+(\d+)"
    :extract-fn (fn [[_ n]] {:container-props {:fighter-count (Integer/parseInt n)}})}
   {:regex #"awake-fighters\s+(\d+)"
    :extract-fn (fn [[_ n]] {:container-props {:awake-fighters (Integer/parseInt n)}})}
   {:regex #"(\w+)\s+fighters?\b(?!\s+in)"
    :extract-fn (fn [[_ n]]
                  (when-let [cnt (h/parse-count n)]
                    {:container-props {:fighter-count cnt}}))}
   {:regex #"no\s+awake\s+fighters?"
    :extract-fn (fn [_] {:container-props {:awake-fighters 0}})}
   {:regex #"(\w+)\s+awake\s+fighters?"
    :extract-fn (fn [[_ n]]
                  (when (not= n "no")
                    (when-let [cnt (h/parse-count n)]
                      {:container-props {:awake-fighters cnt}})))}
   ;; "has mission <value>" for transport-mission
   {:regex #"(?:with|has)\s+mission\s+(\w+)"
    :extract-fn (fn [[_ v]]
                  {:props {:transport-mission (keyword v)}})}
   ;; "has escort destroyer" - natural language for escort-destroyer-id
   {:regex #"(?:with|has)\s+(?:an?\s+)?escort\s+destroyer"
    :extract-fn (fn [_] {:props {:escort-destroyer-id 1}})}
   ;; "has heading <degrees>" for sailing heading
   {:regex #"(?:with|has)\s+heading\s+(\d+)"
    :extract-fn (fn [[_ n]] {:props {:heading (Integer/parseInt n)}})}
   ;; "has path [...]" or "has sail-path [...]" for EDN vector values
   {:regex #"(?:with|has)\s+((?:sail-)?path)\s+(\[.*\])"
    :extract-fn (fn [[_ k v]]
                  {:props {(keyword k) (edn/read-string v)}})}
   ;; "has <property> [x y]" for coordinate values (e.g., target-city [2 0])
   {:regex #"(?:with|has)\s+([\w][\w-]*)\s+\[(\d+)\s+(\d+)\]"
    :extract-fn (fn [[_ k x y]]
                  {:props {(keyword k) [(Integer/parseInt x) (Integer/parseInt y)]}})}
   ;; Catch-all: "has <hyphenated-property> <value>" for unit properties like country-id, been-to-sea
   {:regex #"(?:with|has)\s+([\w]+-[\w-]+)\s+([^\[\s]+)"
    :extract-fn (fn [[_ k v]]
                  (when-not (#{"army-count" "fighter-count" "awake-fighters"} k)
                    {:props {(keyword k) (or (h/parse-number v)
                                             (case v "true" true "false" false nil)
                                             (keyword v))}}))}])

(defn- parse-unit-props-line [line]
  (let [clean (h/strip-trailing-period (str/trim line))
        clean (h/strip-keyword-prefix clean)]
    (when-let [[_ unit rest-str] (re-matches #"(\w+)\s+(.*)" clean)]
      (when (h/city-or-unit-char? unit)
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
  (let [clean (h/strip-trailing-period (str/trim line))
        clean (h/strip-keyword-prefix clean)]
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
        (when-let [count (h/parse-count n)]
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
                      [(h/resolve-cell-prop k) (or (h/parse-number v)
                                       (h/parse-coords v)
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
   {:regex #"([+\w]+)\s+is\s+visible\s+to\s+computer"
    :handler (fn [[_ ref] _ctx]
               {:directive :visible-to-computer
                :ir {:type :visible-to-computer :ref ref}})}
   {:regex #"(\w+)\s+has\s+city-status\s+(\w+)"
    :handler (fn [[_ ref status] _ctx]
               (when (contains? h/city-chars ref)
                 {:directive :city-prop
                  :ir {:type :city-prop :city ref :prop :city-status :value (keyword status)}}))}
   {:regex #"territory\s+around\s+(\w+)\s+belongs\s+to\s+country\s+(\d+)"
    :handler (fn [[_ ref n] _ctx]
               {:directive :territory-around
                :ir {:type :territory-around :city ref :country-id (Integer/parseInt n)}})}
   {:regex #"(\w+)\s+belongs\s+to\s+country\s+(\d+)"
    :handler (fn [[_ ref n] _ctx]
               (let [country-id (Integer/parseInt n)]
                 (if (contains? h/city-chars ref)
                   {:directive :city-prop
                    :ir {:type :city-prop :city ref :prop :country-id :value country-id}}
                   {:directive :unit-props
                    :ir {:type :unit-props :unit ref :props {:country-id country-id}}})))}
   {:regex #"(\w+)\s+patrols\s+(?:for\s+)?country\s+(\d+)"
    :handler (fn [[_ ref n] _ctx]
               {:directive :unit-props
                :ir {:type :unit-props :unit ref :props {:country-id (Integer/parseInt n)
                                                         :patrol-mode :crawling}}})}])

(defn- parse-given-line [line context]
  (let [clean (str/trim line)
        stripped (h/strip-trailing-period clean)
        given-text (h/strip-keyword-prefix stripped)]
    (or (h/first-matching-pattern given-map-patterns stripped)
        (h/first-matching-pattern-with-context given-directive-patterns given-text context)
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
        (if (h/blank-or-comment? line)
          (swap! i inc)
          (let [parsed (parse-given-line trimmed @context)]
            (case (:directive parsed)
              :map-start
              (let [target (:target parsed)
                    _ (swap! i inc)
                    rows (atom [])]
                (while (and (< @i (count lines))
                            (h/map-row? (nth lines @i)))
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
               :shipyard-state :city-unit :territory-around :visible-to-computer)
              (do (swap! givens conj (:ir parsed))
                  (swap! i inc))

              :unrecognized
              (do (swap! givens conj (:ir parsed))
                  (swap! i inc))

              ;; default
              (swap! i inc))))))
    {:givens @givens :context @context}))

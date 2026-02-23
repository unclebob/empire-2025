(ns empire.acceptance.parser.then
  (:require [clojure.string :as str]
            [empire.acceptance.parser.helpers :as h]))

;; --- THEN parsing: handler functions ---

(defn- then-handle-after-moves [[_ n unit target]]
  {:type :unit-after-moves :unit unit :moves (h/parse-count n) :target target})

(defn- then-handle-after-steps-coords [[_ n unit x y]]
  {:type :unit-after-steps :unit unit :steps (h/parse-count n)
   :coords [(Integer/parseInt x) (Integer/parseInt y)]})

(defn- then-handle-after-steps-target [[_ n unit target]]
  {:type :unit-after-steps :unit unit :steps (h/parse-count n) :target target})

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
  {:type :unit-absent :unit (h/normalize-unit-ref unit)})

(defn- then-handle-no-message [[_ area]]
  {:type :no-message :area (keyword area)})

(defn- then-handle-message-for-unit [[_ area unit key-str]]
  {:type :message-for-unit :area (keyword area) :unit unit
   :config-key (keyword (h/strip-trailing-period key-str))})

(defn- then-handle-message-contains-literal [[_ area text]]
  {:type :message-contains :area (keyword area) :text text})

(defn- then-handle-message-contains-key [[_ area key-str]]
  {:type :message-contains :area (keyword area)
   :config-key (keyword (h/strip-trailing-period key-str))})

(defn- then-handle-message-is-key [[_ area key-str]]
  {:type :message-is :area (keyword area)
   :config-key (keyword (h/strip-trailing-period key-str))})

(defn- then-handle-message-is-format [[_ area key-str args-str]]
  (let [args (mapv #(let [s (str/trim %)]
                      (or (h/parse-number s) s))
                   (str/split args-str #"\s+"))]
    {:type :message-is :area (keyword area) :format {:key (keyword key-str) :args args}}))

(defn- then-handle-bare-message-literal [[_ text]]
  {:type :message-contains :area :attention :text text})

(defn- then-handle-bare-message-key [[_ key-str]]
  {:type :message-contains :area :attention
   :config-key (keyword (h/strip-trailing-period key-str))})

(defn- then-handle-out-of-fuel [_]
  {:type :message-contains :area :attention :config-key :fighter-out-of-fuel})

(defn- then-handle-player-map-not-nil [[_ x y]]
  {:type :player-map-cell-not-nil :coords [(Integer/parseInt x) (Integer/parseInt y)]})

(defn- then-handle-player-map-nil [[_ x y]]
  {:type :player-map-cell-nil :coords [(Integer/parseInt x) (Integer/parseInt y)]})

(defn- then-handle-cell-prop [[_ x y prop val]]
  {:type :cell-prop :coords [(Integer/parseInt x) (Integer/parseInt y)]
   :property (h/resolve-cell-prop prop) :expected (keyword val)})

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
  (when (h/city-or-unit-char? unit)
    {:type :unit-prop :unit unit :property :transport-mission :expected (keyword val)}))

(defn- then-handle-unit-has-prop [[_ unit prop val]]
  (let [val (str/trim val)]
    (when (h/city-or-unit-char? unit)
      {:type :unit-prop :unit unit
       :property (keyword prop)
       :expected (or (h/parse-number val) (h/parse-coords val) (keyword val))})))

(defn- then-handle-unit-is-mode [[_ unit val]]
  (when (h/city-or-unit-char? unit)
    {:type :unit-prop :unit unit :property :mode :expected (keyword val)}))

(defn- then-handle-unit-prop-absent [[_ unit prop]]
  {:type :unit-prop-absent :unit unit :property (keyword prop)})

;; --- THEN parsing: timed-pattern handlers ---

(defn- then-handle-will-be-at [[_ unit target]]
  (if-let [coords (h/parse-coords (str "[" (str/replace target #"[\[\]]" "") "]"))]
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
   :lookup (if (contains? h/city-chars target) :city :unit)})

(defn- then-handle-awake-fighters [[_ target n]]
  {:type :container-prop :target target :property :awake-fighters
   :expected (h/parse-count n)
   :lookup (if (contains? h/city-chars target) :city :unit)})

(defn- then-handle-unit-absent-short [[_ unit]]
  {:type :unit-absent :unit (h/normalize-unit-ref unit)})

(defn- then-handle-refueling-position-near [[_ unit target]]
  {:type :refueling-position-near :unit unit :target target})

(defn- then-handle-unit-present-coords [[_ unit x y]]
  {:type :unit-present :unit unit :coords [(Integer/parseInt x) (Integer/parseInt y)]})

(defn- then-handle-unit-present-target [[_ unit target]]
  {:type :unit-present :unit unit :target target})

(defn- then-handle-shipyard-has-ship [[_ city ship-type hits]]
  {:type :shipyard-has-ship :city city :ship-type (keyword ship-type) :hits (Integer/parseInt hits)})

(defn- then-handle-shipyard-empty [[_ city]]
  {:type :shipyard-empty :city city})

(defn- then-handle-map-is [[_ map-str]]
  {:type :map-is :expected (h/strip-trailing-period map-str)})

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
   {:regex #"^(\w+)\s+has\s+(?:a|an)\s+(\w+)\s+with\s+(\d+)\s+hits?\s+in\s+its\s+shipyard"
    :handler then-handle-shipyard-has-ship}
   {:regex #"^(\w+)\s+has\s+no\s+ships?\s+in\s+its\s+shipyard"
    :handler then-handle-shipyard-empty}
   {:regex #"^(?:the\s+)?map\s+is\s+(\S+)"
    :handler then-handle-map-is}
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
               (when-let [cnt (h/parse-count n)]
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
        stripped (h/strip-trailing-period clean)
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
      (or (h/first-matching-pattern then-bare-patterns bare-text)
          (h/first-matching-pattern then-timed-patterns timed-text)
          {:type :unrecognized :text clean}))))

(defn- split-then-continuations [lines]
  (let [result (atom [])
        current (atom nil)]
    (doseq [line lines]
      (let [trimmed (str/trim line)]
        (cond
          (h/blank-or-comment? trimmed)
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
  (let [clean (str/trim clause)]
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
                        (h/map-row? (nth lines @i)))
              (swap! rows conj (str/trim (nth lines @i)))
              (swap! i inc))
            (swap! map-thens conj {:type :player-map-visibility :rows @rows}))

          (re-matches #"(?i)^THEN\s+territory\s+map\s*\.?\s*$" trimmed)
          (let [_ (swap! i inc)
                rows (atom [])]
            (while (and (< @i (count lines))
                        (h/territory-map-row? (nth lines @i)))
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

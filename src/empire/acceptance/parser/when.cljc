(ns empire.acceptance.parser.when
  (:require [clojure.string :as str]
            [empire.acceptance.parser.helpers :as h]))

;; --- WHEN parsing ---

(defn- determine-key-type [key-str context]
  (let [k (keyword key-str)]
    (cond
      (= key-str "space") {:key :space :input-fn :key-down}
      (h/uppercase-direction? k) {:key k :input-fn :key-down}
      (and (h/lowercase-direction? k) (:has-waiting-for-input context)) {:key k :input-fn :handle-key}
      :else {:key k :input-fn :key-down})))

(defn- determine-combat-type [context]
  (let [unit-types (or (:unit-types context) #{})]
    (if (some h/ship-unit-chars unit-types)
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
  [{:type :computer-rounds :count (h/parse-count n)}])

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
            stripped (h/strip-trailing-period clean)
            when-text (h/strip-keyword-prefix stripped)]
        (when-not (h/blank-or-comment? clean)
          (let [line-ctx (assoc context :when-text when-text :clean-text clean)
                result (or (h/first-matching-pattern-with-context when-patterns when-text line-ctx)
                           [{:type :unrecognized :text clean}])]
            (doseq [ir result]
              (swap! whens conj ir))))))
    {:whens @whens}))

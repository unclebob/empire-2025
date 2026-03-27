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

(defn- when-handle-process-computer-fighter [[_ unit] _ctx]
  [{:type :process-computer-fighter :unit unit}])

(defn- when-handle-computer-rounds [[_ n] _ctx]
  [{:type :computer-rounds :count (h/parse-count n)}])

(defn- when-handle-rounds-complete [[_ n] _ctx]
  [{:type :rounds-complete :count (h/parse-count n)}])

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
   {:regex #"player\s+saves\s+the\s+game"
    :handler (fn [_ _ctx] [{:type :save-game}])}
   {:regex #"player\s+opens\s+the\s+load\s+menu"
    :handler (fn [_ _ctx] [{:type :open-load-menu}])}
   {:regex #"player\s+presses\s+(\S+)"
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
   {:regex #"(?:production\s+for|computer\s+chooses\s+production\s+at)\s+(\w+)(?:\s+is\s+evaluated)?$"
    :handler when-handle-evaluate-production}
   {:regex #"computer\s+transport\s+(\w+)\s+is\s+processed"
    :handler when-handle-process-computer-transport}
   {:regex #"computer\s+fighter\s+(\w+)\s+is\s+processed"
    :handler when-handle-process-computer-fighter}
   {:regex #"computer\s+(destroyer|patrol-boat|submarine|battleship|carrier)\s+(\w+)\s+is\s+processed"
    :handler (fn [[_ ship-type unit] _ctx]
               [{:type :process-computer-ship :ship-type (keyword ship-type) :unit unit}])}
   {:regex #"(\w+)\s+computer\s+rounds?\s+pass"
    :handler when-handle-computer-rounds}
   {:regex #"(\w+)\s+rounds?\s+(?:are|is)\s+complete"
    :handler when-handle-rounds-complete}
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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T22:12:34.606989-05:00", :module-hash "-361712211", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 3, :hash "-112499927"} {:id "defn-/determine-key-type", :kind "defn-", :line 7, :end-line 13, :hash "-876841018"} {:id "defn-/determine-combat-type", :kind "defn-", :line 15, :end-line 19, :hash "-1311274883"} {:id "defn-/when-handle-backtick", :kind "defn-", :line 24, :end-line 27, :hash "-45958683"} {:id "defn-/when-handle-mouse-at-key", :kind "defn-", :line 29, :end-line 32, :hash "-740535273"} {:id "defn-/when-handle-waiting-and-key", :kind "defn-", :line 34, :end-line 37, :hash "1970732768"} {:id "defn-/when-handle-battle", :kind "defn-", :line 39, :end-line 44, :hash "691632944"} {:id "defn-/when-handle-key-and-advance", :kind "defn-", :line 46, :end-line 49, :hash "-1294158161"} {:id "defn-/when-handle-key-press", :kind "defn-", :line 51, :end-line 56, :hash "-1793791518"} {:id "defn-/when-handle-types-keys", :kind "defn-", :line 58, :end-line 60, :hash "1417327614"} {:id "defn-/when-handle-new-round-and-waiting", :kind "defn-", :line 62, :end-line 64, :hash "1152234579"} {:id "defn-/when-handle-new-round", :kind "defn-", :line 66, :end-line 70, :hash "1232872444"} {:id "defn-/when-handle-advance-game-batch", :kind "defn-", :line 72, :end-line 73, :hash "400654373"} {:id "defn-/when-handle-advance-game", :kind "defn-", :line 75, :end-line 76, :hash "1702004536"} {:id "defn-/when-handle-process-player-items", :kind "defn-", :line 78, :end-line 79, :hash "-1655805387"} {:id "defn-/when-handle-visibility-update", :kind "defn-", :line 81, :end-line 82, :hash "-582259326"} {:id "defn-/when-handle-standalone-waiting", :kind "defn-", :line 84, :end-line 85, :hash "-813927020"} {:id "defn-/when-handle-evaluate-production", :kind "defn-", :line 87, :end-line 88, :hash "949770422"} {:id "defn-/when-handle-process-computer-transport", :kind "defn-", :line 90, :end-line 91, :hash "672382407"} {:id "defn-/when-handle-process-computer-fighter", :kind "defn-", :line 93, :end-line 94, :hash "-1135516072"} {:id "defn-/when-handle-computer-rounds", :kind "defn-", :line 96, :end-line 97, :hash "-1401375306"} {:id "defn-/when-handle-rounds-complete", :kind "defn-", :line 99, :end-line 100, :hash "984317594"} {:id "def/when-patterns", :kind "def", :line 104, :end-line 152, :hash "1642303151"} {:id "defn/parse-when", :kind "defn", :line 154, :end-line 168, :hash "-1001977750"}]}
;; clj-mutate-manifest-end

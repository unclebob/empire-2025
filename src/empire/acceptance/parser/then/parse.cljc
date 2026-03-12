(ns empire.acceptance.parser.then.parse
  (:require [clojure.string :as str]
            [empire.acceptance.parser.helpers :as h]
            [empire.acceptance.parser.then.patterns :as patterns]))

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
                (or (h/first-matching-pattern patterns/then-bare-patterns bare-text)
                    (h/first-matching-pattern patterns/then-timed-patterns timed-text)
                    {:type :unrecognized :text clean}))))

(defn- then-line-kind [trimmed]
  (cond
    (h/blank-or-comment? trimmed) :blank
    (str/starts-with? (str/upper-case trimmed) "THEN ") :then
    (boolean (re-matches #"^and\s+.*" trimmed)) :and
    :else :text))

(defn- flush-current-state [{:keys [result current] :as state}]
  (if current
    (assoc state :result (conj result current))
    state))

(defn- split-continuation-step [state line]
  (let [trimmed (str/trim line)
        kind (then-line-kind trimmed)]
    (case kind
      :blank state
      :then (assoc (flush-current-state state) :current trimmed)
      :and (assoc (flush-current-state state) :current trimmed)
      :text (if-let [current (:current state)]
              (assoc state :current (str current " " trimmed))
              (assoc state :current trimmed)))))

(defn- split-then-continuations [lines]
  (let [{:keys [result current]} (reduce split-continuation-step {:result [] :current nil} lines)]
    (if current
      (conj result current)
      result)))

(defn- split-compound-then [clause]
  (let [clean (str/trim clause)]
    (if-let [[_ part1 part2] (re-find #"(.*?\b(?:airport|aboard))\s+and\s+(there\s+is\s+.*)" clean)]
      [part1 part2]
      (if-let [[_ part1 part2] (re-find #"(.*?cell)\s+and\s+(there\s+is\s+.*)" clean)]
        [part1 part2]
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

(defn- append-parsed-result! [thens parsed]
  (if (vector? parsed)
    (doseq [p parsed]
      (when p (swap! thens conj p)))
    (when parsed
      (swap! thens conj parsed))))

(defn- append-parsed-clause! [thens clause]
  (doseq [part (split-compound-then clause)]
    (append-parsed-result! thens (parse-single-then-clause part))))

(defn parse-then
  "Parse THEN lines into IR. Returns {:thens [...]}"
  [lines _context]
  (let [{:keys [remaining-lines map-thens]} (extract-then-map-blocks lines)
        clauses (split-then-continuations remaining-lines)
        thens (atom (vec map-thens))]
    (doseq [clause clauses]
      (append-parsed-clause! thens clause))
    {:thens @thens}))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T11:56:56.750579-05:00", :module-hash "-1359026113", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 4, :hash "237150099"} {:id "defn-/strip-then-preamble", :kind "defn-", :line 6, :end-line 24, :hash "-472023872"} {:id "defn-/tag-timing", :kind "defn-", :line 26, :end-line 31, :hash "308839077"} {:id "defn-/parse-single-then-clause", :kind "defn-", :line 33, :end-line 38, :hash "-419514995"} {:id "defn-/then-line-kind", :kind "defn-", :line 40, :end-line 45, :hash "730669561"} {:id "defn-/flush-current-state", :kind "defn-", :line 47, :end-line 50, :hash "-1006522326"} {:id "defn-/split-continuation-step", :kind "defn-", :line 52, :end-line 61, :hash "-1042312601"} {:id "defn-/split-then-continuations", :kind "defn-", :line 63, :end-line 67, :hash "-53732953"} {:id "defn-/split-compound-then", :kind "defn-", :line 69, :end-line 75, :hash "1389237095"} {:id "defn-/extract-then-map-blocks", :kind "defn-", :line 77, :end-line 110, :hash "-50361550"} {:id "defn-/append-parsed-result!", :kind "defn-", :line 112, :end-line 117, :hash "91126385"} {:id "defn-/append-parsed-clause!", :kind "defn-", :line 119, :end-line 121, :hash "-1697293023"} {:id "defn/parse-then", :kind "defn", :line 123, :end-line 131, :hash "-675406355"}]}
;; clj-mutate-manifest-end

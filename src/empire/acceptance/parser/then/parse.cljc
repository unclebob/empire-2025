;; mutation-tested: 2026-02-28
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

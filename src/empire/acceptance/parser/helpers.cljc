(ns empire.acceptance.parser.helpers
  (:require [clojure.string :as str]))

;; --- Helpers ---

(defn strip-trailing-period [s]
  (if (str/ends-with? s ".")
    (subs s 0 (dec (count s)))
    s))

(defn strip-keyword-prefix [line]
  (-> line
      (str/replace #"^(?:GIVEN|WHEN|THEN)\s+" "")
      str/trim))

(defn blank-or-comment? [line]
  (or (str/blank? line)
      (str/starts-with? (str/trim line) ";")))

(defn separator-line? [line]
  (re-matches #"\s*;=+\s*" line))

(defn map-row? [line]
  (let [trimmed (str/trim line)]
    (and (not (str/blank? trimmed))
         (not (str/starts-with? trimmed ";"))
         (not (re-matches #"(?i)^(GIVEN|WHEN|THEN)\b.*" trimmed))
         (not (re-matches #"^[A-Za-z].*\s+(is|has|are|with)\b.*" trimmed))
         (re-matches #"^[~#.=% +XOAFTDCSBPVatdfcsbpv\-]+$" trimmed))))

(defn territory-map-row? [line]
  (let [trimmed (str/trim line)]
    (and (not (str/blank? trimmed))
         (re-matches #"^[0-9~.]+$" trimmed))))

(def direction-keys #{"q" "w" "e" "a" "d" "z" "x" "c"})

(defn lowercase-direction? [k]
  (contains? direction-keys (name k)))

(defn uppercase-direction? [k]
  (let [n (name k)]
    (and (= 1 (count n))
         (contains? direction-keys (str/lower-case n))
         (Character/isUpperCase ^char (first n)))))

(defn parse-coords [s]
  (when-let [[_ x y] (re-find #"\[(\d+)\s+(\d+)\]" s)]
    [(Integer/parseInt x) (Integer/parseInt y)]))

(defn parse-number [s]
  (try (Integer/parseInt s) (catch Exception _ nil)))

(def unit-name->char
  {"army" "A" "fighter" "F" "transport" "T" "destroyer" "D"
   "carrier" "C" "submarine" "S" "battleship" "B" "patrol-boat" "P"
   "satellite" "V"})

(defn normalize-unit-ref [s]
  (get unit-name->char (str/lower-case s) s))

(def player-unit-chars #{"A" "F" "T" "D" "C" "S" "B" "P" "V"})
(def computer-unit-chars #{"a" "f" "t" "d" "c" "s" "b" "p" "v"})
(def city-chars #{"O" "X" "+"})
(def ship-unit-chars #{"T" "D" "C" "S" "B" "P" "t" "d" "c" "s" "b" "p"})

(defn unit-char? [s]
  (or (contains? player-unit-chars s)
      (contains? computer-unit-chars s)
      (re-matches #"[A-Za-z]\d+" s)))

(defn city-or-unit-char? [s]
  (or (unit-char? s) (contains? city-chars s)))

(def word->number
  {"no" 0 "one" 1 "two" 2 "three" 3 "four" 4 "five" 5
   "six" 6 "seven" 7 "eight" 8 "nine" 9 "ten" 10})

(defn parse-count [s]
  (or (get word->number (str/lower-case s))
      (parse-number s)))

(def cell-prop-aliases
  {"spawn-orders" :marching-orders
   "flight-orders" :flight-path})

(defn resolve-cell-prop [k]
  (or (get cell-prop-aliases k) (keyword k)))

;; --- Pattern table dispatch ---

(defn first-matching-pattern
  "Scan patterns for the first whose :regex matches text.
   Returns (handler match) or nil."
  [patterns text]
  (loop [entries patterns]
    (when-let [{:keys [regex handler]} (first entries)]
      (if-let [match (re-find regex text)]
        (handler match)
        (recur (rest entries))))))

(defn first-matching-pattern-with-context
  "Like first-matching-pattern, but passes (match, context) to handler."
  [patterns text context]
  (loop [entries patterns]
    (when-let [{:keys [regex handler]} (first entries)]
      (if-let [match (re-find regex text)]
        (handler match context)
        (recur (rest entries))))))

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
         (re-matches #"^[~#.=% +XOAFTDCSBPVJatdfcsbpvj\-]+$" trimmed))))

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

(def player-unit-chars #{"A" "F" "T" "D" "C" "S" "B" "P" "V" "J"})
(def computer-unit-chars #{"a" "f" "t" "d" "c" "s" "b" "p" "v" "j"})
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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T22:05:42.265837-05:00", :module-hash "1612032256", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 2, :hash "-701480839"} {:id "defn/strip-trailing-period", :kind "defn", :line 6, :end-line 9, :hash "-97293752"} {:id "defn/strip-keyword-prefix", :kind "defn", :line 11, :end-line 14, :hash "1015078940"} {:id "defn/blank-or-comment?", :kind "defn", :line 16, :end-line 18, :hash "637332503"} {:id "defn/separator-line?", :kind "defn", :line 20, :end-line 21, :hash "-1734281103"} {:id "defn/map-row?", :kind "defn", :line 23, :end-line 29, :hash "458792631"} {:id "defn/territory-map-row?", :kind "defn", :line 31, :end-line 34, :hash "1625014475"} {:id "def/direction-keys", :kind "def", :line 36, :end-line 36, :hash "-1714180391"} {:id "defn/lowercase-direction?", :kind "defn", :line 38, :end-line 39, :hash "-536966305"} {:id "defn/uppercase-direction?", :kind "defn", :line 41, :end-line 45, :hash "915554226"} {:id "defn/parse-coords", :kind "defn", :line 47, :end-line 49, :hash "-2121680923"} {:id "defn/parse-number", :kind "defn", :line 51, :end-line 52, :hash "145624889"} {:id "def/unit-name->char", :kind "def", :line 54, :end-line 57, :hash "-1142197524"} {:id "defn/normalize-unit-ref", :kind "defn", :line 59, :end-line 60, :hash "-1127803195"} {:id "def/player-unit-chars", :kind "def", :line 62, :end-line 62, :hash "-1802762879"} {:id "def/computer-unit-chars", :kind "def", :line 63, :end-line 63, :hash "-1899078062"} {:id "def/city-chars", :kind "def", :line 64, :end-line 64, :hash "-928646798"} {:id "def/ship-unit-chars", :kind "def", :line 65, :end-line 65, :hash "-1759258231"} {:id "defn/unit-char?", :kind "defn", :line 67, :end-line 70, :hash "-100203318"} {:id "defn/city-or-unit-char?", :kind "defn", :line 72, :end-line 73, :hash "1748609697"} {:id "def/word->number", :kind "def", :line 75, :end-line 77, :hash "2031742282"} {:id "defn/parse-count", :kind "defn", :line 79, :end-line 81, :hash "-661326555"} {:id "def/cell-prop-aliases", :kind "def", :line 83, :end-line 85, :hash "183993901"} {:id "defn/resolve-cell-prop", :kind "defn", :line 87, :end-line 88, :hash "-727056438"} {:id "defn/first-matching-pattern", :kind "defn", :line 92, :end-line 100, :hash "-278169590"} {:id "defn/first-matching-pattern-with-context", :kind "defn", :line 102, :end-line 109, :hash "873710303"}]}
;; clj-mutate-manifest-end

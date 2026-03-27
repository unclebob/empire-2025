(ns empire.ui.util.core
  (:require [clojure.string :as str]
            [empire.state.api :as sa]
            [empire.config.core :as config]))

(defn screen->cell
  "Converts screen pixel coordinates to map cell coordinates [row col].
   Note: Uses legacy formula where width is divided by rows and height by cols."
  [pixel-x pixel-y map-pixel-width map-pixel-height map-rows map-cols]
  (let [cell-w (/ map-pixel-width map-rows)
        cell-h (/ map-pixel-height map-cols)]
    [(int (Math/floor (/ pixel-x cell-w)))
     (int (Math/floor (/ pixel-y cell-h)))]))

(defn compute-screen-dimensions
  "Computes pixel rendering dimensions from known map-size and fixed cell-size.
   Returns a map with :map-screen-dimensions and :text-area-dimensions."
  [cols rows cell-w cell-h]
  (let [map-display-w (* cols cell-w)
        map-display-h (* rows cell-h)
        text-h (* config/text-area-rows cell-h)
        text-x 0
        text-y (+ map-display-h config/text-area-gap)
        text-w map-display-w]
    {:map-screen-dimensions [map-display-w map-display-h]
     :text-area-dimensions [text-x text-y text-w text-h]}))

(defn calculate-screen-dimensions
  "Sets pixel rendering dimensions from known map-size and fixed cell-size."
  []
  (let [[cols rows] (sa/read-state :map-size)
        [cell-w cell-h] config/cell-size
        dims (compute-screen-dimensions cols rows cell-w cell-h)]
    (sa/write-state! :map-screen-dimensions (:map-screen-dimensions dims))
    (sa/write-state! :text-area-dimensions (:text-area-dimensions dims))))

(defn help-requested?
  [args]
  (boolean (some #{"--help" "-h"} args)))

(defn headless-requested?
  [args]
  (boolean (some #(.startsWith ^String % "--headless=") args)))

(defn log-requested?
  [args]
  (boolean (some #{"--log"} args)))

(defn debug-dump-requested?
  [args]
  (boolean (some #{"--debug-dump"} args)))

(def ^:private limit-code->unit-type
  {\a :army
   \b :battleship
   \c :carrier
   \d :destroyer
   \f :fighter
   \p :patrol-boat
   \s :submarine
   \t :transport
   \v :satellite})

(defn- parse-limit-entry
  [entry]
  (let [[code-text n-text] (str/split entry #":" 2)
        code (some-> code-text str/lower-case first)
        unit-type (get limit-code->unit-type code)]
    (when-not (and unit-type n-text)
      (throw (ex-info "Invalid --limits entry"
                      {:entry entry})))
    [unit-type (Long/parseLong n-text)]))

(defn parse-production-limits
  [arg]
  (if-let [limits-text (some-> arg not-empty)]
    (into {}
          (map parse-limit-entry)
          (str/split limits-text #","))
    {}))

(defn startup-timestamp
  []
  (let [now (java.time.LocalDateTime/now)
        fmt (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd-HHmmss")]
    (.format now fmt)))

(defn unit-log-filename
  []
  (str "empire-units" (startup-timestamp) ".log"))

(defn usage-text
  []
  (str "Usage: clj -M:run [options] [cols rows]\n"
       "\n"
       "Options:\n"
       "  --help, -h        Print this help and exit.\n"
       "  --log             Append every computer unit once per round\n"
       "                    to a timestamped empire-units log file.\n"
       "  --debug-dump      Write a full-map debug dump when the game\n"
       "                    process exits for any reason.\n"
       "  --seed=N          Use N as the random seed.\n"
       "  --limits=SPEC     Cap computer production by unit code, e.g.\n"
       "                    t:8,p:4. When a cap is hit, produce army.\n"
       "  --headless=N      Run headlessly for up to N rounds with\n"
       "                    handicap N. Exits early on game over and\n"
       "                    prints a progress line every 20 rounds.\n"
       "  --handicap=N      Let the computer play N rounds before the\n"
       "                    player gets the first turn. Default: 50.\n"
       "\n"
       "Arguments:\n"
       "  cols rows         Optional map size. Default: 100 60.\n"))

(defn- option? [^String arg]
  (or (.startsWith arg "--seed=")
      (.startsWith arg "--limits=")
      (= "--log" arg)
      (= "--debug-dump" arg)
      (.startsWith arg "--headless=")
      (.startsWith arg "--handicap=")))

(defn- extract-long [^String prefix args]
  (some #(when (.startsWith ^String % prefix)
           (Long/parseLong (subs % (count prefix)))) args))

(defn- parse-options [args]
  (let [seed (extract-long "--seed=" args)
        headless-rounds (extract-long "--headless=" args)
        handicap (or headless-rounds (extract-long "--handicap=" args) 50)
        non-options (remove option? args)
        [cols rows] (if (>= (count non-options) 2)
                      [(Integer/parseInt (first non-options))
                       (Integer/parseInt (second non-options))]
                      config/default-map-size)]
    {:cols cols :rows rows :seed seed
     :production-limits (or (some #(when (.startsWith ^String % "--limits=")
                                     (parse-production-limits (subs % 9))) args) {})
     :log-enabled (log-requested? args)
     :debug-dump-enabled (debug-dump-requested? args)
     :headless-rounds headless-rounds
     :handicap handicap}))

(defn- compute-window-dimensions [cols rows]
  (let [[cell-w cell-h] config/cell-size
        text-area-h (* config/text-area-rows cell-h)]
    {:window-w (* cols cell-w)
     :window-h (+ (* rows cell-h) text-area-h config/text-area-gap)}))

(defn- check-screen-bounds! [cols rows window-w window-h screen-w screen-h]
  (when (and screen-w screen-h
             (or (> window-w screen-w) (> window-h screen-h)))
    (let [[cell-w cell-h] config/cell-size
          text-area-h (* config/text-area-rows cell-h)]
      (throw (ex-info "Map exceeds monitor bounds"
                      {:cols cols :rows rows :screen-w screen-w :screen-h screen-h
                       :max-cols (quot screen-w cell-w)
                       :max-rows (quot (- screen-h text-area-h config/text-area-gap) cell-h)})))))

(defn parse-args
  "Parses command-line args into a map of {:cols :rows :seed :headless-rounds :handicap :window-w :window-h :production-limits}.
   Throws ex-info if map exceeds screen bounds when screen dimensions are provided."
  [args screen-w screen-h]
  (let [{:keys [cols rows] :as opts} (parse-options args)
        {:keys [window-w window-h]} (compute-window-dimensions cols rows)]
    (check-screen-bounds! cols rows window-w window-h screen-w screen-h)
    (merge opts {:window-w window-w :window-h window-h})))

(defn key-released [_ _]
  (sa/write-state! :last-key nil))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T02:45:56.41221-05:00", :module-hash "1824091958", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 4, :hash "-438958262"} {:id "defn/screen->cell", :kind "defn", :line 6, :end-line 13, :hash "2032235543"} {:id "defn/compute-screen-dimensions", :kind "defn", :line 15, :end-line 26, :hash "1230315301"} {:id "defn/calculate-screen-dimensions", :kind "defn", :line 28, :end-line 35, :hash "-829060484"} {:id "defn/help-requested?", :kind "defn", :line 37, :end-line 39, :hash "437685303"} {:id "defn/headless-requested?", :kind "defn", :line 41, :end-line 43, :hash "1619027532"} {:id "defn/log-requested?", :kind "defn", :line 45, :end-line 47, :hash "1331327177"} {:id "defn/debug-dump-requested?", :kind "defn", :line 49, :end-line 51, :hash "-392896688"} {:id "def/limit-code->unit-type", :kind "def", :line 53, :end-line 62, :hash "122874635"} {:id "defn-/parse-limit-entry", :kind "defn-", :line 64, :end-line 72, :hash "-1496807893"} {:id "defn/parse-production-limits", :kind "defn", :line 74, :end-line 80, :hash "-2060254564"} {:id "defn/startup-timestamp", :kind "defn", :line 82, :end-line 86, :hash "128677367"} {:id "defn/unit-log-filename", :kind "defn", :line 88, :end-line 90, :hash "761914187"} {:id "defn/usage-text", :kind "defn", :line 92, :end-line 112, :hash "-2046205397"} {:id "defn-/option?", :kind "defn-", :line 114, :end-line 120, :hash "-376527735"} {:id "defn-/extract-long", :kind "defn-", :line 122, :end-line 124, :hash "1655898196"} {:id "defn-/parse-options", :kind "defn-", :line 126, :end-line 141, :hash "41651041"} {:id "defn-/compute-window-dimensions", :kind "defn-", :line 143, :end-line 147, :hash "1537539270"} {:id "defn-/check-screen-bounds!", :kind "defn-", :line 149, :end-line 157, :hash "1020723339"} {:id "defn/parse-args", :kind "defn", :line 159, :end-line 166, :hash "-242057136"} {:id "defn/key-released", :kind "defn", :line 168, :end-line 169, :hash "-2012139141"}]}
;; clj-mutate-manifest-end

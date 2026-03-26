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
       "  --monitor-threshold=N\n"
       "                    Profile rounds that take longer than N ms.\n"
       "                    Monitors 20 rounds after first trigger,\n"
       "                    then prints a summary report.\n"
       "\n"
       "Arguments:\n"
       "  cols rows         Optional map size. Default: 100 60.\n"))

(defn parse-args
  "Parses command-line args into a map of {:cols :rows :seed :headless-rounds :handicap :window-w :window-h :production-limits}.
   Throws ex-info if map exceeds screen bounds when screen dimensions are provided."
  [args screen-w screen-h]
  (let [seed (some #(when (.startsWith ^String % "--seed=")
                      (Long/parseLong (subs % 7))) args)
        production-limits (some #(when (.startsWith ^String % "--limits=")
                                   (parse-production-limits (subs % 9))) args)
        log-enabled (log-requested? args)
        debug-dump-enabled (debug-dump-requested? args)
        monitor-threshold (some #(when (.startsWith ^String % "--monitor-threshold=")
                                  (Long/parseLong (subs % 20))) args)
        headless-rounds (some #(when (.startsWith ^String % "--headless=")
                                 (Long/parseLong (subs % 11))) args)
        handicap (or headless-rounds
                     (some #(when (.startsWith ^String % "--handicap=")
                              (Long/parseLong (subs % 11))) args)
                     50)
        non-options (remove #(or (.startsWith ^String % "--seed=")
                                 (.startsWith ^String % "--limits=")
                                 (= "--log" %)
                                 (= "--debug-dump" %)
                                 (.startsWith ^String % "--headless=")
                                 (.startsWith ^String % "--handicap=")
                                 (.startsWith ^String % "--monitor-threshold="))
                            args)
        [cols rows] (if (>= (count non-options) 2)
                      [(Integer/parseInt (first non-options))
                       (Integer/parseInt (second non-options))]
                      config/default-map-size)
        [cell-w cell-h] config/cell-size
        text-area-h (* config/text-area-rows cell-h)
        window-w (* cols cell-w)
        window-h (+ (* rows cell-h) text-area-h config/text-area-gap)
        max-cols (when screen-w (quot screen-w cell-w))
        max-rows (when screen-h (quot (- screen-h text-area-h config/text-area-gap) cell-h))]
    (when (and screen-w
               screen-h
               (or (> window-w screen-w) (> window-h screen-h)))
      (throw (ex-info "Map exceeds monitor bounds"
                      {:cols cols :rows rows :screen-w screen-w :screen-h screen-h
                       :max-cols max-cols :max-rows max-rows})))
    {:cols cols
     :rows rows
     :seed seed
     :production-limits (or production-limits {})
     :log-enabled log-enabled
     :debug-dump-enabled debug-dump-enabled
     :headless-rounds headless-rounds
     :handicap handicap
     :monitor-threshold monitor-threshold
     :window-w window-w
     :window-h window-h}))

(defn key-released [_ _]
  (sa/write-state! :last-key nil))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-16T15:30:24.18978-05:00", :module-hash "-304662784", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 3, :hash "1156805815"} {:id "defn/screen->cell", :kind "defn", :line 5, :end-line 12, :hash "2032235543"} {:id "defn/compute-screen-dimensions", :kind "defn", :line 14, :end-line 25, :hash "1230315301"} {:id "defn/calculate-screen-dimensions", :kind "defn", :line 27, :end-line 34, :hash "-829060484"} {:id "defn/help-requested?", :kind "defn", :line 36, :end-line 38, :hash "437685303"} {:id "defn/usage-text", :kind "defn", :line 40, :end-line 52, :hash "-197511163"} {:id "defn/parse-args", :kind "defn", :line 54, :end-line 88, :hash "-934643690"} {:id "defn/key-released", :kind "defn", :line 90, :end-line 91, :hash "-2012139141"}]}
;; clj-mutate-manifest-end

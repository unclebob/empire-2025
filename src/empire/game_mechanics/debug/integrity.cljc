(ns empire.game-mechanics.debug.integrity
  "Round-level world integrity validation and error logging."
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [empire.state.api :as sa])
  #?(:clj (:require [clojure.pprint :as pprint]))
  #?(:clj (:import [java.time LocalDateTime]
                   [java.time.format DateTimeFormatter])))

(s/def ::type keyword?)
(s/def ::owner keyword?)
(s/def ::contents
  (s/nilable
   (s/and map?
          (s/keys :req-un [::type ::owner]))))
(s/def ::cell
  (s/and map?
         (s/keys :req-un [::type]
                 :opt-un [::contents])))

(defn invalid-cell-report
  [pos cell]
  (when-not (s/valid? ::cell cell)
    {:pos pos
     :cell cell
     :explain-data (s/explain-data ::cell cell)}))

(defn invalid-cells
  [world]
  (vec
   (for [x (range (count world))
         y (range (count (first world)))
         :let [cell (get-in world [x y])
               report (invalid-cell-report [x y] cell)]
         :when report]
     report)))

(defn- format-action-entry
  [{:keys [timestamp action]}]
  (str "  " timestamp " " (pr-str action)))

(defn- format-computer-event-entry
  [{:keys [event pos] :as entry}]
  (let [extras (dissoc entry :round :event :pos)
        extra-str (when (seq extras) (str " " (pr-str extras)))]
    (str "  " (name event) " " pos extra-str)))

(defn- format-movement-entry
  [{:keys [unit-type from to mode event reason]}]
  (str "  " (name unit-type) " " from "->" to
       " " (name mode)
       (when (not= event :move) (str " " (name event)))
       (when reason (str " (" (name reason) ")"))))

(defn- format-invalid-cell
  [{:keys [pos cell explain-data]}]
  (str "Position: " pos "\n"
       "Cell: " (pr-str cell) "\n"
       #?(:clj
          (str "Explain:\n"
               (with-out-str (pprint/pprint explain-data)))
          :cljs
          (str "Explain: " (pr-str explain-data) "\n"))))

(defn format-integrity-report
  [invalids]
  (let [round (sa/read-state :round-number)
        actions (take-last 50 (or (sa/read-state :action-log) []))
        player-moves (filter #(= round (:round %)) (or (sa/read-state :player-movement-log) []))
        computer-events (filter #(= round (:round %)) (or (sa/read-state :computer-event-log) []))]
    (str "=== Empire Integrity Error ===\n"
         "Round: " round "\n"
         "Timestamp: " (System/currentTimeMillis) "\n"
         "Invalid cell count: " (count invalids) "\n\n"
         "=== Invalid Cells ===\n"
         (str/join "\n" (map format-invalid-cell invalids))
         "\n=== Actions (last 50) ===\n"
         (if (seq actions)
           (str (str/join "\n" (map format-action-entry actions)) "\n")
           "  (none)\n")
         "\n=== Computer Events (this round) ===\n"
         (if (seq computer-events)
           (str (str/join "\n" (map format-computer-event-entry computer-events)) "\n")
           "  (none)\n")
         "\n=== Player Movements (this round) ===\n"
         (if (seq player-moves)
           (str (str/join "\n" (map format-movement-entry player-moves)) "\n")
           "  (none)\n"))))

(defn generate-error-filename
  []
  #?(:clj
     (let [now (LocalDateTime/now)
           formatter (DateTimeFormatter/ofPattern "yyyy-MM-dd-HHmmssSSS")]
       (str "error-" (.format now formatter) ".log"))
     :cljs
     "error.log"))

(defn- timestamp-with-millis
  []
  #?(:clj
     (let [now (LocalDateTime/now)
           formatter (DateTimeFormatter/ofPattern "yyyy-MM-dd-HHmmssSSS")]
       (.format now formatter))
     :cljs
     "cljs"))

(defn generate-prefixed-error-filename
  [prefix]
  #?(:clj
     (str prefix (timestamp-with-millis) ".log")
     :cljs
     (str prefix "cljs.log")))

(defn- format-stacktrace-report
  [context throwable]
  #?(:clj
     (let [round (sa/read-state :round-number)
           actions (take-last 50 (or (sa/read-state :action-log) []))
           player-moves (filter #(= round (:round %)) (or (sa/read-state :player-movement-log) []))
           computer-events (filter #(= round (:round %)) (or (sa/read-state :computer-event-log) []))]
       (str "=== Empire Error ===\n"
            "Round: " round "\n"
            "Timestamp: " (System/currentTimeMillis) "\n\n"
            "=== Context ===\n"
            (with-out-str (pprint/pprint context))
            "\n=== Stack Trace ===\n"
            (with-out-str (.printStackTrace throwable (java.io.PrintWriter. *out*)))
            "\n=== Actions (last 50) ===\n"
            (if (seq actions)
              (str (str/join "\n" (map format-action-entry actions)) "\n")
              "  (none)\n")
            "\n=== Computer Events (this round) ===\n"
            (if (seq computer-events)
              (str (str/join "\n" (map format-computer-event-entry computer-events)) "\n")
              "  (none)\n")
            "\n=== Player Movements (this round) ===\n"
            (if (seq player-moves)
              (str (str/join "\n" (map format-movement-entry player-moves)) "\n")
              "  (none)\n")))
     :cljs
     (str "=== Empire Error ===\n"
          "Context: " (pr-str context) "\n"
          "Throwable: " (pr-str throwable) "\n")))

(defn write-stacktrace-error-log!
  [prefix context throwable]
  #?(:clj
     (let [filename (generate-prefixed-error-filename prefix)]
       (spit filename (format-stacktrace-report context throwable))
       (println (str prefix " was written to " filename))
       filename)
     :cljs
     nil))

(defn write-integrity-error-log!
  [invalids]
  #?(:clj
     (let [filename (generate-error-filename)]
       (spit filename (format-integrity-report invalids))
       filename)
     :cljs
     nil))

(defn- clear-ghost-contents!
  "Clears :contents maps missing :type from all three maps."
  [invalids]
  (doseq [{:keys [pos cell]} invalids]
    (when (and (:contents cell) (not (:type (:contents cell))))
      (sa/update-world! assoc-in (conj pos :contents) nil)
      (sa/update-state! :player-map assoc-in (conj pos :contents) nil)
      (sa/update-state! :computer-map assoc-in (conj pos :contents) nil))))

(defn check-world-integrity!
  []
  (when (sa/read-state :integrity-check-enabled)
    (let [world (sa/current-world)
          invalids (when (seq world) (invalid-cells world))]
      (when (seq invalids)
        (clear-ghost-contents! invalids)
        (let [filename (write-integrity-error-log! invalids)]
          (binding [*out* *err*]
            (println "World integrity violation; wrote" filename))
          filename)))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T00:58:40.482256-05:00", :module-hash "-1364243090", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 8, :hash "-1364907540"} {:id "form/1/s/def", :kind "s/def", :line 10, :end-line 10, :hash "1499278192"} {:id "form/2/s/def", :kind "s/def", :line 11, :end-line 11, :hash "-1049487860"} {:id "form/3/s/def", :kind "s/def", :line 12, :end-line 15, :hash "1212647453"} {:id "form/4/s/def", :kind "s/def", :line 16, :end-line 19, :hash "1806665889"} {:id "defn/invalid-cell-report", :kind "defn", :line 21, :end-line 26, :hash "1963598714"} {:id "defn/invalid-cells", :kind "defn", :line 28, :end-line 36, :hash "-1412414348"} {:id "defn-/format-action-entry", :kind "defn-", :line 38, :end-line 40, :hash "-708401065"} {:id "defn-/format-computer-event-entry", :kind "defn-", :line 42, :end-line 46, :hash "-1778215681"} {:id "defn-/format-movement-entry", :kind "defn-", :line 48, :end-line 53, :hash "-1948636451"} {:id "defn-/format-invalid-cell", :kind "defn-", :line 55, :end-line 63, :hash "-1759585546"} {:id "defn/format-integrity-report", :kind "defn", :line 65, :end-line 88, :hash "-1391720162"} {:id "defn/generate-error-filename", :kind "defn", :line 90, :end-line 97, :hash "-1052894082"} {:id "defn-/timestamp-with-millis", :kind "defn-", :line 99, :end-line 106, :hash "1045413495"} {:id "defn/generate-prefixed-error-filename", :kind "defn", :line 108, :end-line 113, :hash "-1676729692"} {:id "defn-/format-stacktrace-report", :kind "defn-", :line 115, :end-line 144, :hash "-2053521580"} {:id "defn/write-stacktrace-error-log!", :kind "defn", :line 146, :end-line 154, :hash "1335633568"} {:id "defn/write-integrity-error-log!", :kind "defn", :line 156, :end-line 163, :hash "366317464"} {:id "defn/check-world-integrity!", :kind "defn", :line 165, :end-line 174, :hash "857963789"}]}
;; clj-mutate-manifest-end

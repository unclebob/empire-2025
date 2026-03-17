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

(defn check-world-integrity!
  []
  (when (sa/read-state :integrity-check-enabled)
    (let [world (sa/current-world)
          invalids (when (seq world) (invalid-cells world))]
      (when (seq invalids)
        (let [filename (write-integrity-error-log! invalids)]
          (binding [*out* *err*]
            (println "World integrity violation; wrote" filename))
          filename)))))

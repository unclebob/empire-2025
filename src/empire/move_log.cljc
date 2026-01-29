(ns empire.move-log
  "Move logging utilities for debugging unit movements.
   Creates a timestamped log file at game start and logs all unit moves."
  #?(:clj (:import [java.time LocalDateTime]
                   [java.time.format DateTimeFormatter]
                   [java.io FileWriter])))

(def ^:private log-file-path (atom nil))

(defn generate-log-filename
  "Generate a timestamped filename for the move log.
   Format: move-log-YYYY-MM-DD-HHMMSS.txt"
  []
  #?(:clj
     (let [now (LocalDateTime/now)
           formatter (DateTimeFormatter/ofPattern "yyyy-MM-dd-HHmmss")]
       (str "move-log-" (.format now formatter) ".txt"))
     :cljs
     (let [now (js/Date.)
           pad (fn [n] (if (< n 10) (str "0" n) (str n)))
           year (.getFullYear now)
           month (pad (inc (.getMonth now)))
           day (pad (.getDate now))
           hour (pad (.getHours now))
           min (pad (.getMinutes now))
           sec (pad (.getSeconds now))]
       (str "move-log-" year "-" month "-" day "-" hour min sec ".txt"))))

(defn init-log!
  "Initialize the move log file at game start.
   Creates a new timestamped log file and stores the path."
  []
  #?(:clj
     (let [filename (generate-log-filename)]
       (reset! log-file-path filename)
       (spit filename (str "=== Empire Move Log ===\n"
                           "Started: " (LocalDateTime/now) "\n\n"))
       (println "Move log initialized:" filename)
       filename)
     :cljs
     (let [filename (generate-log-filename)]
       (reset! log-file-path filename)
       filename)))

(defn log-move!
  "Log a unit move to the log file.
   Takes from-coords, to-coords, unit-type, owner, and optional context."
  ([from-coords to-coords unit-type owner]
   (log-move! from-coords to-coords unit-type owner nil))
  ([from-coords to-coords unit-type owner context]
   #?(:clj
      (when-let [path @log-file-path]
        (let [timestamp (System/currentTimeMillis)
              [fr fc] from-coords
              [tr tc] to-coords
              ctx-str (if context (str " " context) "")
              line (str timestamp " " (name owner) " " (name unit-type)
                        " [" fr "," fc "] -> [" tr "," tc "]" ctx-str "\n")]
          (spit path line :append true)))
      :cljs nil)))

(defn log-event!
  "Log a general event to the log file."
  [event-type & args]
  #?(:clj
     (when-let [path @log-file-path]
       (let [timestamp (System/currentTimeMillis)
             args-str (clojure.string/join " " (map pr-str args))
             line (str timestamp " EVENT " (name event-type) " " args-str "\n")]
         (spit path line :append true)))
     :cljs nil))

(defn log-round!
  "Log the start of a new round."
  [round-number]
  #?(:clj
     (when-let [path @log-file-path]
       (let [line (str "\n=== Round " round-number " ===\n")]
         (spit path line :append true)))
     :cljs nil))

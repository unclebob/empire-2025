(ns empire.game.save-load.persistence
  (:require [clojure.edn :as edn]
            [clojure.string :as string]
            [empire.config.domain.core.messages :as messages]
            [empire.game.production-status :as production-status]
            [empire.state.api :as sa]))

(def saveable-atoms
  "Stable keys that should be persisted."
  {:game-map true
   :player-map true
   :computer-map true
   :production true
   :destination true
   :round-number true
   :cells-needing-attention true
   :player-items true
   :computer-items true
   :waiting-for-input true
   :paused true
   :computer-turn true
   :next-computer-unit-id true
   :next-transport-id true
   :next-country-id true
   :next-unload-event-id true
   :next-destroyer-id true
   :next-carrier-id true
   :next-escort-id true
   :claimed-objectives true
   :claimed-transport-targets true
   :fighter-leg-records true
   :coast-walkers-produced true
   :opening-satellite-produced? true
   :major-invasion-state true
   :transport-fully-loaded? true
   :early-patrol-boat-produced? true
   :early-satellite-produced? true
   :distant-city-pairs true
   :lake-max-cells true
   :known-lake-cells true})

(defn- timestamp []
  (let [now (java.time.LocalDateTime/now)
        fmt (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd-HHmmss")]
    (.format now fmt)))

(defn default-save-basename
  []
  (str "save-" (timestamp)))

(defn normalize-save-filename
  [input-name]
  (let [trimmed (some-> input-name string/trim)
        base-name (if (string/blank? trimmed)
                    (default-save-basename)
                    trimmed)]
    (if (string/ends-with? base-name ".edn")
      base-name
      (str base-name ".edn"))))

(defn- read-save-key
  [k]
  (case k
    :game-map (sa/current-world)
    (sa/read-state k)))

(defn- write-save-key!
  [k value]
  (case k
    :game-map (sa/update-world! (constantly value))
    (sa/write-state! k value)))

(defn list-save-files
  ([] (list-save-files "saves"))
  ([dir-path]
   (let [dir (java.io.File. dir-path)]
     (if (.exists dir)
       (->> (.listFiles dir)
            (filter #(.endsWith (.getName %) ".edn"))
            (sort-by #(- (.lastModified %)))
            (mapv #(.getName %)))
       []))))

(defn save-game!
  ([] (save-game! "saves" nil))
  ([dir-path] (save-game! dir-path nil))
  ([dir-path filename]
   (let [dir (java.io.File. dir-path)]
     (when-not (.exists dir)
       (.mkdirs dir))
     (let [data (reduce-kv (fn [acc k _] (assoc acc k (read-save-key k)))
                           {}
                           saveable-atoms)
           save-filename (normalize-save-filename filename)
           filepath (str dir-path "/" save-filename)]
       (spit filepath (pr-str data))
       save-filename))))

(defn load-game!
  ([filename] (load-game! "saves" filename))
  ([dir-path filename]
   (let [filepath (str dir-path "/" filename)
         data (edn/read-string (slurp filepath))]
     (doseq [[k _] saveable-atoms]
       (when-let [value (get data k)]
         (write-save-key! k value)))
     (sa/write-state! :load-menu-open false)
     (sa/write-state! :load-menu-files [])
     (sa/write-state! :load-menu-hovered nil)
     (sa/rebuild-refueling-caches!)
     (sa/write-state! :production-status
                      (production-status/format-production-status
                       (sa/current-world)
                       (sa/read-state :player-map)))
     (sa/write-state! :turn-message (str "Loaded " filename))
     (sa/write-state! :turn-message-until
                      (messages/expires-at (System/currentTimeMillis) 3000)))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-13T16:20:19.14143-05:00", :module-hash "-1256815150", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 5, :hash "-1485057323"} {:id "def/saveable-atoms", :kind "def", :line 7, :end-line 39, :hash "159421838"} {:id "defn-/timestamp", :kind "defn-", :line 41, :end-line 44, :hash "1770911414"} {:id "defn/default-save-basename", :kind "defn", :line 46, :end-line 48, :hash "-67941117"} {:id "defn/normalize-save-filename", :kind "defn", :line 50, :end-line 58, :hash "-1419190907"} {:id "defn-/read-save-key", :kind "defn-", :line 60, :end-line 64, :hash "1502581137"} {:id "defn-/write-save-key!", :kind "defn-", :line 66, :end-line 70, :hash "-861186194"} {:id "defn/list-save-files", :kind "defn", :line 72, :end-line 81, :hash "-418918482"} {:id "defn/save-game!", :kind "defn", :line 83, :end-line 96, :hash "1456296839"} {:id "defn/load-game!", :kind "defn", :line 98, :end-line 112, :hash "-1725248497"}]}
;; clj-mutate-manifest-end

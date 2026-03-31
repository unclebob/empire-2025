(ns empire.game.save-load.persistence
  (:require [clojure.edn :as edn]
            [clojure.string :as string]
            [empire.config.domain.core.messages :as messages]
            [empire.player.attention :as attention]
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

(defn- clear-ghost-contents!
  "Clears :contents maps missing :type from all map cells."
  [map-key]
  (let [world (sa/read-state map-key)]
    (when (sequential? world)
      (doseq [i (range (count world))
              j (range (count (first world)))
              :let [contents (get-in world [i j :contents])]
              :when (and contents (not (:type contents)))]
        (sa/update-state! map-key assoc-in [i j :contents] nil)))))

(defn- clamp-awake-count
  [entity count-key awake-key]
  (if (and (contains? entity awake-key)
           (contains? entity count-key))
    (assoc entity awake-key (min (get entity awake-key 0)
                                 (get entity count-key 0)))
    entity))

(defn- normalize-airport-awake-count
  [cell]
  (if (and (= :city (:type cell))
           (contains? cell :awake-fighters))
    (assoc cell :awake-fighters (min (get cell :fighter-count 0)
                                     (get cell :awake-fighters 0)))
    cell))

(defn- normalize-container-counts!
  [map-key]
  (let [world (sa/read-state map-key)]
    (when (sequential? world)
      (doseq [i (range (count world))
              j (range (count (first world)))
              :let [cell (get-in world [i j])
                    contents (:contents cell)
                    normalized-cell (-> cell
                                        (clamp-awake-count :fighter-count :awake-fighters)
                                        (normalize-airport-awake-count)
                                        (clamp-awake-count :kamikazee-fighter-count :awake-fighters))
                    normalized-contents (cond-> contents
                                          (= (:type contents) :carrier)
                                          (clamp-awake-count :fighter-count :awake-fighters)

                                          (= (:type contents) :transport)
                                          (clamp-awake-count :army-count :awake-armies))]
              :when (or (not= normalized-cell cell)
                        (not= normalized-contents contents))]
        (sa/update-state! map-key assoc-in [i j] (assoc normalized-cell :contents normalized-contents))))))

(defn- sanitize-loaded-maps! []
  (clear-ghost-contents! :game-map)
  (clear-ghost-contents! :player-map)
  (clear-ghost-contents! :computer-map)
  (normalize-container-counts! :game-map)
  (normalize-container-counts! :player-map)
  (normalize-container-counts! :computer-map))

(defn load-game!
  ([filename] (load-game! "saves" filename))
  ([dir-path filename]
   (let [filepath (str dir-path "/" filename)
         data (edn/read-string (slurp filepath))]
     (doseq [[k _] saveable-atoms]
       (when-let [value (get data k)]
         (write-save-key! k value)))
     (sanitize-loaded-maps!)
     (sa/write-state! :load-menu-open false)
     (sa/write-state! :load-menu-files [])
     (sa/write-state! :load-menu-hovered nil)
     (sa/write-state! :handicap-rounds-remaining 0)
     (sa/write-state! :handicap-display-rounds nil)
     (sa/rebuild-refueling-caches!)
     (sa/write-state! :production-status
                      (production-status/format-production-status
                       (sa/current-world)
                       (sa/read-state :player-map)))
     (if (and (sa/read-state :waiting-for-input)
              (seq (sa/read-state :cells-needing-attention)))
       (attention/set-attention-message (first (sa/read-state :cells-needing-attention)))
       (sa/write-state! :attention-message ""))
     (sa/write-state! :command-message (str "Loaded " filename)))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T02:14:21.663309-05:00", :module-hash "-565560700", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 6, :hash "-216890708"} {:id "def/saveable-atoms", :kind "def", :line 8, :end-line 40, :hash "-652094246"} {:id "defn-/timestamp", :kind "defn-", :line 42, :end-line 45, :hash "1770911414"} {:id "defn/default-save-basename", :kind "defn", :line 47, :end-line 49, :hash "-67941117"} {:id "defn/normalize-save-filename", :kind "defn", :line 51, :end-line 59, :hash "-1419190907"} {:id "defn-/read-save-key", :kind "defn-", :line 61, :end-line 65, :hash "1502581137"} {:id "defn-/write-save-key!", :kind "defn-", :line 67, :end-line 71, :hash "-861186194"} {:id "defn/list-save-files", :kind "defn", :line 73, :end-line 82, :hash "936793038"} {:id "defn/save-game!", :kind "defn", :line 84, :end-line 97, :hash "1456296839"} {:id "defn/load-game!", :kind "defn", :line 99, :end-line 117, :hash "513559975"}]}
;; clj-mutate-manifest-end

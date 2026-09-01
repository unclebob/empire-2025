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

(defn- normalize-cell-counts
  [cell]
  (-> cell
      (clamp-awake-count :fighter-count :awake-fighters)
      (normalize-airport-awake-count)
      (clamp-awake-count :kamikazee-fighter-count :awake-fighters)))

(def ^:private container-awake-keys
  {:carrier [:fighter-count :awake-fighters]
   :transport [:army-count :awake-armies]})

(defn- normalize-contents-counts
  [contents]
  (if-let [[count-key awake-key] (container-awake-keys (:type contents))]
    (clamp-awake-count contents count-key awake-key)
    contents))

(defn- map-coords
  [world]
  (for [i (range (count world))
        j (range (count (first world)))]
    [i j]))

(defn- update-normalized-cell!
  [map-key pos cell contents normalized-cell normalized-contents]
  (when (or (not= normalized-cell cell)
            (not= normalized-contents contents))
    (sa/update-state! map-key assoc-in pos (assoc normalized-cell :contents normalized-contents))))

(defn- normalize-container-counts!
  [map-key]
  (let [world (sa/read-state map-key)]
    (when (sequential? world)
      (doseq [pos (map-coords world)
              :let [cell (get-in world pos)
                    contents (:contents cell)
                    normalized-cell (normalize-cell-counts cell)
                    normalized-contents (normalize-contents-counts contents)]]
        (update-normalized-cell! map-key pos cell contents normalized-cell normalized-contents)))))

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
;; {:version 1, :tested-at "2026-05-07T18:22:26.768427-05:00", :module-hash "1707415866", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 7, :hash "908100692"} {:id "def/saveable-atoms", :kind "def", :line 9, :end-line 41, :hash "-652094246"} {:id "defn-/timestamp", :kind "defn-", :line 43, :end-line 46, :hash "1770911414"} {:id "defn/default-save-basename", :kind "defn", :line 48, :end-line 50, :hash "-67941117"} {:id "defn/normalize-save-filename", :kind "defn", :line 52, :end-line 60, :hash "-1419190907"} {:id "defn-/read-save-key", :kind "defn-", :line 62, :end-line 66, :hash "1502581137"} {:id "defn-/write-save-key!", :kind "defn-", :line 68, :end-line 72, :hash "-861186194"} {:id "defn/list-save-files", :kind "defn", :line 74, :end-line 83, :hash "936793038"} {:id "defn/save-game!", :kind "defn", :line 85, :end-line 98, :hash "1456296839"} {:id "defn-/clear-ghost-contents!", :kind "defn-", :line 100, :end-line 109, :hash "-1465913443"} {:id "defn-/clamp-awake-count", :kind "defn-", :line 111, :end-line 117, :hash "-1061339377"} {:id "defn-/normalize-airport-awake-count", :kind "defn-", :line 119, :end-line 125, :hash "-2109530129"} {:id "defn-/normalize-cell-counts", :kind "defn-", :line 127, :end-line 132, :hash "2004146037"} {:id "defn-/normalize-contents-counts", :kind "defn-", :line 134, :end-line 139, :hash "86467152"} {:id "defn-/map-coords", :kind "defn-", :line 141, :end-line 145, :hash "-894103162"} {:id "defn-/update-normalized-cell!", :kind "defn-", :line 147, :end-line 151, :hash "1557001882"} {:id "defn-/normalize-container-counts!", :kind "defn-", :line 153, :end-line 162, :hash "1595651220"} {:id "defn-/sanitize-loaded-maps!", :kind "defn-", :line 164, :end-line 170, :hash "585532354"} {:id "defn/load-game!", :kind "defn", :line 172, :end-line 195, :hash "-1678387145"}]}
;; clj-mutate-manifest-end

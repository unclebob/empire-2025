;; mutation-tested: 2026-02-25
(ns empire.save-load
  (:require [empire.atoms :as atoms]))

(def saveable-atoms
  "Map of atom names to atoms that should be saved/restored."
  {:game-map atoms/game-map
   :player-map atoms/player-map
   :computer-map atoms/computer-map
   :production atoms/production
   :destination atoms/destination
   :round-number atoms/round-number
   :cells-needing-attention atoms/cells-needing-attention
   :player-items atoms/player-items
   :computer-items atoms/computer-items
   :waiting-for-input atoms/waiting-for-input
   :paused atoms/paused
   :computer-turn atoms/computer-turn
   :next-transport-id atoms/next-transport-id
   :next-country-id atoms/next-country-id
   :next-unload-event-id atoms/next-unload-event-id
   :next-destroyer-id atoms/next-destroyer-id
   :next-carrier-id atoms/next-carrier-id
   :next-escort-id atoms/next-escort-id
   :sea-lane-network atoms/sea-lane-network
   :claimed-objectives atoms/claimed-objectives
   :claimed-transport-targets atoms/claimed-transport-targets
   :fighter-leg-records atoms/fighter-leg-records
   :coast-walkers-produced atoms/coast-walkers-produced
   :distant-city-pairs atoms/distant-city-pairs})

(defn list-save-files
  "Returns a vector of save filenames sorted by modification time (newest first).
   If dir-path is not provided, defaults to 'saves'."
  ([] (list-save-files "saves"))
  ([dir-path]
   (let [dir (java.io.File. dir-path)]
     (if (.exists dir)
       (->> (.listFiles dir)
            (filter #(.endsWith (.getName %) ".edn"))
            (sort-by #(- (.lastModified %)))
            (mapv #(.getName %)))
       []))))

(defn- timestamp []
  (let [now (java.time.LocalDateTime/now)
        fmt (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd-HHmmss")]
    (.format now fmt)))

(defn save-game!
  "Saves the current game state to a timestamped EDN file.
   Returns the filename. If dir-path is not provided, defaults to 'saves'."
  ([] (save-game! "saves"))
  ([dir-path]
   (let [dir (java.io.File. dir-path)]
     (when-not (.exists dir)
       (.mkdirs dir))
     (let [filename (str "save-" (timestamp) ".edn")
           filepath (str dir-path "/" filename)
           data (into {} (map (fn [[k atom]] [k @atom]) saveable-atoms))]
       (spit filepath (pr-str data))
       filename))))

(defn load-game!
  "Loads game state from an EDN file. Closes the load menu after loading.
   If dir-path is not provided, defaults to 'saves'."
  ([filename] (load-game! "saves" filename))
  ([dir-path filename]
   (let [filepath (str dir-path "/" filename)
         data (clojure.edn/read-string (slurp filepath))]
     (doseq [[k atom] saveable-atoms]
       (when-let [value (get data k)]
         (reset! atom value)))
     (reset! atoms/load-menu-open false)
     (reset! atoms/load-menu-files [])
     (reset! atoms/load-menu-hovered nil)
     (atoms/set-turn-message (str "Loaded " filename) 3000))))

(defn open-load-menu!
  "Opens the load menu, populating it with available save files."
  ([] (open-load-menu! "saves"))
  ([dir-path]
   (reset! atoms/load-menu-files (list-save-files dir-path))
   (reset! atoms/load-menu-hovered nil)
   (reset! atoms/load-menu-open true)))

(defn close-load-menu!
  "Closes the load menu without loading."
  []
  (reset! atoms/load-menu-open false)
  (reset! atoms/load-menu-files [])
  (reset! atoms/load-menu-hovered nil))

(def menu-width 350)
(def menu-padding 15)
(def menu-title-height 30)
(def menu-item-height 25)

(defn menu-geometry
  "Calculates menu geometry for given screen dimensions and file count."
  [screen-w screen-h file-count]
  (let [content-height (* menu-item-height (max 1 file-count))
        total-height (+ menu-title-height content-height (* 2 menu-padding))
        left (/ (- screen-w menu-width) 2)
        top (/ (- screen-h total-height) 2)]
    {:left left
     :top top
     :right (+ left menu-width)
     :bottom (+ top total-height)
     :width menu-width
     :height total-height
     :content-top (+ top menu-padding menu-title-height)
     :item-height menu-item-height}))

(defn hovered-file-index
  "Returns the index of the file at mouse position, or nil if none."
  [mouse-x mouse-y geom file-count]
  (when (and (> file-count 0)
             (>= mouse-x (:left geom))
             (<= mouse-x (:right geom))
             (>= mouse-y (:content-top geom))
             (< mouse-y (+ (:content-top geom) (* file-count (:item-height geom)))))
    (int (/ (- mouse-y (:content-top geom)) (:item-height geom)))))

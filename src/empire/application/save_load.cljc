;; mutation-tested: 2026-02-25
(ns empire.application.save-load
  (:require [empire.adapters.persistence.files :as persistence-files]
            [empire.application.ports.persistence :as ports]
            [empire.application.state-access :as sa]
            [empire.domain.core.messages :as messages]))

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
   :land-ho-targets true
   :major-invasion-state true
   :transport-fully-loaded? true
   :early-patrol-boat-produced? true
   :early-satellite-produced? true
   :distant-city-pairs true
   :lake-max-cells true
   :known-lake-cells true})

(defonce ^:private persistence
  (delay (persistence-files/persistence-adapter)))

(defn- load-world
  []
  ((:load-world (sa/state-ctx))))

(defn- save-world!
  [world]
  ((:save-world! (sa/state-ctx)) world))

(defn- load-major-invasion-state
  []
  ((:load-major-invasion-state (sa/state-ctx))))

(defn- rebuild-country-stats!
  []
  (when-let [f (:rebuild-country-stats! (sa/state-ctx))]
    (f)))

(defn- save-major-invasion-state!
  [state]
  ((:save-major-invasion-state! (sa/state-ctx)) state))

(defn- read-save-key
  [k]
  (case k
    :game-map (load-world)
    :major-invasion-state (load-major-invasion-state)
    (sa/read-state k)))

(defn- write-save-key!
  [k value]
  (case k
    :game-map (save-world! value)
    :major-invasion-state (save-major-invasion-state! value)
    (sa/write-state! k value)))

(defn list-save-files
  "Returns a vector of save filenames sorted by modification time (newest first).
   If dir-path is not provided, defaults to 'saves'."
  ([] (list-save-files "saves"))
  ([dir-path]
   (ports/list-saves @persistence dir-path)))

(defn save-game!
  "Saves the current game state to a timestamped EDN file.
   Returns the filename. If dir-path is not provided, defaults to 'saves'."
  ([] (save-game! "saves"))
  ([dir-path]
   (let [data (reduce-kv (fn [acc k _] (assoc acc k (read-save-key k)))
                         {}
                         saveable-atoms)]
     (ports/save-state! @persistence dir-path data))))

(defn load-game!
  "Loads game state from an EDN file. Closes the load menu after loading.
   If dir-path is not provided, defaults to 'saves'."
  ([filename] (load-game! "saves" filename))
  ([dir-path filename]
   (let [data (ports/load-state @persistence dir-path filename)]
     (doseq [[k _] saveable-atoms]
       (when-let [value (get data k)]
         (write-save-key! k value)))
     (sa/write-state! :load-menu-open false)
     (sa/write-state! :load-menu-files [])
     (sa/write-state! :load-menu-hovered nil)
     ((:rebuild-refueling-caches! (sa/state-ctx)))
     (rebuild-country-stats!)
     (sa/write-state! :turn-message (str "Loaded " filename))
     (sa/write-state! :turn-message-until
                           (messages/expires-at (System/currentTimeMillis) 3000)))))

(defn open-load-menu!
  "Opens the load menu, populating it with available save files."
  ([] (open-load-menu! "saves"))
  ([dir-path]
   (sa/write-state! :load-menu-files (list-save-files dir-path))
   (sa/write-state! :load-menu-hovered nil)
   (sa/write-state! :load-menu-open true)))

(defn close-load-menu!
  "Closes the load menu without loading."
  []
  (sa/write-state! :load-menu-open false)
  (sa/write-state! :load-menu-files [])
  (sa/write-state! :load-menu-hovered nil))

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

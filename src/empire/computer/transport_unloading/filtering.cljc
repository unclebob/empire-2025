;; mutation-tested: 2026-03-02
(ns empire.computer.transport-unloading.filtering
  (:require [empire.computer.land-objectives :as land-objectives]
            [empire.computer.threat-response :as threat-response]))

(defn pickup-exclude-ids
  "Returns set of country-ids to exclude: transport's own country-id,
   pickup-country-id, and the country-id at pickup-continent-pos."
  [world transport]
  (disj (set [(:country-id transport)
              (:pickup-country-id transport)
              (when-let [pcp (:pickup-continent-pos transport)]
                (:country-id (get-in world pcp)))])
        nil))

(defn pickup-continent-if-needed
  "Returns the pickup continent set for pickup-continent-pos using cached flood-fill.
   Always prefer geography-based exclusion to avoid load/unload loops when
   country-id stamping changes on the same landmass."
  [transport]
  (when-let [pcp (:pickup-continent-pos transport)]
    (land-objectives/flood-fill-continent pcp)))

(defn unloadable-land-cell?
  "Returns true if cell is empty land/city not excluded by country-id or pickup continent."
  [cell neighbor-pos exclude-ids pickup-continent major-invasion?]
  (and cell
       (#{:land :city} (:type cell))
       (nil? (:contents cell))
       (or (not major-invasion?)
           (threat-response/major-invasion-target-land? neighbor-pos))
       (or (empty? exclude-ids)
           (not (contains? exclude-ids (:country-id cell))))
       (or (nil? pickup-continent)
           (not (contains? pickup-continent neighbor-pos)))))

(defn adjacent-empty-land
  "Returns adjacent land/city positions that are empty (no unit).
   Excludes positions on the pickup continent and land belonging to
   any country-id in exclude-ids set."
  [world get-neighbors-fn pos exclude-ids pickup-continent major-invasion?]
  (filter (fn [neighbor]
            (unloadable-land-cell?
              (get-in world neighbor) neighbor exclude-ids pickup-continent major-invasion?))
          (get-neighbors-fn pos)))

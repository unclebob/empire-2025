;; mutation-tested: no
(ns empire.computer.threat-response.country-defense
  "Country-level homeland defense helpers for detected player armies."
  (:require [empire.computer.core :as core]))

(def ^:private defense-threat-keys
  [:threat-mission :threat-center :threat-radius :threat-rounds-left])

(def ^:private absent-sentinel ::absent)

(defn player-armies-by-country
  "Returns {country-id #{[x y] ...}} from currently visible computer-map cells."
  [computer-map]
  (reduce (fn [acc [x y]]
            (let [cell (get-in computer-map [x y])
                  unit (:contents cell)
                  country-id (:country-id cell)]
              (if (and (= :player (:owner unit))
                       (= :army (:type unit))
                       country-id)
                (update acc country-id (fnil conj #{}) [x y])
                acc)))
          {}
          (for [x (range (count computer-map))
                y (range (count (first computer-map)))]
            [x y])))

(defn- nearest-target
  [pos targets]
  (when (seq targets)
    (apply min-key #(core/distance pos %) targets)))

(defn- snapshot-threat-fields
  [unit]
  (into {}
        (for [k defense-threat-keys]
          [k (if (contains? unit k) (get unit k) absent-sentinel)])))

(defn apply-country-defense
  "Applies/updates country-defense targeting for a single unit."
  [unit pos targets threat-radius]
  (if-let [target (nearest-target pos targets)]
    (let [prepared (if (:country-defense-active unit)
                     unit
                     (-> unit
                         (assoc :country-defense-active true)
                         (assoc :country-defense-prev-threat (snapshot-threat-fields unit))))]
      (-> prepared
          (assoc :threat-mission :country-defense
                 :threat-center target
                 :threat-radius threat-radius)
          (dissoc :threat-rounds-left)))
    unit))

(defn clear-country-defense
  "Restores prior threat fields for a unit leaving country-defense mode."
  [unit]
  (if-not (:country-defense-active unit)
    unit
    (let [prev (:country-defense-prev-threat unit)
          cleared (dissoc unit :country-defense-active :country-defense-prev-threat)]
      (reduce (fn [u k]
                (let [v (get prev k absent-sentinel)]
                  (if (= absent-sentinel v)
                    (dissoc u k)
                    (assoc u k v))))
              cleared
              defense-threat-keys))))

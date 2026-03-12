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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T11:58:39.677299-05:00", :module-hash "1538708626", :forms [{:id "form/0/ns", :kind "ns", :line 2, :end-line 4, :hash "52339648"} {:id "def/defense-threat-keys", :kind "def", :line 6, :end-line 7, :hash "-943903286"} {:id "def/absent-sentinel", :kind "def", :line 9, :end-line 9, :hash "508355299"} {:id "defn/player-armies-by-country", :kind "defn", :line 11, :end-line 26, :hash "733139256"} {:id "defn-/nearest-target", :kind "defn-", :line 28, :end-line 31, :hash "816381016"} {:id "defn-/snapshot-threat-fields", :kind "defn-", :line 33, :end-line 37, :hash "1979160693"} {:id "defn/apply-country-defense", :kind "defn", :line 39, :end-line 53, :hash "450910269"} {:id "defn/clear-country-defense", :kind "defn", :line 55, :end-line 68, :hash "1211599436"}]}
;; clj-mutate-manifest-end

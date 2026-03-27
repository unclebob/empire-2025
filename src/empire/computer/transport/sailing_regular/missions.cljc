(ns empire.computer.transport.sailing-regular.missions
  (:require [empire.computer.transport.core :as tc]
            [empire.computer.transport.load-targeting :as load-targeting]
            [empire.computer.transport.loading :as loading]
            [empire.computer.transport.reservations :as reservations]
            [empire.computer.transport.sailing-regular.follow :as follow]
            [empire.computer.transport.sailing-regular.transitions :as transitions]
            [empire.computer.transport.sailing-support :as support]
            [empire.computer.transport.unloading :as unloading]
            [empire.computer.shared.world-query :as world-query]
            [empire.state.api :as sa]))

(declare process-leave-city-mission)

(defn claimed-land?
  [cell]
  (and cell
       (or (and (= :land (:type cell))
                (some? (:country-id cell)))
           (and (= :city (:type cell))
                (= :computer (:city-status cell))))))

(defn- adjacent-claimed-land?
  [pos]
  (some (fn [n]
          (claimed-land? (get-in (sa/read-state :computer-map) n)))
        (world-query/get-neighbors pos)))

(defn- ensure-sail-to-load-transport
  [pos transport]
  (if (or (:load-target-cell transport)
          (seq (:sail-path transport))
          (contains? transport :load-manifest))
    transport
    (transitions/enter-sail-to-load! pos)))

(defn- arrived-at-load-target?
  [pos transport]
  (if-let [target (:load-target-cell transport)]
    (load-targeting/target-reached? pos target)
    (adjacent-claimed-land? pos)))

(defn- sail-path-exhausted?
  [transport]
  (or (and (vector? (:load-manifest transport))
           (empty? (:load-manifest transport)))
      (empty? (:sail-path transport))))

(defn process-sail-to-load-mission
  [pos transport]
  (let [transport (ensure-sail-to-load-transport pos transport)
        mission (:transport-mission transport)
        city-cell? (= :city (:type (get-in (sa/read-state :computer-map) pos)))]
    (cond
      (= :hold-sail-to-load mission) nil
      city-cell? (process-leave-city-mission pos transport)
      (arrived-at-load-target? pos transport) (transitions/transition-to-loading! pos)
      (sail-path-exhausted? transport)
      (do (reservations/release! (:transport-id transport))
          (tc/enter-hold-sail-to-load! pos))
      (seq (:sail-path transport))
      (follow/follow-load-sail-path pos (:sail-path transport) (:load-target-cell transport))
      :else
      (follow/compute-and-follow-load-target-path! pos transport))))

(defn process-sail-to-unload-mission
  [pos transport]
  (let [computer-map (sa/read-state :computer-map)
        city-cell? (= :city (:type (get-in computer-map pos)))
        sail-path (:sail-path transport)]
    (cond
      (unloading/has-nearby-unloadable-land? pos transport 0)
      (tc/set-transport-mission pos :unloading)

      city-cell?
      (transitions/handle-launch-and-follow! pos transport support/compute-sail-to-unload-path)

      (seq sail-path)
      (follow/follow-unload-sail-path pos sail-path)

      :else
      (follow/compute-and-follow-path! pos support/compute-sail-to-unload-path))))

(defn process-hold-sail-to-load-mission
  [pos transport]
  (loading/load-adjacent-armies pos)
  (let [transport' (get-in (sa/read-state :computer-map) (conj pos :contents))
        army-count' (:army-count transport' 0)]
    (cond
      (>= army-count' 6)
      (transitions/enter-sail-to-unload! pos transport')

      (tc/hold-sail-to-load-elapsed? transport')
      (transitions/enter-sail-to-load! pos))))

(defn process-leave-city-mission
  [pos transport]
  (let [computer-map (sa/read-state :computer-map)]
    (if (= :city (:type (get-in computer-map pos)))
      (when-let [sea-pos (transitions/launch-from-city-to-sea pos transport)]
        (transitions/enter-sail-to-load! sea-pos))
      (transitions/enter-sail-to-load! pos))))

(defn process-sailing-default
  [pos transport]
  (if (seq (:sail-path transport))
    (follow/follow-path-action pos (:sail-path transport))
    (if (zero? (:army-count transport 0))
      (process-sail-to-load-mission pos transport)
      (process-sail-to-unload-mission pos transport))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T10:31:06.993711-05:00", :module-hash "1372925606", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 11, :hash "-101043929"} {:id "form/1/declare", :kind "declare", :line 13, :end-line 13, :hash "-785031257"} {:id "defn/claimed-land?", :kind "defn", :line 15, :end-line 21, :hash "-431888260"} {:id "defn-/adjacent-claimed-land?", :kind "defn-", :line 23, :end-line 27, :hash "-1519096910"} {:id "defn-/ensure-sail-to-load-transport", :kind "defn-", :line 29, :end-line 35, :hash "698444351"} {:id "defn-/arrived-at-load-target?", :kind "defn-", :line 37, :end-line 41, :hash "-69419407"} {:id "defn-/sail-path-exhausted?", :kind "defn-", :line 43, :end-line 47, :hash "1525548043"} {:id "defn/process-sail-to-load-mission", :kind "defn", :line 49, :end-line 64, :hash "-1652646905"} {:id "defn/process-sail-to-unload-mission", :kind "defn", :line 66, :end-line 82, :hash "1733876705"} {:id "defn/process-hold-sail-to-load-mission", :kind "defn", :line 84, :end-line 94, :hash "1248060946"} {:id "defn/process-leave-city-mission", :kind "defn", :line 96, :end-line 102, :hash "1684899086"} {:id "defn/process-sailing-default", :kind "defn", :line 104, :end-line 110, :hash "73198835"}]}
;; clj-mutate-manifest-end

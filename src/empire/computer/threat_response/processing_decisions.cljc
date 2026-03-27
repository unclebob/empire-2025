(ns empire.computer.threat-response.processing-decisions)

(defn fighter-threat-active?
  [unit]
  (or (= :fighter-sweep (:threat-mission unit))
      (= :country-defense (:threat-mission unit))
      (:major-invasion unit)))

(defn fighter-threat-center
  [{:keys [unit nearest-major-target pos]}]
  (or (:threat-center unit)
      (:major-invasion-target unit)
      (when nearest-major-target
        (nearest-major-target pos))))

(defn fighter-threat-action
  [{:keys [enemy? low-fuel? outside-radius?]}]
  (cond
    enemy? :attack
    low-fuel? :refuel
    outside-radius? :outside-radius
    :else :patrol))

(defn next-threat-state
  [remaining step]
  (when (and (pos? remaining) step)
    {:pos (:pos step)
     :remaining (- remaining (:steps-used step))}))

(defn fighter-threat-round-mode
  [{:keys [random-walk?]}]
  (if random-walk?
    :random-walk
    :active-threat))

(defn ship-threat-mode
  [{:keys [random-walk? sea-scout? major-invasion?]}]
  (cond
    random-walk? :random-walk
    sea-scout? :sea-scout
    major-invasion? :major-invasion
    :else nil))

(defn sea-scout-move-target
  [{:keys [pos center radius distance-fn]}]
  (when (and center (> (distance-fn pos center) radius))
    center))

(defn major-invasion-move-target
  [{:keys [center nearest-major-target pos]}]
  (or center
      (when nearest-major-target
        (nearest-major-target pos))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T23:53:26.05101-05:00", :module-hash "-1650730758", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "1781175175"} {:id "defn/fighter-threat-active?", :kind "defn", :line 3, :end-line 7, :hash "-309559388"} {:id "defn/fighter-threat-center", :kind "defn", :line 9, :end-line 14, :hash "-1366406760"} {:id "defn/fighter-threat-action", :kind "defn", :line 16, :end-line 22, :hash "-887284322"} {:id "defn/next-threat-state", :kind "defn", :line 24, :end-line 28, :hash "1058774497"} {:id "defn/fighter-threat-round-mode", :kind "defn", :line 30, :end-line 34, :hash "-1039904369"} {:id "defn/ship-threat-mode", :kind "defn", :line 36, :end-line 42, :hash "1870616932"} {:id "defn/sea-scout-move-target", :kind "defn", :line 44, :end-line 47, :hash "-186017952"} {:id "defn/major-invasion-move-target", :kind "defn", :line 49, :end-line 53, :hash "1532122303"}]}
;; clj-mutate-manifest-end

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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-16T09:15:46.455831-05:00", :module-hash "1280974712", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "1781175175"} {:id "defn/fighter-threat-active?", :kind "defn", :line 3, :end-line 7, :hash "-309559388"} {:id "defn/fighter-threat-center", :kind "defn", :line 9, :end-line 14, :hash "-1366406760"} {:id "defn/fighter-threat-action", :kind "defn", :line 16, :end-line 22, :hash "-887284322"} {:id "defn/next-threat-state", :kind "defn", :line 24, :end-line 28, :hash "1058774497"}]}
;; clj-mutate-manifest-end

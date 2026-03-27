(ns empire.computer.fighter.decisions)

(defn objective-action
  [{:keys [exploring? at-flight-target? low-fuel? has-target?]}]
  (cond
    at-flight-target? :arrive
    low-fuel? :low-fuel
    exploring? :explore
    has-target? :navigate
    :else :patrol))

(defn fighter-step-action
  [enemy-pos objective-action]
  (if enemy-pos
    :attack
    objective-action))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T22:55:00.472202-05:00", :module-hash "1641743872", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "-163500929"} {:id "defn/objective-action", :kind "defn", :line 3, :end-line 10, :hash "-1150455377"} {:id "defn/fighter-step-action", :kind "defn", :line 12, :end-line 16, :hash "522996551"}]}
;; clj-mutate-manifest-end

(ns empire.computer.fighter.decisions)

(defn objective-action
  [{:keys [exploring? drone? at-flight-target? low-fuel? has-target?]}]
  (cond
    (or exploring? drone?) :explore
    at-flight-target? :arrive
    low-fuel? :low-fuel
    has-target? :navigate
    :else :patrol))

(defn fighter-step-action
  [enemy-pos objective-action]
  (if enemy-pos
    :attack
    objective-action))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-15T15:50:44.892343-05:00", :module-hash "1615791537", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "-2044879280"} {:id "defn/objective-action", :kind "defn", :line 3, :end-line 10, :hash "-388173081"} {:id "defn/fighter-step-action", :kind "defn", :line 12, :end-line 16, :hash "522996551"}]}
;; clj-mutate-manifest-end

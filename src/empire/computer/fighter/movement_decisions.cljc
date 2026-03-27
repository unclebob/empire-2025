(ns empire.computer.fighter.movement-decisions
  (:require [empire.config.units.dispatcher :as dispatcher]))

(defn ensure-hits
  [unit]
  (cond-> unit
    (and unit (nil? (:hits unit)))
    (assoc :hits (dispatcher/hits (:type unit)))))

(defn attack-context
  [world fighter-pos enemy-pos attackable-enemy-cell?]
  (let [attacker (ensure-hits (get-in world (conj fighter-pos :contents)))
        target-cell (get-in world enemy-pos)
        defender (ensure-hits (:contents target-cell))]
    {:attacker attacker
     :defender defender
     :target-cell target-cell
     :attackable? (and attacker
                      (attackable-enemy-cell? target-cell)
                      (number? (:hits attacker))
                      (number? (:hits defender)))}))

(defn fuel-action
  [unit default-fuel]
  (let [valid-fighter? (and (= :fighter (:type unit))
                            (= :computer (:owner unit)))
        next-fuel (dec (:fuel unit default-fuel))]
    (cond
      (not valid-fighter?)
      {:action :invalid}

      (<= next-fuel 0)
      {:action :destroy}

      :else
      {:action :update-fuel
       :fuel next-fuel})))

(defn hop-result
  [new-pos hops fuel-consumed?]
  (cond
    (nil? new-pos) nil
    fuel-consumed? {:result :moved :pos new-pos :hops hops}
    :else {:result :destroyed :pos new-pos :hops hops}))

(defn attack-result-action
  [winner]
  (if (= :attacker winner)
    :occupy-target
    :attacker-lost))

(defn patrol-action
  [{:keys [has-target? has-hop?]}]
  (cond
    (not has-target?) nil
    has-hop? :execute-hop
    :else nil))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T22:59:15.152333-05:00", :module-hash "1720620318", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 2, :hash "879661074"} {:id "defn/ensure-hits", :kind "defn", :line 4, :end-line 8, :hash "-1822430338"} {:id "defn/attack-context", :kind "defn", :line 10, :end-line 21, :hash "59552181"} {:id "defn/fuel-action", :kind "defn", :line 23, :end-line 37, :hash "578237959"} {:id "defn/hop-result", :kind "defn", :line 39, :end-line 44, :hash "-2117937626"} {:id "defn/attack-result-action", :kind "defn", :line 46, :end-line 50, :hash "-1687731722"} {:id "defn/patrol-action", :kind "defn", :line 52, :end-line 57, :hash "1250770406"}]}
;; clj-mutate-manifest-end

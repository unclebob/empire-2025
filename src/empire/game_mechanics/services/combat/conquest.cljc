(ns empire.game-mechanics.services.combat.conquest
  (:require [empire.state.api :as sa]
            [empire.config.core :as config]
            [empire.config.domain.model.combat :as domain-combat]
            [empire.game-mechanics.services.combat.resolution :as resolution]))

(defn- dead-escort-unit?
  [dead-unit unit-type escort-id-key]
  (and (= unit-type (:type dead-unit))
       (escort-id-key dead-unit)))

(defn dead-escort-destroyer?
  [dead-unit]
  (dead-escort-unit? dead-unit :destroyer :escort-transport-id))

(defn dead-escort-transport?
  [dead-unit]
  (dead-escort-unit? dead-unit :transport :escort-destroyer-id))

(defn- find-units-where
  [world pred]
  (for [i (range (count world))
        j (range (count (first world)))
        :let [unit (get-in world [i j :contents])]
        :when (and unit (pred unit))]
    [i j]))

(defn- clear-dead-escort-from-carrier
  [world dead-unit]
  (let [carrier-id (:escort-carrier-id dead-unit)
        escort-id (:escort-id dead-unit)
        coords (find-units-where world
                #(and (= :carrier (:type %))
                      (= carrier-id (:carrier-id %))))]
    (reduce (fn [w pos]
              (case (:type dead-unit)
                :battleship (assoc-in w (conj pos :contents :group-battleship-id) nil)
                :submarine (update-in w (conj pos :contents :group-submarine-ids)
                                      (fn [ids] (vec (remove #{escort-id} ids))))))
	            world coords)))

(defn- release-escort
  [unit & keys-to-clear]
  (apply dissoc (assoc unit :escort-mode :seeking) keys-to-clear))

(defn- update-linked-units
  [world dead-unit dead-id-key target-type target-id-key update-unit & args]
  (let [dead-id (dead-id-key dead-unit)
        coords (find-units-where world
                #(and (= target-type (:type %))
                      (= dead-id (target-id-key %))))]
    (reduce (fn [w pos]
              (apply update-in w (conj pos :contents) update-unit args))
            world coords)))

(defn- release-carrier-escorts
  [world dead-unit]
  (let [carrier-id (:carrier-id dead-unit)
        coords (find-units-where world #(= carrier-id (:escort-carrier-id %)))]
    (reduce (fn [w pos]
              (update-in w (conj pos :contents) release-escort :escort-carrier-id :orbit-angle))
            world coords)))

(defn- clear-carrier-group-on-death
  [world dead-unit]
  (cond
    (and (#{:battleship :submarine} (:type dead-unit))
         (:escort-carrier-id dead-unit))
    (clear-dead-escort-from-carrier world dead-unit)

    (and (= :carrier (:type dead-unit))
         (:carrier-id dead-unit))
    (release-carrier-escorts world dead-unit)

    :else world))

(defn- clear-destroyer-escort
  [world dead-unit]
  (update-linked-units world dead-unit
                       :escort-transport-id
                       :transport
                       :transport-id
                       dissoc
                       :escort-destroyer-id))

(defn- clear-transport-escort
  [world dead-unit]
  (update-linked-units world dead-unit
                       :escort-destroyer-id
                       :destroyer
                       :destroyer-id
                       release-escort
                       :escort-transport-id))

(defn clear-escort-on-death-world
  [world dead-unit]
  (-> world
      (cond->
        (dead-escort-destroyer? dead-unit) (clear-destroyer-escort dead-unit)
        (dead-escort-transport? dead-unit) (clear-transport-escort dead-unit))
      (clear-carrier-group-on-death dead-unit)))

(defn clear-escort-on-death
  "Side-effectful wrapper for computer callers. Reads/writes world atom."
  [dead-unit]
  (sa/update-world! (fn [w] (clear-escort-on-death-world w dead-unit))))

(defn conquer-city-contents-pure
  "Returns updated world after conquering city contents. Pure function."
  [world city-coords new-owner]
  (domain-combat/conquer-city-contents-world world city-coords new-owner))

(defn conquer-city-contents
  "Side-effectful wrapper for computer callers."
  [city-coords new-owner]
  (sa/update-world! (fn [w] (conquer-city-contents-pure w city-coords new-owner)))
  (sa/update-state! :production dissoc city-coords)
  nil)

(defn hostile-city?
  [world target-coords]
  (domain-combat/hostile-city? world target-coords))

(defn- has-city?
  [world owner]
  (boolean
   (some (fn [col]
           (some #(and (= :city (:type %))
                       (= owner (:city-status %)))
                 col))
         world)))

(defn- city-elimination-game-over-updates
  [message]
  {:paused true
   :warning-message message
   :map-to-display :actual-map
   :player-items []
   :computer-items []})

(defn attempt-city-conquest
  [world city-coords]
  (let [city-cell (get-in world city-coords)]
    (if (< (rand) 0.5)
      (let [captured-computer-city? (= :computer (:city-status city-cell))
            new-world (-> world
                          (assoc-in city-coords (assoc city-cell :city-status :player))
                          (conquer-city-contents-pure city-coords :player))
            resigns? (and (sa/read-state :game-over-check-enabled)
                          captured-computer-city?
                          (not (has-city? new-world :computer)))]
        {:world new-world
         :messages (resolution/command-message-map "City conquered.")
         :state-updates (cond-> {:production #(dissoc % city-coords)
                                 :computer-carrier-positions #(disj % city-coords)
                                 :computer-map #(assoc-in % (conj city-coords :city-status) :player)}
                          captured-computer-city?
                          (assoc :computer-city-positions #(disj % city-coords))
                          resigns?
                          (merge (city-elimination-game-over-updates
                                  "****GAME OVER*****  I Resign  YOU WIN!")))
         :visibility [{:pos city-coords :owner :player}]
         :combatant true})
      {:world world
       :messages (resolution/warning-message-map (:conquest-failed config/messages))
       :combatant true})))

(defn attempt-conquest
  [world army-coords city-coords]
  (let [army-cell (get-in world army-coords)
        world-without-army (assoc-in world army-coords (dissoc army-cell :contents))
        city-result (attempt-city-conquest world-without-army city-coords)]
    (update city-result :visibility
            (fnil conj []) {:pos army-coords :owner :player})))

(defn attempt-fighter-overfly
  [world fighter-coords city-coords]
  (let [fighter-cell (get-in world fighter-coords)
        fighter (:contents fighter-cell)
        shot-down-fighter (assoc fighter :mode :awake :hits 0 :steps-remaining 0 :reason :fighter-shot-down)
        new-world (domain-combat/apply-fighter-overfly-world world fighter-coords city-coords shot-down-fighter)]
    {:world new-world
     :messages (resolution/warning-message-map (:fighter-destroyed-by-city config/messages))
     :combatant true}))

(defn hostile-unit?
  [unit owner]
  (domain-combat/hostile-unit? unit owner))

(defn- combat-visibility-effects
  [attacker-coords target-coords attacker defender]
  (->> [(:owner attacker) (:owner defender)]
       distinct
       (mapcat (fn [owner]
                 [{:pos attacker-coords :owner owner}
                  {:pos target-coords :owner owner}]))
       vec))

(defn- valid-combatant?
  [unit]
  (and unit
       (:type unit)
       (number? (:hits unit))))

(defn attempt-attack
  [world attacker-coords target-coords]
  (let [attacker-cell (get-in world attacker-coords)
        target-cell (get-in world target-coords)
        attacker (:contents attacker-cell)
        defender (:contents target-cell)]
    (if (or (not (valid-combatant? attacker))
            (not (valid-combatant? defender))
            (= :satellite (:type attacker))
            (= :satellite (:type defender))
            (not (hostile-unit? defender (:owner attacker))))
      false
      (let [result (resolution/resolve-combat attacker defender)
            message (resolution/format-combat-outcome (:type attacker)
                                          (:type defender)
                                          (:winner result))
            dead-unit (if (= :attacker (:winner result)) defender attacker)
            new-world (-> (domain-combat/apply-attack-world world attacker-coords target-coords (:survivor result))
                          (resolution/drown-excess-cargo-world target-coords (:survivor result))
                          (clear-escort-on-death-world dead-unit))]
        {:world new-world
         :messages (resolution/warning-message-map message)
         :visibility (combat-visibility-effects attacker-coords target-coords attacker defender)
         :winner (:winner result)
         :combatant true}))))

(defn attempt-coastal-army-attack
  [world army-coords target-coords]
  (when-let [result (attempt-attack world army-coords target-coords)]
    (let [attacker-won? (= :attacker (:winner result))
          updated-world (if attacker-won?
                          (assoc-in (:world result) (conj target-coords :contents) nil)
                          (:world result))
          visibility [{:pos army-coords :owner :player}
                      {:pos target-coords :owner :player}]
          drowned-message (str (get-in result [:messages :warning-message])
                               " "
                               (:army-drowned config/messages))]
      (assoc result
             :world updated-world
             :visibility visibility
             :messages (resolution/warning-message-map drowned-message)))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T11:49:45.486828-05:00", :module-hash "1315548920", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 5, :hash "309402068"} {:id "defn/dead-escort-destroyer?", :kind "defn", :line 7, :end-line 10, :hash "737415372"} {:id "defn/dead-escort-transport?", :kind "defn", :line 12, :end-line 15, :hash "1031563454"} {:id "defn-/find-units-where", :kind "defn-", :line 17, :end-line 23, :hash "-81025216"} {:id "defn-/clear-dead-escort-from-carrier", :kind "defn-", :line 25, :end-line 37, :hash "-733742303"} {:id "defn-/release-carrier-escorts", :kind "defn-", :line 39, :end-line 47, :hash "1253875709"} {:id "defn-/clear-carrier-group-on-death", :kind "defn-", :line 49, :end-line 60, :hash "-1988931291"} {:id "defn-/clear-destroyer-escort", :kind "defn-", :line 62, :end-line 70, :hash "-898033435"} {:id "defn-/clear-transport-escort", :kind "defn-", :line 72, :end-line 82, :hash "196027487"} {:id "defn/clear-escort-on-death-world", :kind "defn", :line 84, :end-line 90, :hash "752680484"} {:id "defn/clear-escort-on-death", :kind "defn", :line 92, :end-line 95, :hash "-2078012011"} {:id "defn/conquer-city-contents-pure", :kind "defn", :line 97, :end-line 100, :hash "1854750815"} {:id "defn/conquer-city-contents", :kind "defn", :line 102, :end-line 107, :hash "308072096"} {:id "defn/hostile-city?", :kind "defn", :line 109, :end-line 111, :hash "1158340970"} {:id "defn-/has-city?", :kind "defn-", :line 113, :end-line 120, :hash "-451495346"} {:id "defn-/city-elimination-game-over-updates", :kind "defn-", :line 122, :end-line 129, :hash "1302793836"} {:id "defn/attempt-city-conquest", :kind "defn", :line 131, :end-line 160, :hash "-702606488"} {:id "defn/attempt-conquest", :kind "defn", :line 162, :end-line 168, :hash "-614548960"} {:id "defn/attempt-fighter-overfly", :kind "defn", :line 170, :end-line 178, :hash "-1425906069"} {:id "defn/hostile-unit?", :kind "defn", :line 180, :end-line 182, :hash "1440002517"} {:id "defn-/valid-combatant?", :kind "defn-", :line 184, :end-line 188, :hash "1499655824"} {:id "defn/attempt-attack", :kind "defn", :line 190, :end-line 214, :hash "1622214336"} {:id "defn/attempt-coastal-army-attack", :kind "defn", :line 216, :end-line 231, :hash "1646130995"}]}
;; clj-mutate-manifest-end

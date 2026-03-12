(ns empire.game-mechanics.services.combat
  (:require [empire.state.api :as sa]
            [empire.config.core :as config]
            [empire.config.domain.model.combat :as domain-combat]
            [empire.game-mechanics.services.combat-visibility-port :as visibility-port]
            [empire.config.units.dispatcher :as dispatcher]))

(defn- error-message-map
  [msg ms]
  {:error-message msg
   :error-until (+ (System/currentTimeMillis) ms)})

(defn- turn-message-map
  [msg ms]
  {:turn-message msg
   :turn-message-until (if (= ms Long/MAX_VALUE)
                         Long/MAX_VALUE
                         (+ (System/currentTimeMillis) ms))})

(defn apply-combat-result!
  "Applies a combat result map's side effects: world update, messages, state changes, visibility."
  [{:keys [world messages state-updates visibility]}]
  (when world
    (sa/update-world! (constantly world)))
  (doseq [[k v] messages]
    (sa/write-state! k v))
  (doseq [[k v] state-updates]
    (if (fn? v)
      (sa/update-state! k v)
      (sa/write-state! k v)))
  (visibility-port/apply-visibility-effects!
    (visibility-port/combat-visibility-port)
    visibility))

(defn- drown-excess-cargo-world
  [world coords survivor]
  (if-not (#{:transport :carrier} (:type survivor))
    world
    (let [cap (dispatcher/effective-capacity (:type survivor) (:hits survivor))
          [count-key awake-key] (if (= :transport (:type survivor))
                                  [:army-count :awake-armies]
                                  [:fighter-count :awake-fighters])
          current-count (get survivor count-key 0)
          excess (- current-count cap)]
      (if (pos? excess)
        (let [current-awake (get survivor awake-key 0)
              new-awake (min current-awake cap)]
          (update-in world (conj coords :contents)
                     assoc count-key cap awake-key new-awake))
        world))))

(defn format-combat-log
  [log attacker-type defender-type winner]
  (domain-combat/format-combat-log log attacker-type defender-type winner))

(defn format-combat-status
  [log attacker-type defender-type winner]
  (domain-combat/format-combat-status log attacker-type defender-type winner))

(defn fight-round
  [attacker defender]
  (domain-combat/fight-round attacker defender))

(defn resolve-combat
  [attacker defender]
  (domain-combat/resolve-combat attacker defender))

(defn dead-escort-destroyer?
  [dead-unit]
  (and (= :destroyer (:type dead-unit))
       (:escort-transport-id dead-unit)))

(defn dead-escort-transport?
  [dead-unit]
  (and (= :transport (:type dead-unit))
       (:escort-destroyer-id dead-unit)))

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

(defn- release-carrier-escorts
  [world dead-unit]
  (let [carrier-id (:carrier-id dead-unit)
        coords (find-units-where world #(= carrier-id (:escort-carrier-id %)))]
    (reduce (fn [w pos]
              (update-in w (conj pos :contents)
                         #(-> % (assoc :escort-mode :seeking)
                              (dissoc :escort-carrier-id :orbit-angle))))
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
  (let [tid (:escort-transport-id dead-unit)
        coords (find-units-where world
                #(and (= :transport (:type %))
                      (= tid (:transport-id %))))]
    (reduce (fn [w pos]
              (update-in w (conj pos :contents) dissoc :escort-destroyer-id))
            world coords)))

(defn- clear-transport-escort
  [world dead-unit]
  (let [did (:escort-destroyer-id dead-unit)
        coords (find-units-where world
                #(and (= :destroyer (:type %))
                      (= did (:destroyer-id %))))]
    (reduce (fn [w pos]
              (update-in w (conj pos :contents)
                         #(-> % (assoc :escort-mode :seeking)
                              (dissoc :escort-transport-id))))
            world coords)))

(defn- clear-escort-on-death-world
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
   :error-message message
   :error-until Long/MAX_VALUE
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
         :messages {}
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
       :messages (error-message-map (:conquest-failed config/messages) config/error-message-duration)
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
     :messages (error-message-map (:fighter-destroyed-by-city config/messages) config/error-message-duration)
     :combatant true}))

(defn hostile-unit?
  [unit owner]
  (domain-combat/hostile-unit? unit owner))

(defn attempt-attack
  [world attacker-coords target-coords]
  (let [attacker-cell (get-in world attacker-coords)
        target-cell (get-in world target-coords)
        attacker (:contents attacker-cell)
        defender (:contents target-cell)]
    (if (or (nil? attacker)
            (nil? defender)
            (= :satellite (:type attacker))
            (= :satellite (:type defender))
            (not (hostile-unit? defender (:owner attacker))))
      false
      (let [result (resolve-combat attacker defender)
            message (format-combat-status (:log result)
                                          (:type attacker)
                                          (:type defender)
                                          (:winner result))
            dead-unit (if (= :attacker (:winner result)) defender attacker)
            new-world (-> (domain-combat/apply-attack-world world attacker-coords target-coords (:survivor result))
                          (drown-excess-cargo-world target-coords (:survivor result))
                          (clear-escort-on-death-world dead-unit))]
        {:world new-world
         :messages (turn-message-map message Long/MAX_VALUE)
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
          drowned-message (str (get-in result [:messages :turn-message])
                               " "
                               (:army-drowned config/messages))]
      (assoc result
             :world updated-world
             :visibility visibility
             :messages (turn-message-map drowned-message Long/MAX_VALUE)))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T13:30:14.939863-05:00", :module-hash "337160135", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 6, :hash "1999468528"} {:id "defn-/error-message-map", :kind "defn-", :line 8, :end-line 11, :hash "1979326496"} {:id "defn-/turn-message-map", :kind "defn-", :line 13, :end-line 18, :hash "1534129019"} {:id "defn/apply-combat-result!", :kind "defn", :line 20, :end-line 33, :hash "1493705292"} {:id "defn-/drown-excess-cargo-world", :kind "defn-", :line 35, :end-line 50, :hash "1757356590"} {:id "defn/format-combat-log", :kind "defn", :line 52, :end-line 54, :hash "-298786363"} {:id "defn/format-combat-status", :kind "defn", :line 56, :end-line 58, :hash "552980926"} {:id "defn/fight-round", :kind "defn", :line 60, :end-line 62, :hash "-400173913"} {:id "defn/resolve-combat", :kind "defn", :line 64, :end-line 66, :hash "-1610119610"} {:id "defn/dead-escort-destroyer?", :kind "defn", :line 68, :end-line 71, :hash "737415372"} {:id "defn/dead-escort-transport?", :kind "defn", :line 73, :end-line 76, :hash "1031563454"} {:id "defn-/find-units-where", :kind "defn-", :line 78, :end-line 84, :hash "-81025216"} {:id "defn-/clear-dead-escort-from-carrier", :kind "defn-", :line 86, :end-line 98, :hash "1447648774"} {:id "defn-/release-carrier-escorts", :kind "defn-", :line 100, :end-line 108, :hash "897637745"} {:id "defn-/clear-carrier-group-on-death", :kind "defn-", :line 110, :end-line 121, :hash "-1988931291"} {:id "defn-/clear-destroyer-escort", :kind "defn-", :line 123, :end-line 131, :hash "-178586945"} {:id "defn-/clear-transport-escort", :kind "defn-", :line 133, :end-line 143, :hash "-687114603"} {:id "defn-/clear-escort-on-death-world", :kind "defn-", :line 145, :end-line 151, :hash "299233813"} {:id "defn/clear-escort-on-death", :kind "defn", :line 153, :end-line 156, :hash "-2078012011"} {:id "defn/conquer-city-contents-pure", :kind "defn", :line 158, :end-line 161, :hash "1854750815"} {:id "defn/conquer-city-contents", :kind "defn", :line 163, :end-line 168, :hash "308072096"} {:id "defn/hostile-city?", :kind "defn", :line 170, :end-line 172, :hash "1158340970"} {:id "defn-/has-city?", :kind "defn-", :line 174, :end-line 181, :hash "-293485877"} {:id "defn-/city-elimination-game-over-updates", :kind "defn-", :line 183, :end-line 190, :hash "1302793836"} {:id "defn/attempt-city-conquest", :kind "defn", :line 192, :end-line 217, :hash "224169094"} {:id "defn/attempt-conquest", :kind "defn", :line 219, :end-line 225, :hash "-614548960"} {:id "defn/attempt-fighter-overfly", :kind "defn", :line 227, :end-line 235, :hash "1396203059"} {:id "defn/hostile-unit?", :kind "defn", :line 237, :end-line 239, :hash "1440002517"} {:id "defn/attempt-attack", :kind "defn", :line 241, :end-line 265, :hash "-771057316"} {:id "defn/attempt-coastal-army-attack", :kind "defn", :line 267, :end-line 282, :hash "736294134"}]}
;; clj-mutate-manifest-end

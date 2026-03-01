;; mutation-tested: no
(ns empire.combat.escorts)

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

(defn- clear-dead-escort-from-carrier!
  [{:keys [current-world update-game-map!]} dead-unit]
  (let [carrier-id (:escort-carrier-id dead-unit)
        escort-id (:escort-id dead-unit)
        coords (find-units-where
                (current-world)
                #(and (= :carrier (:type %))
                      (= carrier-id (:carrier-id %))))]
    (doseq [pos coords]
      (case (:type dead-unit)
        :battleship
        (update-game-map! assoc-in (conj pos :contents :group-battleship-id) nil)
        :submarine
        (update-game-map! update-in (conj pos :contents :group-submarine-ids)
                          (fn [ids] (vec (remove #{escort-id} ids))))))))

(defn- release-carrier-escorts!
  [{:keys [current-world update-game-map!]} dead-unit]
  (let [carrier-id (:carrier-id dead-unit)
        coords (find-units-where
                (current-world)
                #(= carrier-id (:escort-carrier-id %)))]
    (doseq [pos coords]
      (update-game-map! update-in (conj pos :contents)
                        #(-> % (assoc :escort-mode :seeking)
                             (dissoc :escort-carrier-id :orbit-angle))))))

(defn- clear-carrier-group-on-death!
  [ctx dead-unit]
  (cond
    (and (#{:battleship :submarine} (:type dead-unit))
         (:escort-carrier-id dead-unit))
    (clear-dead-escort-from-carrier! ctx dead-unit)

    (and (= :carrier (:type dead-unit))
         (:carrier-id dead-unit))
    (release-carrier-escorts! ctx dead-unit)))

(defn- clear-destroyer-escort!
  [{:keys [current-world update-game-map!]} dead-unit]
  (let [tid (:escort-transport-id dead-unit)
        coords (find-units-where
                (current-world)
                #(and (= :transport (:type %))
                      (= tid (:transport-id %))))]
    (doseq [pos coords]
      (update-game-map! update-in (conj pos :contents) dissoc :escort-destroyer-id))))

(defn- clear-transport-escort!
  [{:keys [current-world update-game-map!]} dead-unit]
  (let [did (:escort-destroyer-id dead-unit)
        coords (find-units-where
                (current-world)
                #(and (= :destroyer (:type %))
                      (= did (:destroyer-id %))))]
    (doseq [pos coords]
      (update-game-map! update-in (conj pos :contents)
                        #(-> % (assoc :escort-mode :seeking)
                             (dissoc :escort-transport-id))))))

(defn clear-escort-on-death!
  [ctx dead-unit]
  (cond
    (dead-escort-destroyer? dead-unit) (clear-destroyer-escort! ctx dead-unit)
    (dead-escort-transport? dead-unit) (clear-transport-escort! ctx dead-unit))
  (clear-carrier-group-on-death! ctx dead-unit))

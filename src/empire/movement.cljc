(ns empire.movement
  (:require [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.unit-container :as uc]))

(defn is-players?
  "Returns true if the cell is owned by the player."
  [cell]
  (or (= (:city-status cell) :player)
      (= (:owner (:contents cell)) :player)))

(defn is-computers?
  "Returns true if the cell is owned by the computer."
  [cell]
  (or (= (:city-status cell) :computer)
      (= (:owner (:contents cell)) :computer)))

(defn next-step-pos [pos target]
  (let [[x y] pos
        [tx ty] target
        dx (cond (zero? (- tx x)) 0
                 (pos? (- tx x)) 1
                 :else -1)
        dy (cond (zero? (- ty y)) 0
                 (pos? (- ty y)) 1
                 :else -1)]
    [(+ x dx) (+ y dy)]))

(defn update-combatant-map
  "Updates a combatant's visible map by revealing cells near their owned units."
  [visible-map-atom owner]
  (when @visible-map-atom
    (let [ownership-predicate (if (= owner :player) is-players? is-computers?)
          height (count @atoms/game-map)
          width (count (first @atoms/game-map))]
      (doseq [i (range height)
              j (range width)
              :when (ownership-predicate (get-in @atoms/game-map [i j]))]
        (doseq [di [-1 0 1]
                dj [-1 0 1]]
          (let [ni (max 0 (min (dec height) (+ i di)))
                nj (max 0 (min (dec width) (+ j dj)))]
            (swap! visible-map-atom assoc-in [ni nj] (get-in @atoms/game-map [ni nj]))))))))

(defn update-cell-visibility [pos owner]
  "Updates visibility around a specific cell for the given owner."
  (let [visible-map-atom (if (= owner :player) atoms/player-map atoms/computer-map)
        [x y] pos]
    (when @visible-map-atom
      (let [height (count @atoms/game-map)
            width (count (first @atoms/game-map))]
        (doseq [di [-1 0 1]
                dj [-1 0 1]]
          (let [ni (max 0 (min (dec height) (+ x di)))
                nj (max 0 (min (dec width) (+ y dj)))
                game-cell (get-in @atoms/game-map [ni nj])]
            (swap! visible-map-atom assoc-in [ni nj] game-cell)))))))

(defn wake-before-move [unit next-cell]
  (let [next-contents (:contents next-cell)
        fighter-landing-carrier? (and (= (:type unit) :fighter)
                                      (= (:type next-contents) :carrier)
                                      (= (:owner next-contents) (:owner unit))
                                      (not (uc/full? next-contents :fighter-count config/carrier-capacity)))]
    (cond
      (and (:contents next-cell) (not fighter-landing-carrier?))
      [(assoc (dissoc (assoc unit :mode :awake) :target) :reason :somethings-in-the-way) true]

      (and (= (:type unit) :army) (= (:type next-cell) :sea))
      [(assoc (dissoc (assoc unit :mode :awake) :target) :reason :cant-move-into-water) true]

      (and (= (:type unit) :army) (= (:type next-cell) :city) (= (:city-status next-cell) :player))
      [(assoc (dissoc (assoc unit :mode :awake) :target) :reason :cant-move-into-city) true]

      (and (= (:type unit) :fighter)
           (= (:type next-cell) :city)
           (config/hostile-city? (:city-status next-cell)))
      [(assoc (dissoc (assoc unit :mode :awake) :target) :reason :fighter-over-defended-city) true]

      (and (config/naval-unit? (:type unit)) (= (:type next-cell) :land))
      [(assoc (dissoc (assoc unit :mode :awake) :target) :reason :ships-cant-drive-on-land) true]

      :else [unit false])))

(defn near-hostile-city? [pos current-map]
  (some (fn [[di dj]]
          (let [ni (+ (first pos) di)
                nj (+ (second pos) dj)
                adj-cell (get-in @current-map [ni nj])]
            (and adj-cell
                 (= (:type adj-cell) :city)
                 (config/hostile-city? (:city-status adj-cell)))))
        (for [di [-1 0 1] dj [-1 0 1]] [di dj])))

(defn friendly-city-in-range? [pos max-dist current-map]
  (let [[px py] pos
        height (count @current-map)
        width (count (first @current-map))]
    (some (fn [[i j]]
            (let [cell (get-in @current-map [i j])]
              (and (= (:type cell) :city)
                   (= (:city-status cell) :player)
                   (<= (max (abs (- i px)) (abs (- j py))) max-dist))))
          (for [i (range height) j (range width)] [i j]))))

(defn adjacent-to-land?
  "Returns true if the position is adjacent to a land cell."
  [pos current-map]
  (let [[x y] pos
        height (count @current-map)
        width (count (first @current-map))]
    (some (fn [[dx dy]]
            (let [nx (+ x dx)
                  ny (+ y dy)]
              (and (>= nx 0) (< nx height)
                   (>= ny 0) (< ny width)
                   (= :land (:type (get-in @current-map [nx ny]))))))
          [[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]])))

(defn wake-after-move [unit final-pos current-map]
  (let [is-at-target? (= final-pos (:target unit))
        dest-cell (get-in @current-map final-pos)
        [unit-wakes?
         reason refuel?
         shot-down?] (case (:type unit)
                       :army [(near-hostile-city? final-pos current-map) :army-found-city false false]
                       :fighter (let [entering-city? (= (:type dest-cell) :city)
                                      friendly-city? (= (:city-status dest-cell) :player)
                                      hostile-city? (and entering-city? (not friendly-city?))
                                      fuel (:fuel unit config/fighter-fuel)
                                      low-fuel? (<= fuel 1)
                                      bingo-fuel? (and (<= fuel (quot config/fighter-fuel 4))
                                                       (friendly-city-in-range? final-pos fuel current-map))]
                                  (cond
                                    hostile-city? [true :fighter-shot-down false true]
                                    entering-city? [true :fighter-landed-and-refueled true false]
                                    low-fuel? [true :fighter-out-of-fuel false false]
                                    bingo-fuel? [true :fighter-bingo false false]
                                    :else [false nil false false]))
                       :transport (let [has-armies? (pos? (:army-count unit 0))
                                        at-beach? (adjacent-to-land? final-pos current-map)]
                                    [(and has-armies? at-beach?) :transport-at-beach false false])
                       [false nil false false])
        wake-up? (or is-at-target? unit-wakes?)]
    (when shot-down?
      (reset! atoms/line3-message (:fighter-destroyed-by-city config/messages)))
    (if wake-up?
      (dissoc (cond-> (assoc unit :mode :awake)
                      (and unit-wakes? reason) (assoc :reason reason)
                      refuel? (assoc :fuel config/fighter-fuel)
                      shot-down? (assoc :hits 0 :steps-remaining 0)) :target)
      unit)))

(defn process-consumables [unit to-cell]
  (if (and unit (= (:type unit) :fighter))
    (if (= (:type to-cell) :city)
      unit
      (let [current-fuel (:fuel unit config/fighter-fuel)
            new-fuel (dec current-fuel)]
        (if (<= new-fuel -1)
          nil
          (assoc unit :fuel new-fuel))))
    unit))

(defn load-adjacent-sentry-armies
  "Loads adjacent sentry armies onto a transport at the given coords.
   Wakes up the transport if it has armies and is at a beach."
  [transport-coords]
  (let [cell (get-in @atoms/game-map transport-coords)
        unit (:contents cell)]
    (when (and (= (:type unit) :transport)
               (not (uc/full? unit :army-count config/transport-capacity)))
      (let [[tx ty] transport-coords
            height (count @atoms/game-map)
            width (count (first @atoms/game-map))]
        (doseq [[dx dy] [[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]]]
          (let [nx (+ tx dx)
                ny (+ ty dy)]
            (when (and (>= nx 0) (< nx height) (>= ny 0) (< ny width))
              (let [adj-cell (get-in @atoms/game-map [nx ny])
                    adj-unit (:contents adj-cell)
                    transport (get-in @atoms/game-map (conj transport-coords :contents))]
                (when (and adj-unit
                           (= (:type adj-unit) :army)
                           (= (:mode adj-unit) :sentry)
                           (= (:owner adj-unit) (:owner transport))
                           (not (uc/full? transport :army-count config/transport-capacity)))
                  (swap! atoms/game-map assoc-in [nx ny] (dissoc adj-cell :contents))
                  (swap! atoms/game-map update-in (conj transport-coords :contents) uc/add-unit :army-count))))))
        ;; After loading, wake transport if at beach with armies
        (let [updated-transport (get-in @atoms/game-map (conj transport-coords :contents))
              has-armies? (pos? (uc/get-count updated-transport :army-count))
              at-beach? (adjacent-to-land? transport-coords atoms/game-map)]
          (when (and has-armies? at-beach? (= (:mode updated-transport) :sentry))
            (swap! atoms/game-map update-in (conj transport-coords :contents)
                   #(assoc % :mode :awake :reason :transport-at-beach))))))))

(defn do-move [from-coords final-pos cell final-unit]
  (let [from-cell (dissoc cell :contents)
        to-cell (get-in @atoms/game-map final-pos)
        processed-unit (process-consumables final-unit to-cell)
        to-contents (:contents to-cell)
        fighter-landing-city? (and processed-unit
                                   (= (:type processed-unit) :fighter)
                                   (= (:type to-cell) :city)
                                   (= (:city-status to-cell) :player))
        fighter-at-target? (and fighter-landing-city?
                                (= (:target final-unit) final-pos))
        fighter-landing-carrier? (and processed-unit
                                      (= (:type processed-unit) :fighter)
                                      (= (:type to-contents) :carrier)
                                      (= (:owner to-contents) (:owner processed-unit))
                                      (not (uc/full? to-contents :fighter-count config/carrier-capacity)))
        updated-to-cell (cond
                          (nil? processed-unit) (dissoc to-cell :contents)
                          fighter-at-target? (-> to-cell
                                                 (uc/add-unit :fighter-count)
                                                 (update :sleeping-fighters (fnil inc 0)))
                          fighter-landing-city? (-> to-cell
                                                    (uc/add-unit :fighter-count)
                                                    (update :resting-fighters (fnil inc 0)))
                          fighter-landing-carrier? (update to-cell :contents uc/add-unit :fighter-count)
                          :else (assoc to-cell :contents processed-unit))]
    (swap! atoms/game-map assoc-in from-coords from-cell)
    (swap! atoms/game-map assoc-in final-pos updated-to-cell)
    (update-cell-visibility final-pos (:owner (:contents cell)))
    (when (= (:type processed-unit) :transport)
      (load-adjacent-sentry-armies final-pos))))

(defn move-unit [from-coords target-coords cell current-map]
  (let [unit (:contents cell)
        next-pos (next-step-pos from-coords target-coords)
        next-cell (get-in @current-map next-pos)
        [unit woke?] (wake-before-move unit next-cell)]
    (if woke?
      (let [updated-cell (assoc cell :contents unit)]
        (swap! atoms/game-map assoc-in from-coords updated-cell)
        (update-cell-visibility from-coords (:owner unit)))
      ;; Normal move
      (let [final-unit (wake-after-move unit next-pos current-map)]
        (do-move from-coords next-pos cell final-unit)))))

(defn set-unit-movement [unit-coords target-coords]
  (let [first-cell (get-in @atoms/game-map unit-coords)
        unit (:contents first-cell)
        updated-contents (-> unit
                             (assoc :mode :moving :target target-coords)
                             (dissoc :reason))]
    (swap! atoms/game-map assoc-in unit-coords (assoc first-cell :contents updated-contents))))

(defn get-active-unit
  "Returns the unit currently needing attention: awake army aboard transport, awake fighter on carrier,
   then awake contents, then awake airport fighter.
   For armies aboard transport, returns a synthetic army map with :aboard-transport true.
   For fighters on carrier, returns a synthetic fighter map with :from-carrier true.
   For fighters in airport, returns a synthetic fighter map with :from-airport true."
  [cell]
  (let [contents (:contents cell)
        has-awake-army-aboard? (and (= (:type contents) :transport)
                                    (uc/has-awake? contents :awake-armies))
        has-awake-carrier-fighter? (and (= (:type contents) :carrier)
                                        (uc/has-awake? contents :awake-fighters))
        has-awake-airport-fighter? (uc/has-awake? cell :awake-fighters)]
    (cond
      has-awake-army-aboard? {:type :army :mode :awake :owner (:owner contents) :aboard-transport true}
      has-awake-carrier-fighter? {:type :fighter :mode :awake :owner (:owner contents) :fuel config/fighter-fuel :from-carrier true}
      (and contents (= (:mode contents) :awake)) contents
      has-awake-airport-fighter? {:type :fighter :mode :awake :owner :player :fuel config/fighter-fuel :from-airport true}
      :else nil)))

(defn is-army-aboard-transport?
  "Returns true if the active unit is an army aboard a transport."
  [cell active-unit]
  (and active-unit
       (:aboard-transport active-unit)))

(defn is-fighter-from-airport?
  "Returns true if the active unit is a fighter from the airport."
  [cell active-unit]
  (and active-unit
       (:from-airport active-unit)))

(defn is-fighter-from-carrier?
  "Returns true if the active unit is a fighter from a carrier."
  [cell active-unit]
  (and active-unit
       (:from-carrier active-unit)))

(defn launch-fighter-from-airport
  "Removes first awake fighter from airport and sets it moving to target.
   Returns the coordinates where the fighter was placed."
  [city-coords target-coords]
  (let [cell (get-in @atoms/game-map city-coords)
        after-remove (uc/remove-awake-unit cell :fighter-count :awake-fighters)
        moving-fighter {:type :fighter :mode :moving :owner :player :fuel config/fighter-fuel :target target-coords :hits 1
                        :steps-remaining (config/unit-speed :fighter)}
        updated-cell (assoc after-remove :contents moving-fighter)]
    (swap! atoms/game-map assoc-in city-coords updated-cell)
    city-coords))

(defn wake-armies-on-transport
  "Wakes up all armies aboard the transport at the given coords."
  [transport-coords]
  (let [cell (get-in @atoms/game-map transport-coords)
        transport (:contents cell)
        updated-transport (-> transport
                              (uc/wake-all :army-count :awake-armies)
                              (assoc :mode :sentry)
                              (dissoc :reason))
        updated-cell (assoc cell :contents updated-transport)]
    (swap! atoms/game-map assoc-in transport-coords updated-cell)))

(defn sleep-armies-on-transport
  "Puts all armies aboard the transport back to sleep (sentry mode).
   Wakes up the transport so it can receive orders."
  [transport-coords]
  (let [cell (get-in @atoms/game-map transport-coords)
        transport (:contents cell)
        updated-transport (-> transport
                              (uc/sleep-all :awake-armies)
                              (assoc :mode :awake)
                              (dissoc :reason))
        updated-cell (assoc cell :contents updated-transport)]
    (swap! atoms/game-map assoc-in transport-coords updated-cell)))

(defn disembark-army-from-transport
  "Removes first awake army from transport and places it on target land cell.
   Army remains awake and ready for orders. Other armies remain on transport.
   Wakes the transport when no more awake armies remain.
   Returns the coordinates where the army was placed."
  [transport-coords target-coords]
  (let [cell (get-in @atoms/game-map transport-coords)
        transport (:contents cell)
        after-remove (uc/remove-awake-unit transport :army-count :awake-armies)
        no-more-awake? (not (uc/has-awake? after-remove :awake-armies))
        disembarked-army {:type :army :mode :awake :owner (:owner transport) :hits 1
                         :steps-remaining (config/unit-speed :army)}
        updated-transport (cond-> after-remove
                            no-more-awake? (assoc :mode :awake)
                            no-more-awake? (dissoc :reason))
        updated-cell (assoc cell :contents updated-transport)]
    (swap! atoms/game-map assoc-in transport-coords updated-cell)
    (swap! atoms/game-map assoc-in (conj target-coords :contents) disembarked-army)
    (update-cell-visibility target-coords (:owner transport))
    target-coords))

(defn disembark-army-with-target
  "Removes first awake army from transport and places it on adjacent cell in moving mode.
   Army will continue moving toward the extended target on subsequent turns.
   Steps-remaining is 0 because the disembark used the army's one step."
  [transport-coords adjacent-coords extended-target]
  (let [cell (get-in @atoms/game-map transport-coords)
        transport (:contents cell)
        after-remove (uc/remove-awake-unit transport :army-count :awake-armies)
        no-more-awake? (not (uc/has-awake? after-remove :awake-armies))
        moving-army {:type :army :mode :moving :owner (:owner transport) :hits 1
                     :steps-remaining 0
                     :target extended-target}
        updated-transport (cond-> after-remove
                            no-more-awake? (assoc :mode :awake)
                            no-more-awake? (dissoc :reason))
        updated-cell (assoc cell :contents updated-transport)]
    (swap! atoms/game-map assoc-in transport-coords updated-cell)
    (swap! atoms/game-map assoc-in (conj adjacent-coords :contents) moving-army)
    (update-cell-visibility adjacent-coords (:owner transport))))

(defn disembark-army-to-explore
  "Removes first awake army from transport and places it on target land cell in explore mode.
   Returns the coordinates where the army was placed."
  [transport-coords target-coords]
  (let [cell (get-in @atoms/game-map transport-coords)
        transport (:contents cell)
        after-remove (uc/remove-awake-unit transport :army-count :awake-armies)
        no-more-awake? (not (uc/has-awake? after-remove :awake-armies))
        exploring-army {:type :army :mode :explore :owner (:owner transport) :hits 1
                        :steps-remaining (config/unit-speed :army)
                        :explore-steps config/explore-steps
                        :visited #{target-coords}}
        updated-transport (cond-> after-remove
                            no-more-awake? (assoc :mode :awake)
                            no-more-awake? (dissoc :reason))
        updated-cell (assoc cell :contents updated-transport)]
    (swap! atoms/game-map assoc-in transport-coords updated-cell)
    (swap! atoms/game-map assoc-in (conj target-coords :contents) exploring-army)
    (update-cell-visibility target-coords (:owner transport))
    target-coords))

(defn wake-fighters-on-carrier
  "Wakes up all fighters aboard the carrier at the given coords."
  [carrier-coords]
  (let [cell (get-in @atoms/game-map carrier-coords)
        carrier (:contents cell)
        updated-carrier (-> carrier
                            (uc/wake-all :fighter-count :awake-fighters)
                            (assoc :mode :sentry)
                            (dissoc :reason))
        updated-cell (assoc cell :contents updated-carrier)]
    (swap! atoms/game-map assoc-in carrier-coords updated-cell)))

(defn sleep-fighters-on-carrier
  "Puts all fighters aboard the carrier back to sleep.
   Wakes up the carrier so it can receive orders."
  [carrier-coords]
  (let [cell (get-in @atoms/game-map carrier-coords)
        carrier (:contents cell)
        updated-carrier (-> carrier
                            (uc/sleep-all :awake-fighters)
                            (assoc :mode :awake)
                            (dissoc :reason))
        updated-cell (assoc cell :contents updated-carrier)]
    (swap! atoms/game-map assoc-in carrier-coords updated-cell)))

(defn launch-fighter-from-carrier
  "Removes first awake fighter from carrier and sets it moving to target.
   Fighter is placed at the adjacent cell toward target.
   Wakes the carrier when no more awake fighters remain.
   Returns the coordinates where the fighter was placed."
  [carrier-coords target-coords]
  (let [cell (get-in @atoms/game-map carrier-coords)
        carrier (:contents cell)
        after-remove (uc/remove-awake-unit carrier :fighter-count :awake-fighters)
        no-more-awake? (not (uc/has-awake? after-remove :awake-fighters))
        ;; Calculate first step toward target
        [cx cy] carrier-coords
        [tx ty] target-coords
        dx (cond (zero? (- tx cx)) 0 (pos? (- tx cx)) 1 :else -1)
        dy (cond (zero? (- ty cy)) 0 (pos? (- ty cy)) 1 :else -1)
        first-step [(+ cx dx) (+ cy dy)]
        moving-fighter {:type :fighter :mode :moving :owner (:owner carrier) :fuel config/fighter-fuel :target target-coords :hits 1
                        :steps-remaining (config/unit-speed :fighter)}
        updated-carrier (cond-> after-remove
                          no-more-awake? (assoc :mode :awake)
                          no-more-awake? (dissoc :reason))
        updated-cell (assoc cell :contents updated-carrier)
        target-cell (get-in @atoms/game-map first-step)]
    ;; Update carrier
    (swap! atoms/game-map assoc-in carrier-coords updated-cell)
    ;; Place fighter at first step position
    (swap! atoms/game-map assoc-in first-step (assoc target-cell :contents moving-fighter))
    (update-cell-visibility first-step (:owner carrier))
    first-step))

(defn set-unit-mode [coords mode]
  (let [cell (get-in @atoms/game-map coords)
        unit (:contents cell)
        updated-unit (dissoc (assoc unit :mode mode) :reason)
        updated-cell (assoc cell :contents updated-unit)]
    (swap! atoms/game-map assoc-in coords updated-cell)))

(defn set-explore-mode [coords]
  (let [cell (get-in @atoms/game-map coords)
        unit (:contents cell)
        updated-unit (-> unit
                         (assoc :mode :explore
                                :explore-steps config/explore-steps
                                :visited #{coords})
                         (dissoc :reason :target))]
    (swap! atoms/game-map assoc-in coords (assoc cell :contents updated-unit))))

(defn valid-explore-cell?
  "Returns true if a cell is valid for army exploration (land, no city, no unit)."
  [cell]
  (and cell
       (= :land (:type cell))
       (nil? (:contents cell))))

(defn adjacent-to-sea?
  "Returns true if the position has an adjacent sea cell."
  [pos current-map]
  (let [[x y] pos
        height (count @current-map)
        width (count (first @current-map))]
    (some (fn [[dx dy]]
            (let [nx (+ x dx)
                  ny (+ y dy)]
              (and (>= nx 0) (< nx height)
                   (>= ny 0) (< ny width)
                   (= :sea (:type (get-in @current-map [nx ny]))))))
          [[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]])))

(defn get-valid-explore-moves
  "Returns list of valid adjacent positions for exploration."
  [pos current-map]
  (let [[x y] pos
        height (count @current-map)
        width (count (first @current-map))]
    (for [[dx dy] [[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]]
          :let [nx (+ x dx)
                ny (+ y dy)
                cell (when (and (>= nx 0) (< nx height)
                                (>= ny 0) (< ny width))
                       (get-in @current-map [nx ny]))]
          :when (valid-explore-cell? cell)]
      [nx ny])))

(defn adjacent-to-unexplored?
  "Returns true if the position has an adjacent unexplored cell."
  [pos]
  (let [[x y] pos
        height (count @atoms/player-map)
        width (count (first @atoms/player-map))]
    (some (fn [[dx dy]]
            (let [nx (+ x dx)
                  ny (+ y dy)]
              (and (>= nx 0) (< nx height)
                   (>= ny 0) (< ny width)
                   (nil? (get-in @atoms/player-map [nx ny])))))
          [[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]])))

(defn get-unexplored-explore-moves
  "Returns valid moves that are adjacent to unexplored cells."
  [pos current-map]
  (filter adjacent-to-unexplored?
          (get-valid-explore-moves pos current-map)))

(defn pick-explore-move
  "Picks the next explore move - prefers unexplored, then coast following, then random.
   Avoids visited cells unless no other option."
  [pos current-map visited]
  (let [all-moves (get-valid-explore-moves pos current-map)
        unvisited-moves (remove visited all-moves)
        unexplored-moves (filter adjacent-to-unexplored? unvisited-moves)
        on-coast? (adjacent-to-sea? pos current-map)
        coastal-moves (when on-coast? (filter #(adjacent-to-sea? % current-map) unvisited-moves))]
    (cond
      ;; Prefer moves towards unexplored areas
      (seq unexplored-moves)
      (rand-nth unexplored-moves)

      ;; On coast with coastal moves available - follow coast
      (seq coastal-moves)
      (rand-nth coastal-moves)

      ;; Unvisited random walk
      (seq unvisited-moves)
      (rand-nth unvisited-moves)

      ;; All visited - allow revisiting as last resort
      (seq all-moves)
      (rand-nth all-moves)

      ;; No valid moves - stuck
      :else nil)))

(defn move-explore-unit
  "Moves an exploring army one step. Returns new position or nil if done/stuck."
  [coords]
  (let [cell (get-in @atoms/game-map coords)
        unit (:contents cell)
        remaining-steps (dec (:explore-steps unit config/explore-steps))
        visited (or (:visited unit) #{})]
    (if (<= remaining-steps 0)
      ;; Wake up after 50 steps
      (do
        (swap! atoms/game-map assoc-in coords
               (assoc cell :contents (-> unit
                                         (assoc :mode :awake)
                                         (dissoc :explore-steps :visited))))
        nil)
      ;; Try to move (return nil to limit to one step per round)
      (if-let [next-pos (pick-explore-move coords atoms/game-map visited)]
        (let [next-cell (get-in @atoms/game-map next-pos)
              found-city? (near-hostile-city? next-pos atoms/game-map)
              moved-unit (if found-city?
                           (-> unit
                               (assoc :mode :awake :reason :army-found-city)
                               (dissoc :explore-steps :visited))
                           (-> unit
                               (assoc :explore-steps remaining-steps)
                               (assoc :visited (conj visited next-pos))))]
          (swap! atoms/game-map assoc-in coords (dissoc cell :contents))
          (swap! atoms/game-map assoc-in next-pos (assoc next-cell :contents moved-unit))
          (update-cell-visibility next-pos (:owner unit))
          nil)
        ;; Stuck - wake up
        (do
          (swap! atoms/game-map assoc-in coords
                 (assoc cell :contents (-> unit
                                           (assoc :mode :awake)
                                           (dissoc :explore-steps :visited))))
          nil)))))
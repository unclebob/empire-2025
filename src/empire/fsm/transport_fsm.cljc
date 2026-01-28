(ns empire.fsm.transport-fsm
  "Transport FSM - naval unit for invasion operations.
   Cycles through loading, sailing, unloading, and returning.
   When exploring, searches for new continents; when directed, sails to known target."
  (:require [empire.atoms :as atoms]
            [empire.movement.map-utils :as map-utils]))

;; --- Helper Functions ---

(defn- count-adjacent-land-cells
  "Count land cells adjacent to given sea position."
  [pos game-map]
  (count (map-utils/get-matching-neighbors pos game-map map-utils/neighbor-offsets
                                           #(= :land (:type %)))))

(defn- any-adjacent-land?
  "Returns true if any adjacent cell is land."
  [pos game-map]
  (pos? (count-adjacent-land-cells pos game-map)))

;; --- Guards ---

(defn at-home-landing?
  "Guard: Transport is at its designated home transport-landing (sea cell)."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        home-landing (get-in ctx [:entity :fsm-data :home-landing :transport-landing])]
    (= pos home-landing)))

(defn fully-loaded?
  "Guard: Transport has 6 armies aboard."
  [ctx]
  (let [cargo (get-in ctx [:entity :fsm-data :cargo])]
    (= 6 (count cargo))))

(defn fully-unloaded?
  "Guard: All armies have disembarked."
  [ctx]
  (let [cargo (get-in ctx [:entity :fsm-data :cargo])]
    (empty? cargo)))

(defn land-found?
  "Guard: While exploring, transport has detected land."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        game-map (:game-map ctx)]
    (any-adjacent-land? pos game-map)))

(defn landing-found?
  "Guard: While scouting, transport found a suitable transport-landing (3-7 adjacent land cells)."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        game-map (:game-map ctx)
        adjacent-land (count-adjacent-land-cells pos game-map)]
    (and (>= adjacent-land 3)
         (<= adjacent-land 7))))

(defn directed-target?
  "Guard: Transport has a directed target (not exploring)."
  [ctx]
  (= :directed (get-in ctx [:entity :fsm-data :target :type])))

(defn exploring-target?
  "Guard: Transport is in explore mode (no directed target)."
  [ctx]
  (= :exploring (get-in ctx [:entity :fsm-data :target :type])))

(defn- always [_ctx] true)

;; --- Actions ---

(defn arrive-landing-action
  "Action: Arrived at home transport-landing. Ready to load."
  [ctx]
  (let [lt-id (get-in ctx [:entity :fsm-data :lieutenant-id])
        transport-id (get-in ctx [:entity :fsm-data :transport-id])]
    {:events [{:type :transport-arrived
               :priority :normal
               :to lt-id
               :data {:transport-id transport-id}}]}))

(defn sail-to-landing-action
  "Action: Continue sailing toward home transport-landing."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        home-landing (get-in ctx [:entity :fsm-data :home-landing :transport-landing])]
    ;; Simple movement toward target - actual pathfinding would be more complex
    {:move-to home-landing}))

(defn depart-action
  "Action: Leave beach for target. Notify Lieutenant."
  [ctx]
  (let [target (get-in ctx [:entity :fsm-data :target])
        lt-id (get-in ctx [:entity :fsm-data :lieutenant-id])
        transport-id (get-in ctx [:entity :fsm-data :transport-id])
        cargo (get-in ctx [:entity :fsm-data :cargo])]
    {:events [{:type :transport-departed
               :priority :normal
               :to lt-id
               :data {:transport-id transport-id
                      :cargo-count (count cargo)
                      :target target}}]}))

(defn depart-exploring-action
  "Action: Depart for exploration (no specific target)."
  [ctx]
  (depart-action ctx))

(defn depart-sailing-action
  "Action: Depart for directed invasion target."
  [ctx]
  (depart-action ctx))

(defn wait-for-army-action
  "Action: Wait at transport-landing for armies to board."
  [_ctx]
  nil)

(defn explore-action
  "Action: Continue exploring away from home."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        home-landing (get-in ctx [:entity :fsm-data :home-landing :transport-landing])
        ;; Simple: move away from home
        [hr hc] home-landing
        [pr pc] pos
        dr (if (> pr hr) 1 (if (< pr hr) -1 0))
        dc (if (> pc hc) 1 (if (< pc hc) -1 0))
        next-pos [(+ pr dr) (+ pc dc)]]
    {:move-to next-pos}))

(defn begin-scout-action
  "Action: Land found, begin scouting coastline for suitable landing."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        lt-id (get-in ctx [:entity :fsm-data :lieutenant-id])
        transport-id (get-in ctx [:entity :fsm-data :transport-id])]
    {:scouting-data {:land-found pos :coastline-visited #{pos}}
     :events [{:type :continent-found
               :priority :high
               :to lt-id
               :data {:transport-id transport-id :coords pos}}]}))

(defn scout-coast-action
  "Action: Continue scouting coastline for transport-landing."
  [ctx]
  ;; Follow coastline looking for suitable landing
  (let [pos (get-in ctx [:entity :fsm-data :position])
        visited (get-in ctx [:entity :fsm-data :scouting-data :coastline-visited] #{})]
    {:scouting-data {:coastline-visited (conj visited pos)}}))

(defn begin-unload-action
  "Action: Arrived at beachhead. Spawn new Lieutenant if first time."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        spawned-lt (get-in ctx [:entity :fsm-data :spawned-lieutenant-id])
        lt-id (get-in ctx [:entity :fsm-data :lieutenant-id])
        transport-id (get-in ctx [:entity :fsm-data :transport-id])]
    (if spawned-lt
      ;; Already spawned Lieutenant, just set beachhead position
      {:beachhead-pos pos}
      ;; First time: spawn new Lieutenant
      (let [new-lt-id (keyword (str "lieutenant-" (gensym)))]
        {:spawned-lieutenant-id new-lt-id
         :beachhead-pos pos
         :events [{:type :lieutenant-spawned
                   :priority :high
                   :to lt-id
                   :data {:new-lieutenant-id new-lt-id
                          :beachhead-coords pos
                          :parent-lieutenant-id lt-id
                          :transport-id transport-id}}]}))))

(defn unload-army-action
  "Action: Unload one army onto beachhead."
  [ctx]
  (let [cargo (get-in ctx [:entity :fsm-data :cargo])
        army-id (first cargo)
        new-lt-id (get-in ctx [:entity :fsm-data :spawned-lieutenant-id])
        pos (get-in ctx [:entity :fsm-data :position])]
    {:cargo (vec (rest cargo))
     :unload-to-beach pos
     :events [{:type :assign-mission
               :priority :high
               :data {:unit-id army-id
                      :mission-type :disembark-and-rally
                      :new-lieutenant-id new-lt-id}}]}))

(defn depart-home-action
  "Action: All armies unloaded. Begin return journey."
  [ctx]
  (let [lt-id (get-in ctx [:entity :fsm-data :lieutenant-id])
        transport-id (get-in ctx [:entity :fsm-data :transport-id])
        pos (get-in ctx [:entity :fsm-data :position])]
    {:events [{:type :transport-unloaded
               :priority :normal
               :to lt-id
               :data {:transport-id transport-id
                      :beachhead-coords pos}}]}))

(defn sail-home-action
  "Action: Continue sailing home."
  [ctx]
  (let [home-landing (get-in ctx [:entity :fsm-data :home-landing :transport-landing])]
    {:move-to home-landing}))

(defn arrive-home-action
  "Action: Returned to home beach. Ready for next load."
  [ctx]
  (let [lt-id (get-in ctx [:entity :fsm-data :lieutenant-id])
        transport-id (get-in ctx [:entity :fsm-data :transport-id])]
    {:target {:type :exploring :coords nil}  ; Reset target for next cycle
     :events [{:type :transport-returned
               :priority :normal
               :to lt-id
               :data {:transport-id transport-id}}]}))

(defn sail-toward-target-action
  "Action: Continue sailing toward directed target."
  [ctx]
  (let [target-coords (get-in ctx [:entity :fsm-data :target :coords])]
    {:move-to target-coords}))

(defn at-destination-action
  "Action: Arrived at directed destination, begin unloading."
  [ctx]
  (begin-unload-action ctx))

(defn- at-destination?
  "Guard: Transport has reached directed destination."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        target-coords (get-in ctx [:entity :fsm-data :target :coords])]
    (and target-coords (= pos target-coords))))

;; --- FSM Definition ---

(defn- fully-loaded-and-directed?
  "Guard: Transport fully loaded with a directed target."
  [ctx]
  (and (fully-loaded? ctx) (directed-target? ctx)))

(defn- fully-loaded-and-exploring?
  "Guard: Transport fully loaded with exploring target."
  [ctx]
  (and (fully-loaded? ctx) (exploring-target? ctx)))

(def transport-fsm
  "FSM transitions for transport invasion cycle.
   Format: [current-state guard-fn new-state action-fn]"
  [;; Moving to landing
   [:moving-to-landing  at-home-landing?  :loading             arrive-landing-action]
   [:moving-to-landing  always            :moving-to-landing   sail-to-landing-action]

   ;; Loading at home beach - branch directly to sailing or exploring
   [:loading  fully-loaded-and-directed?   :sailing    depart-sailing-action]
   [:loading  fully-loaded-and-exploring?  :exploring  depart-exploring-action]
   [:loading  always                       :loading    wait-for-army-action]

   ;; Sailing to directed target
   [:sailing  at-destination?  :unloading  at-destination-action]
   [:sailing  always           :sailing    sail-toward-target-action]

   ;; Exploring for new continent
   [:exploring  land-found?  :scouting   begin-scout-action]
   [:exploring  always       :exploring  explore-action]

   ;; Scouting coastline for landing
   [:scouting  landing-found?  :unloading  begin-unload-action]
   [:scouting  always          :scouting   scout-coast-action]

   ;; Unloading at beachhead
   [:unloading  fully-unloaded?  :returning  depart-home-action]
   [:unloading  always           :unloading  unload-army-action]

   ;; Returning home
   [:returning  at-home-landing?  :loading    arrive-home-action]
   [:returning  always            :returning  sail-home-action]])

;; --- Cargo Management ---

(defn load-army
  "Add an army to the transport's cargo."
  [transport army-id]
  (update-in transport [:fsm-data :cargo] conj army-id))

(defn unload-army
  "Remove the first army from the transport's cargo."
  [transport]
  (update-in transport [:fsm-data :cargo] #(vec (rest %))))

;; --- Create Transport ---

(defn create-transport
  "Create a transport for invasion operations.
   transport-id - unique identifier
   production-city-pos - where transport was produced
   home-landing - {:transport-landing [r c] :beach [[r c] ...]}
   lieutenant-id - owning Lieutenant"
  [transport-id production-city-pos home-landing lieutenant-id]
  {:fsm transport-fsm
   :fsm-state :moving-to-landing
   :fsm-data {:transport-id transport-id
              :position production-city-pos
              :lieutenant-id lieutenant-id
              :home-landing home-landing
              :target {:type :exploring :coords nil}
              :cargo []
              :spawned-lieutenant-id nil
              :scouting-data nil}
   :event-queue []})

(ns empire.fsm.integration
  "Integration layer between FSM command hierarchy and game loop.
   Manages General lifecycle and coordinates FSM processing with game turns."
  (:require [empire.atoms :as atoms]
            [empire.fsm.general :as general]
            [empire.fsm.engine :as engine]))

(defn find-computer-cities
  "Returns a vector of all computer city coordinates."
  []
  (vec (for [i (range (count @atoms/game-map))
             j (range (count (first @atoms/game-map)))
             :let [cell (get-in @atoms/game-map [i j])]
             :when (and (= :city (:type cell))
                        (= :computer (:city-status cell)))]
         [i j])))

(defn initialize-general
  "Creates the Commanding General if computer has at least one city.
   Posts city-needs-orders event for each existing computer city."
  []
  (let [computer-cities (find-computer-cities)]
    (when (seq computer-cities)
      (let [gen (general/create-general)
            ;; Post city-needs-orders for each computer city
            gen-with-events (reduce (fn [g city-coords]
                                      (engine/post-event g {:type :city-needs-orders
                                                            :priority :high
                                                            :data {:coords city-coords}}))
                                    gen
                                    computer-cities)]
        (reset! atoms/commanding-general gen-with-events)))))

(defn process-general-turn
  "Processes one step of the General FSM.
   Called each computer turn to advance the command hierarchy."
  []
  (when @atoms/commanding-general
    (swap! atoms/commanding-general general/process-general)))

(defn notify-city-captured
  "Notifies the General that computer has captured a new city.
   Posts city-needs-orders event to the General's queue."
  [city-coords]
  (when @atoms/commanding-general
    (swap! atoms/commanding-general
           engine/post-event
           {:type :city-needs-orders
            :priority :high
            :data {:coords city-coords}})))

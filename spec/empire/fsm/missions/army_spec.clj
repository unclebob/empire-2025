(ns empire.fsm.missions.army-spec
  (:require [speclj.core :refer :all]
            [empire.fsm.missions.army :as army]
            [empire.fsm.engine :as engine]
            [empire.fsm.context :as context]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [reset-all-atoms! build-test-map]]))

(describe "Army Explorer Missions"

  (describe "create-coastline-explorer"
    (it "creates explorer in :exploring state"
      (let [explorer (army/create-coastline-explorer :lt-1 [5 5])]
        (should= :exploring (:fsm-state explorer))))

    (it "sets mission-type to :explore-coastline"
      (let [explorer (army/create-coastline-explorer :lt-1 [5 5])]
        (should= :explore-coastline (get-in explorer [:fsm-data :mission-type]))))

    (it "records start position"
      (let [explorer (army/create-coastline-explorer :lt-1 [5 5])]
        (should= [5 5] (get-in explorer [:fsm-data :start-pos]))))

    (it "stores lieutenant-id for reporting"
      (let [explorer (army/create-coastline-explorer :lt-1 [5 5])]
        (should= :lt-1 (get-in explorer [:fsm-data :lieutenant-id]))))

    (it "has FSM defined"
      (let [explorer (army/create-coastline-explorer :lt-1 [5 5])]
        (should-not-be-nil (:fsm explorer)))))

  (describe "create-interior-explorer"
    (it "creates explorer in :exploring state"
      (let [explorer (army/create-interior-explorer :lt-1 [5 5])]
        (should= :exploring (:fsm-state explorer))))

    (it "sets mission-type to :explore-interior"
      (let [explorer (army/create-interior-explorer :lt-1 [5 5])]
        (should= :explore-interior (get-in explorer [:fsm-data :mission-type]))))

    (it "records start position"
      (let [explorer (army/create-interior-explorer :lt-1 [5 5])]
        (should= [5 5] (get-in explorer [:fsm-data :start-pos]))))

    (it "has FSM defined"
      (let [explorer (army/create-interior-explorer :lt-1 [5 5])]
        (should-not-be-nil (:fsm explorer)))))

  (describe "coastline exploration"
    (before
      (reset-all-atoms!)
      ;; Larger map with interior land
      ;; ~~~~
      ;; ~###
      ;; ~###
      ;; ~##+
      (reset! atoms/game-map (build-test-map ["~~~~"
                                               "~###"
                                               "~###"
                                               "~##+"]))
      (reset! atoms/computer-map (build-test-map ["~~~~"
                                                   "~###"
                                                   "~###"
                                                   "~##."])))

    (it "detects when explorer is on coastline"
      (should (army/on-coastline? [1 1])))  ; land adjacent to sea

    (it "detects when explorer is not on coastline"
      (should-not (army/on-coastline? [2 2])))  ; interior land, not adjacent to sea

    (it "finds free city when adjacent"
      (let [explorer (-> (army/create-coastline-explorer :lt-1 [3 2])
                         (assoc-in [:fsm-data :current-pos] [3 2]))
            ctx (context/build-context explorer)]
        (should= [3 3] (army/find-adjacent-free-city ctx [3 2])))))

  (describe "interior exploration"
    (before
      (reset-all-atoms!)
      ;; Map with unexplored interior
      (reset! atoms/game-map (build-test-map ["####"
                                               "####"
                                               "####"
                                               "####"]))
      ;; Computer has only explored edges
      (reset! atoms/computer-map (build-test-map ["### "
                                                   "#   "
                                                   "#   "
                                                   "    "])))

    (it "finds direction toward unexplored territory"
      (let [explorer (army/create-interior-explorer :lt-1 [0 0])
            ctx (context/build-context explorer)]
        ;; Should find unexplored cells to the right or down
        (should-not-be-nil (army/find-unexplored-direction ctx [0 0]))))

    (it "returns nil when surrounded by explored territory"
      (reset! atoms/computer-map [[{:type :land} {:type :land}]
                                   [{:type :land} {:type :land}]])
      (let [explorer (army/create-interior-explorer :lt-1 [0 0])
            ctx (context/build-context explorer)]
        (should-be-nil (army/find-unexplored-direction ctx [0 0])))))

  (describe "event generation"
    (before (reset-all-atoms!))

    (it "creates free-city-found event with correct structure"
      (let [event (army/make-city-found-event [10 20] :army-1)]
        (should= :free-city-found (:type event))
        (should= :high (:priority event))
        (should= [10 20] (get-in event [:data :coords]))
        (should= :army-1 (:from event))))

    (it "creates coastline-mapped event with correct structure"
      (let [event (army/make-coastline-mapped-event [5 5] :army-1)]
        (should= :coastline-mapped (:type event))
        (should= :normal (:priority event))
        (should= [5 5] (get-in event [:data :coords]))
        (should= :army-1 (:from event)))))

  (describe "terminal conditions"
    (it "coastline explorer can reach terminal state"
      (let [explorer (-> (army/create-coastline-explorer :lt-1 [5 5])
                         (assoc :fsm-state [:terminal :circuit-complete]))]
        (should (engine/terminal? explorer))))

    (it "interior explorer can reach terminal state"
      (let [explorer (-> (army/create-interior-explorer :lt-1 [5 5])
                         (assoc :fsm-state [:terminal :no-unexplored]))]
        (should (engine/terminal? explorer))))))

(ns empire.computer.army-reporting-spec
  (:require [speclj.core :refer :all]
            [empire.computer.army :as army]
            [empire.fsm.integration :as integration]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [reset-all-atoms! build-test-map]]))

(describe "Army Event Reporting"

  (describe "detecting free cities"
    (before
      (reset-all-atoms!)
      (reset! atoms/game-map (build-test-map ["X##"
                                               "#+#"
                                               "###"]))
      (reset! atoms/computer-map [[{:type :city :city-status :computer} {:type :land} {:type :land}]
                                   [{:type :land} {:type :city :city-status :free} {:type :land}]
                                   [{:type :land} {:type :land} {:type :land}]])
      (swap! atoms/game-map assoc-in [1 0 :contents]
             {:type :army :owner :computer :hits 1 :mode :explore :explore-steps 50 :visited #{[1 0]}})
      (integration/initialize-general)
      (integration/process-general-turn))

    (it "reports free-city-found to Lieutenant when adjacent to free city"
      (army/process-army [1 0])
      (let [lt (first (:lieutenants @atoms/commanding-general))
            events (:event-queue lt)]
        (should (some (fn [e] (= :free-city-found (:type e))) events)))))

  (describe "detecting coastline"
    (before
      (reset-all-atoms!)
      (reset! atoms/game-map (build-test-map ["X~~~"
                                               "##~~"
                                               "###~"
                                               "####"]))
      (reset! atoms/computer-map [[{:type :city :city-status :computer} {:type :sea} {:type :sea} {:type :sea}]
                                   [{:type :land} {:type :land} {:type :sea} {:type :sea}]
                                   [{:type :land} {:type :land} {:type :land} {:type :sea}]
                                   [{:type :land} {:type :land} {:type :land} {:type :land}]])
      (swap! atoms/game-map assoc-in [2 2 :contents]
             {:type :army :owner :computer :hits 1 :mode :explore :explore-steps 50 :visited #{[2 2]}})
      (integration/initialize-general)
      (integration/process-general-turn))

    (it "reports coastline-mapped to Lieutenant when on valid beach"
      (army/process-army [2 2])
      (let [lt (first (:lieutenants @atoms/commanding-general))
            events (:event-queue lt)]
        (should (some (fn [e] (= :coastline-mapped (:type e))) events))))))

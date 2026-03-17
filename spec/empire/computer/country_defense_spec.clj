(ns empire.computer.country-defense-spec
  (:require [empire.computer.threat-response.country-defense :as sut]
            [speclj.core :refer :all]))

(describe "country-defense helpers"
  (it "groups visible player armies by country"
    (let [computer-map [[{:country-id 1
                          :contents {:owner :player :type :army}}
                         {:country-id 1
                          :contents {:owner :computer :type :army}}]
                        [{:country-id 2
                          :contents {:owner :player :type :army}}
                         {:contents {:owner :player :type :fighter}}]]]
      (should= {1 #{[0 0]}
                2 #{[1 0]}}
               (sut/player-armies-by-country computer-map))))

  (it "applies country-defense and snapshots prior threat state"
    (should= {:country-defense-active true
              :country-defense-prev-threat {:threat-mission :patrol
                                            :threat-center [9 9]
                                            :threat-radius 2
                                            :threat-rounds-left 3}
              :threat-mission :country-defense
              :threat-center [1 1]
              :threat-radius 5}
             (sut/apply-country-defense {:threat-mission :patrol
                                         :threat-center [9 9]
                                         :threat-radius 2
                                         :threat-rounds-left 3}
                                        [0 0]
                                        #{[1 1] [2 2]}
                                        5)))

  (it "leaves the unit unchanged when no country-defense target exists"
    (let [unit {:threat-mission :patrol}]
      (should= unit
               (sut/apply-country-defense unit [0 0] nil 4))))

  (it "restores previous threat fields and removes absent ones on clear"
    (should= {:threat-mission :patrol
              :threat-center [4 4]}
             (sut/clear-country-defense
              {:country-defense-active true
               :country-defense-prev-threat {:threat-mission :patrol
                                             :threat-center [4 4]
                                             :threat-radius :empire.computer.threat-response.country-defense/absent
                                             :threat-rounds-left :empire.computer.threat-response.country-defense/absent}
               :threat-mission :country-defense
               :threat-center [1 1]
               :threat-radius 9}))))

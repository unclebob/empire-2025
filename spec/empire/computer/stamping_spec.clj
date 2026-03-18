(ns empire.computer.stamping-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.stamping :as stamping]
            [empire.computer.production :as computer-production]
            [empire.config.core :as config]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-player-map! set-test-computer-map! set-test-world! update-test-world!]]))

(describe "stamp-computer-fields"
  (before (reset-all-atoms!))

  (context "satellite direction"
    (it "adds random direction to computer satellites"
      (let [unit {:type :satellite :owner :computer :hits 1 :mode :awake
                  :turns-remaining config/satellite-turns}
            cell {:type :city :city-status :computer}
            stamped (stamping/stamp-computer-fields unit cell)]
        (should-contain :direction stamped)
        (should-contain (:direction stamped)
                        #{[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]})))

    (it "does not add direction to player satellites"
      (let [unit {:type :satellite :owner :player :hits 1 :mode :awake
                  :turns-remaining config/satellite-turns}
            cell {:type :city :city-status :player}
            stamped (stamping/stamp-computer-fields unit cell)]
        (should-not-contain :direction stamped))))

  (context "transport fields"
    (it "assigns transport-mission and transport-id to computer transports"
      (test-utils/set-test-state! :next-transport-id 5)
      (let [unit {:type :transport :owner :computer :hits 3 :mode :awake}
            cell {:type :city :city-status :computer}
            stamped (stamping/stamp-computer-fields unit cell)]
        (should= :loading (:transport-mission stamped))
        (should= 5 (:transport-id stamped))
        (should= 0 (:army-count stamped))
        (should= 6 (test-utils/read-test-state :next-transport-id))))

    (it "does not assign transport fields to player transports"
      (test-utils/set-test-state! :next-transport-id 5)
      (let [unit {:type :transport :owner :player :hits 3 :mode :awake}
            cell {:type :city :city-status :player}
            stamped (stamping/stamp-computer-fields unit cell)]
        (should-not-contain :transport-mission stamped)
        (should-not-contain :stuck-since-round stamped)
        (should-not-contain :transport-id stamped)
        (should= 5 (test-utils/read-test-state :next-transport-id)))))

  (context "country-id"
    (it "assigns city country-id to computer armies"
      (let [unit {:type :army :owner :computer :hits 1 :mode :awake}
            cell {:type :city :city-status :computer :country-id 3}
            stamped (stamping/stamp-computer-fields unit cell)]
        (should= 3 (:country-id stamped))))

    (it "assigns city country-id to computer transports"
      (let [unit {:type :transport :owner :computer :hits 3 :mode :awake}
            cell {:type :city :city-status :computer :country-id 7}
            stamped (stamping/stamp-computer-fields unit cell)]
        (should= 7 (:country-id stamped))))

    (it "assigns city country-id to computer fighters"
      (let [unit {:type :fighter :owner :computer :hits 1 :mode :awake :fuel 32}
            cell {:type :city :city-status :computer :country-id 5}
            stamped (stamping/stamp-computer-fields unit cell)]
        (should= 5 (:country-id stamped))))

    (it "does not assign country-id when cell lacks it"
      (let [unit {:type :army :owner :computer :hits 1 :mode :awake}
            cell {:type :city :city-status :computer}
            stamped (stamping/stamp-computer-fields unit cell)]
        (should-not-contain :country-id stamped)))

    (it "assigns city country-id to computer patrol boats"
      (let [unit {:type :patrol-boat :owner :computer :hits 1 :mode :awake}
            cell {:type :city :city-status :computer :country-id 2}
            stamped (stamping/stamp-computer-fields unit cell)]
        (should= 2 (:country-id stamped))))

    (it "does not assign country-id to non-army/transport/fighter/patrol-boat types"
      (let [unit {:type :destroyer :owner :computer :hits 3 :mode :awake}
            cell {:type :city :city-status :computer :country-id 3}
            stamped (stamping/stamp-computer-fields unit cell)]
        (should-not-contain :country-id stamped))))

  (context "patrol fields"
    (it "stamps patrol-mode :crawling on computer patrol-boats from country cities"
      (let [unit {:type :patrol-boat :owner :computer :hits 1 :mode :awake}
            cell {:type :city :city-status :computer :country-id 2}
            stamped (stamping/stamp-computer-fields unit cell)]
        (should= :crawling (:patrol-mode stamped))))

    (it "does not stamp patrol fields on patrol-boats from non-country cities"
      (let [unit {:type :patrol-boat :owner :computer :hits 1 :mode :awake}
            cell {:type :city :city-status :computer}
            stamped (stamping/stamp-computer-fields unit cell)]
        (should-not-contain :patrol-mode stamped)))

    (it "does not stamp patrol fields on non-patrol-boat units"
      (let [unit {:type :destroyer :owner :computer :hits 3 :mode :awake}
            cell {:type :city :city-status :computer :country-id 2}
            stamped (stamping/stamp-computer-fields unit cell)]
        (should-not-contain :patrol-mode stamped))))

  (context "carrier fields"
    (it "stamps carrier fields on computer carriers"
      (test-utils/set-test-state! :next-carrier-id 3)
      (let [unit {:type :carrier :owner :computer :hits 8 :mode :awake}
            cell {:type :city :city-status :computer}
            stamped (stamping/stamp-computer-fields unit cell)]
        (should= :positioning (:carrier-mode stamped))
        (should= 3 (:carrier-id stamped))
        (should-be-nil (:group-battleship-id stamped))
        (should= [] (:group-submarine-ids stamped))
        (should= 4 (test-utils/read-test-state :next-carrier-id))))

    (it "does not stamp carrier fields on player carriers"
      (test-utils/set-test-state! :next-carrier-id 3)
      (let [unit {:type :carrier :owner :player :hits 8 :mode :awake}
            cell {:type :city :city-status :player}
            stamped (stamping/stamp-computer-fields unit cell)]
        (should-not-contain :carrier-mode stamped)
        (should= 3 (test-utils/read-test-state :next-carrier-id)))))

  (context "escort fields"
    (it "stamps escort fields on computer battleships"
      (test-utils/set-test-state! :next-escort-id 10)
      (let [unit {:type :battleship :owner :computer :hits 12 :mode :awake}
            cell {:type :city :city-status :computer}
            stamped (stamping/stamp-computer-fields unit cell)]
        (should= 10 (:escort-id stamped))
        (should= :seeking (:escort-mode stamped))
        (should= 11 (test-utils/read-test-state :next-escort-id))))

    (it "stamps escort fields on computer submarines"
      (test-utils/set-test-state! :next-escort-id 7)
      (let [unit {:type :submarine :owner :computer :hits 2 :mode :awake}
            cell {:type :city :city-status :computer}
            stamped (stamping/stamp-computer-fields unit cell)]
        (should= 7 (:escort-id stamped))
        (should= :seeking (:escort-mode stamped))
        (should= 8 (test-utils/read-test-state :next-escort-id))))

    (it "does not stamp escort fields on player battleships"
      (test-utils/set-test-state! :next-escort-id 10)
      (let [unit {:type :battleship :owner :player :hits 12 :mode :awake}
            cell {:type :city :city-status :player}
            stamped (stamping/stamp-computer-fields unit cell)]
        (should-not-contain :escort-id stamped)
        (should= 10 (test-utils/read-test-state :next-escort-id)))))

  (context "destroyer fields"
    (it "stamps destroyer-id and escort-mode on computer destroyers"
      (test-utils/set-test-state! :next-destroyer-id 4)
      (let [unit {:type :destroyer :owner :computer :hits 3 :mode :awake}
            cell {:type :city :city-status :computer}
            stamped (stamping/stamp-computer-fields unit cell)]
        (should= 4 (:destroyer-id stamped))
        (should= :seeking (:escort-mode stamped))
        (should= 5 (test-utils/read-test-state :next-destroyer-id))))

    (it "does not stamp destroyer fields on player destroyers"
      (test-utils/set-test-state! :next-destroyer-id 4)
      (let [unit {:type :destroyer :owner :player :hits 3 :mode :awake}
            cell {:type :city :city-status :player}
            stamped (stamping/stamp-computer-fields unit cell)]
        (should-not-contain :destroyer-id stamped)
        (should= 4 (test-utils/read-test-state :next-destroyer-id)))))

  (context "no-op for player units"
    (it "returns unit unchanged for player army"
      (let [unit {:type :army :owner :player :hits 1 :mode :awake}
            cell {:type :city :city-status :player}
            stamped (stamping/stamp-computer-fields unit cell)]
        (should= unit stamped)))))

(describe "apply-coast-walk-fields"
  (before (reset-all-atoms!))

  (it "uses the opening strategy coast-walk limit while coastal cells are unexplored"
    ;; Map with unexplored coastal cells
    (set-test-world! (build-test-map ["~###~"]))
    (doseq [col [1 2 3]]
      (update-test-world! assoc-in [col 0 :country-id] 1))
    (test-utils/set-test-state! :country-stats
                                {1 {:coastal-explored? false
                                    :coastal-city-positions #{[1 0]}}})
    (with-redefs [empire.config.ai/opening-exploration-profile
                  (constantly {:coast-walk-limit 3 :random-explore-chance 1/5})]
      (let [unit {:type :army :owner :computer :hits 1 :mode :awake}
            cell {:type :city :city-status :computer :country-id 1}]
        ;; First → clockwise
        (let [s (stamping/apply-coast-walk-fields unit :army cell [1 0])]
          (should= :coast-walk (:mode s))
          (should= :clockwise (:coast-direction s))
          (should= [1 0] (:coast-start s))
          (should= [[1 0]] (:coast-visited s)))
        ;; Second → counter-clockwise
        (let [s (stamping/apply-coast-walk-fields unit :army cell [1 0])]
          (should= :counter-clockwise (:coast-direction s)))
        ;; Third still gets coast-walk at the higher opening limit.
        (let [s (stamping/apply-coast-walk-fields unit :army cell [1 0])]
          (should= :coast-walk (:mode s)))
        ;; Fourth does not.
        (let [s (stamping/apply-coast-walk-fields unit :army cell [1 0])]
          (should= :awake (:mode s))))))

  (it "no coast-walk when all coastal cells explored"
    (set-test-world! (build-test-map ["~###~"]))
    (set-test-computer-map! (build-test-map ["~###~"]))
    (doseq [col [1 2 3]]
      (update-test-world! assoc-in [col 0 :country-id] 1))
    (let [unit {:type :army :owner :computer :hits 1 :mode :awake}
          cell {:type :city :city-status :computer :country-id 1}
          stamped (stamping/apply-coast-walk-fields unit :army cell [1 0])]
      (should= :awake (:mode stamped))
      (should-not-contain :coast-direction stamped)))

  (it "player army does not get coast-walk"
    (set-test-world! (build-test-map ["~###~"]))
    (set-test-computer-map! [[{:type :sea}] [nil] [nil] [nil] [{:type :sea}]])
    (doseq [col [1 2 3]]
      (update-test-world! assoc-in [col 0 :country-id] 1))
    (let [unit {:type :army :owner :player :hits 1 :mode :awake}
          cell {:type :city :city-status :player :country-id 1}
          stamped (stamping/apply-coast-walk-fields unit :army cell [1 0])]
      (should= :awake (:mode stamped))
      (should-not-contain :coast-direction stamped)))

  (it "non-army unit does not get coast-walk"
    (let [unit {:type :transport :owner :computer :hits 3 :mode :awake}
          cell {:type :city :city-status :computer :country-id 1}
          stamped (stamping/apply-coast-walk-fields unit :transport cell [1 0])]
      (should-not-contain :coast-direction stamped)))

  (it "army from city without country-id does not get coast-walk"
    (let [unit {:type :army :owner :computer :hits 1 :mode :awake}
          cell {:type :city :city-status :computer}
          stamped (stamping/apply-coast-walk-fields unit :army cell [1 0])]
      (should= :awake (:mode stamped))
      (should-not-contain :coast-direction stamped))))

(describe "apply-random-explore-fields"
  (before (reset-all-atoms!))

  (it "stamps random-explore on computer army when rand is below the opening profile chance"
    (with-redefs [empire.computer.early-game.strategy/opening-exploration-profile
                  (constantly {:coast-walk-limit 1 :random-explore-chance 1/5})
                  rand (constantly 0.0)
                  rand-nth (fn [v] (first v))]
      (let [unit {:type :army :owner :computer :hits 1 :mode :awake}
            cell {:type :city :city-status :computer :country-id 1}
            stamped (stamping/apply-random-explore-fields unit :army cell [1 0])]
        (should= :random-explore (:mode stamped))
        (should-contain :random-explore-direction stamped))))

  (it "does not stamp when rand is above the opening profile chance"
    (with-redefs [empire.computer.early-game.strategy/opening-exploration-profile
                  (constantly {:coast-walk-limit 1 :random-explore-chance 1/5})
                  rand (constantly 0.5)]
      (let [unit {:type :army :owner :computer :hits 1 :mode :awake}
            cell {:type :city :city-status :computer :country-id 1}
            stamped (stamping/apply-random-explore-fields unit :army cell [1 0])]
        (should= :awake (:mode stamped)))))

  (it "does not stamp on non-army item"
    (with-redefs [empire.computer.early-game.strategy/opening-exploration-profile
                  (constantly {:coast-walk-limit 1 :random-explore-chance 1/5})
                  rand (constantly 0.0)]
      (let [unit {:type :transport :owner :computer :hits 3 :mode :awake}
            cell {:type :city :city-status :computer :country-id 1}
            stamped (stamping/apply-random-explore-fields unit :transport cell [1 0])]
        (should-not-contain :random-explore-direction stamped))))

  (it "does not stamp on player army"
    (with-redefs [empire.computer.early-game.strategy/opening-exploration-profile
                  (constantly {:coast-walk-limit 1 :random-explore-chance 1/5})
                  rand (constantly 0.0)]
      (let [unit {:type :army :owner :player :hits 1 :mode :awake}
            cell {:type :city :city-status :player :country-id 1}
            stamped (stamping/apply-random-explore-fields unit :army cell [1 0])]
        (should-not-contain :random-explore-direction stamped))))

  (it "does not stamp on coast-walk army"
    (with-redefs [empire.computer.early-game.strategy/opening-exploration-profile
                  (constantly {:coast-walk-limit 1 :random-explore-chance 1/5})
                  rand (constantly 0.0)]
      (let [unit {:type :army :owner :computer :hits 1 :mode :coast-walk}
            cell {:type :city :city-status :computer :country-id 1}
            stamped (stamping/apply-random-explore-fields unit :army cell [1 0])]
        (should= :coast-walk (:mode stamped))))))

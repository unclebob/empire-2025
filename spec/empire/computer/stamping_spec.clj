(ns empire.computer.stamping-spec
  (:require [speclj.core :refer :all]
            [empire.computer.stamping :as stamping]
            [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.test-utils :refer [build-test-map reset-all-atoms!]]))

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
      (reset! atoms/next-transport-id 5)
      (let [unit {:type :transport :owner :computer :hits 3 :mode :awake}
            cell {:type :city :city-status :computer}
            stamped (stamping/stamp-computer-fields unit cell)]
        (should= :loading (:transport-mission stamped))
        (should= 5 (:transport-id stamped))
        (should= 0 (:army-count stamped))
        (should= 6 @atoms/next-transport-id)))

    (it "does not assign transport fields to player transports"
      (reset! atoms/next-transport-id 5)
      (let [unit {:type :transport :owner :player :hits 3 :mode :awake}
            cell {:type :city :city-status :player}
            stamped (stamping/stamp-computer-fields unit cell)]
        (should-not-contain :transport-mission stamped)
        (should-not-contain :stuck-since-round stamped)
        (should-not-contain :transport-id stamped)
        (should= 5 @atoms/next-transport-id))))

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

    (it "does not assign country-id to non-army/transport/fighter types"
      (let [unit {:type :destroyer :owner :computer :hits 3 :mode :awake}
            cell {:type :city :city-status :computer :country-id 3}
            stamped (stamping/stamp-computer-fields unit cell)]
        (should-not-contain :country-id stamped))))

  (context "patrol fields"
    (it "stamps patrol fields on computer patrol-boats from country cities"
      (let [unit {:type :patrol-boat :owner :computer :hits 1 :mode :awake}
            cell {:type :city :city-status :computer :country-id 2}
            stamped (stamping/stamp-computer-fields unit cell)]
        (should= 2 (:patrol-country-id stamped))
        (should= :clockwise (:patrol-direction stamped))
        (should= :homing (:patrol-mode stamped))))

    (it "stamps patrol-number incrementing per country"
      (reset! atoms/patrol-boats-produced {})
      (let [unit {:type :patrol-boat :owner :computer :hits 1 :mode :awake}
            cell {:type :city :city-status :computer :country-id 2}
            first-boat (stamping/stamp-computer-fields unit cell)
            second-boat (stamping/stamp-computer-fields unit cell)
            third-boat (stamping/stamp-computer-fields unit cell)]
        (should= 1 (:patrol-number first-boat))
        (should= 2 (:patrol-number second-boat))
        (should= 3 (:patrol-number third-boat))))

    (it "does not stamp patrol fields on patrol-boats from non-country cities"
      (let [unit {:type :patrol-boat :owner :computer :hits 1 :mode :awake}
            cell {:type :city :city-status :computer}
            stamped (stamping/stamp-computer-fields unit cell)]
        (should-not-contain :patrol-country-id stamped)))

    (it "does not stamp patrol fields on non-patrol-boat units"
      (let [unit {:type :destroyer :owner :computer :hits 3 :mode :awake}
            cell {:type :city :city-status :computer :country-id 2}
            stamped (stamping/stamp-computer-fields unit cell)]
        (should-not-contain :patrol-country-id stamped))))

  (context "carrier fields"
    (it "stamps carrier fields on computer carriers"
      (reset! atoms/next-carrier-id 3)
      (let [unit {:type :carrier :owner :computer :hits 8 :mode :awake}
            cell {:type :city :city-status :computer}
            stamped (stamping/stamp-computer-fields unit cell)]
        (should= :positioning (:carrier-mode stamped))
        (should= 3 (:carrier-id stamped))
        (should-be-nil (:group-battleship-id stamped))
        (should= [] (:group-submarine-ids stamped))
        (should= 4 @atoms/next-carrier-id)))

    (it "does not stamp carrier fields on player carriers"
      (reset! atoms/next-carrier-id 3)
      (let [unit {:type :carrier :owner :player :hits 8 :mode :awake}
            cell {:type :city :city-status :player}
            stamped (stamping/stamp-computer-fields unit cell)]
        (should-not-contain :carrier-mode stamped)
        (should= 3 @atoms/next-carrier-id))))

  (context "escort fields"
    (it "stamps escort fields on computer battleships"
      (reset! atoms/next-escort-id 10)
      (let [unit {:type :battleship :owner :computer :hits 12 :mode :awake}
            cell {:type :city :city-status :computer}
            stamped (stamping/stamp-computer-fields unit cell)]
        (should= 10 (:escort-id stamped))
        (should= :seeking (:escort-mode stamped))
        (should= 11 @atoms/next-escort-id)))

    (it "stamps escort fields on computer submarines"
      (reset! atoms/next-escort-id 7)
      (let [unit {:type :submarine :owner :computer :hits 2 :mode :awake}
            cell {:type :city :city-status :computer}
            stamped (stamping/stamp-computer-fields unit cell)]
        (should= 7 (:escort-id stamped))
        (should= :seeking (:escort-mode stamped))
        (should= 8 @atoms/next-escort-id)))

    (it "does not stamp escort fields on player battleships"
      (reset! atoms/next-escort-id 10)
      (let [unit {:type :battleship :owner :player :hits 12 :mode :awake}
            cell {:type :city :city-status :player}
            stamped (stamping/stamp-computer-fields unit cell)]
        (should-not-contain :escort-id stamped)
        (should= 10 @atoms/next-escort-id))))

  (context "destroyer fields"
    (it "stamps destroyer-id and escort-mode on computer destroyers"
      (reset! atoms/next-destroyer-id 4)
      (let [unit {:type :destroyer :owner :computer :hits 3 :mode :awake}
            cell {:type :city :city-status :computer}
            stamped (stamping/stamp-computer-fields unit cell)]
        (should= 4 (:destroyer-id stamped))
        (should= :seeking (:escort-mode stamped))
        (should= 5 @atoms/next-destroyer-id)))

    (it "does not stamp destroyer fields on player destroyers"
      (reset! atoms/next-destroyer-id 4)
      (let [unit {:type :destroyer :owner :player :hits 3 :mode :awake}
            cell {:type :city :city-status :player}
            stamped (stamping/stamp-computer-fields unit cell)]
        (should-not-contain :destroyer-id stamped)
        (should= 4 @atoms/next-destroyer-id))))

  (context "no-op for player units"
    (it "returns unit unchanged for player army"
      (let [unit {:type :army :owner :player :hits 1 :mode :awake}
            cell {:type :city :city-status :player}
            stamped (stamping/stamp-computer-fields unit cell)]
        (should= unit stamped)))))

(describe "apply-coast-walk-fields"
  (before (reset-all-atoms!))

  (it "first 2 armies get coast-walk while coastal cells unexplored"
    ;; Map with unexplored coastal cells
    (reset! atoms/game-map (build-test-map ["~###~"]))
    (doseq [col [1 2 3]]
      (swap! atoms/game-map assoc-in [col 0 :country-id] 1))
    ;; Computer map: coastal cells unexplored
    (reset! atoms/computer-map [[{:type :sea}] [nil] [nil] [nil] [{:type :sea}]])
    (let [unit {:type :army :owner :computer :hits 1 :mode :awake}
          cell {:type :city :city-status :computer :country-id 1}]
      ;; First → clockwise
      (let [s (stamping/apply-coast-walk-fields unit :army cell [3 4])]
        (should= :coast-walk (:mode s))
        (should= :clockwise (:coast-direction s))
        (should= [3 4] (:coast-start s))
        (should= [[3 4]] (:coast-visited s)))
      ;; Second → counter-clockwise
      (let [s (stamping/apply-coast-walk-fields unit :army cell [3 5])]
        (should= :counter-clockwise (:coast-direction s)))
      ;; Third → no coast-walk (limit of 2 per country)
      (let [s (stamping/apply-coast-walk-fields unit :army cell [3 6])]
        (should= :awake (:mode s)))))

  (it "no coast-walk when all coastal cells explored"
    (reset! atoms/game-map (build-test-map ["~###~"]))
    (reset! atoms/computer-map (build-test-map ["~###~"]))
    (doseq [col [1 2 3]]
      (swap! atoms/game-map assoc-in [col 0 :country-id] 1))
    (let [unit {:type :army :owner :computer :hits 1 :mode :awake}
          cell {:type :city :city-status :computer :country-id 1}
          stamped (stamping/apply-coast-walk-fields unit :army cell [3 4])]
      (should= :awake (:mode stamped))
      (should-not-contain :coast-direction stamped)))

  (it "player army does not get coast-walk"
    (reset! atoms/game-map (build-test-map ["~###~"]))
    (reset! atoms/computer-map [[{:type :sea}] [nil] [nil] [nil] [{:type :sea}]])
    (doseq [col [1 2 3]]
      (swap! atoms/game-map assoc-in [col 0 :country-id] 1))
    (let [unit {:type :army :owner :player :hits 1 :mode :awake}
          cell {:type :city :city-status :player :country-id 1}
          stamped (stamping/apply-coast-walk-fields unit :army cell [3 4])]
      (should= :awake (:mode stamped))
      (should-not-contain :coast-direction stamped)))

  (it "non-army unit does not get coast-walk"
    (let [unit {:type :transport :owner :computer :hits 3 :mode :awake}
          cell {:type :city :city-status :computer :country-id 1}
          stamped (stamping/apply-coast-walk-fields unit :transport cell [3 4])]
      (should-not-contain :coast-direction stamped)))

  (it "army from city without country-id does not get coast-walk"
    (let [unit {:type :army :owner :computer :hits 1 :mode :awake}
          cell {:type :city :city-status :computer}
          stamped (stamping/apply-coast-walk-fields unit :army cell [3 4])]
      (should= :awake (:mode stamped))
      (should-not-contain :coast-direction stamped))))

(describe "apply-random-explore-fields"
  (before (reset-all-atoms!))

  (it "stamps random-explore on computer army when rand < 1/3"
    (with-redefs [rand (constantly 0.0)
                  rand-nth (fn [v] (first v))]
      (let [unit {:type :army :owner :computer :hits 1 :mode :awake}
            cell {:type :city :city-status :computer :country-id 1}
            stamped (stamping/apply-random-explore-fields unit :army cell)]
        (should= :random-explore (:mode stamped))
        (should-contain :random-explore-direction stamped))))

  (it "does not stamp when rand >= 1/3"
    (with-redefs [rand (constantly 0.5)]
      (let [unit {:type :army :owner :computer :hits 1 :mode :awake}
            cell {:type :city :city-status :computer :country-id 1}
            stamped (stamping/apply-random-explore-fields unit :army cell)]
        (should= :awake (:mode stamped)))))

  (it "does not stamp on non-army item"
    (with-redefs [rand (constantly 0.0)]
      (let [unit {:type :transport :owner :computer :hits 3 :mode :awake}
            cell {:type :city :city-status :computer :country-id 1}
            stamped (stamping/apply-random-explore-fields unit :transport cell)]
        (should-not-contain :random-explore-direction stamped))))

  (it "does not stamp on player army"
    (with-redefs [rand (constantly 0.0)]
      (let [unit {:type :army :owner :player :hits 1 :mode :awake}
            cell {:type :city :city-status :player :country-id 1}
            stamped (stamping/apply-random-explore-fields unit :army cell)]
        (should-not-contain :random-explore-direction stamped))))

  (it "does not stamp on coast-walk army"
    (with-redefs [rand (constantly 0.0)]
      (let [unit {:type :army :owner :computer :hits 1 :mode :coast-walk}
            cell {:type :city :city-status :computer :country-id 1}
            stamped (stamping/apply-random-explore-fields unit :army cell)]
        (should= :coast-walk (:mode stamped))))))

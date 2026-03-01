(ns empire.combat-carrier-group-spec
  (:require [speclj.core :refer :all]
            [empire.combat :as combat]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [build-test-map reset-all-atoms! set-test-world! update-test-world!]]))

(describe "clear-carrier-group-on-death"
  (before (reset-all-atoms!))

  (context "dead battleship"
    (it "clears group-battleship-id on the paired carrier"
      ;; Multiple cells: empty sea, carrier with matching id, carrier with wrong id,
      ;; non-carrier unit — exercises all :when sub-conditions
      (set-test-world! (build-test-map ["~~~~"]))
      (update-test-world! assoc-in [0 0 :contents]
                          {:type :carrier :owner :computer :hits 1
                           :carrier-id 42 :group-battleship-id 7})
      (update-test-world! assoc-in [1 0 :contents]
                          {:type :carrier :owner :computer :hits 1
                           :carrier-id 99 :group-battleship-id 7})
      (update-test-world! assoc-in [2 0 :contents]
                          {:type :destroyer :owner :computer :hits 3})
      ;; [3,0] is empty sea — nil unit
      (let [dead {:type :battleship :owner :computer
                  :escort-carrier-id 42 :escort-id 7}]
        (combat/clear-escort-on-death dead))
      ;; Matching carrier cleared
      (should-be-nil (get-in @atoms/game-map [0 0 :contents :group-battleship-id]))
      ;; Non-matching carrier untouched
      (should= 7 (get-in @atoms/game-map [1 0 :contents :group-battleship-id]))))

  (context "dead submarine"
    (it "removes escort-id from carrier's group-submarine-ids"
      (set-test-world! (build-test-map ["~~~~"]))
      (update-test-world! assoc-in [0 0 :contents]
                          {:type :carrier :owner :computer :hits 1
                           :carrier-id 10 :group-submarine-ids [3 5]})
      (update-test-world! assoc-in [1 0 :contents]
                          {:type :carrier :owner :computer :hits 1
                           :carrier-id 99 :group-submarine-ids [3]})
      (update-test-world! assoc-in [2 0 :contents]
                          {:type :destroyer :owner :computer :hits 3})
      (let [dead {:type :submarine :owner :computer
                  :escort-carrier-id 10 :escort-id 3}]
        (combat/clear-escort-on-death dead))
      (should= [5] (get-in @atoms/game-map [0 0 :contents :group-submarine-ids]))
      ;; Non-matching carrier untouched
      (should= [3] (get-in @atoms/game-map [1 0 :contents :group-submarine-ids])))

    (it "leaves empty vector when last submarine dies"
      (set-test-world! (build-test-map ["~"]))
      (update-test-world! assoc-in [0 0 :contents]
                          {:type :carrier :owner :computer :hits 1
                           :carrier-id 10 :group-submarine-ids [3]})
      (let [dead {:type :submarine :owner :computer
                  :escort-carrier-id 10 :escort-id 3}]
        (combat/clear-escort-on-death dead))
      (should= [] (get-in @atoms/game-map [0 0 :contents :group-submarine-ids]))))

  (context "dead carrier"
    (it "releases escorts to seeking mode"
      ;; Multiple cells: matching escorts, non-matching unit, empty cell
      (set-test-world! (build-test-map ["~~~~"]))
      (update-test-world! assoc-in [0 0 :contents]
                          {:type :battleship :owner :computer :hits 4
                           :escort-carrier-id 42 :escort-mode :escorting :orbit-angle 1.5})
      (update-test-world! assoc-in [1 0 :contents]
                          {:type :submarine :owner :computer :hits 2
                           :escort-carrier-id 42 :escort-mode :escorting :orbit-angle 0.5})
      (update-test-world! assoc-in [2 0 :contents]
                          {:type :destroyer :owner :computer :hits 3
                           :escort-carrier-id 99 :escort-mode :escorting})
      (let [dead {:type :carrier :owner :computer :carrier-id 42}]
        (combat/clear-escort-on-death dead))
      ;; Matching escorts released
      (should= :seeking (get-in @atoms/game-map [0 0 :contents :escort-mode]))
      (should-be-nil (get-in @atoms/game-map [0 0 :contents :escort-carrier-id]))
      (should-be-nil (get-in @atoms/game-map [0 0 :contents :orbit-angle]))
      (should= :seeking (get-in @atoms/game-map [1 0 :contents :escort-mode]))
      (should-be-nil (get-in @atoms/game-map [1 0 :contents :escort-carrier-id]))
      ;; Non-matching escort untouched
      (should= :escorting (get-in @atoms/game-map [2 0 :contents :escort-mode]))
      (should= 99 (get-in @atoms/game-map [2 0 :contents :escort-carrier-id]))))

  (context "non-group unit"
    (it "does nothing for a unit without group fields"
      (set-test-world! (build-test-map ["~~"]))
      (update-test-world! assoc-in [0 0 :contents]
                          {:type :carrier :owner :computer :hits 1 :carrier-id 10})
      (let [dead {:type :army :owner :computer}]
        (combat/clear-escort-on-death dead))
      ;; Carrier untouched
      (should= 10 (get-in @atoms/game-map [0 0 :contents :carrier-id])))))

(run-specs)

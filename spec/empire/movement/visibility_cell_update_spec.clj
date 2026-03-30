(ns empire.game-mechanics.movement.visibility-cell-update-spec
  (:require [empire.game-mechanics.debug.logging :as debug-logging]
            [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.game-mechanics.visibility :refer :all]
            [empire.test.utils :refer [build-test-map set-test-unit reset-all-atoms! set-test-player-map! set-test-computer-map! make-initial-test-map set-test-world!]]))
(describe "update-cell-visibility"
  (before (reset-all-atoms!))
  (it "reveals cells near player-owned units"
    (set-test-world! (build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----A#---"
                                             "----#----"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :awake)
    (set-test-player-map! (make-initial-test-map 9 9 nil))
    (update-cell-visibility [4 4] :player)
    ;; Check that the unit's cell and neighbors are revealed
    (should= {:type :land :contents {:type :army :owner :player :hits 1 :mode :awake}} (get-in (test-utils/read-test-state :player-map) [4 4]))
    (should= {:type :land} (get-in (test-utils/read-test-state :player-map) [5 4]))
    (should= {:type :land} (get-in (test-utils/read-test-state :player-map) [4 5]))
    ;; Check that distant cells are not revealed
    (should= nil (get-in (test-utils/read-test-state :player-map) [0 0]))
    (should= nil (get-in (test-utils/read-test-state :player-map) [8 8])))

  (it "stores last-known production on revealed computer cities in the player map"
    (set-test-world! (build-test-map ["----"
                                      "-A+-"
                                      "----"
                                      "----"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :awake)
    (test-utils/set-test-state! :production {[2 1] {:item :fighter :remaining-rounds 4}})
    (set-test-player-map! (make-initial-test-map 4 4 nil))
    (update-cell-visibility [1 1] :player)
    (should= {:item :fighter :remaining-rounds 4}
             (get-in (test-utils/read-test-state :player-map) [2 1 :known-production])))

  (it "stamps country-id when 3-arity called with computer army unit"
    (set-test-world! (build-test-map ["###"
                                             "#a#"
                                             "###"]))
    (set-test-unit (test-utils/game-map-atom) "a" :mode :moving :target [2 1] :country-id 7)
    (set-test-computer-map! (make-initial-test-map 3 3 nil))
    (let [unit {:type :army :owner :computer :country-id 7}]
      (update-cell-visibility [1 1] :computer unit))
    (should= 7 (get-in (test-utils/read-test-state :game-map) [0 0 :country-id])))

  (it "does not stamp when visible-map is nil"
    (set-test-world! (build-test-map ["###"
                                             "#a#"
                                             "###"]))
    (set-test-unit (test-utils/game-map-atom) "a" :mode :moving :target [2 1])
    (set-test-computer-map! nil)
    (update-cell-visibility [1 1] :computer)
    (should-not (get-in (test-utils/read-test-state :game-map) [0 0 :country-id])))

  (it "3-arity with player army does not stamp and does not track"
    (set-test-world! (build-test-map ["###"
                                             "#A#"
                                             "##+"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :moving :target [2 2])
    (set-test-player-map! (make-initial-test-map 3 3 nil))
    (let [unit {:type :army :owner :player}]
      (update-cell-visibility [1 1] :player unit))
    (should= {:type :land} (get-in (test-utils/read-test-state :player-map) [0 0]))
    (should-not (get-in (test-utils/read-test-state :game-map) [0 0 :country-id])))

  (it "reveals corner cell with edge clamping"
    (set-test-world! (build-test-map ["A~~"
                                             "~~~"
                                             "~~~"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :awake)
    (set-test-player-map! (make-initial-test-map 3 3 nil))
    (update-cell-visibility [0 0] :player)
    (should (get-in (test-utils/read-test-state :player-map) [0 0]))
    (should (get-in (test-utils/read-test-state :player-map) [0 1]))
    (should (get-in (test-utils/read-test-state :player-map) [1 0]))
    (should (get-in (test-utils/read-test-state :player-map) [1 1]))
    (should-not (get-in (test-utils/read-test-state :player-map) [2 2])))

  (it "computer army 3-arity stamps land"
    (set-test-world! (build-test-map ["##+"
                                             "#a#"
                                             "###"]))
    (set-test-unit (test-utils/game-map-atom) "a" :mode :moving :target [0 2])
    (set-test-computer-map! (make-initial-test-map 3 3 nil))
    (let [unit {:type :army :owner :computer :country-id 5}]
      (update-cell-visibility [1 1] :computer unit))
    (should= 5 (get-in (test-utils/read-test-state :game-map) [0 0 :country-id])))

  (it "stamps newly exposed unclaimed land from adjacent claimed computer territory"
    (set-test-world! (build-test-map ["X##"
                                      "#f#"
                                      "###"
                                      "###"]))
    (test-utils/update-test-world! assoc-in [0 0 :country-id] 3)
    (set-test-computer-map! (make-initial-test-map 3 3 nil))
    (update-cell-visibility [1 1] :computer {:type :fighter :owner :computer})
    (should= 3 (get-in (test-utils/read-test-state :game-map) [0 1 :country-id]))
    (should= 3 (get-in (test-utils/read-test-state :game-map) [1 0 :country-id]))
    (should= 3 (get-in (test-utils/read-test-state :game-map) [2 2 :country-id]))
    (should= 3 (get-in (test-utils/read-test-state :computer-map) [0 1 :country-id]))
    (should= 3 (get-in (test-utils/read-test-state :computer-map) [1 0 :country-id]))
    (should= 3 (get-in (test-utils/read-test-state :computer-map) [2 2 :country-id])))

  (it "uses the lower adjacent claimed country-id when multiple claims border the exposure"
    (set-test-world! (build-test-map ["X#X"
                                      "#f#"
                                      "###"]))
    (test-utils/update-test-world! assoc-in [0 0 :country-id] 5)
    (test-utils/update-test-world! assoc-in [2 0 :country-id] 2)
    (set-test-computer-map! (make-initial-test-map 3 3 nil))
    (update-cell-visibility [1 1] :computer {:type :fighter :owner :computer})
    (should= 2 (get-in (test-utils/read-test-state :game-map) [0 1 :country-id]))
    (should= 2 (get-in (test-utils/read-test-state :game-map) [1 0 :country-id]))
    (should= 2 (get-in (test-utils/read-test-state :game-map) [2 2 :country-id])))

  (it "claims previously visible connected unclaimed land when new exposure touches claimed territory"
    (set-test-world! (build-test-map ["X#f"
                                      "###"
                                      "###"]))
    (test-utils/update-test-world! assoc-in [0 0 :country-id] 4)
    (set-test-computer-map! (-> (make-initial-test-map 3 3 nil)
                                (assoc-in [0 0] {:type :land :country-id 4})
                                (assoc-in [0 1] {:type :land})))
    (update-cell-visibility [0 2] :computer {:type :fighter :owner :computer})
    (should= 4 (get-in (test-utils/read-test-state :game-map) [0 1 :country-id]))
    (should= 4 (get-in (test-utils/read-test-state :game-map) [1 1 :country-id]))
    (should= 4 (get-in (test-utils/read-test-state :game-map) [1 2 :country-id])))

  (it "reveals two rectangular rings for satellites"
    (set-test-world! (build-test-map ["#####"
                                             "#####"
                                             "##V##"
                                             "#####"
                                             "#####"]))
    (set-test-unit (test-utils/game-map-atom) "V" :target [4 4] :turns-remaining 50)
    (set-test-player-map! (make-initial-test-map 5 5 nil))
    (update-cell-visibility [2 2] :player)
    ;; All 25 cells in the 5x5 map should be visible (rings 1 and 2 plus center)
    (doseq [row (range 5)
            col (range 5)]
      (should (get-in (test-utils/read-test-state :player-map) [row col]))))

  (it "records discovered cells for the active computer unit when revealing unexplored cells"
    (set-test-world! (build-test-map ["###"
                                      "#f#"
                                      "###"]))
    (set-test-computer-map! (make-initial-test-map 3 3 {:type :unexplored}))
    (test-utils/set-test-state! :computer-unit-log-file "test.log")
    (debug-logging/begin-computer-unit-log-round!)
    (debug-logging/with-computer-unit-context
      31
      #(update-cell-visibility [1 1] :computer {:type :fighter :owner :computer :computer-unit-id 31}))
    (should= {31 9}
             (test-utils/read-test-state :computer-unit-round-discoveries)))

  (it "does not record discovered cells when the cells were already visible"
    (set-test-world! (build-test-map ["###"
                                      "#f#"
                                      "###"]))
    (set-test-computer-map! (build-test-map ["###"
                                             "#f#"
                                             "###"]))
    (test-utils/set-test-state! :computer-unit-log-file "test.log")
    (debug-logging/begin-computer-unit-log-round!)
    (debug-logging/with-computer-unit-context
      31
      #(update-cell-visibility [1 1] :computer {:type :fighter :owner :computer :computer-unit-id 31}))
    (should= {}
             (test-utils/read-test-state :computer-unit-round-discoveries))))

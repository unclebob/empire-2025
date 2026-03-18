(ns empire.computer.fighter-movement-primitives-spec
  "Low-level fighter movement primitive tests (hop/fuel/combat edge cases)."
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.fighter :as fighter]
            [empire.computer.fighter-movement :as fm]
            [empire.game-mechanics.services.combat :as combat]
            [empire.test.utils :refer [build-test-map
                                       set-test-unit
                                       get-test-unit reset-all-atoms! set-test-computer-map! set-test-world! update-test-world!]]))

(describe "fighter-movement-primitives"
  (before (reset-all-atoms!))

  (context "hop-over-friendly (L47, L83-89)"
    (it "returns hops 1 for unoccupied neighbor"
      (set-test-world! [[{:type :land :contents {:type :fighter :owner :computer}}
                         {:type :land}
                         {:type :land}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (should= {:dest [0 1] :hops 1}
               (fm/hop-over-friendly [0 0] [0 2])))

    (it "hops over one friendly with hops 2 (L83, L87)"
      (set-test-world! [[{:type :land :contents {:type :fighter :owner :computer}}
                         {:type :land :contents {:type :army :owner :computer :hits 1}}
                         {:type :land}
                         {:type :land}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [result (fm/hop-over-friendly [0 0] [0 3])]
        (should= 2 (:hops result))
        (should= [0 2] (:dest result))))

    (it "hops over two friendlies with hops 3 (L89 inc->dec)"
      (set-test-world! [[{:type :land :contents {:type :fighter :owner :computer}}
                         {:type :land :contents {:type :army :owner :computer :hits 1}}
                         {:type :land :contents {:type :army :owner :computer :hits 1}}
                         {:type :land}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (should= 3 (:hops (fm/hop-over-friendly [0 0] [0 3]))))

    (it "scans forward not backward (L84, L89 + -> -)"
      (set-test-world! [[{:type :land}]
                        [{:type :land :contents {:type :fighter :owner :computer}}]
                        [{:type :land :contents {:type :army :owner :computer :hits 1}}]
                        [{:type :land}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [result (fm/hop-over-friendly [1 0] [3 0])]
        (should= [3 0] (:dest result))
        (should= 2 (:hops result))))

    (it "marks attack when enemy at end (L88 if->if-not)"
      (set-test-world! [[{:type :land :contents {:type :fighter :owner :computer}}
                         {:type :land :contents {:type :army :owner :computer :hits 1}}
                         {:type :land :contents {:type :army :owner :player :hits 1}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [result (fm/hop-over-friendly [0 0] [0 2])]
        (should (:attack result))
        (should= 2 (:hops result))))

    (it "does not mark attack when the hop chain ends at a city occupant"
      (set-test-world! [[{:type :land :contents {:type :fighter :owner :computer}}
                         {:type :land :contents {:type :army :owner :computer :hits 1}}
                         {:type :city :city-status :player
                          :contents {:type :fighter :owner :player :hits 1}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (should-be-nil (fm/hop-over-friendly [0 0] [0 2])))

    (it "returns nil when neighbor is enemy not friendly"
      (set-test-world! [[{:type :land :contents {:type :fighter :owner :computer}}
                         {:type :land :contents {:type :army :owner :player :hits 1}}
                         {:type :land}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (should-be-nil (fm/hop-over-friendly [0 0] [0 2])))

    (it "does not hop over friendly units visible only on the game map"
      (set-test-world! [[{:type :land :contents {:type :fighter :owner :computer}}
                         {:type :land :contents {:type :army :owner :computer :hits 1}}
                         {:type :land}]])
      (set-test-computer-map! [[{:type :land :contents {:type :fighter :owner :computer}}
                                {:type :land}
                                {:type :land}]])
      (should= {:dest [0 1] :hops 1}
               (fm/hop-over-friendly [0 0] [0 2])))

    (it "hops backward when target is behind (L58 - -> +)"
      (set-test-world! [[{:type :land}]
                        [{:type :land :contents {:type :army :owner :computer :hits 1}}]
                        [{:type :land :contents {:type :fighter :owner :computer}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [result (fm/hop-over-friendly [2 0] [0 0])]
        (should= [0 0] (:dest result))
        (should= 2 (:hops result))))

    (it "hops diagonally over two friendlies (L89 first + -> -)"
      (set-test-world! (vec (for [r (range 4)]
                              (vec (for [c (range 4)]
                                     (cond
                                       (and (= r 0) (= c 0))
                                       {:type :land :contents {:type :fighter :owner :computer}}
                                       (or (and (= r 1) (= c 1))
                                           (and (= r 2) (= c 2)))
                                       {:type :land :contents {:type :army :owner :computer :hits 1}}
                                       :else {:type :land}))))))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [result (fm/hop-over-friendly [0 0] [3 3])]
        (should= [3 3] (:dest result))
        (should= 3 (:hops result))))

    (it "sidesteps when forward friendly chain is blocked by map edge"
      (set-test-world! (build-test-map ["###"
                                        "#fa"
                                        "###"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [result (fm/hop-over-friendly [1 1] [4 1])]
        (should-not-be-nil result)
        (should= 1 (:hops result))
        (should (contains? #{[2 0] [2 2]} (:dest result))))))

  (context "consume-hop-fuel (L189-192)"
    (it "returns true when fuel sufficient for hops"
      (set-test-world! [[{:type :land :contents {:type :fighter :owner :computer :fuel 5}}]])
      (should (fm/consume-hop-fuel [0 0] 3))
      (should= 3 (get-in (test-utils/read-test-state :game-map) [0 0 :contents :fuel])))

    (it "returns false when fuel runs out during hop"
      (set-test-world! [[{:type :land :contents {:type :fighter :owner :computer :fuel 1}}]])
      (should-not (fm/consume-hop-fuel [0 0] 3))
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [0 0 :contents])))

    (it "returns true for hops 1 (no intermediate burn)"
      (set-test-world! [[{:type :land :contents {:type :fighter :owner :computer :fuel 1}}]])
      (should (fm/consume-hop-fuel [0 0] 1))
      (should= 1 (get-in (test-utils/read-test-state :game-map) [0 0 :contents :fuel]))))

  (context "attack-enemy winner branch (L111)"
    (it "attacker wins and moves to enemy position"
      (set-test-world! (build-test-map ["fA"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 20)
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (with-redefs [combat/resolve-combat
                    (fn [attacker _defender]
                      {:winner :attacker :survivor attacker})]
        (let [unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
          (fighter/process-fighter [0 0] unit)
          (let [result (get-test-unit (test-utils/game-map-atom) "f")]
            (should-not-be-nil result)
            (should= [1 0] (:pos result))))))

    (it "attacker loses and is removed from map"
      (set-test-world! (build-test-map ["fA"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 20)
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (with-redefs [combat/resolve-combat
                    (fn [_attacker defender]
                      {:winner :defender :survivor defender})]
        (let [unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
          (fighter/process-fighter [0 0] unit)
          (should-be-nil (get-test-unit (test-utils/game-map-atom) "f"))
          (should= :player (get-in (test-utils/read-test-state :game-map) [1 0 :contents :owner]))))))

    (it "returns nil when the defender has no combat hits"
      (set-test-world! (build-test-map ["fA"]))
      (update-test-world! assoc-in [1 0 :contents] {:type :mystery :owner :player})
      (should-be-nil (fm/attack-enemy [0 0] [1 0]))
      (should= :fighter (get-in (test-utils/read-test-state :game-map) [0 0 :contents :type]))))

  (context "fuel boundary (L175)"
    (it "fighter with fuel 2 survives one consume step"
      (set-test-world! (build-test-map ["f#X"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 2
                     :flight-target-site [2 0]
                     :flight-origin-site [2 0])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
        (fighter/process-fighter [0 0] unit)
        (let [result (get-test-unit (test-utils/game-map-atom) "f")]
          (should (or (some? result)
                      (pos? (:fighter-count (get-in (test-utils/read-test-state :game-map) [2 0]) 0))))))))

  (context "should-return-to-refuel boundary (L146)"
    (it "returns true when fuel equals return distance + 2"
      (with-redefs [fm/find-nearest-refueling-site (fn [_] [0 0])
                    fm/distance-to (fn [_ _] 3)]
        (should (fm/should-return-to-refuel? [3 0] 5)))))

  (context "move-fighter-once with combat (L579-580)"
    (it "fighter attacks enemy and reports hops 1"
      (set-test-world! (build-test-map ["fA#"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 20)
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (with-redefs [combat/resolve-combat
                    (fn [attacker _defender]
                      {:winner :attacker :survivor attacker})]
        (let [unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
          (fighter/process-fighter [0 0] unit)
          (let [result (get-test-unit (test-utils/game-map-atom) "f")]
            (should-not-be-nil result)
            (should= [1 0] (:pos result)))))))

  (context "process-fighter at map edges (L21-22, L66)"
    (it "fighter at [0,0] corner can still move"
      (set-test-world! (build-test-map ["f##"
                                        "###"
                                        "###"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 20)
      (set-test-computer-map! (build-test-map ["f.."
                                               "..."
                                               "..."]))
      (let [unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
        (fighter/process-fighter [0 0] unit)
        (should (< (get-in (test-utils/read-test-state :game-map) [0 0 :contents :fuel] 0) 20))))

    (it "fighter at bottom-right corner can still move"
      (set-test-world! (build-test-map ["###"
                                        "###"
                                        "##f"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 20)
      (set-test-computer-map! (build-test-map ["..."
                                               "..."
                                               "..f"]))
      (let [unit (get-in (test-utils/read-test-state :game-map) [2 2 :contents])]
        (fighter/process-fighter [2 2] unit)
        (should (< (get-in (test-utils/read-test-state :game-map) [2 2 :contents :fuel] 0) 20)))))

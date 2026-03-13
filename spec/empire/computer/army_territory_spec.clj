(ns empire.computer.army-territory-spec
  "Tests for VMS Empire style computer army movement."
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.army :as army]
            [empire.computer.core :as core]
            [empire.computer.production :as production]
            [empire.computer.stamping :as stamping]
            [empire.game-mechanics.services.unit-stamping :as unit-stamping]
            [empire.game-mechanics.services.combat :as combat]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-world! update-test-world!]]))

(describe "army stamping and territory"
  (before (reset-all-atoms!))

  (context "army stamping"
    (it "uses the opening strategy coast-walk limit"
      (let [cell {:type :city :city-status :computer :country-id 1}
            base {:type :army :owner :computer :hits 1 :mode :awake}]
        (test-utils/set-test-state! :coast-walkers-produced {})
        (with-redefs [empire.game-mechanics.services.unit-stamping/country-coastal-cells-explored? (constantly false)
                      empire.computer.early-game.strategy/opening-exploration-profile
                      (constantly {:coast-walk-limit 3 :random-explore-chance 1/5})]
          (let [u1 (stamping/apply-coast-walk-fields base :army cell [0 0])
                u2 (stamping/apply-coast-walk-fields base :army cell [1 0])
                u3 (stamping/apply-coast-walk-fields base :army cell [2 0])
                u4 (stamping/apply-coast-walk-fields base :army cell [3 0])]
            (should= :coast-walk (:mode u1))
            (should= :coast-walk (:mode u2))
            (should= :coast-walk (:mode u3))
            (should= :awake (:mode u4))))))

    (it "uses the opening strategy random-explore chance"
      (let [cell {:type :city :city-status :computer :country-id 1}
            base {:type :army :owner :computer :hits 1 :mode :awake}]
        (with-redefs [empire.computer.early-game.strategy/opening-exploration-profile
                      (constantly {:coast-walk-limit 1 :random-explore-chance 1/5})
                      rand (constantly 0.1)
                      rand-nth (constantly [0 1])]
          (let [result (stamping/apply-random-explore-fields base :army cell [0 0])]
            (should= :random-explore (:mode result))
            (should= [0 1] (:random-explore-direction result))))))

    (it "keeps armies awake when rand exceeds the opening explore chance"
      (let [cell {:type :city :city-status :computer :country-id 1}
            base {:type :army :owner :computer :hits 1 :mode :awake}]
        (with-redefs [empire.computer.early-game.strategy/opening-exploration-profile
                      (constantly {:coast-walk-limit 1 :random-explore-chance 1/5})
                      rand (constantly 0.5)]
          (let [result (stamping/apply-random-explore-fields base :army cell [0 0])]
            (should= :awake (:mode result))))))

    (it "does not override coast-walk to random-explore"
      (let [cell {:type :city :city-status :computer :country-id 1}
            cw {:type :army :owner :computer :hits 1 :mode :coast-walk
                :coast-direction :clockwise :coast-start [0 0] :coast-visited [[0 0]]}]
        (with-redefs [empire.computer.early-game.strategy/opening-exploration-profile
                      (constantly {:coast-walk-limit 1 :random-explore-chance 1/5})
                      rand (constantly 0.1)]
          (let [result (stamping/apply-random-explore-fields cw :army cell [0 0])]
            (should= :coast-walk (:mode result)))))))

  (context "stamp-territory on cities"
    (it "stamps city cell with army's country-id"
      (set-test-world! [[{:type :city :city-status :computer}]])
      (let [army {:type :army :owner :computer :country-id 5}]
        (core/stamp-territory [0 0] army)
        (should= 5 (:country-id (get-in (test-utils/read-test-state :game-map) [0 0])))))))

;; --- Targeted mutation-killing tests ---

(describe "combat and objectives"
  (before (reset-all-atoms!))

  (context "attack-enemy deterministic combat (L115)"
    (it "attacker wins — moves to enemy position"
      (set-test-world! (build-test-map ["aA#"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (with-redefs [combat/resolve-combat
                    (fn [atk _def] {:winner :attacker :survivor atk})]
        (army/process-army [0 0]))
      ;; Computer army should now be at [1 0]
      (should= :computer (get-in (test-utils/read-test-state :game-map) [1 0 :contents :owner]))
      (should= :army (get-in (test-utils/read-test-state :game-map) [1 0 :contents :type]))
      ;; Original position empty
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [0 0 :contents])))

    (it "attacker loses — removed from map"
      (set-test-world! (build-test-map ["aA#"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (with-redefs [combat/resolve-combat
                    (fn [_atk def] {:winner :defender :survivor def})]
        (army/process-army [0 0]))
      ;; Computer army at [0 0] removed
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [0 0 :contents]))
      ;; Player army still at [1 0]
      (should= :player (get-in (test-utils/read-test-state :game-map) [1 0 :contents :owner]))))

  (context "find-city-objective filters (L142, L143, L149)"
    (it "player-cities filter finds player city (L142)"
      ;; Army at [0 0] no country-id. Player city right, free city left (opposite directions).
      ;; "O#a#+" → col0=player-city, col1=land, col2=army, col3=land, col4=free-city
      ;; Player city at [0 0], free at [4 0]. Army at [2 0].
      ;; find-city-objective: player-cities = [[0 0]], free-cities = [[4 0]].
      ;; Targets player city first → army moves LEFT toward [0 0].
      ;; With L142 mutation (= → not=): player-cities filter broken → empty.
      ;; Falls to free-cities → army moves RIGHT toward [4 0].
      (set-test-world! (build-test-map ["O#a#+"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [2 0 :contents]
             {:type :army :owner :computer :hits 1 :mode :awake})
      (army/process-army [2 0])
      ;; Army should have moved toward player city (left, to [1 0])
      (should= :army (get-in (test-utils/read-test-state :game-map) [1 0 :contents :type])))

    (it "free-cities filter finds free city (L143)"
      ;; Army at [0 0] no country-id. Only a free city, no player cities.
      ;; Need to distinguish find-city-objective success from explore-randomly.
      ;; Put army at center, free city at one end, obstacle at other end.
      ;; "~a#+" → col0=sea, col1=army, col2=land, col3=free-city
      ;; Army at [1 0]. Only land neighbor toward city: [2 0].
      ;; With L143 mutation: free-cities broken → no target → explore-randomly.
      ;; explore-randomly with rand-nth controlled → might go to [2 0] anyway.
      ;; Block by mocking rand-nth to pick first candidate.
      ;; explore-randomly: empty neighbors = [[2 0]], frontier check, then target.
      ;; Both paths move to [2 0]. Can't distinguish. Need different topology.
      ;;
      ;; Better: army at [2 0], free city at [0 0], multiple paths.
      ;; "+#a#" → col0=free, col1=land, col2=army, col3=land
      ;; find-city-objective → target [0 0] → step to [1 0].
      ;; With mutation: no target → explore-randomly → might pick [1 0] or [3 0].
      ;; Mock rand-nth to pick last → would pick [3 0] with explore.
      (set-test-world! (build-test-map ["+#a#"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [2 0 :contents]
             {:type :army :owner :computer :hits 1 :mode :awake})
      (with-redefs [rand-nth last]
        (army/process-army [2 0]))
      ;; With correct code: army targets [0 0], steps to [1 0].
      ;; With mutation: no target, explore-randomly with rand-nth=last picks [3 0].
      (should= :army (get-in (test-utils/read-test-state :game-map) [1 0 :contents :type])))

    (it "returns target and updates claimed when target exists (L149)"
      ;; Army at center, free city far left. All cities pre-claimed → fallback min-key.
      ;; "+##a##" → col0=free, col1-2=land, col3=army, col4-5=land
      ;; Army at [3 0], city at [0 0] (distance 3). Not adjacent → no attack.
      (set-test-world! (build-test-map ["+##a##"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [3 0 :contents]
             {:type :army :owner :computer :hits 1 :mode :awake})
      (test-utils/set-test-state! :claimed-objectives #{[0 0]})
      ;; rand-nth last so explore-randomly picks rightmost neighbor [4 0]
      (with-redefs [rand-nth last]
        (army/process-army [3 0]))
      ;; Correct: target [0 0], army steps left to [2 0].
      ;; With mutation: no target, explore picks [4 0] (rightmost via rand-nth=last).
      (should= :army (get-in (test-utils/read-test-state :game-map) [2 0 :contents :type]))))

  (context "move-toward-objective preferred-in-history fallback (L175)"
    (it "falls through to sorted empty neighbors when preferred is in history"
      ;; Army at [1 0] with attack-target [3 0] (free city), move-history [[2 0]]
      ;; pathfinding prefers [2 0] but it's in history → fallback to sorted empty
      (set-test-world! (build-test-map ["#a#+"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [1 0 :contents]
             {:type :army :owner :computer :hits 1 :mode :awake
              :attack-target [3 0] :move-history [[2 0]]})
      (army/process-army [1 0])
      ;; Army should have moved to [0 0] (history blocks [2 0])
      (should= :army (get-in (test-utils/read-test-state :game-map) [0 0 :contents :type]))))

  (context "move-toward-objective sovereignty passability (L176)"
    (it "uses sovereignty check when country-id set"
      ;; Army at [0 0] with country-id 1, attack-target [2 0]. Cell [1 0] has foreign country-id 2.
      ;; With correct code: pass-fn checks sovereignty → [1 0] impassable → no path.
      ;; Fallback to empty neighbors → [1 0] blocked by sovereignty → no valid moves.
      ;; With mutation (when-not): pass-fn used when country-id nil → for non-nil country-id,
      ;; pass-fn = nil → default passability → [1 0] passable → army moves to [1 0].
      (set-test-world! (build-test-map ["a##"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :army :owner :computer :hits 1 :mode :awake
              :attack-target [2 0] :country-id 1})
      (update-test-world! assoc-in [0 0 :country-id] 1)
      (update-test-world! assoc-in [1 0 :country-id] 2)
      (update-test-world! assoc-in [2 0 :country-id] 2)
      (army/process-army [0 0])
      ;; Correct: army can't pass foreign territory → stays at [0 0], target cleared.
      ;; With mutation: army walks through foreign territory to [1 0].
      (should= :army (get-in (test-utils/read-test-state :game-map) [0 0 :contents :type])))))

(describe "exploration and coastal movement"
  (before (reset-all-atoms!))

  (context "explore-randomly history fallback (L212)"
    (it "falls back to unfiltered empty when all empty neighbors in history"
      ;; Army at [1 0], only neighbor [0 0] and [2 0], both in history
      ;; No country-id, no city objectives → falls to explore-randomly
      (set-test-world! (build-test-map ["#a#"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [1 0 :contents]
             {:type :army :owner :computer :hits 1 :mode :awake
              :move-history [[0 0] [2 0]]})
      ;; Make sure explore-randomly is the one running (no city objectives, no country-id)
      (with-redefs [rand-nth first]
        (army/process-army [1 0]))
      ;; Army should have moved to one of the neighbors despite them being in history
      (should (or (= :army (get-in (test-utils/read-test-state :game-map) [0 0 :contents :type]))
                  (= :army (get-in (test-utils/read-test-state :game-map) [2 0 :contents :type]))))))

  (context "coast-walk multiple best candidates (L238)"
    (it "handles two equally-scored coast candidates"
      ;; Map: 3x2, army at [1 0], sea on row 1.
      ;; [0 0] and [2 0] both adjacent to sea, both unvisited, equal unexplored neighbors
      (set-test-world! (build-test-map ["#a#"
                                               "~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [1 0 :contents]
             {:type :army :owner :computer :hits 1
              :mode :coast-walk :coast-direction :clockwise
              :coast-start [2 0] :coast-visited [[1 0]]})
      (with-redefs [rand-nth first]
        (army/process-army [1 0]))
      ;; Army should have moved to one of the two candidates
      (should (or (= :army (get-in (test-utils/read-test-state :game-map) [0 0 :contents :type]))
                  (= :army (get-in (test-utils/read-test-state :game-map) [2 0 :contents :type]))))))

  (context "start-interior-exploration target calculation (L354)"
    (it "correctly adds direction to position"
      ;; Call start-interior-exploration directly via var reference
      ;; Army at [2 1], direction [1 1] → target = [3 2]
      (set-test-world! (build-test-map ["#####"
                                               "#####"
                                               "#####"
                                               "~~~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (doseq [col (range 5) row (range 3)]
        (update-test-world! assoc-in [col row :country-id] 1))
      (update-test-world! assoc-in [2 1 :contents]
             {:type :army :owner :computer :hits 1 :mode :awake :country-id 1})
      (with-redefs [rand-nth (constantly [1 1])]
        (@#'army/start-interior-exploration [2 1] 1))
      ;; Army should have moved to [3 2] = [2+1, 1+1]
      (should= :army (get-in (test-utils/read-test-state :game-map) [3 2 :contents :type]))
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [2 1 :contents]))))

  (context "find-and-execute-land-action city objective (L370)"
    (it "moves toward city objective when one exists"
      ;; Call find-and-execute-land-action directly via var reference
      ;; Army at [0 0] with country-id 1, free city at [3 0]
      (set-test-world! (build-test-map ["a##+"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :army :owner :computer :hits 1 :mode :awake :country-id 1})
      (doseq [col (range 4)]
        (update-test-world! assoc-in [col 0 :country-id] 1))
      (with-redefs [rand (constantly 0.5)]
        (@#'army/find-and-execute-land-action [0 0] 1))
      ;; Army should have moved toward the city
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [0 0 :contents]))
      (should= :army (get-in (test-utils/read-test-state :game-map) [1 0 :contents :type]))))

  (context "process-random-explore goes sentry on coast after move (L391)"
    (it "goes sentry after moving to coastal cell"
      ;; Army at [1 0] in random-explore mode heading [0 1] (south).
      ;; Target [1 1] is land adjacent to sea (coastal).
      (set-test-world! (build-test-map ["###"
                                               "###"
                                               "~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [1 0 :contents]
             {:type :army :owner :computer :hits 1
              :mode :random-explore :random-explore-direction [0 1] :country-id 1})
      (army/process-army [1 0])
      ;; Army moved to [1 1] which is coastal → should be sentry
      (let [unit (get-in (test-utils/read-test-state :game-map) [1 1 :contents])]
        (should= :army (:type unit))
        (should= :sentry (:mode unit)))))

  (context "sentry army in city triggers fill-coastal-cell (L438)"
    (it "sentry army in computer city fills coastal cell"
      ;; Sentry army in a computer city. With country-id, should trigger fill-coastal-cell.
      ;; Coastal cell at [1 0] is empty, adjacent to sea at [1 1].
      (set-test-world! [[{:type :city :city-status :computer :country-id 1
                                 :contents {:type :army :owner :computer :hits 1
                                            :mode :sentry :country-id 1}}
                                {:type :sea}]
                               [{:type :land :country-id 1}
                                {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (army/process-army [0 0])
      ;; Army should have moved to [1 0] (coastal fill)
      (should= :army (get-in (test-utils/read-test-state :game-map) [1 0 :contents :type]))
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [0 0 :contents])))

    (it "sentry army NOT in city does NOT trigger fill-coastal-cell"
      ;; Sentry army on plain land (not a city) should NOT move
      (set-test-world! [[{:type :land :country-id 1
                                 :contents {:type :army :owner :computer :hits 1
                                            :mode :sentry :country-id 1}}
                                {:type :sea}]
                               [{:type :land :country-id 1}
                                {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (army/process-army [0 0])
      ;; Army should stay put as sentry on non-city land
      (should= :army (get-in (test-utils/read-test-state :game-map) [0 0 :contents :type]))
      (should= :sentry (get-in (test-utils/read-test-state :game-map) [0 0 :contents :mode])))))

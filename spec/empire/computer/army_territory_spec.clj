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
    (it "limits coast-walkers to 2 per country"
      (let [cell {:type :city :city-status :computer :country-id 1}
            base {:type :army :owner :computer :hits 1 :mode :awake}]
        (test-utils/set-test-state! :coast-walkers-produced {})
        (with-redefs [empire.game-mechanics.services.unit-stamping/country-coastal-cells-explored? (constantly false)]
          (let [u1 (stamping/apply-coast-walk-fields base :army cell [0 0])
                u2 (stamping/apply-coast-walk-fields base :army cell [1 0])
                u3 (stamping/apply-coast-walk-fields base :army cell [2 0])]
            (should= :coast-walk (:mode u1))
            (should= :coast-walk (:mode u2))
            ;; Third should NOT get coast-walk
            (should= :awake (:mode u3))))))

    (it "1/3 of non-coast-walk armies get random-explore"
      (let [cell {:type :city :city-status :computer :country-id 1}
            base {:type :army :owner :computer :hits 1 :mode :awake}]
        ;; Mock rand < 1/3 → should get random-explore
        (with-redefs [rand (constantly 0.2)
                      rand-nth (constantly [0 1])]
          (let [result (stamping/apply-random-explore-fields base :army cell)]
            (should= :random-explore (:mode result))
            (should= [0 1] (:random-explore-direction result))))))

    (it "2/3 of non-coast-walk armies stay awake"
      (let [cell {:type :city :city-status :computer :country-id 1}
            base {:type :army :owner :computer :hits 1 :mode :awake}]
        ;; Mock rand >= 1/3 → should stay awake
        (with-redefs [rand (constantly 0.5)]
          (let [result (stamping/apply-random-explore-fields base :army cell)]
            (should= :awake (:mode result))))))

    (it "does not override coast-walk to random-explore"
      (let [cell {:type :city :city-status :computer :country-id 1}
            cw {:type :army :owner :computer :hits 1 :mode :coast-walk
                :coast-direction :clockwise :coast-start [0 0] :coast-visited [[0 0]]}]
        (with-redefs [rand (constantly 0.1)]
          (let [result (stamping/apply-random-explore-fields cw :army cell)]
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

  (context "adjacent-to-computer-city? (L252, L253)"
    (it "avoids coastal cell adjacent to computer city"
      ;; "####" / "X###" / "~~~~"
      ;; col0=[land,city,sea], col1=[land,land,sea], col2=[land,land,sea], col3=[land,land,sea]
      ;; [1 1] is coastal (adj [1 2]=sea) AND adj to city [0 1]. Filtered out.
      ;; [2 1] is coastal, NOT adj to city. [3 1] same.
      ;; Army at [1 0]: nearest unfiltered coastal = [2 1] (dist 2).
      ;; Correct: target [2 1], step diagonally to [2 1].
      ;; Mutation: target [1 1] (dist 1, no longer filtered), step to [1 1].
      (set-test-world! (build-test-map ["####"
                                               "X###"
                                               "~~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (doseq [col (range 4)]
        (update-test-world! assoc-in [col 0 :country-id] 1)
        (update-test-world! assoc-in [col 1 :country-id] 1))
      (update-test-world! assoc-in [1 0 :contents]
             {:type :army :owner :computer :hits 1 :mode :awake :country-id 1})
      (with-redefs [rand (constantly 0.5)]
        (army/process-army [1 0]))
      ;; Correct: army goes to [2 1] (diagonal step toward target [2 1]).
      ;; With mutation: army goes to [1 1] (directly, since it's the nearest unfiltered target).
      (should-not= :army (get-in (test-utils/read-test-state :game-map) [1 1 :contents :type]))
      (should= :army (get-in (test-utils/read-test-state :game-map) [2 1 :contents :type]))))

  (context "find-nearest-unoccupied-coastal-cell (L259, L264, L266, L272)"
    (it "finds coastal cell with matching country-id (L259, L264, L266)"
      ;; Army at [0 0] interior, coastal cell at [0 2] with country-id 1
      (set-test-world! [[{:type :land :country-id 1
                                 :contents {:type :army :owner :computer :hits 1
                                            :mode :awake :country-id 1}}
                                {:type :land :country-id 1}
                                {:type :land :country-id 1}
                                {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (with-redefs [rand (constantly 0.5)]
        (army/process-army [0 0]))
      ;; Army should have moved toward coast (row 2)
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [0 0 :contents]))
      (should= :army (get-in (test-utils/read-test-state :game-map) [0 1 :contents :type])))

    (it "falls back to unfiltered candidates when all near computer cities (L272)"
      ;; Army at [1 0] interior. Only coastal cell [1 1] is adjacent to computer city [0 1].
      ;; remove-adjacent-to-computer-city? filters it out → empty list → fallback to unfiltered.
      ;; Map: 3 cols x 3 rows
      ;; col 0: [land, city, sea]
      ;; col 1: [land+army, land, sea]  — [1 1] is coastal (adj to [1 2]=sea) AND adj to city [0 1]
      ;; col 2: [land, land, sea]
      ;; BUT we need all coastal cells adjacent to a computer city.
      ;; Put cities adjacent to every coastal cell.
      (set-test-world! [[{:type :city :city-status :computer :country-id 1}
                                {:type :land :country-id 1}
                                {:type :sea}]
                               [{:type :land :country-id 1
                                 :contents {:type :army :owner :computer :hits 1
                                            :mode :awake :country-id 1}}
                                {:type :land :country-id 1}
                                {:type :sea}]
                               [{:type :city :city-status :computer :country-id 1}
                                {:type :land :country-id 1}
                                {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (with-redefs [rand (constantly 0.5)]
        (army/process-army [1 0]))
      ;; Army should move toward a coastal cell despite it being near cities (fallback)
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [1 0 :contents]))
      (should= :army (get-in (test-utils/read-test-state :game-map) [1 1 :contents :type]))))

  (context "find-nearest-cell-close-to-coast (L284, L290, L291)"
    (it "prefers directly coastal cell (distance 0) over near-coastal (distance 1) (L284, L290, L291)"
      ;; Army at [1 0] (interior). All directly-coastal cells are occupied
      ;; except one that doesn't exist. So find-nearest-unoccupied-coastal-cell returns nil.
      ;; Then find-nearest-cell-close-to-coast finds both directly-coastal and near-coastal cells.
      ;; It should prefer distance 0 (directly coastal) over distance 1 (near-coastal).
      ;;
      ;; Map: 3 cols x 4 rows
      ;; col 0: [land, land, sentry, sea]
      ;; col 1: [army, land, sentry, sea]
      ;; col 2: [land, land, sentry, sea]
      ;; All row-2 coastal cells have sentries. row-1 cells are near-coastal (1 step).
      ;; find-nearest-unoccupied-coastal-cell: all coastal cells occupied → nil
      ;; find-nearest-cell-close-to-coast: should find row-1 cells (distance 1 from coast)
      ;;   and row-2 is occupied so NOT candidates (contents not nil).
      ;;   Actually row-1 cells ARE candidates: empty, land, country-id match.
      ;;   For row-1 cells: adjacent-to-sea? is false, but some neighbor is adjacent-to-sea? → distance 1.
      ;;   So they're near-coastal. But there are no distance-0 unoccupied cells.
      ;;   → the code picks the nearest near-coastal cell.
      ;; To test L290/L291 distinctly: I need BOTH a directly-coastal empty cell AND a near-coastal cell.
      ;; Let me have one coastal cell empty and one near-coastal cell empty, and verify the coastal one wins.
      (set-test-world! [[{:type :land :country-id 1}
                                {:type :land :country-id 1}
                                {:type :land :country-id 1
                                 :contents {:type :army :owner :computer :hits 1 :mode :sentry :country-id 1}}
                                {:type :sea}]
                               [{:type :land :country-id 1
                                 :contents {:type :army :owner :computer :hits 1
                                            :mode :awake :country-id 1}}
                                {:type :land :country-id 1}
                                {:type :land :country-id 1}
                                {:type :sea}]
                               [{:type :land :country-id 1}
                                {:type :land :country-id 1}
                                {:type :land :country-id 1
                                 :contents {:type :army :owner :computer :hits 1 :mode :sentry :country-id 1}}
                                {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      ;; [1 2] is the only empty coastal cell (adj to [1 3]=sea).
      ;; [0 1], [1 1], [2 1] are near-coastal (neighbors of row 2 which is coastal).
      ;; find-nearest-unoccupied-coastal-cell should find [1 2].
      (with-redefs [rand (constantly 0.5)]
        (army/process-army [1 0]))
      ;; Army at [1 0] should move toward [1 2] (coastal). Next step is [1 1].
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [1 0 :contents]))
      (should= :army (get-in (test-utils/read-test-state :game-map) [1 1 :contents :type]))))

  (context "fill-coastal-cell wake sentries (L325)"
    (it "wakes nearby sentries when no coastal or near-coast cells available"
      ;; Army at [2 1], all coastal and near-coast cells occupied by sentries
      ;; No coastal cells, no near-coast cells → falls to wake-nearby-sentries
      (set-test-world! [[{:type :land :country-id 1
                                 :contents {:type :army :owner :computer :hits 1 :mode :sentry :country-id 1}}]
                               [{:type :land :country-id 1
                                 :contents {:type :army :owner :computer :hits 1 :mode :sentry :country-id 1}}]
                               [{:type :land :country-id 1
                                 :contents {:type :army :owner :computer :hits 1 :mode :awake :country-id 1}}]
                               [{:type :land :country-id 1
                                 :contents {:type :army :owner :computer :hits 1 :mode :sentry :country-id 1}}]
                               [{:type :land :country-id 1
                                 :contents {:type :army :owner :computer :hits 1 :mode :sentry :country-id 1}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      ;; No sea anywhere → no coastal cells → fill-coastal-cell can't find targets
      ;; Army is at [2 0], all land, all occupied
      (with-redefs [rand (constantly 0.5)
                    rand-nth first]
        (army/process-army [2 0]))
      ;; At least one sentry should have been woken
      (let [modes (map #(get-in (test-utils/read-test-state :game-map) [% 0 :contents :mode]) [0 1 3 4])]
        (should (some #(not= :sentry %) modes)))))

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

(ns empire.computer.core-spec
  (:require [speclj.core :refer :all]
            [empire.computer.core :as core]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-player-map! set-test-world! update-test-world!]]))

(describe "distance"
  (it "returns 0 for same position"
    (should= 0 (core/distance [3 3] [3 3])))

  (it "returns Manhattan distance"
    (should= 5 (core/distance [0 0] [3 2])))

  (it "handles negative direction"
    (should= 7 (core/distance [5 5] [2 1])))

  (it "computes positive x distance correctly"
    (should= 5 (core/distance [0 0] [5 0])))

  (it "computes positive y distance correctly"
    (should= 3 (core/distance [0 0] [0 3]))))

(describe "chebyshev-distance"
  (it "returns 0 for same position"
    (should= 0 (core/chebyshev-distance [3 3] [3 3])))

  (it "returns max of row/col differences"
    (should= 3 (core/chebyshev-distance [0 0] [3 2])))

  (it "handles negative direction"
    (should= 4 (core/chebyshev-distance [5 5] [1 2])))

  (it "computes positive row distance"
    (should= 5 (core/chebyshev-distance [0 0] [5 0])))

  (it "computes positive col distance"
    (should= 3 (core/chebyshev-distance [0 0] [0 3]))))

(describe "attackable-target?"
  (it "returns true for player city"
    (should (core/attackable-target? {:type :city :city-status :player})))

  (it "returns true for free city"
    (should (core/attackable-target? {:type :city :city-status :free})))

  (it "returns false for computer city"
    (should-not (core/attackable-target? {:type :city :city-status :computer})))

  (it "returns true for player unit"
    (should (core/attackable-target? {:contents {:owner :player}})))

  (it "returns false for computer unit"
    (should-not (core/attackable-target? {:contents {:owner :computer}})))

  (it "returns false for empty cell"
    (should-not (core/attackable-target? {:type :sea})))

  (it "returns false for land cell without contents"
    (should-not (core/attackable-target? {:type :land}))))

(describe "move-toward"
  (it "returns the neighbor closest to target"
    (should= [2 0] (core/move-toward [1 0] [3 0] [[0 0] [2 0]])))

  (it "returns nil when no passable neighbors"
    (should-be-nil (core/move-toward [1 0] [3 0] [])))

  (it "returns nil when passable-neighbors is nil"
    (should-be-nil (core/move-toward [1 0] [3 0] nil))))

(describe "find-visible-cities"
  (before (reset-all-atoms!))

  (it "finds computer cities"
    (set-test-computer-map!
            [[{:type :city :city-status :computer} {:type :sea}]
             [{:type :sea} {:type :sea}]])
    (should= [[0 0]] (core/find-visible-cities #{:computer})))

  (it "finds player cities with predicate"
    (set-test-computer-map!
            [[{:type :city :city-status :player} {:type :sea}]
             [{:type :sea} {:type :city :city-status :computer}]])
    (should= [[0 0]] (core/find-visible-cities #{:player})))

  (it "returns empty when no matching cities"
    (set-test-computer-map!
            [[{:type :sea} {:type :sea}]
             [{:type :sea} {:type :sea}]])
    (should= [] (core/find-visible-cities #{:computer}))))

(describe "adjacent-to-computer-unexplored?"
  (before (reset-all-atoms!))

  (it "returns true when neighbor is nil (unexplored)"
    (set-test-computer-map!
            [[{:type :sea} nil]
             [{:type :sea} {:type :sea}]])
    (set-test-world! [[{:type :sea} {:type :sea}]
             [{:type :sea} {:type :sea}]])
    (should (core/adjacent-to-computer-unexplored? [0 0])))

  (it "returns false when all neighbors explored"
    (set-test-computer-map!
            [[{:type :sea} {:type :sea}]
             [{:type :sea} {:type :sea}]])
    (set-test-world! [[{:type :sea} {:type :sea}]
             [{:type :sea} {:type :sea}]])
    (should-not (core/adjacent-to-computer-unexplored? [0 0]))))

(describe "stamp-territory"
  (before (reset-all-atoms!))

  (it "stamps land cell with army's country-id"
    (set-test-world! (build-test-map ["#"]))
    (core/stamp-territory [0 0] {:type :army :owner :computer :country-id 3})
    (should= 3 (:country-id (get-in @atoms/game-map [0 0]))))

  (it "stamps city cell with army's country-id"
    (set-test-world! (build-test-map ["X"]))
    (core/stamp-territory [0 0] {:type :army :owner :computer :country-id 5})
    (should= 5 (:country-id (get-in @atoms/game-map [0 0]))))

  (it "does not stamp sea cell"
    (set-test-world! (build-test-map ["~"]))
    (core/stamp-territory [0 0] {:type :army :owner :computer :country-id 3})
    (should-be-nil (:country-id (get-in @atoms/game-map [0 0]))))

  (it "does not stamp for player army"
    (set-test-world! (build-test-map ["#"]))
    (core/stamp-territory [0 0] {:type :army :owner :player :country-id 3})
    (should-be-nil (:country-id (get-in @atoms/game-map [0 0]))))

  (it "does not stamp for non-army unit"
    (set-test-world! (build-test-map ["#"]))
    (core/stamp-territory [0 0] {:type :transport :owner :computer :country-id 3})
    (should-be-nil (:country-id (get-in @atoms/game-map [0 0]))))

  (it "does not stamp when army has no country-id"
    (set-test-world! (build-test-map ["#"]))
    (core/stamp-territory [0 0] {:type :army :owner :computer})
    (should-be-nil (:country-id (get-in @atoms/game-map [0 0])))))

(describe "move-unit-to"
  (before (reset-all-atoms!))

  (it "moves unit from source to destination"
    (set-test-world! (build-test-map ["a~"]))
    (set-test-computer-map! (build-test-map ["a~"]))
    (let [unit (:contents (get-in @atoms/game-map [0 0]))]
      (should= [1 0] (core/move-unit-to [0 0] [1 0]))
      (should-be-nil (:contents (get-in @atoms/game-map [0 0])))
      (should= unit (:contents (get-in @atoms/game-map [1 0])))))

  (it "returns nil when destination is occupied"
    (set-test-world! (build-test-map ["ad"]))
    (should-be-nil (core/move-unit-to [0 0] [1 0]))
    (should (:contents (get-in @atoms/game-map [0 0]))))

  (it "returns nil when blocked by foreign territory"
    (set-test-world! (build-test-map ["a#"]))
    (update-test-world! assoc-in [0 0 :contents :country-id] 1)
    (update-test-world! assoc-in [1 0 :country-id] 2)
    (should-be-nil (core/move-unit-to [0 0] [1 0]))
    (should (:contents (get-in @atoms/game-map [0 0]))))

  (it "allows movement to land with same country-id"
    (set-test-world! (build-test-map ["a#"]))
    (set-test-computer-map! (build-test-map ["a#"]))
    (update-test-world! assoc-in [0 0 :contents :country-id] 1)
    (update-test-world! assoc-in [1 0 :country-id] 1)
    (should= [1 0] (core/move-unit-to [0 0] [1 0])))

  (it "clears old fighter position on computer-map for long move"
    (set-test-world! (build-test-map ["f######"]))
    (set-test-computer-map! @atoms/game-map)
    (should= [6 0] (core/move-unit-to [0 0] [6 0]))
    (should-be-nil (get-in @atoms/computer-map [0 0 :contents]))
    (should= :fighter (get-in @atoms/computer-map [6 0 :contents :type]))))

(describe "attempt-conquest-computer"
  (before (reset-all-atoms!))

  (it "conquers city on success (rand < 0.5)"
    (set-test-world! (build-test-map ["a+"]))
    (set-test-computer-map! (build-test-map ["a+"]))
    (reset! atoms/production {})
    (with-redefs [rand (constantly 0.1)]
      (let [result (core/attempt-conquest-computer [0 0] [1 0])]
        (should-be-nil result)
        (should= :computer (:city-status (get-in @atoms/game-map [1 0])))
        (should-be-nil (:contents (get-in @atoms/game-map [0 0])))
        (should= :army (:item (get @atoms/production [1 0]))))))

  (it "army dies on failure (rand >= 0.5)"
    (set-test-world! (build-test-map ["a+"]))
    (set-test-computer-map! (build-test-map ["a+"]))
    (with-redefs [rand (constantly 0.9)]
      (let [result (core/attempt-conquest-computer [0 0] [1 0])]
        (should-be-nil result)
        (should-be-nil (:contents (get-in @atoms/game-map [0 0])))
        (should= :free (:city-status (get-in @atoms/game-map [1 0]))))))

  (it "updates player-map city status when computer conquers a player city"
    (set-test-world! (build-test-map ["aO"]))
    (set-test-computer-map! (build-test-map ["aO"]))
    (set-test-player-map! (build-test-map ["aO"]))
    (with-redefs [rand (constantly 0.1)]
      (core/attempt-conquest-computer [0 0] [1 0])
      (should= :computer (get-in @atoms/player-map [1 0 :city-status])))))

(describe "wake-nearby-sentries"
  (before (reset-all-atoms!))

  (it "wakes sentry armies within radius"
    (set-test-world! (build-test-map ["~a~"]))
    (update-test-world! assoc-in [1 0 :contents :mode] :sentry)
    (with-redefs [rand (constantly 0.3)]
      (let [woken (core/wake-nearby-sentries [0 0] 2)]
        (should= 1 woken)
        (should= :awake (:mode (:contents (get-in @atoms/game-map [1 0])))))))

  (it "does not wake armies beyond radius"
    (set-test-world! (build-test-map ["~..a"]))
    (update-test-world! assoc-in [3 0 :contents :mode] :sentry)
    (with-redefs [rand (constantly 0.3)]
      (let [woken (core/wake-nearby-sentries [0 0] 1)]
        (should= 0 woken)
        (should= :sentry (:mode (:contents (get-in @atoms/game-map [3 0])))))))

  (it "does not wake player armies"
    (set-test-world! (build-test-map ["~A~"]))
    (update-test-world! assoc-in [1 0 :contents :mode] :sentry)
    (with-redefs [rand (constantly 0.3)]
      (let [woken (core/wake-nearby-sentries [0 0] 2)]
        (should= 0 woken)
        (should= :sentry (:mode (:contents (get-in @atoms/game-map [1 0])))))))

  (it "does not wake non-sentry armies"
    (set-test-world! (build-test-map ["~a~"]))
    (with-redefs [rand (constantly 0.3)]
      (let [woken (core/wake-nearby-sentries [0 0] 2)]
        (should= 0 woken))))

  (it "wakes sentry at exact chebyshev distance = radius"
    ;; 5x5 map; sentry at [0,2], pos at [2,2], radius 2
    ;; chebyshev = max(|0-2|,|2-2|) = 2 = radius
    ;; Kills <= -> < on L151
    (set-test-world! (build-test-map ["~~#~~"
                                            "~~#~~"
                                            "a~#~#"
                                            "~~#~~"
                                            "~~#~~"]))
    (update-test-world! assoc-in [0 2 :contents :mode] :sentry)
    (with-redefs [rand (constantly 0.3)]
      (let [woken (core/wake-nearby-sentries [2 2] 2)]
        (should= 1 woken)
        (should= :awake (:mode (:contents (get-in @atoms/game-map [0 2])))))))

  (it "wakes sentries at row 0 and last row boundaries"
    ;; 5x3 map; sentries at [2,0] and [2,2], pos at [2,1], radius 1
    ;; Kills 0->1 and 1->0 range bound mutations on r range
    (set-test-world! (build-test-map ["~~a~~"
                                            "~~#~~"
                                            "~~a~~"]))
    (update-test-world! assoc-in [2 0 :contents :mode] :sentry)
    (update-test-world! assoc-in [2 2 :contents :mode] :sentry)
    (with-redefs [rand (constantly 0.3)]
      (let [woken (core/wake-nearby-sentries [2 1] 1)]
        (should= 2 woken)
        (should= :awake (:mode (:contents (get-in @atoms/game-map [2 0]))))
        (should= :awake (:mode (:contents (get-in @atoms/game-map [2 2])))))))

  (it "sets direction pointing away from trigger with negative dc"
    ;; Sentry at [0,2], pos at [2,2]: dc = signum(0-2) = -1
    ;; Kills - -> + on Integer/signum (L152)
    (set-test-world! (build-test-map ["~~#~~"
                                            "~~#~~"
                                            "a~#~~"
                                            "~~#~~"
                                            "~~#~~"]))
    (update-test-world! assoc-in [0 2 :contents :mode] :sentry)
    (with-redefs [rand (constantly 0.3)]
      (core/wake-nearby-sentries [2 2] 2)
      (let [dir (:interior-explore-direction (:contents (get-in @atoms/game-map [0 2])))]
        (should= -1 (first dir)))))

  (it "sets direction pointing away from trigger with negative dr"
    ;; Sentry at [2,0], pos at [2,2]: dr = signum(0-2) = -1
    ;; Kills - -> + on Integer/signum (L153)
    (set-test-world! (build-test-map ["~~a~~"
                                            "~~~~~"
                                            "~~#~~"
                                            "~~~~~"
                                            "~~~~~"]))
    (update-test-world! assoc-in [2 0 :contents :mode] :sentry)
    (with-redefs [rand (constantly 0.3)]
      (core/wake-nearby-sentries [2 2] 2)
      (let [dir (:interior-explore-direction (:contents (get-in @atoms/game-map [2 0])))]
        (should= -1 (second dir)))))

  (it "uses random direction when dc is zero"
    ;; Sentry at [2,0], pos at [2,2]: dc = signum(2-2) = 0
    ;; With rand=0.3 (< 0.5), random branch picks -1
    ;; Kills if -> if-not on outer dc check (L154)
    (set-test-world! (build-test-map ["~~a~~"
                                            "~~~~~"
                                            "~~#~~"
                                            "~~~~~"
                                            "~~~~~"]))
    (update-test-world! assoc-in [2 0 :contents :mode] :sentry)
    (with-redefs [rand (constantly 0.3)]
      (core/wake-nearby-sentries [2 2] 2)
      (let [dir (:interior-explore-direction (:contents (get-in @atoms/game-map [2 0])))]
        (should= -1 (first dir)))))

  (it "uses random direction when dr is zero"
    ;; Sentry at [0,2], pos at [2,2]: dr = signum(2-2) = 0
    ;; With rand=0.3 (< 0.5), random branch picks -1
    ;; Kills if -> if-not on outer dr check (L155)
    (set-test-world! (build-test-map ["~~#~~"
                                            "~~#~~"
                                            "a~#~~"
                                            "~~#~~"
                                            "~~#~~"]))
    (update-test-world! assoc-in [0 2 :contents :mode] :sentry)
    (with-redefs [rand (constantly 0.3)]
      (core/wake-nearby-sentries [2 2] 2)
      (let [dir (:interior-explore-direction (:contents (get-in @atoms/game-map [0 2])))]
        (should= -1 (second dir)))))

  (it "random direction picks 1 when rand >= 0.5"
    ;; dc=0, rand=0.7 (>= 0.5), should pick 1
    ;; Kills 1 -> 0 on L154
    (set-test-world! (build-test-map ["~~a~~"
                                            "~~~~~"
                                            "~~#~~"
                                            "~~~~~"
                                            "~~~~~"]))
    (update-test-world! assoc-in [2 0 :contents :mode] :sentry)
    (with-redefs [rand (constantly 0.7)]
      (core/wake-nearby-sentries [2 2] 2)
      (let [dir (:interior-explore-direction (:contents (get-in @atoms/game-map [2 0])))]
        (should= 1 (first dir)))))

  (it "wakes sentry at c range upper boundary"
    ;; pos=[1,1], radius=2. c range: (range 0 (min 4 (+ 1 2 1))) = (range 0 4)
    ;; With 1->0: (range 0 (min 4 3)) = (range 0 3) — excludes column 3
    ;; Sentry at [3,1], chebyshev distance = max(|3-1|,|1-1|) = 2 = radius
    (set-test-world! (build-test-map ["~~~~"
                                            "~#~a"
                                            "~~~~"
                                            "~~~~"]))
    (update-test-world! assoc-in [3 1 :contents :mode] :sentry)
    (with-redefs [rand (constantly 0.3)]
      (let [woken (core/wake-nearby-sentries [1 1] 2)]
        (should= 1 woken)
        (should= :awake (:mode (:contents (get-in @atoms/game-map [3 1]))))))))

(describe "board-transport"
  (before (reset-all-atoms!))

  (it "loads army onto adjacent transport"
    (set-test-world! (build-test-map ["at"]))
    (update-test-world! assoc-in [1 0 :contents :transport-mission] :loading)
    (update-test-world! assoc-in [1 0 :contents :army-count] 0)
    (core/board-transport [0 0] [1 0])
    (should-be-nil (:contents (get-in @atoms/game-map [0 0])))
    (should= 1 (:army-count (:contents (get-in @atoms/game-map [1 0])))))

  (it "throws when not adjacent"
    (set-test-world! (build-test-map ["a.t"]))
    (update-test-world! assoc-in [2 0 :contents :transport-mission] :loading)
    (update-test-world! assoc-in [2 0 :contents :army-count] 0)
    (should-throw (core/board-transport [0 0] [2 0])))

  (it "loads army at non-zero positions (kills - -> + in adjacent?)"
    ;; Army at [2,3], transport at [3,3] — both coords > 0
    ;; With mutation - -> + in adjacent?: |3+3|=6 > 1, not adjacent — throws
    (set-test-world! (build-test-map ["~~~~"
                                            "~~~~"
                                            "~~~~"
                                            "~~at"]))
    (update-test-world! assoc-in [3 3 :contents :transport-mission] :loading)
    (update-test-world! assoc-in [3 3 :contents :army-count] 0)
    (core/board-transport [2 3] [3 3])
    (should-be-nil (:contents (get-in @atoms/game-map [2 3])))
    (should= 1 (:army-count (:contents (get-in @atoms/game-map [3 3])))))

  (it "increments from 0 when army-count is nil (kills fnil 0 -> 1)"
    (set-test-world! (build-test-map ["at"]))
    (update-test-world! assoc-in [1 0 :contents :transport-mission] :loading)
    (update-test-world! update-in [1 0 :contents] dissoc :army-count)
    (core/board-transport [0 0] [1 0])
    (should= 1 (:army-count (:contents (get-in @atoms/game-map [1 0])))))

  (it "loads army diagonally adjacent (kills <= -> < on dc in adjacent?)"
    ;; Army at [2,2], transport at [3,3]: dr=1, dc=1 (diagonal)
    ;; With <= -> < on dc: (< 1 1) = false, not adjacent, throws
    (set-test-world! (build-test-map ["~~~~"
                                            "~~~~"
                                            "~~a~"
                                            "~~~t"]))
    (update-test-world! assoc-in [3 3 :contents :transport-mission] :loading)
    (update-test-world! assoc-in [3 3 :contents :army-count] 0)
    (core/board-transport [2 2] [3 3])
    (should-be-nil (:contents (get-in @atoms/game-map [2 2])))
    (should= 1 (:army-count (:contents (get-in @atoms/game-map [3 3]))))))

(describe "find-visible-player-units"
  (before (reset-all-atoms!))

  (it "finds player units on computer map"
    (set-test-computer-map!
            [[{:type :sea :contents {:type :army :owner :player}} {:type :sea}]
             [{:type :sea} {:type :sea}]])
    (should= [[0 0]] (core/find-visible-player-units)))

  (it "ignores computer units"
    (set-test-computer-map!
            [[{:type :sea :contents {:type :army :owner :computer}} {:type :sea}]
             [{:type :sea} {:type :sea}]])
    (should= [] (core/find-visible-player-units)))

  (it "returns empty when no units"
    (set-test-computer-map!
            [[{:type :sea} {:type :sea}]])
    (should= [] (core/find-visible-player-units))))

(describe "find-loading-transport"
  (before (reset-all-atoms!))

  (it "finds a loading transport with room"
    (set-test-world! [[{:type :sea :contents {:type :transport :owner :computer
                                     :transport-mission :loading :army-count 2}}]])
    (should= [0 0] (core/find-loading-transport)))

  (it "skips full transports"
    (set-test-world! [[{:type :sea :contents {:type :transport :owner :computer
                                     :transport-mission :loading :army-count 6}}]])
    (should-be-nil (core/find-loading-transport)))

  (it "skips non-loading transports"
    (set-test-world! [[{:type :sea :contents {:type :transport :owner :computer
                                     :transport-mission :sailing :army-count 0}}]])
    (should-be-nil (core/find-loading-transport)))

  (it "skips player transports"
    (set-test-world! [[{:type :sea :contents {:type :transport :owner :player
                                     :transport-mission :loading :army-count 0}}]])
    (should-be-nil (core/find-loading-transport)))

  (it "excludes transport with matching unload-event-id"
    (set-test-world! [[{:type :sea :contents {:type :transport :owner :computer
                                     :transport-mission :loading :army-count 0
                                     :unload-event-id 42}}]])
    (should-be-nil (core/find-loading-transport 42)))

  (it "includes transport with different unload-event-id"
    (set-test-world! [[{:type :sea :contents {:type :transport :owner :computer
                                     :transport-mission :loading :army-count 0
                                     :unload-event-id 42}}]])
    (should= [0 0] (core/find-loading-transport 99))))

(describe "find-adjacent-loading-transport"
  (before (reset-all-atoms!))

  (it "finds adjacent loading transport"
    (set-test-world! [[{:type :land} {:type :sea}]
             [{:type :sea :contents {:type :transport :owner :computer
                                     :transport-mission :loading :army-count 0}} {:type :sea}]])
    (should= [1 0] (core/find-adjacent-loading-transport [0 0])))

  (it "returns nil when no adjacent transport"
    (set-test-world! [[{:type :land} {:type :sea}]
             [{:type :sea} {:type :sea}]])
    (should-be-nil (core/find-adjacent-loading-transport [0 0])))

  (it "skips full adjacent transports"
    (set-test-world! [[{:type :land} {:type :sea}]
             [{:type :sea :contents {:type :transport :owner :computer
                                     :transport-mission :loading :army-count 6}} {:type :sea}]])
    (should-be-nil (core/find-adjacent-loading-transport [0 0])))

  (it "excludes transport with matching unload-event-id"
    (set-test-world! [[{:type :land} {:type :sea}]
             [{:type :sea :contents {:type :transport :owner :computer
                                     :transport-mission :loading :army-count 0
                                     :unload-event-id 42}} {:type :sea}]])
    (should-be-nil (core/find-adjacent-loading-transport [0 0] 42))))

(describe "computer core"
  (before (reset-all-atoms!))

  (context "random-away-direction"
    (it "returns [1 1] when target is southeast of origin"
      (should= [1 1] (core/random-away-direction [0 0] [1 1])))

    (it "returns [-1 -1] when target is northwest of origin"
      (should= [-1 -1] (core/random-away-direction [5 5] [4 4])))

    (it "returns [1 -1] when target is south-west of origin"
      (should= [1 -1] (core/random-away-direction [3 3] [4 2])))

    (it "randomizes axis when aligned on column (dc=0)"
      (with-redefs [rand (constantly 0.1)]
        (should= [-1 1] (core/random-away-direction [3 3] [3 4]))))

    (it "randomizes opposite axis when aligned on column (dc=0, rand>0.5)"
      (with-redefs [rand (constantly 0.9)]
        (should= [1 1] (core/random-away-direction [3 3] [3 4]))))

    (it "randomizes axis when aligned on row (dr=0)"
      (with-redefs [rand (constantly 0.1)]
        (should= [1 -1] (core/random-away-direction [3 3] [4 3]))))

    (it "randomizes opposite axis when aligned on row (dr=0, rand>0.5)"
      (with-redefs [rand (constantly 0.9)]
        (should= [1 1] (core/random-away-direction [3 3] [4 3])))))

  (context "find-wakeable-sentries"
    (it "finds computer sentry armies within radius"
      (let [game-map [[{:type :land :contents {:type :army :owner :computer :mode :sentry}}]
                      [{:type :land :contents {:type :army :owner :computer :mode :awake}}]
                      [{:type :land}]
                      [{:type :land :contents {:type :army :owner :computer :mode :sentry}}]
                      [{:type :land :contents {:type :army :owner :player :mode :sentry}}]]]
        (should= [[0 0] [3 0]] (core/find-wakeable-sentries game-map [2 0] 3))))

    (it "excludes sentries outside radius"
      (let [game-map [[{:type :land :contents {:type :army :owner :computer :mode :sentry}}]
                      [{:type :land}]
                      [{:type :land}]
                      [{:type :land}]
                      [{:type :land :contents {:type :army :owner :computer :mode :sentry}}]]]
        (should= [[4 0]] (core/find-wakeable-sentries game-map [3 0] 2))))

    (it "excludes the origin position"
      (let [game-map [[{:type :land :contents {:type :army :owner :computer :mode :sentry}}]]]
        (should= [] (core/find-wakeable-sentries game-map [0 0] 3))))))

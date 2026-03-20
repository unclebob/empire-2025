(ns empire.computer.shared.threat-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.shared.threat :as threat]
            [empire.test.utils :refer [reset-all-atoms! set-test-computer-map!]]))

(describe "unit-threat"
  (it "returns 10 for battleship"
    (should= 10 (threat/unit-threat :battleship)))

  (it "returns 8 for carrier"
    (should= 8 (threat/unit-threat :carrier)))

  (it "returns 6 for destroyer"
    (should= 6 (threat/unit-threat :destroyer)))

  (it "returns 5 for submarine"
    (should= 5 (threat/unit-threat :submarine)))

  (it "returns 4 for fighter"
    (should= 4 (threat/unit-threat :fighter)))

  (it "returns 3 for patrol-boat"
    (should= 3 (threat/unit-threat :patrol-boat)))

  (it "returns 2 for army"
    (should= 2 (threat/unit-threat :army)))

  (it "returns 1 for transport"
    (should= 1 (threat/unit-threat :transport)))

  (it "returns 0 for unknown type"
    (should= 0 (threat/unit-threat :satellite))))

(describe "threat-level"
  (it "returns 0 when no enemies nearby"
    (let [computer-map [[{:type :sea} {:type :sea} {:type :sea}]
                          [{:type :sea} {:type :sea} {:type :sea}]
                          [{:type :sea} {:type :sea} {:type :sea}]]]
      (should= 0 (threat/threat-level computer-map [1 1]))))

  (it "sums threat from adjacent player units"
    (let [computer-map [[{:type :sea :contents {:type :army :owner :player}} {:type :sea}]
                          [{:type :sea :contents {:type :destroyer :owner :player}} {:type :sea}]]]
      (should= 8 (threat/threat-level computer-map [0 0]))))

  (it "ignores computer-owned units"
    (let [computer-map [[{:type :sea :contents {:type :destroyer :owner :computer}} {:type :sea}]
                          [{:type :sea} {:type :sea}]]]
      (should= 0 (threat/threat-level computer-map [0 0]))))

  (it "ignores cells beyond radius 2"
    ;; 6x1 map: player destroyer at [5,0], checking threat at [0,0]
    (let [computer-map [[{:type :sea}] [{:type :sea}] [{:type :sea}]
                          [{:type :sea}] [{:type :sea}]
                          [{:type :sea :contents {:type :destroyer :owner :player}}]]]
      (should= 0 (threat/threat-level computer-map [0 0]))))

  (it "includes units at radius 2"
    ;; 5x1 map: player army at [4,0], checking threat at [2,0]
    (let [computer-map [[{:type :sea}] [{:type :sea}] [{:type :sea}]
                          [{:type :sea}] [{:type :sea :contents {:type :army :owner :player}}]]]
      (should= 2 (threat/threat-level computer-map [2 0]))))

  (it "handles nil cells gracefully"
    (let [computer-map [[{:type :sea}]]]
      (should= 0 (threat/threat-level computer-map [0 0]))))

  (it "ignores cells with no contents"
    (let [computer-map [[{:type :sea} {:type :sea}]
                          [{:type :sea} {:type :land}]]]
      (should= 0 (threat/threat-level computer-map [0 0]))))

  (it "detects threat asymmetrically (enemy only at positive offset)"
    ;; 3x1 map: [0,0]=empty [1,0]=checking [2,0]=player army
    ;; Kills + -> - on coordinate offsets: with -, [2,0] would become [-2,0]
    (let [computer-map [[{:type :sea}] [{:type :sea}]
                          [{:type :sea :contents {:type :army :owner :player}}]]]
      (should= 2 (threat/threat-level computer-map [1 0]))))

  (it "detects threat at positive row offset"
    ;; 1x3 map: row0=empty, row1=checking, row2=player army
    (let [computer-map [[{:type :sea} {:type :sea}
                           {:type :sea :contents {:type :army :owner :player}}]]]
      (should= 2 (threat/threat-level computer-map [0 1]))))

  (it "excludes unit at radius 3 (just beyond range)"
    ;; 7x1 map: player army at [6,0], checking at [3,0]. Distance=3 > radius=2
    (let [computer-map [[{:type :sea}] [{:type :sea}] [{:type :sea}]
                          [{:type :sea}] [{:type :sea}] [{:type :sea}]
                          [{:type :sea :contents {:type :army :owner :player}}]]]
      (should= 0 (threat/threat-level computer-map [3 0])))))

(describe "distance"
  (it "returns 0 for same position"
    (should= 0 (threat/distance [3 3] [3 3])))

  (it "returns Manhattan distance"
    (should= 5 (threat/distance [0 0] [3 2])))

  (it "handles negative direction"
    (should= 7 (threat/distance [5 5] [2 1])))

  (it "computes positive x distance correctly"
    ;; Kills - -> + on x component: abs(5-0)=5, not abs(0+5-0)=5 but with +: abs(0+0)=0
    (should= 5 (threat/distance [0 0] [5 0])))

  (it "computes positive y distance correctly"
    (should= 3 (threat/distance [0 0] [0 3]))))

(describe "safe-moves"
  (before (reset-all-atoms!))

  (it "sorts moves by threat when unit is damaged"
    (let [computer-map [[{:type :sea} {:type :sea}]
                          [{:type :sea :contents {:type :army :owner :player}} {:type :sea}]]
          unit {:type :destroyer :hits 1}
          moves [[0 0] [1 0]]]
      ;; [1,0] is adjacent to player army, [0,0] is not
      ;; Damaged unit should prefer [0,0] (lower threat)
      (let [result (threat/safe-moves computer-map [0 0] unit moves)]
        (should= [0 0] (first result)))))

  (it "returns moves unchanged when at full health"
    (let [computer-map [[{:type :sea :contents {:type :army :owner :player}} {:type :sea}]
                          [{:type :sea} {:type :sea}]]
          unit {:type :destroyer :hits 3}
          moves [[1 0] [0 0]]]
      (should= moves (threat/safe-moves computer-map [0 0] unit moves))))

  (it "returns moves unchanged when hits equals max (not damaged)"
    ;; 6x1 map: player battleship at [0,0]. Moves [1,0] (threat=10) and [5,0] (threat=0).
    ;; At full health, order should be preserved: [1,0] first.
    ;; With <= mutation, damaged?=true → sorted → [5,0] first (lower threat).
    (let [computer-map [[{:type :sea :contents {:type :battleship :owner :player}}]
                          [{:type :sea}] [{:type :sea}] [{:type :sea}]
                          [{:type :sea}] [{:type :sea}]]
          unit {:type :destroyer :hits 3}
          moves [[1 0] [5 0]]]
      (should= [[1 0] [5 0]] (threat/safe-moves computer-map [3 0] unit moves)))))

(describe "should-retreat?"
  (before (reset-all-atoms!))

  (it "returns true when damaged and under threat"
    (let [computer-map [[{:type :sea :contents {:type :destroyer :owner :player}} {:type :sea}]
                          [{:type :sea} {:type :sea}]]
          unit {:type :destroyer :hits 1}]
      (should (threat/should-retreat? [0 0] unit computer-map))))

  (it "returns false when damaged but no threat"
    (let [computer-map [[{:type :sea} {:type :sea}]
                          [{:type :sea} {:type :sea}]]
          ;; Destroyer has 3 max hits; hits=2 is damaged but above 50%
          unit {:type :destroyer :hits 2}]
      (should-not (threat/should-retreat? [0 0] unit computer-map))))

  (it "returns true for loaded transport under high threat"
    (let [computer-map [[{:type :sea :contents {:type :battleship :owner :player}} {:type :sea}]
                          [{:type :sea} {:type :sea}]]
          unit {:type :transport :hits 1 :army-count 2}]
      (should (threat/should-retreat? [0 0] unit computer-map))))

  (it "returns false for empty transport under moderate threat"
    (let [computer-map [[{:type :sea :contents {:type :army :owner :player}} {:type :sea}]
                          [{:type :sea} {:type :sea}]]
          unit {:type :transport :hits 1 :army-count 0}]
      (should-not (threat/should-retreat? [0 0] unit computer-map))))

  (it "returns false for empty transport even under high threat"
    ;; Kills > -> >= on army-count: (>= 0 0) would be true
    ;; Also kills 0 -> 1 default: (:army-count unit 1) would default to 1
    (let [computer-map [[{:type :sea :contents {:type :battleship :owner :player}} {:type :sea}]
                          [{:type :sea} {:type :sea}]]
          ;; Transport at full health (1 hit), empty, threat=10 > 5
          unit {:type :transport :hits 1 :army-count 0}]
      (should-not (threat/should-retreat? [0 0] unit computer-map))))

  (it "returns true for transport with exactly 1 army under high threat"
    ;; Kills 0 -> 1 on comparison: (> 1 1) would be false
    (let [computer-map [[{:type :sea :contents {:type :battleship :owner :player}} {:type :sea}]
                          [{:type :sea} {:type :sea}]]
          unit {:type :transport :hits 1 :army-count 1}]
      (should (threat/should-retreat? [0 0] unit computer-map))))

  (it "returns false for transport without army-count key under high threat"
    ;; Kills 0 -> 1 on default: (:army-count unit 1) would give 1 instead of 0
    (let [computer-map [[{:type :sea :contents {:type :battleship :owner :player}} {:type :sea}]
                          [{:type :sea} {:type :sea}]]
          unit {:type :transport :hits 1}]  ;; no :army-count key
      (should-not (threat/should-retreat? [0 0] unit computer-map))))

  (it "returns true when severely damaged below 50%"
    (let [computer-map [[{:type :sea} {:type :sea}]
                          [{:type :sea} {:type :sea}]]
          ;; Battleship has 10 hits; 4 < 10/2 = 5
          unit {:type :battleship :hits 4}]
      (should (threat/should-retreat? [0 0] unit computer-map))))

  (it "returns false at full health with no threat"
    (let [computer-map [[{:type :sea} {:type :sea}]
                          [{:type :sea} {:type :sea}]]
          unit {:type :destroyer :hits 3}]
      (should-not (threat/should-retreat? [0 0] unit computer-map))))

  (it "returns false when damaged with threat <= 3"
    ;; Kills L60 < -> <= and > -> >= : need threat exactly 3
    ;; army=2 + transport=1 = 3; damaged destroyer with hits 2 (above 50%)
    (let [computer-map [[{:type :sea :contents {:type :army :owner :player}}
                           {:type :sea :contents {:type :transport :owner :player}}]
                          [{:type :sea} {:type :sea}]]
          unit {:type :destroyer :hits 2}]
      (should-not (threat/should-retreat? [0 0] unit computer-map))))

  (it "returns true when damaged with threat > 3"
    ;; Kills L60 > -> >= boundary: need threat exactly 4
    ;; fighter=4; damaged destroyer with hits 2
    (let [computer-map [[{:type :sea :contents {:type :fighter :owner :player}} {:type :sea}]
                          [{:type :sea} {:type :sea}]]
          unit {:type :destroyer :hits 2}]
      (should (threat/should-retreat? [0 0] unit computer-map))))

  (it "returns false for loaded transport with threat <= 5"
    ;; Kills L63 > -> >= : need threat exactly 5
    ;; submarine=5; transport with armies
    (let [computer-map [[{:type :sea :contents {:type :submarine :owner :player}} {:type :sea}]
                          [{:type :sea} {:type :sea}]]
          unit {:type :transport :hits 1 :army-count 2}]
      (should-not (threat/should-retreat? [0 0] unit computer-map))))

  (it "returns true for loaded transport with threat > 5"
    ;; Kills L63 boundary: need threat exactly 6
    ;; destroyer=6; transport with armies
    (let [computer-map [[{:type :sea :contents {:type :destroyer :owner :player}} {:type :sea}]
                          [{:type :sea} {:type :sea}]]
          unit {:type :transport :hits 1 :army-count 2}]
      (should (threat/should-retreat? [0 0] unit computer-map))))

  (it "returns false for non-transport with armies field"
    ;; Kills L62 = -> not= : only transports trigger this branch
    (let [computer-map [[{:type :sea :contents {:type :battleship :owner :player}} {:type :sea}]
                          [{:type :sea} {:type :sea}]]
          ;; Battleship with 8 hits at full health, but carries no armies anyway
          unit {:type :destroyer :hits 3 :army-count 2}]
      (should-not (threat/should-retreat? [0 0] unit computer-map))))

  (it "returns false when at exactly 50% health"
    ;; Kills L66 < -> <= : battleship 10 max, hits=5 = 10/2
    (let [computer-map [[{:type :sea} {:type :sea}]
                          [{:type :sea} {:type :sea}]]
          unit {:type :battleship :hits 5}]
      (should-not (threat/should-retreat? [0 0] unit computer-map)))))

(describe "find-nearest-friendly-base"
  (before (reset-all-atoms!))

  (it "finds nearest computer city"
    (set-test-computer-map! [[{:type :sea} {:type :sea}]
                                 [{:type :sea} {:type :sea}]
                                 [{:type :city :city-status :computer} {:type :sea}]])
    (should= [2 0] (threat/find-nearest-friendly-base [0 0] :destroyer)))

  (it "returns nil when no computer cities exist"
    (set-test-computer-map! [[{:type :sea} {:type :sea}]
                                 [{:type :sea} {:type :sea}]])
    (should-be-nil (threat/find-nearest-friendly-base [0 0] :destroyer)))

  (it "picks closest among multiple cities"
    (set-test-computer-map! [[{:type :city :city-status :computer} {:type :sea} {:type :sea}]
                                 [{:type :sea} {:type :sea} {:type :sea}]
                                 [{:type :sea} {:type :sea} {:type :sea}]
                                 [{:type :city :city-status :computer} {:type :sea} {:type :sea}]])
    ;; From [1 1], city at [0 0] is distance 2, city at [3 0] is distance 3
    (should= [0 0] (threat/find-nearest-friendly-base [1 1] :destroyer)))

  (it "ignores player and free cities"
    (set-test-computer-map! [[{:type :city :city-status :player} {:type :sea}]
                                 [{:type :city :city-status :free} {:type :sea}]
                                 [{:type :city :city-status :computer} {:type :sea}]])
    (should= [2 0] (threat/find-nearest-friendly-base [0 0] :destroyer))))

(describe "retreat-move"
  (before (reset-all-atoms!))

  (it "picks move toward friendly base with lowest threat"
    (set-test-computer-map! [[{:type :sea} {:type :sea}]
                                 [{:type :sea} {:type :sea}]
                                 [{:type :city :city-status :computer} {:type :sea}]])
    (let [computer-map (test-utils/read-test-state :computer-map)
          unit {:type :destroyer :hits 1}
          moves [[0 0] [1 0]]]
      ;; Base at [2,0]; [1,0] is closer to base
      (should= [1 0] (threat/retreat-move [0 1] unit computer-map moves))))

  (it "returns nil when no passable moves"
    (set-test-computer-map! [[{:type :sea} {:type :sea}]
                                 [{:type :city :city-status :computer} {:type :sea}]])
    (let [unit {:type :destroyer :hits 1}]
      (should-be-nil (threat/retreat-move [0 0] unit (test-utils/read-test-state :computer-map) []))))

  (it "returns nil when no friendly base exists"
    (set-test-computer-map! [[{:type :sea} {:type :sea}]
                                 [{:type :sea} {:type :sea}]])
    (let [unit {:type :destroyer :hits 1}
          moves [[0 0] [1 0]]]
      (should-be-nil (threat/retreat-move [0 0] unit (test-utils/read-test-state :computer-map) moves))))

  (it "prefers safe move farther from base over dangerous move near base"
    ;; Base at [0,0]. Move A=[1,0] near base but near player battleship.
    ;; Move B=[4,0] far from base but safe.
    ;; Score = dist + 2*threat. A: 1+20=21. B: 4+0=4. B wins.
    ;; With -: A: 1-20=-19. B: 4-0=4. A wins (wrong).
    (set-test-computer-map! [[{:type :city :city-status :computer} {:type :sea}]
                                 [{:type :sea :contents {:type :battleship :owner :player}} {:type :sea}]
                                 [{:type :sea} {:type :sea}]
                                 [{:type :sea} {:type :sea}]
                                 [{:type :sea} {:type :sea}]])
    (let [computer-map (test-utils/read-test-state :computer-map)
          unit {:type :destroyer :hits 1}
          moves [[1 0] [4 0]]]
      (should= [4 0] (threat/retreat-move [2 0] unit computer-map moves)))))

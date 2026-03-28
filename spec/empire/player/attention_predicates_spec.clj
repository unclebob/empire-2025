(ns empire.player.attention-predicates-spec
  (:require [empire.player.attention :as attention]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map get-test-city get-test-unit reset-all-atoms! set-test-player-map! set-test-unit set-test-world! update-test-world!]]
            [speclj.core :refer :all]))

(describe "is-city-needing-attention?"
  (before (reset-all-atoms!))

  (it "returns true for player city at first attention position"
    (let [cell {:type :city :city-status :player}
          clicked-coords [0 0]
          attention-coords [[0 0] [1 1]]]
      (should (attention/is-city-needing-attention? cell clicked-coords attention-coords))))

  (it "returns false when city-status is not player"
    (let [cell {:type :city :city-status :computer}
          clicked-coords [0 0]
          attention-coords [[0 0]]]
      (should-not (attention/is-city-needing-attention? cell clicked-coords attention-coords))))

  (it "returns false when cell is not a city"
    (let [cell {:type :land :city-status :player}
          clicked-coords [0 0]
          attention-coords [[0 0]]]
      (should-not (attention/is-city-needing-attention? cell clicked-coords attention-coords))))

  (it "returns false when clicked coords differ from first attention coords"
    (let [cell {:type :city :city-status :player}
          clicked-coords [1 1]
          attention-coords [[0 0]]]
      (should-not (attention/is-city-needing-attention? cell clicked-coords attention-coords))))

  (it "returns false when attention-coords is empty"
    (let [cell {:type :city :city-status :player}
          clicked-coords [0 0]
          attention-coords []]
      (should-not (attention/is-city-needing-attention? cell clicked-coords attention-coords))))

  (it "returns false for free city"
    (let [cell {:type :city :city-status :free}
          clicked-coords [0 0]
          attention-coords [[0 0]]]
      (should-not (attention/is-city-needing-attention? cell clicked-coords attention-coords)))))

(describe "is-unit-needing-attention?"
  (before (reset-all-atoms!))

  (it "returns true when attention coords contain a unit"
    (set-test-world! (build-test-map ["A"]))
    (should (attention/is-unit-needing-attention? [[0 0]])))

  (it "returns false when attention coords is empty"
    (should-not (attention/is-unit-needing-attention? [])))

  (it "returns false when cell has no contents and no airport fighters"
    (set-test-world! (build-test-map ["#"]))
    (should-not (attention/is-unit-needing-attention? [[0 0]])))

  (it "returns true when city has awake airport fighters"
    (set-test-world! (assoc-in (build-test-map ["O"]) [0 0 :fighter-count] 1))
    (should (attention/is-unit-needing-attention? [[0 0]]))))

(describe "needs-attention?"
  (before (reset-all-atoms!))

  (it "returns true for awake player unit"
    (set-test-player-map! (build-test-map ["A"]))
    (set-test-unit (test-utils/player-map-atom) "A" :mode :awake)
    (test-utils/set-test-state! :production {})
    (should (attention/needs-attention? 0 0)))

  (it "returns false for sleeping player unit"
    (set-test-player-map! (build-test-map ["A"]))
    (set-test-unit (test-utils/player-map-atom) "A" :mode :sentry)
    (test-utils/set-test-state! :production {})
    (should-not (attention/needs-attention? 0 0)))

  (it "returns true for player city without production"
    (set-test-player-map! (build-test-map ["O"]))
    (test-utils/set-test-state! :production {})
    (should (attention/needs-attention? 0 0)))

  (it "returns false for player city with production"
    (set-test-player-map! (build-test-map ["O"]))
    (test-utils/set-test-state! :production {[0 0] {:item :army :remaining 5}})
    (should-not (attention/needs-attention? 0 0)))

  (it "returns true for city with awake airport fighter"
    (set-test-player-map! (assoc-in (build-test-map ["O"]) [0 0 :fighter-count] 1))
    (test-utils/set-test-state! :production {[0 0] :army})
    (should (attention/needs-attention? 0 0)))

  (it "returns false for computer city"
    (set-test-player-map! (build-test-map ["X"]))
    (test-utils/set-test-state! :production {})
    (should-not (attention/needs-attention? 0 0)))

  (it "returns true for carrier with awake fighters"
    (set-test-player-map! (build-test-map ["C"]))
    (set-test-unit (test-utils/player-map-atom) "C" :mode :sentry :awake-fighters 1)
    (test-utils/set-test-state! :production {})
    (should (attention/needs-attention? 0 0)))

  (it "returns true for transport with awake armies"
    (set-test-player-map! (build-test-map ["T"]))
    (set-test-unit (test-utils/player-map-atom) "T" :mode :sentry :awake-armies 1)
    (test-utils/set-test-state! :production {})
    (should (attention/needs-attention? 0 0)))

  (it "returns true for satellite without target"
    (set-test-player-map! (build-test-map ["V"]))
    (set-test-unit (test-utils/player-map-atom) "V" :mode :awake :turns-remaining 50)
    (test-utils/set-test-state! :production {})
    (should (attention/needs-attention? 0 0)))

  (it "returns false for satellite with target"
    (set-test-player-map! (build-test-map ["V"]))
    (set-test-unit (test-utils/player-map-atom) "V" :mode :awake :target [5 5] :turns-remaining 50)
    (test-utils/set-test-state! :production {})
    (should-not (attention/needs-attention? 0 0))))

(describe "item-needs-attention?"
  (before (reset-all-atoms!))

  (it "returns true for awake unit"
    (set-test-world! (build-test-map ["A"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :awake)
    (let [unit-coords (:pos (get-test-unit (test-utils/game-map-atom) "A"))]
      (should (attention/item-needs-attention? unit-coords))))

  (it "returns false for awake computer fighter sitting in a player city"
    (set-test-world! (build-test-map ["O"]))
    (update-test-world! assoc-in [0 0 :contents] {:type :fighter :owner :computer :mode :awake :fuel 20})
    (test-utils/set-test-state! :production {[0 0] :army})
    (should-not (attention/item-needs-attention? [0 0])))

  (it "returns false for sleeping unit"
    (set-test-world! (build-test-map ["A"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :sentry)
    (let [unit-coords (:pos (get-test-unit (test-utils/game-map-atom) "A"))]
      (should-not (attention/item-needs-attention? unit-coords))))

  (it "returns false for moving unit"
    (set-test-world! (build-test-map ["A"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :moving)
    (let [unit-coords (:pos (get-test-unit (test-utils/game-map-atom) "A"))]
      (should-not (attention/item-needs-attention? unit-coords))))

  (it "returns true for player city without production"
    (set-test-world! (build-test-map ["O"]))
    (test-utils/set-test-state! :production {})
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))]
      (should (attention/item-needs-attention? city-coords))))

  (it "returns false for player city with production"
    (set-test-world! (build-test-map ["O"]))
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))]
      (test-utils/set-test-state! :production {city-coords :army})
      (should-not (attention/item-needs-attention? city-coords))))

  (it "returns true for carrier with awake fighters"
    (set-test-world! (build-test-map ["C"]))
    (set-test-unit (test-utils/game-map-atom) "C" :mode :sentry :awake-fighters 1)
    (let [unit-coords (:pos (get-test-unit (test-utils/game-map-atom) "C"))]
      (should (attention/item-needs-attention? unit-coords))))

  (it "returns true for transport with awake armies"
    (set-test-world! (build-test-map ["T"]))
    (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :awake-armies 1)
    (let [unit-coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))]
      (should (attention/item-needs-attention? unit-coords))))

  (it "returns true for satellite without target"
    (set-test-world! (build-test-map ["V"]))
    (set-test-unit (test-utils/game-map-atom) "V" :mode :awake :turns-remaining 50)
    (let [unit-coords (:pos (get-test-unit (test-utils/game-map-atom) "V"))]
      (should (attention/item-needs-attention? unit-coords))))

  (it "returns false for satellite with target"
    (set-test-world! (build-test-map ["V"]))
    (set-test-unit (test-utils/game-map-atom) "V" :mode :awake :target [5 5] :turns-remaining 50)
    (let [unit-coords (:pos (get-test-unit (test-utils/game-map-atom) "V"))]
      (should-not (attention/item-needs-attention? unit-coords)))))

(describe "cells-needing-attention"
  (before (reset-all-atoms!))

  (it "returns coordinates of cells needing attention"
    (set-test-player-map! (build-test-map ["AO"
                                           "#X"]))
    (set-test-unit (test-utils/player-map-atom) "A" :mode :awake)
    (test-utils/set-test-state! :production {})
    (let [cells (attention/cells-needing-attention)]
      (should-contain [0 0] cells)
      (should-contain [1 0] cells)
      (should-not-contain [0 1] cells)
      (should-not-contain [1 1] cells)))

  (it "excludes player cities with production"
    (set-test-player-map! (build-test-map ["O"]))
    (test-utils/set-test-state! :production {[0 0] {:item :army}})
    (should= [] (attention/cells-needing-attention))))

(ns empire.attention-spec
  (:require [speclj.core :refer :all]
            [empire.attention :as attention]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [build-test-map get-test-city get-test-unit set-test-unit reset-all-atoms!]]))

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
    (reset! atoms/game-map @(build-test-map ["A"]))
    (should (attention/is-unit-needing-attention? [[0 0]])))

  (it "returns false when attention coords is empty"
    (should-not (attention/is-unit-needing-attention? [])))

  (it "returns false when cell has no contents and no airport fighters"
    (reset! atoms/game-map @(build-test-map ["#"]))
    (should-not (attention/is-unit-needing-attention? [[0 0]])))

  (it "returns true when city has awake airport fighters"
    (reset! atoms/game-map (assoc-in @(build-test-map ["O"]) [0 0 :awake-fighters] 1))
    (should (attention/is-unit-needing-attention? [[0 0]]))))

(describe "needs-attention?"
  (before (reset-all-atoms!))
  (it "returns true for awake player unit"
    (reset! atoms/player-map @(build-test-map ["A"]))
    (set-test-unit atoms/player-map "A" :mode :awake)
    (reset! atoms/production {})
    (should (attention/needs-attention? 0 0)))

  (it "returns false for sleeping player unit"
    (reset! atoms/player-map @(build-test-map ["A"]))
    (set-test-unit atoms/player-map "A" :mode :sentry)
    (reset! atoms/production {})
    (should-not (attention/needs-attention? 0 0)))

  (it "returns true for player city without production"
    (reset! atoms/player-map @(build-test-map ["O"]))
    (reset! atoms/production {})
    (should (attention/needs-attention? 0 0)))

  (it "returns false for player city with production"
    (reset! atoms/player-map @(build-test-map ["O"]))
    (reset! atoms/production {[0 0] {:item :army :remaining 5}})
    (should-not (attention/needs-attention? 0 0)))

  (it "returns true for city with awake airport fighter"
    (reset! atoms/player-map (assoc-in @(build-test-map ["O"]) [0 0 :awake-fighters] 1))
    (reset! atoms/production {[0 0] :army})
    (should (attention/needs-attention? 0 0)))

  (it "returns false for computer city"
    (reset! atoms/player-map @(build-test-map ["X"]))
    (reset! atoms/production {})
    (should-not (attention/needs-attention? 0 0)))

  (it "returns true for carrier with awake fighters"
    (reset! atoms/player-map @(build-test-map ["C"]))
    (set-test-unit atoms/player-map "C" :mode :sentry :awake-fighters 1)
    (reset! atoms/production {})
    (should (attention/needs-attention? 0 0)))

  (it "returns true for transport with awake armies"
    (reset! atoms/player-map @(build-test-map ["T"]))
    (set-test-unit atoms/player-map "T" :mode :sentry :awake-armies 1)
    (reset! atoms/production {})
    (should (attention/needs-attention? 0 0)))

  (it "returns true for satellite without target"
    (reset! atoms/player-map @(build-test-map ["V"]))
    (set-test-unit atoms/player-map "V" :mode :awake :turns-remaining 50)
    (reset! atoms/production {})
    (should (attention/needs-attention? 0 0)))

  (it "returns false for satellite with target"
    (reset! atoms/player-map @(build-test-map ["V"]))
    (set-test-unit atoms/player-map "V" :mode :awake :target [5 5] :turns-remaining 50)
    (reset! atoms/production {})
    (should-not (attention/needs-attention? 0 0))))

(describe "item-needs-attention?"
  (before (reset-all-atoms!))
  (it "returns true for awake unit"
    (reset! atoms/game-map @(build-test-map ["A"]))
    (set-test-unit atoms/game-map "A" :mode :awake)
    (let [unit-coords (:pos (get-test-unit atoms/game-map "A"))]
      (should (attention/item-needs-attention? unit-coords))))

  (it "returns false for sleeping unit"
    (reset! atoms/game-map @(build-test-map ["A"]))
    (set-test-unit atoms/game-map "A" :mode :sentry)
    (let [unit-coords (:pos (get-test-unit atoms/game-map "A"))]
      (should-not (attention/item-needs-attention? unit-coords))))

  (it "returns false for moving unit"
    (reset! atoms/game-map @(build-test-map ["A"]))
    (set-test-unit atoms/game-map "A" :mode :moving)
    (let [unit-coords (:pos (get-test-unit atoms/game-map "A"))]
      (should-not (attention/item-needs-attention? unit-coords))))

  (it "returns true for player city without production"
    (reset! atoms/game-map @(build-test-map ["O"]))
    (reset! atoms/production {})
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (should (attention/item-needs-attention? city-coords))))

  (it "returns false for player city with production"
    (reset! atoms/game-map @(build-test-map ["O"]))
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (reset! atoms/production {city-coords :army})
      (should-not (attention/item-needs-attention? city-coords))))

  (it "returns true for carrier with awake fighters"
    (reset! atoms/game-map @(build-test-map ["C"]))
    (set-test-unit atoms/game-map "C" :mode :sentry :awake-fighters 1)
    (let [unit-coords (:pos (get-test-unit atoms/game-map "C"))]
      (should (attention/item-needs-attention? unit-coords))))

  (it "returns true for transport with awake armies"
    (reset! atoms/game-map @(build-test-map ["T"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :awake-armies 1)
    (let [unit-coords (:pos (get-test-unit atoms/game-map "T"))]
      (should (attention/item-needs-attention? unit-coords))))

  (it "returns true for satellite without target"
    (reset! atoms/game-map @(build-test-map ["V"]))
    (set-test-unit atoms/game-map "V" :mode :awake :turns-remaining 50)
    (let [unit-coords (:pos (get-test-unit atoms/game-map "V"))]
      (should (attention/item-needs-attention? unit-coords))))

  (it "returns false for satellite with target"
    (reset! atoms/game-map @(build-test-map ["V"]))
    (set-test-unit atoms/game-map "V" :mode :awake :target [5 5] :turns-remaining 50)
    (let [unit-coords (:pos (get-test-unit atoms/game-map "V"))]
      (should-not (attention/item-needs-attention? unit-coords)))))

(describe "cells-needing-attention"
  (before (reset-all-atoms!))
  (it "returns coordinates of cells needing attention"
    (reset! atoms/player-map @(build-test-map ["AO"
                                               "#X"]))
    (set-test-unit atoms/player-map "A" :mode :awake)
    (reset! atoms/production {})
    (let [cells (attention/cells-needing-attention)]
      (should-contain [0 0] cells)
      (should-contain [0 1] cells)
      (should-not-contain [1 0] cells)
      (should-not-contain [1 1] cells)))

  (it "excludes player cities with production"
    (reset! atoms/player-map @(build-test-map ["O"]))
    (reset! atoms/production {[0 0] {:item :army}})
    (should= [] (attention/cells-needing-attention))))

(describe "set-attention-message"
  (before (reset-all-atoms!))
  (it "sets message for airport fighter"
    (reset! atoms/game-map @(build-test-map ["O"]))
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (swap! atoms/game-map assoc-in (conj city-coords :awake-fighters) 1)
      (swap! atoms/game-map assoc-in (conj city-coords :fighter-count) 1)
      (reset! atoms/message "")
      (attention/set-attention-message city-coords)
      (should-contain "Fighter" @atoms/message)
      (should-contain "needs attention" @atoms/message)))

  (it "sets message for carrier fighter"
    (reset! atoms/game-map @(build-test-map ["C"]))
    (set-test-unit atoms/game-map "C" :mode :sentry :awake-fighters 1 :fighter-count 2)
    (let [unit-coords (:pos (get-test-unit atoms/game-map "C"))]
      (reset! atoms/message "")
      (attention/set-attention-message unit-coords)
      (should-contain "Fighter" @atoms/message)
      (should-contain "carrier" @atoms/message)
      (should-contain "2 fighters" @atoms/message)))

  (it "sets message for army aboard transport"
    (reset! atoms/game-map @(build-test-map ["T"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :awake-armies 1 :army-count 3)
    (let [unit-coords (:pos (get-test-unit atoms/game-map "T"))]
      (reset! atoms/message "")
      (attention/set-attention-message unit-coords)
      (should-contain "Army" @atoms/message)
      (should-contain "transport" @atoms/message)
      (should-contain "3 armies" @atoms/message)))

  (it "sets message for regular awake army"
    (reset! atoms/game-map @(build-test-map ["A"]))
    (set-test-unit atoms/game-map "A" :mode :awake :hits 1)
    (let [unit-coords (:pos (get-test-unit atoms/game-map "A"))]
      (reset! atoms/message "")
      (attention/set-attention-message unit-coords)
      (should-contain "army" @atoms/message)
      (should-contain "needs attention" @atoms/message)))

  (it "sets message for transport with cargo count"
    (reset! atoms/game-map @(build-test-map ["T"]))
    (set-test-unit atoms/game-map "T" :mode :awake :hits 1 :army-count 4)
    (let [unit-coords (:pos (get-test-unit atoms/game-map "T"))]
      (reset! atoms/message "")
      (attention/set-attention-message unit-coords)
      (should-contain "transport" @atoms/message)
      (should-contain "4 armies" @atoms/message)))

  (it "sets message for carrier with cargo count"
    (reset! atoms/game-map @(build-test-map ["C"]))
    (set-test-unit atoms/game-map "C" :mode :awake :hits 8 :fighter-count 3)
    (let [unit-coords (:pos (get-test-unit atoms/game-map "C"))]
      (reset! atoms/message "")
      (attention/set-attention-message unit-coords)
      (should-contain "carrier" @atoms/message)
      (should-contain "3 fighters" @atoms/message)))

  (it "sets message for unit with reason"
    (reset! atoms/game-map @(build-test-map ["A"]))
    (set-test-unit atoms/game-map "A" :mode :awake :hits 1 :reason :somethings-in-the-way)
    (let [unit-coords (:pos (get-test-unit atoms/game-map "A"))]
      (reset! atoms/message "")
      (attention/set-attention-message unit-coords)
      (should-contain "army" @atoms/message)))

  (it "sets message for army adjacent to enemy city"
    (reset! atoms/game-map @(build-test-map ["AX"]))
    (set-test-unit atoms/game-map "A" :mode :awake :hits 1)
    (let [unit-coords (:pos (get-test-unit atoms/game-map "A"))]
      (reset! atoms/message "")
      (attention/set-attention-message unit-coords)
      (should-contain "army" @atoms/message)))

  (it "sets message for player city without production"
    (reset! atoms/game-map @(build-test-map ["O"]))
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (reset! atoms/message "")
      (attention/set-attention-message city-coords)
      (should-contain "City" @atoms/message)
      (should-contain "needs" @atoms/message))))

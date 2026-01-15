(ns empire.attention-spec
  (:require [speclj.core :refer :all]
            [empire.attention :as attention]
            [empire.atoms :as atoms]))

(describe "is-city-needing-attention?"
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
  (it "returns true when attention coords contain a unit"
    (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :player}}]])
    (should (attention/is-unit-needing-attention? [[0 0]])))

  (it "returns false when attention coords is empty"
    (should-not (attention/is-unit-needing-attention? [])))

  (it "returns false when cell has no contents and no airport fighters"
    (reset! atoms/game-map [[{:type :land}]])
    (should-not (attention/is-unit-needing-attention? [[0 0]])))

  (it "returns true when city has awake airport fighters"
    (reset! atoms/game-map [[{:type :city :city-status :player :awake-fighters 1}]])
    (should (attention/is-unit-needing-attention? [[0 0]]))))

(describe "needs-attention?"
  (it "returns true for awake player unit"
    (reset! atoms/player-map [[{:type :land :contents {:type :army :mode :awake :owner :player}}]])
    (reset! atoms/production {})
    (should (attention/needs-attention? 0 0)))

  (it "returns false for sleeping player unit"
    (reset! atoms/player-map [[{:type :land :contents {:type :army :mode :sentry :owner :player}}]])
    (reset! atoms/production {})
    (should-not (attention/needs-attention? 0 0)))

  (it "returns true for player city without production"
    (reset! atoms/player-map [[{:type :city :city-status :player}]])
    (reset! atoms/production {})
    (should (attention/needs-attention? 0 0)))

  (it "returns false for player city with production"
    (reset! atoms/player-map [[{:type :city :city-status :player}]])
    (reset! atoms/production {[0 0] {:item :army :remaining 5}})
    (should-not (attention/needs-attention? 0 0)))

  (it "returns true for city with awake airport fighter"
    (reset! atoms/player-map [[{:type :city :city-status :player :awake-fighters 1}]])
    (reset! atoms/production {[0 0] :army})
    (should (attention/needs-attention? 0 0)))

  (it "returns false for computer city"
    (reset! atoms/player-map [[{:type :city :city-status :computer}]])
    (reset! atoms/production {})
    (should-not (attention/needs-attention? 0 0)))

  (it "returns true for carrier with awake fighters"
    (reset! atoms/player-map [[{:type :sea :contents {:type :carrier :mode :sentry :owner :player :awake-fighters 1}}]])
    (reset! atoms/production {})
    (should (attention/needs-attention? 0 0)))

  (it "returns true for transport with awake armies"
    (reset! atoms/player-map [[{:type :sea :contents {:type :transport :mode :sentry :owner :player :awake-armies 1}}]])
    (reset! atoms/production {})
    (should (attention/needs-attention? 0 0)))

  (it "returns true for satellite without target"
    (reset! atoms/player-map [[{:type :land :contents {:type :satellite :mode :awake :owner :player :turns-remaining 50}}]])
    (reset! atoms/production {})
    (should (attention/needs-attention? 0 0)))

  (it "returns false for satellite with target"
    (reset! atoms/player-map [[{:type :land :contents {:type :satellite :mode :awake :owner :player :target [5 5] :turns-remaining 50}}]])
    (reset! atoms/production {})
    (should-not (attention/needs-attention? 0 0))))

(describe "item-needs-attention?"
  (it "returns true for awake unit"
    (reset! atoms/game-map [[{:type :land :contents {:type :army :mode :awake}}]])
    (should (attention/item-needs-attention? [0 0])))

  (it "returns false for sleeping unit"
    (reset! atoms/game-map [[{:type :land :contents {:type :army :mode :sentry}}]])
    (should-not (attention/item-needs-attention? [0 0])))

  (it "returns false for moving unit"
    (reset! atoms/game-map [[{:type :land :contents {:type :army :mode :moving}}]])
    (should-not (attention/item-needs-attention? [0 0])))

  (it "returns true for player city without production"
    (reset! atoms/game-map [[{:type :city :city-status :player}]])
    (reset! atoms/production {})
    (should (attention/item-needs-attention? [0 0])))

  (it "returns false for player city with production"
    (reset! atoms/game-map [[{:type :city :city-status :player}]])
    (reset! atoms/production {[0 0] :army})
    (should-not (attention/item-needs-attention? [0 0])))

  (it "returns true for carrier with awake fighters"
    (reset! atoms/game-map [[{:type :sea :contents {:type :carrier :mode :sentry :awake-fighters 1}}]])
    (should (attention/item-needs-attention? [0 0])))

  (it "returns true for transport with awake armies"
    (reset! atoms/game-map [[{:type :sea :contents {:type :transport :mode :sentry :awake-armies 1}}]])
    (should (attention/item-needs-attention? [0 0])))

  (it "returns true for satellite without target"
    (reset! atoms/game-map [[{:type :land :contents {:type :satellite :mode :awake :turns-remaining 50}}]])
    (should (attention/item-needs-attention? [0 0])))

  (it "returns false for satellite with target"
    (reset! atoms/game-map [[{:type :land :contents {:type :satellite :mode :awake :target [5 5] :turns-remaining 50}}]])
    (should-not (attention/item-needs-attention? [0 0]))))

(describe "cells-needing-attention"
  (it "returns coordinates of cells needing attention"
    (reset! atoms/player-map [[{:type :land :contents {:type :army :mode :awake :owner :player}}
                               {:type :city :city-status :player}]
                              [{:type :land}
                               {:type :city :city-status :computer}]])
    (reset! atoms/production {})
    (let [cells (attention/cells-needing-attention)]
      (should-contain [0 0] cells)
      (should-contain [0 1] cells)
      (should-not-contain [1 0] cells)
      (should-not-contain [1 1] cells)))

  (it "excludes player cities with production"
    (reset! atoms/player-map [[{:type :city :city-status :player}]])
    (reset! atoms/production {[0 0] {:item :army}})
    (should= [] (attention/cells-needing-attention))))

(describe "set-attention-message"
  (it "sets message for airport fighter"
    (reset! atoms/game-map [[{:type :city :city-status :player :awake-fighters 1 :fighter-count 1}]])
    (reset! atoms/message "")
    (attention/set-attention-message [0 0])
    (should-contain "Fighter" @atoms/message)
    (should-contain "needs attention" @atoms/message))

  (it "sets message for carrier fighter"
    (reset! atoms/game-map [[{:type :sea :contents {:type :carrier :mode :sentry :owner :player :awake-fighters 1 :fighter-count 2}}]])
    (reset! atoms/message "")
    (attention/set-attention-message [0 0])
    (should-contain "Fighter" @atoms/message)
    (should-contain "carrier" @atoms/message)
    (should-contain "2 fighters" @atoms/message))

  (it "sets message for army aboard transport"
    (reset! atoms/game-map [[{:type :sea :contents {:type :transport :mode :sentry :owner :player :awake-armies 1 :army-count 3}}]])
    (reset! atoms/message "")
    (attention/set-attention-message [0 0])
    (should-contain "Army" @atoms/message)
    (should-contain "transport" @atoms/message)
    (should-contain "3 armies" @atoms/message))

  (it "sets message for regular awake army"
    (reset! atoms/game-map [[{:type :land :contents {:type :army :mode :awake :owner :player :hits 1}}]])
    (reset! atoms/message "")
    (attention/set-attention-message [0 0])
    (should-contain "army" @atoms/message)
    (should-contain "needs attention" @atoms/message))

  (it "sets message for transport with cargo count"
    (reset! atoms/game-map [[{:type :sea :contents {:type :transport :mode :awake :owner :player :hits 1 :army-count 4}}]])
    (reset! atoms/message "")
    (attention/set-attention-message [0 0])
    (should-contain "transport" @atoms/message)
    (should-contain "4 armies" @atoms/message))

  (it "sets message for carrier with cargo count"
    (reset! atoms/game-map [[{:type :sea :contents {:type :carrier :mode :awake :owner :player :hits 8 :fighter-count 3}}]])
    (reset! atoms/message "")
    (attention/set-attention-message [0 0])
    (should-contain "carrier" @atoms/message)
    (should-contain "3 fighters" @atoms/message))

  (it "sets message for unit with reason"
    (reset! atoms/game-map [[{:type :land :contents {:type :army :mode :awake :owner :player :hits 1 :reason :somethings-in-the-way}}]])
    (reset! atoms/message "")
    (attention/set-attention-message [0 0])
    (should-contain "army" @atoms/message))

  (it "sets message for army adjacent to enemy city"
    (reset! atoms/game-map [[{:type :land :contents {:type :army :mode :awake :owner :player :hits 1}}
                             {:type :city :city-status :computer}]])
    (reset! atoms/message "")
    (attention/set-attention-message [0 0])
    (should-contain "army" @atoms/message))

  (it "sets message for player city without production"
    (reset! atoms/game-map [[{:type :city :city-status :player}]])
    (reset! atoms/message "")
    (attention/set-attention-message [0 0])
    (should-contain "City" @atoms/message)
    (should-contain "needs" @atoms/message)))

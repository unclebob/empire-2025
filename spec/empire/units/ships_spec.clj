(ns empire.config.units.ships-spec
  (:require [speclj.core :refer :all]
            [empire.config.units.ships :as ships]))

(describe "ships data-driven module"
  (context "patrol-boat configuration"
    (it "has speed of 4" (should= 4 (ships/config :patrol-boat :speed)))
    (it "has cost of 15" (should= 15 (ships/config :patrol-boat :cost)))
    (it "has 1 hit point" (should= 1 (ships/config :patrol-boat :hits)))
    (it "has strength of 1" (should= 1 (ships/config :patrol-boat :strength)))
    (it "displays as P" (should= "P" (ships/config :patrol-boat :display-char)))
    (it "has visibility radius of 1" (should= 1 (ships/config :patrol-boat :visibility-radius))))

  (context "destroyer configuration"
    (it "has speed of 2" (should= 2 (ships/config :destroyer :speed)))
    (it "has cost of 20" (should= 20 (ships/config :destroyer :cost)))
    (it "has 3 hit points" (should= 3 (ships/config :destroyer :hits)))
    (it "has strength of 1" (should= 1 (ships/config :destroyer :strength)))
    (it "displays as D" (should= "D" (ships/config :destroyer :display-char)))
    (it "has visibility radius of 1" (should= 1 (ships/config :destroyer :visibility-radius))))

  (context "submarine configuration"
    (it "has speed of 2" (should= 2 (ships/config :submarine :speed)))
    (it "has cost of 20" (should= 20 (ships/config :submarine :cost)))
    (it "has 2 hit points" (should= 2 (ships/config :submarine :hits)))
    (it "has strength of 3" (should= 3 (ships/config :submarine :strength)))
    (it "displays as S" (should= "S" (ships/config :submarine :display-char)))
    (it "has visibility radius of 1" (should= 1 (ships/config :submarine :visibility-radius))))

  (context "battleship configuration"
    (it "has speed of 2" (should= 2 (ships/config :battleship :speed)))
    (it "has cost of 40" (should= 40 (ships/config :battleship :cost)))
    (it "has 10 hit points" (should= 10 (ships/config :battleship :hits)))
    (it "has strength of 2" (should= 2 (ships/config :battleship :strength)))
    (it "displays as B" (should= "B" (ships/config :battleship :display-char)))
    (it "has visibility radius of 1" (should= 1 (ships/config :battleship :visibility-radius))))

  (context "shared behavior"
    (context "initial-state"
      (it "returns empty map"
        (should= {} (ships/initial-state))))

    (context "can-move-to?"
      (it "returns true for sea"
        (should (ships/can-move-to? {:type :sea})))
      (it "returns false for land"
        (should-not (ships/can-move-to? {:type :land})))
      (it "returns false for city"
        (should-not (ships/can-move-to? {:type :city})))
      (it "returns false for nil"
        (should-not (ships/can-move-to? nil))))

    (context "needs-attention?"
      (it "returns true when awake"
        (should (ships/needs-attention? {:mode :awake})))
      (it "returns false when sentry"
        (should-not (ships/needs-attention? {:mode :sentry})))
      (it "returns false when moving"
        (should-not (ships/needs-attention? {:mode :moving})))
      (it "returns false when exploring"
        (should-not (ships/needs-attention? {:mode :explore}))))))

(ns empire.game-mechanics.services.combat
  (:require [empire.game-mechanics.services.combat.resolution :as resolution]
            [empire.game-mechanics.services.combat.conquest :as conquest]))

;; resolution
(def apply-combat-result! resolution/apply-combat-result!)
(def format-combat-log resolution/format-combat-log)
(def format-combat-status resolution/format-combat-status)
(def format-combat-outcome resolution/format-combat-outcome)
(def fight-round resolution/fight-round)
(def resolve-combat resolution/resolve-combat)
(def drown-excess-cargo-world resolution/drown-excess-cargo-world)

;; conquest
(def dead-escort-destroyer? conquest/dead-escort-destroyer?)
(def dead-escort-transport? conquest/dead-escort-transport?)
(def clear-escort-on-death-world conquest/clear-escort-on-death-world)
(def clear-escort-on-death conquest/clear-escort-on-death)
(def conquer-city-contents-pure conquest/conquer-city-contents-pure)
(def conquer-city-contents conquest/conquer-city-contents)
(def hostile-city? conquest/hostile-city?)
(def attempt-city-conquest conquest/attempt-city-conquest)
(def attempt-conquest conquest/attempt-conquest)
(def attempt-fighter-overfly conquest/attempt-fighter-overfly)
(def hostile-unit? conquest/hostile-unit?)
(def attempt-attack conquest/attempt-attack)
(def attempt-coastal-army-attack conquest/attempt-coastal-army-attack)

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T11:47:44.060432-05:00", :module-hash "762264833", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 3, :hash "1345409433"} {:id "def/apply-combat-result!", :kind "def", :line 6, :end-line 6, :hash "2032504994"} {:id "def/format-combat-log", :kind "def", :line 7, :end-line 7, :hash "-1140673620"} {:id "def/format-combat-status", :kind "def", :line 8, :end-line 8, :hash "-1975676210"} {:id "def/fight-round", :kind "def", :line 9, :end-line 9, :hash "721080539"} {:id "def/resolve-combat", :kind "def", :line 10, :end-line 10, :hash "1203077251"} {:id "def/drown-excess-cargo-world", :kind "def", :line 11, :end-line 11, :hash "950164209"} {:id "def/dead-escort-destroyer?", :kind "def", :line 14, :end-line 14, :hash "-1902324880"} {:id "def/dead-escort-transport?", :kind "def", :line 15, :end-line 15, :hash "989071645"} {:id "def/clear-escort-on-death-world", :kind "def", :line 16, :end-line 16, :hash "-479277812"} {:id "def/clear-escort-on-death", :kind "def", :line 17, :end-line 17, :hash "933554483"} {:id "def/conquer-city-contents-pure", :kind "def", :line 18, :end-line 18, :hash "-1712274203"} {:id "def/conquer-city-contents", :kind "def", :line 19, :end-line 19, :hash "2031474709"} {:id "def/hostile-city?", :kind "def", :line 20, :end-line 20, :hash "1778985999"} {:id "def/attempt-city-conquest", :kind "def", :line 21, :end-line 21, :hash "-784800727"} {:id "def/attempt-conquest", :kind "def", :line 22, :end-line 22, :hash "-2037088854"} {:id "def/attempt-fighter-overfly", :kind "def", :line 23, :end-line 23, :hash "1054595604"} {:id "def/hostile-unit?", :kind "def", :line 24, :end-line 24, :hash "-164799648"} {:id "def/attempt-attack", :kind "def", :line 25, :end-line 25, :hash "1910738810"} {:id "def/attempt-coastal-army-attack", :kind "def", :line 26, :end-line 26, :hash "63597116"}]}
;; clj-mutate-manifest-end

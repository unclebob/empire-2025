(ns empire.computer.shared.stamping
  (:require [empire.game-mechanics.unit-stamping :as unit-stamping]))

(defn stamp-computer-fields
  [unit cell]
  (unit-stamping/stamp-computer-fields unit cell))

(defn backfill-missing-computer-unit-ids!
  []
  (unit-stamping/backfill-missing-computer-unit-ids!))

(defn apply-coast-walk-fields
  [unit item cell coords]
  (unit-stamping/apply-coast-walk-fields unit item cell coords))

(defn apply-random-explore-fields
  [unit item cell coords]
  (unit-stamping/apply-random-explore-fields unit item cell coords))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T23:16:20.354227-05:00", :module-hash "-2138598518", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 2, :hash "1343572095"} {:id "defn/stamp-computer-fields", :kind "defn", :line 4, :end-line 6, :hash "-1339812224"} {:id "defn/backfill-missing-computer-unit-ids!", :kind "defn", :line 8, :end-line 10, :hash "1225192560"} {:id "defn/apply-coast-walk-fields", :kind "defn", :line 12, :end-line 14, :hash "1168017948"} {:id "defn/apply-random-explore-fields", :kind "defn", :line 16, :end-line 18, :hash "45414821"}]}
;; clj-mutate-manifest-end

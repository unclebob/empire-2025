(ns empire.player.movement-decisions-spec
  (:require [empire.player.movement-decisions :as decisions]
            [speclj.core :refer :all]))

(describe "standard-movement-action"
  (it "returns coastal-army-attack first"
    (should= :coastal-army-attack
             (decisions/standard-movement-action :army false false true false)))

  (it "returns army-conquest for hostile city"
    (should= :army-conquest
             (decisions/standard-movement-action :army false true false false)))

  (it "returns fighter-overfly for hostile city"
    (should= :fighter-overfly
             (decisions/standard-movement-action :fighter false true false false)))

  (it "rejects undamaged ship entry"
    (should= :reject-undamaged-ship
             (decisions/standard-movement-action :destroyer false false false true)))

  (it "defaults to normal move"
    (should= :normal-move
             (decisions/standard-movement-action :army false false false false))))

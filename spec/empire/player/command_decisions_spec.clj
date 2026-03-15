(ns empire.player.command-decisions-spec
  (:require [empire.player.command-decisions :as decisions]
            [speclj.core :refer :all]))

(describe "attention-key-action"
  (context "unit decisions"
    (it "returns a move decision for a directional key"
      (should= {:scope :unit
                :action :move
                :direction [-1 -1]
                :extended? false}
               (decisions/attention-key-action :q {} {:owner :player})))

    (it "returns an extended move decision for a shift-style directional key"
      (should= {:scope :unit
                :action :move
                :direction [1 0]
                :extended? true}
               (decisions/attention-key-action :D {} {:owner :player})))

    (it "returns a mode decision for a player unit"
      (should= {:scope :unit :action :look-around}
               (decisions/attention-key-action :l {} {:owner :player})))

    (it "ignores keys for non-player units"
      (should-be-nil (decisions/attention-key-action :space {} {:owner :computer}))))

  (context "city decisions"
    (it "returns a production decision for a player city"
      (should= {:scope :city :action :set-production :item :army}
               (decisions/attention-key-action :a {:type :city :city-status :player} nil)))

    (it "returns a clear production decision for x"
      (should= {:scope :city :action :clear-production}
               (decisions/attention-key-action :x {:type :city :city-status :player} nil)))

    (it "ignores keys for non-player cities"
      (should-be-nil (decisions/attention-key-action :a {:type :city :city-status :computer} nil)))))

(ns empire.ui.util.rendering.hud-tooltips-spec
  (:require [empire.ui.util.rendering.hud-tooltips :as hud-tooltips]
            [speclj.core :refer :all]))

(describe "status-token-tooltip"
  (it "explains fixed status tokens"
    (should= "Game updates are paused."
             (hud-tooltips/status-token-tooltip "PAUSED" nil)))

  (it "explains round tokens"
    (should= "Round 17."
             (hud-tooltips/status-token-tooltip "R17" nil)))

  (it "explains unit-count tokens"
    (should= "3 armies."
             (hud-tooltips/status-token-tooltip "A3" nil)))

  (it "explains exploration percentages"
    (should= "2% of the map has been explored by the player."
             (hud-tooltips/status-token-tooltip "2%" nil)))

  (it "explains hidden-count tokens"
    (should= "Hidden counts: D1 C1."
             (hud-tooltips/status-token-tooltip "+2" "A:3 F:1 T:1 D:1 C:1 | 75%")))

  (it "falls back to the hidden-count total when production details are absent"
    (should= "2 more unit types not shown."
             (hud-tooltips/status-token-tooltip "+2" nil)))

  (it "returns nil for unknown tokens"
    (should-be-nil (hud-tooltips/status-token-tooltip "bogus" nil))))

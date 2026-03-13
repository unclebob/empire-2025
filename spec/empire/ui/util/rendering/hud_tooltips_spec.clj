(ns empire.ui.util.rendering.hud-tooltips-spec
  (:require [empire.ui.util.rendering.hud-tooltips :as hud-tooltips]
            [speclj.core :refer :all]))

(describe "status-token-tooltip"
  (it "explains unit-count tokens"
    (should= "3 armies."
             (hud-tooltips/status-token-tooltip "A3")))

  (it "explains exploration percentages"
    (should= "2% of the map has been explored by the player."
             (hud-tooltips/status-token-tooltip "2%")))

  (it "explains hidden-count tokens"
    (should= "2 more unit types not shown."
             (hud-tooltips/status-token-tooltip "+2")))

  (it "returns nil for unknown tokens"
    (should-be-nil (hud-tooltips/status-token-tooltip "bogus"))))

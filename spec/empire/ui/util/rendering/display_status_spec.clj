(ns empire.ui.util.rendering.display-status-spec
  (:require [empire.ui.util.rendering.display :as display]
            [speclj.core :refer :all]))

(describe "resolve-status-line"
  (it "formats paused round and map label on the left"
    (should= {:left "PAUSED  R17  Comp"
              :center "Dest 12,7"
              :right "A1 F2 | 75%"}
             (display/resolve-status-line 17 nil true false :computer-map [12 7]
                                          "A:1 F:2 T:0 D:0 S:0 P:0 C:0 B:0 Z:0 | 75%"
                                          [] [])))

  (it "omits player-map label and empty optional fields"
    (should= {:left "R3"
              :center nil
              :right nil}
             (display/resolve-status-line 3 nil false false :player-map nil "" [] [])))

  (it "shows actual-map label when appropriate"
    (should= {:left "R9  Actual"
              :center nil
              :right "0 units | 4%"}
             (display/resolve-status-line 9 nil false false :actual-map nil
                                          "A:0 F:0 T:0 D:0 S:0 P:0 C:0 B:0 Z:0 | 4%"
                                          [] [])))

  (it "truncates long center and right fields"
    (should= {:left "R7"
              :center "Dest 1234567890..."
              :right "A12 F9 D3 | 100%"}
             (display/resolve-status-line 7 nil false false :player-map [123456789012 999]
                                          "A:12 F:9 T:0 D:3 S:0 P:0 C:0 B:0 Z:0 | 100%"
                                          [] [])))

  (it "shows city lookaround in the center when there is no pending destination"
    (should= {:left "R11"
              :center "Lookaround"
              :right nil}
             (display/resolve-status-line 11 nil false false :player-map nil ""
                                          [[{:type :city :city-status :player
                                              :marching-orders :lookaround}]]
                                          [[0 0]])))

  (it "shows transport marching orders from the current attention cell"
    (should= {:left "R5"
              :center "March 8,3"
              :right nil}
             (display/resolve-status-line 5 nil false false :player-map nil ""
                                          [[{:type :sea
                                             :contents {:type :transport
                                                        :owner :player
                                                        :marching-orders [8 3]}}]]
                                          [[0 0]])))

  (it "shows carrier flight orders from the current attention cell"
    (should= {:left "R6"
              :center "Flight 9,4"
              :right nil}
             (display/resolve-status-line 6 nil false false :player-map nil ""
                                          [[{:type :sea
                                             :contents {:type :carrier
                                                        :owner :player
                                                        :flight-path [9 4]}}]]
                                          [[0 0]])))

  (it "shows handicap countdown on the left"
    (should= {:left "R4  HC12"
              :center nil
              :right nil}
             (display/resolve-status-line 4 12 false false :player-map nil "" [] []))))

(describe "resolve-display-map"
  (it "returns player-map for :player-map"
    (should= :p (display/resolve-display-map :player-map :p :c :g)))

  (it "returns computer-map for :computer-map"
    (should= :c (display/resolve-display-map :computer-map :p :c :g)))

  (it "returns game-map for :actual-map"
    (should= :g (display/resolve-display-map :actual-map :p :c :g))))

(describe "resolve-attention-zone"
  (it "returns attention text when present"
    (should= "Fighter [23,15] - Bingo!"
             (display/resolve-attention-zone "Fighter [23,15] - Bingo!")))

  (it "returns nil for empty string"
    (should-be-nil (display/resolve-attention-zone "")))

  (it "returns nil for nil"
    (should-be-nil (display/resolve-attention-zone nil))))

(describe "resolve-warning-zone"
  (it "returns warning text when present"
    (should= "Can't move into water."
             (display/resolve-warning-zone "Can't move into water.")))

  (it "returns nil for empty string"
    (should-be-nil (display/resolve-warning-zone ""))))

(describe "resolve-command-zone"
  (it "returns command text when present"
    (should= "Marching orders set to 5,12"
             (display/resolve-command-zone "Marching orders set to 5,12")))

  (it "returns nil for empty string"
    (should-be-nil (display/resolve-command-zone ""))))

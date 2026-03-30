(ns empire.ui.util.rendering.display-hover-spec
  (:require [empire.ui.util.rendering.display :as display]
            [speclj.core :refer :all]))

(describe "compute-hover-message"
  (it "returns formatted status for cell with unit"
    (let [the-map [[{:type :land :contents {:type :army :hits 1 :mode :awake :owner :player}}]]
          production {}]
      (should= "[0,0] player army [1/1] awake"
               (display/compute-hover-message the-map production [0 0]))))

  (it "returns land status for empty land cell"
    (let [the-map [[{:type :land}]]
          production {}]
      (should= "[0,0] land cid:nil" (display/compute-hover-message the-map production [0 0]))))

  (it "returns city status with production"
    (let [the-map [[{:type :city :city-status :player :fighter-count 0}]]
          production {[0 0] {:item :army :remaining-rounds 3}}]
      (should= "[0,0] city:player producing:army rounds:3"
               (display/compute-hover-message the-map production [0 0]))))

  (it "returns city status with flight and marching coordinates"
    (let [the-map [[{:type :city :city-status :player :fighter-count 0
                     :marching-orders [1 2]
                     :flight-path [5 10]}]]
          production {}]
      (should= "[0,0] city:player march:1,2 flight:5,10"
               (display/compute-hover-message the-map production [0 0]))))

  (it "returns city status for occupied city cells"
    (let [the-map [[{:type :city
                     :city-status :player
                     :fighter-count 0
                     :contents {:type :army :hits 1 :mode :awake :owner :player}}]]
          production {}]
      (should= "[0,0] city:player"
               (display/compute-hover-message the-map production [0 0]))))

  (it "looks up correct cell in multi-cell map"
    (let [the-map [[{:type :land} {:type :sea}]
                   [{:type :city :city-status :free :fighter-count 0} {:type :land}]]
          production {}]
      (should= "[1,0] city:free"
               (display/compute-hover-message the-map production [1 0])))))

(describe "resolve-inspector-lines"
  (it "splits hover text into summary and detail"
    (should= {:summary "[12,7] player carrier [5/8]"
              :detail "cargo:3 sentry"}
             (display/resolve-inspector-lines "[12,7] player carrier [5/8] cargo:3 sentry")))

  (it "truncates overly long detail text"
    (should= {:summary "[12,7] player carrier [5/8]"
              :detail "cargo:3 fuel:32 mis:escort waypoint:14,9 and..."}
             (display/resolve-inspector-lines
              "[12,7] player carrier [5/8] cargo:3 fuel:32 mission:escort waypoint:14,9 and more detail text beyond the limit")))

  (it "preserves more of a long summary before truncating"
    (should= {:summary "[12,7] player very-long-custom-unit-name-that-keeps-g..."
              :detail nil}
             (display/resolve-inspector-lines
              "[12,7] player very-long-custom-unit-name-that-keeps-going [5/8]")))

  (it "returns empty inspector lines when there is no hover text"
    (should= {:summary nil :detail nil}
             (display/resolve-inspector-lines ""))))

(describe "compute-hover-result"
  (it "returns hover message using player-map"
    (let [player-map [[{:type :land :contents {:type :army :hits 1 :mode :awake :owner :player}}]]
          computer-map [[{:type :sea}]]
          game-map [[{:type :land}]]]
      (should= "[0,0] player army [1/1] awake"
               (display/compute-hover-result :player-map player-map computer-map game-map {} [0 0]))))

  (it "returns hover message using computer-map"
    (let [player-map [[{:type :land}]]
          computer-map [[{:type :city :city-status :computer :fighter-count 0}]]
          game-map [[{:type :land}]]]
      (should= "[0,0] city:computer"
               (display/compute-hover-result :computer-map player-map computer-map game-map {} [0 0]))))

  (it "returns land status for empty cell"
    (let [player-map [[{:type :land}]]
          computer-map [[{:type :sea}]]
          game-map [[{:type :land}]]]
      (should= "[0,0] land cid:nil"
               (display/compute-hover-result :player-map player-map computer-map game-map {} [0 0])))))

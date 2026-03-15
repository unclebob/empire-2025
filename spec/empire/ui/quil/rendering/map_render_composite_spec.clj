(ns empire.ui.quil.rendering.map-render-composite-spec
  (:require [empire.config.core :as config]
            [empire.game-mechanics.movement.map-utils :as map-utils]
            [empire.state.api :as sa]
            [empire.test.utils :refer [reset-all-atoms!]]
            [empire.ui.quil.rendering.map :as map-render]
            [quil.core :as q]
            [speclj.core :refer :all]))

(describe "draw-map"
  (before (reset-all-atoms!))

  (it "draws waypoint markers from the world map when the display map is stale"
    (let [calls (atom [])]
      (sa/write-state! :map-screen-dimensions [20 20])
      (sa/write-state! :production {})
      (sa/write-state! :map-to-display :player-map)
      (sa/write-state! :cells-needing-attention [])
      (sa/write-state! :production-char-font :fake-font)
      (sa/write-state! :game-map [[{:type :land :waypoint true}]])
      (with-redefs [empire.ui.util.rendering.display/group-cells-by-color
                    (fn [& _] {[139 69 19] [{:col 0 :row 0 :cell {:type :land}}]})
                    empire.ui.util.rendering.display/production-indicator-data (fn [& _] nil)
                    empire.ui.util.rendering.display/determine-display-unit (fn [& _] nil)
                    map-render/draw-production-indicators (fn [& _] nil)
                    q/no-stroke (fn [& _] nil)
                    q/fill (fn [& args] (swap! calls conj [:fill args]))
                    q/rect (fn [& _] nil)
                    q/stroke (fn [& _] nil)
                    q/line (fn [& _] nil)
                    q/text-font (fn [& _] nil)
                    q/text (fn [& args] (swap! calls conj [:text args]))]
        (map-render/draw-map [[{:type :land}]])
        (should-contain [:text ["*" config/cell-char-x-offset config/cell-char-y-offset]] @calls))))

  (it "shows only the production unit for a city with airport fighters when no unit needs attention"
    (let [calls (atom [])]
      (sa/write-state! :map-screen-dimensions [20 20])
      (sa/write-state! :production {[0 0] {:item :army :remaining-rounds 3}})
      (sa/write-state! :map-to-display :player-map)
      (sa/write-state! :cells-needing-attention [])
      (sa/write-state! :production-char-font :fake-font)
      (sa/write-state! :game-map [[{:type :city :city-status :player :fighter-count 1 :awake-fighters 0}]])
      (with-redefs [q/no-stroke (fn [& _] nil)
                    q/fill (fn [& _] nil)
                    q/rect (fn [& _] nil)
                    q/stroke (fn [& _] nil)
                    q/line (fn [& _] nil)
                    q/text-font (fn [& _] nil)
                    q/text (fn [& args] (swap! calls conj args))]
        (map-render/draw-map [[{:type :city :city-status :player :fighter-count 1 :awake-fighters 0}]])
        (should-contain ["A" config/cell-char-x-offset config/cell-char-y-offset] @calls)
        (should-not-contain ["F" config/cell-char-x-offset config/cell-char-y-offset] @calls))))

  (it "shows only the attention unit for a city with an awake airport fighter"
    (let [calls (atom [])]
      (sa/write-state! :map-screen-dimensions [20 20])
      (sa/write-state! :production {[0 0] {:item :army :remaining-rounds 3}})
      (sa/write-state! :map-to-display :player-map)
      (sa/write-state! :cells-needing-attention [[0 0]])
      (sa/write-state! :production-char-font :fake-font)
      (sa/write-state! :game-map [[{:type :city :city-status :player :fighter-count 1 :awake-fighters 1}]])
      (with-redefs [q/no-stroke (fn [& _] nil)
                    q/fill (fn [& _] nil)
                    q/rect (fn [& _] nil)
                    q/stroke (fn [& _] nil)
                    q/line (fn [& _] nil)
                    q/text-font (fn [& _] nil)
                    map-utils/blink? (fn [_] false)
                    q/text (fn [& args] (swap! calls conj args))]
        (map-render/draw-map [[{:type :city :city-status :player :fighter-count 1 :awake-fighters 1}]])
        (should-contain ["F" config/cell-char-x-offset config/cell-char-y-offset] @calls)
        (should-not-contain ["A" config/cell-char-x-offset config/cell-char-y-offset] @calls)))))

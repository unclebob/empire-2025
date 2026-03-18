(ns empire.ui.quil.rendering.map-render-primitives-spec
  (:require [empire.config.core :as config]
            [empire.test.utils :refer [reset-all-atoms!]]
            [empire.ui.quil.rendering.map :as map-render]
            [quil.core :as q]
            [speclj.core :refer :all]))

(describe "draw-production-indicators"
  (it "draws overlay and production character when indicator data is present"
    (let [calls (atom [])]
      (with-redefs [empire.ui.util.rendering.display/production-indicator-data
                    (fn [& _] {:prod-char "A"
                               :progress 0.5
                               :remaining 2
                               :dark-color [10 20 30]})
                    q/fill (fn [& args] (swap! calls conj [:fill args]))
                    q/rect (fn [& args] (swap! calls conj [:rect args]))
                    q/text (fn [& args] (swap! calls conj [:text args]))]
        (#'map-render/draw-production-indicators 1 2 {:type :city} 10 12 {} :player-map)
        (should-contain [:fill [10 20 30 128]] @calls)
        (should-contain [:rect [20 18.0 10 6.0]] @calls)
        (should-contain [:fill (vec config/production-color)] @calls)
        (should-contain [:text ["A"
                                (+ 20 config/cell-char-x-offset)
                                (+ 12 config/cell-char-y-offset)]] @calls))))

  (it "does nothing when no production indicator is present"
    (let [calls (atom [])]
      (with-redefs [empire.ui.util.rendering.display/production-indicator-data (fn [& _] nil)
                    q/fill (fn [& args] (swap! calls conj [:fill args]))
                    q/rect (fn [& args] (swap! calls conj [:rect args]))
                    q/text (fn [& args] (swap! calls conj [:text args]))]
        (#'map-render/draw-production-indicators 0 0 {:type :land} 10 10 {} :player-map)
        (should= [] @calls)))))

(describe "draw-unit"
  (it "draws an attention unit with the overridden blink color"
    (let [calls (atom [])]
      (with-redefs [empire.ui.util.rendering.display/determine-display-unit
                    (fn [& _] {:type :army :owner :player})
                    empire.ui.util.rendering.display/attention-unit-color
                    (fn [& _] [255 255 255])
                    q/fill (fn [& args] (swap! calls conj [:fill args]))
                    q/text (fn [& args] (swap! calls conj [:text args]))]
        (#'map-render/draw-unit 0 0 {:contents {:type :army :owner :player}} 10 10 [[0 0]] false false)
        (should-contain [:fill [255 255 255]] @calls)
        (should-contain [:text ["A" config/cell-char-x-offset config/cell-char-y-offset]] @calls))))

  (it "lowercases computer unit glyphs"
    (let [calls (atom [])]
      (with-redefs [empire.ui.util.rendering.display/determine-display-unit
                    (fn [& _] {:type :destroyer :owner :computer})
                    empire.ui.util.rendering.display/attention-unit-color
                    (fn [& _] [1 2 3])
                    q/fill (fn [& _] nil)
                    q/text (fn [& args] (swap! calls conj args))]
        (#'map-render/draw-unit 0 0 {:contents {:type :destroyer :owner :computer}} 10 10 nil false false)
        (should= [["d" config/cell-char-x-offset config/cell-char-y-offset]] @calls)))))

(describe "draw-waypoint"
  (it "draws a waypoint marker when the cell has a waypoint and no contents"
    (let [calls (atom [])]
      (with-redefs [q/fill (fn [& args] (swap! calls conj [:fill args]))
                    q/text (fn [& args] (swap! calls conj [:text args]))]
        (#'map-render/draw-waypoint 1 2 {:type :land} {:waypoint true} 10 12)
        (should-contain [:fill (vec config/waypoint-color)] @calls)
        (should-contain [:text ["*"
                                (+ 10 config/cell-char-x-offset)
                                (+ 24 config/cell-char-y-offset)]] @calls))))

  (it "does not draw on unexplored display cells even if the world has a waypoint"
    (let [calls (atom [])]
      (with-redefs [q/fill (fn [& args] (swap! calls conj [:fill args]))
                    q/text (fn [& args] (swap! calls conj [:text args]))]
        (#'map-render/draw-waypoint 1 2 {:type :unexplored} {:waypoint true} 10 12)
        (should= [] @calls)))))

(describe "draw-attention-ring"
  (it "draws a white circle centered on the attention cell once per second"
    (let [calls (atom [])]
      (with-redefs [q/frame-count (fn [] 60)
                    q/no-fill (fn [] (swap! calls conj :no-fill))
                    q/stroke (fn [& args] (swap! calls conj [:stroke args]))
                    q/stroke-weight (fn [& args] (swap! calls conj [:stroke-weight args]))
                    q/ellipse (fn [& args] (swap! calls conj [:ellipse args]))]
        (#'map-render/draw-attention-ring [[2 3]] 10 12 :player-map)
        (should-contain :no-fill @calls)
        (should-contain [:stroke [255 255 255]] @calls)
        (should-contain [:stroke-weight [2]] @calls)
        (should-contain [:ellipse [25.0 42.0 24.0 24.0]] @calls)
        (should-contain [:stroke-weight [1]] @calls))))

  (it "does not draw on non-flash frames"
    (let [calls (atom [])]
      (with-redefs [q/frame-count (fn [] 61)
                    q/ellipse (fn [& args] (swap! calls conj args))]
        (#'map-render/draw-attention-ring [[2 3]] 10 12 :player-map)
        (should= [] @calls))))

  (it "does not draw when the computer map is displayed"
    (let [calls (atom [])]
      (with-redefs [q/frame-count (fn [] 60)
                    q/ellipse (fn [& args] (swap! calls conj args))]
        (#'map-render/draw-attention-ring [[2 3]] 10 12 :computer-map)
        (should= [] @calls)))))

(describe "draw-debug-selection-rectangle"
  (before (reset-all-atoms!))

  (it "draws the drag rectangle when both drag endpoints are set"
    (let [calls (atom [])]
      (empire.state.api/write-state! :debug-drag-start [10 20])
      (empire.state.api/write-state! :debug-drag-current [4 9])
      (with-redefs [q/no-fill (fn [] (swap! calls conj :no-fill))
                    q/stroke (fn [& args] (swap! calls conj [:stroke args]))
                    q/stroke-weight (fn [& args] (swap! calls conj [:stroke-weight args]))
                    q/rect (fn [& args] (swap! calls conj [:rect args]))]
        (map-render/draw-debug-selection-rectangle)
        (should-contain :no-fill @calls)
        (should-contain [:stroke [255 255 0]] @calls)
        (should-contain [:stroke-weight [2]] @calls)
        (should-contain [:rect [4 9 6 11]] @calls)
        (should-contain [:stroke-weight [1]] @calls)))))

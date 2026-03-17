(ns empire.ui.quil.rendering.messages-layout-spec
  (:require [empire.state.api :as sa]
            [empire.test.utils :refer [reset-all-atoms!]]
            [empire.ui.quil.rendering.messages :as messages-render]
            [quil.core :as q]
            [speclj.core :refer :all]))

(describe "draw-message-area layout"
  (before (reset-all-atoms!))

  (it "shows attention-cell order context in the status center when there is no destination"
    (let [calls (atom [])]
      (sa/write-state! :text-area-dimensions [0 100 300 80])
      (sa/write-state! :text-font :fake-font)
      (sa/update-world! (fn [_]
                          [[{:type :city
                             :city-status :player
                             :flight-path [9 4]}]]))
      (sa/write-state! :cells-needing-attention [[0 0]])
      (sa/write-state! :error-message "")
      (sa/write-state! :error-until 0)
      (sa/write-state! :attention-message "")
      (sa/write-state! :turn-message "")
      (sa/write-state! :round-number 6)
      (sa/write-state! :paused false)
      (sa/write-state! :pause-requested false)
      (sa/write-state! :map-to-display :player-map)
      (sa/write-state! :destination nil)
      (sa/write-state! :production-status "")
      (sa/write-state! :hover-message "")
      (with-redefs [q/no-stroke (fn [& _] nil)
                    q/rect (fn [& _] nil)
                    q/stroke (fn [& _] nil)
                    q/line (fn [& _] nil)
                    q/text-font (fn [& _] nil)
                    q/fill (fn [& _] nil)
                    q/text-width (fn [text] (* 8 (count text)))
                    q/mouse-x (fn [] 0)
                    q/mouse-y (fn [] 0)
                    q/text (fn [& args] (swap! calls conj [:text args]))]
        (messages-render/draw-message-area)
        (should-contain [:text ["R6" 14 140]] @calls)
        (should-contain [:text ["Flight 9,4" 110 140]] @calls)))))

(describe "tooltip-box-position"
  (it "places the tooltip down and right when there is room"
    (should= [112 112]
             (messages-render/tooltip-box-position 100 100 80 24 400 300)))

  (it "flips the tooltip left when it would run off the right edge"
    (should= [218 112]
             (messages-render/tooltip-box-position 310 100 80 24 400 300)))

  (it "flips the tooltip up when it would run off the bottom edge"
    (should= [112 254]
             (messages-render/tooltip-box-position 100 290 80 24 400 300)))

  (it "clamps the tooltip when it is wider than the screen"
    (should= [0 112]
             (messages-render/tooltip-box-position 100 100 500 24 400 300))))

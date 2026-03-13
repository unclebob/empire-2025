(ns empire.ui.quil.rendering.messages-spec
  (:require [empire.state.api :as sa]
            [empire.test.utils :refer [reset-all-atoms!]]
            [empire.ui.quil.rendering.messages :as messages-render]
            [quil.core :as q]
            [speclj.core :refer :all]))

(describe "draw-message-area"
  (before (reset-all-atoms!))

  (it "draws the redesigned four-line HUD with banner, status, and inspector"
    (let [calls (atom [])]
      (sa/write-state! :text-area-dimensions [0 100 300 80])
      (sa/write-state! :text-font :fake-font)
      (sa/write-state! :error-message "")
      (sa/write-state! :error-until 0)
      (sa/write-state! :attention-message "Fighter needs attention...")
      (sa/write-state! :turn-message "Moved")
      (sa/write-state! :round-number 17)
      (sa/write-state! :paused false)
      (sa/write-state! :pause-requested false)
      (sa/write-state! :map-to-display :computer-map)
      (sa/write-state! :destination [12 7])
      (sa/write-state! :production-status "Prod: fighter 2r")
      (sa/write-state! :hover-message "[12,7] player carrier [5/8] sentry")
      (with-redefs [q/no-stroke (fn [& args] (swap! calls conj [:no-stroke args]))
                    q/rect (fn [& args] (swap! calls conj [:rect args]))
                    q/stroke (fn [& args] (swap! calls conj [:stroke args]))
                    q/line (fn [& args] (swap! calls conj [:line args]))
                    q/text-font (fn [& args] (swap! calls conj [:text-font args]))
                    q/fill (fn [& args] (swap! calls conj [:fill args]))
                    q/text-width (fn [text] (* 8 (count text)))
                    q/mouse-x (fn [] 0)
                    q/mouse-y (fn [] 0)
                    q/text (fn [& args] (swap! calls conj [:text args]))]
        (messages-render/draw-message-area)
        (should-contain [:no-stroke nil] @calls)
        (should-contain [:fill [14 18 22]] @calls)
        (should-contain [:rect [0 100 300 80]] @calls)
        (should-contain [:stroke [120 128 136]] @calls)
        (should-contain [:line [0 96 300 96]] @calls)
        (should-contain [:stroke [54 60 66]] @calls)
        (should-contain [:line [0 126 300 126]] @calls)
        (should-contain [:text-font [:fake-font]] @calls)
        (should-contain [:fill [255 215 64]] @calls)
        (should-contain [:text ["Fighter needs attention..." 14 116]] @calls)
        (should-contain [:fill [190 198 208]] @calls)
        (should-contain [:text ["R17  Comp" 14 140]] @calls)
        (should-contain [:fill [230 230 230]] @calls)
        (should-contain [:text ["Dest 12,7" 114 140]] @calls)
        (should-contain [:text ["Prod: fighter 2r" 158 140]] @calls)
        (should-contain [:text ["[12,7] player carrier [5/8]" 14 160]] @calls)
        (should-contain [:text ["sentry" 14 176]] @calls))))

  (it "prefers an error banner and leaves lower inspector rows empty when there is no hover text"
    (let [calls (atom [])]
      (sa/write-state! :text-area-dimensions [0 100 240 80])
      (sa/write-state! :text-font :fake-font)
      (sa/write-state! :error-message "Conquest Failed")
      (sa/write-state! :error-until (+ (System/currentTimeMillis) 1000))
      (sa/write-state! :attention-message "Need orders")
      (sa/write-state! :turn-message "Moved")
      (sa/write-state! :round-number 2)
      (sa/write-state! :paused true)
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
                    q/fill (fn [& args] (swap! calls conj [:fill args]))
                    q/text-width (fn [text] (* 8 (count text)))
                    q/mouse-x (fn [] 0)
                    q/mouse-y (fn [] 0)
                    q/text (fn [& args] (swap! calls conj [:text args]))]
        (messages-render/draw-message-area)
        (should-contain [:fill [255 80 80]] @calls)
        (should-contain [:text ["Conquest Failed" 14 116]] @calls)
        (should-contain [:fill [223 199 108]] @calls)
        (should-contain [:text [" | Need orders" 134 116]] @calls)
        (should-contain [:fill [211 217 223]] @calls)
        (should-contain [:text [" | Moved" 246 116]] @calls)
        (should-contain [:text ["PAUSED  R2" 14 140]] @calls)
        (should-not-contain [:text [nil 14 160]] @calls))))

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

  (it "shows additional active banner messages inline after the primary message"
    (let [calls (atom [])]
      (sa/write-state! :text-area-dimensions [0 100 300 80])
      (sa/write-state! :text-font :fake-font)
      (sa/write-state! :error-message "")
      (sa/write-state! :error-until 0)
      (sa/write-state! :attention-message "City needs attention")
      (sa/write-state! :turn-message "City conquered.")
      (with-redefs [q/no-stroke (fn [& _] nil)
                    q/rect (fn [& _] nil)
                    q/stroke (fn [& _] nil)
                    q/line (fn [& _] nil)
                    q/text-font (fn [& _] nil)
                    q/fill (fn [& args] (swap! calls conj [:fill args]))
                    q/text-width (fn [text] (* 8 (count text)))
                    q/mouse-x (fn [] 0)
                    q/mouse-y (fn [] 0)
                    q/text (fn [& args] (swap! calls conj [:text args]))]
        (messages-render/draw-message-area)
        (should-contain [:text ["City needs attention" 14 116]] @calls)
        (should-contain [:fill [211 217 223]] @calls)
        (should-contain [:text [" | City conquered." 174 116]] @calls))))

(describe "hud-tooltip"
  (it "explains production count tokens on the status line"
    (with-redefs [q/text-width (fn [text] (* 8 (count text)))]
      (should= "3 armies."
               (messages-render/hud-tooltip 223 140 0 100 300 "R1" nil "A3 | 20%"))))

  (it "still explains the token when the mouse is anywhere inside the status band"
    (with-redefs [q/text-width (fn [text] (* 8 (count text)))]
      (should= "3 armies."
               (messages-render/hud-tooltip 223 150 0 100 300 "R1" nil "A3 | 20%"))))

  (it "explains exploration percentages on the status line"
    (with-redefs [q/text-width (fn [text] (* 8 (count text)))]
      (should= "20% of the map has been explored by the player."
               (messages-render/hud-tooltip 263 140 0 100 300 "R1" nil "A3 | 20%"))))

  (it "returns nil when the mouse is not over the status row"
    (with-redefs [q/text-width (fn [text] (* 8 (count text)))]
      (should-be-nil
       (messages-render/hud-tooltip 223 170 0 100 300 "R1" nil "A3 | 20%")))))

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

(ns empire.ui.quil.rendering.messages-draw-spec
  (:require [empire.state.api :as sa]
            [empire.test.utils :refer [reset-all-atoms!]]
            [empire.ui.quil.rendering.messages :as messages-render]
            [quil.core :as q]
            [speclj.core :refer :all]))

(describe "draw-message-area"
  (before (reset-all-atoms!))

  (it "draws the 3x2 zone-based HUD with attention, status, and inspector zones"
    (let [calls (atom [])]
      (sa/write-state! :text-area-dimensions [0 100 300 80])
      (sa/write-state! :text-font :fake-font)
      (sa/write-state! :attention-message "Fighter needs attention...")
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
        (should-contain [:line [165.0 100 165.0 180]] @calls)
        (should-contain [:stroke [30 36 40]] @calls)
        (should-contain [:line [0 128 300 128]] @calls)
        (should-contain [:line [0 154 300 154]] @calls)
        (should-contain [:text-font [:fake-font]] @calls)
        (should-contain [:fill [255 215 64]] @calls)
        (should-contain [:text ["Fighter needs attention..." 14 120]] @calls)
        (should-contain [:fill [190 198 208]] @calls)
        (should-contain [:text ["R17  Comp" 179.0 120]] @calls)
        (should-contain [:text ["[12,7] player carrier [5/8]" 179.0 146]] @calls)
        (should-contain [:text ["sentry" 179.0 172]] @calls))))

  (it "draws attention zone and status zone, leaves other zones empty when no hover text"
    (let [calls (atom [])]
      (sa/write-state! :text-area-dimensions [0 100 240 80])
      (sa/write-state! :text-font :fake-font)
      (sa/write-state! :attention-message "Need orders")
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
        (should-contain [:fill [255 215 64]] @calls)
        (should-contain [:text ["Need orders" 14 120]] @calls)
        (should-contain [:fill [190 198 208]] @calls)
        (should-contain [:text ["PAUSED  R2" 146.0 120]] @calls)
        (should-not-contain [:text [nil 14 146]] @calls))))

  (it "draws the attention zone with the stored attention message"
    (let [calls (atom [])]
      (sa/write-state! :text-area-dimensions [0 100 300 80])
      (sa/write-state! :text-font :fake-font)
      (sa/write-state! :attention-message "City needs attention")
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
        (should-contain [:fill [255 215 64]] @calls)
        (should-contain [:text ["City needs attention" 14 120]] @calls)))))

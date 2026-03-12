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
      (sa/write-state! :text-area-dimensions [0 100 300 64])
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
                    q/text (fn [& args] (swap! calls conj [:text args]))]
        (messages-render/draw-message-area)
        (should-contain [:no-stroke nil] @calls)
        (should-contain [:fill [18 22 26]] @calls)
        (should-contain [:rect [0 100 300 64]] @calls)
        (should-contain [:stroke [235 235 235]] @calls)
        (should-contain [:line [0 96 300 96]] @calls)
        (should-contain [:stroke [90 96 102]] @calls)
        (should-contain [:line [0 118 300 118]] @calls)
        (should-contain [:text-font [:fake-font]] @calls)
        (should-contain [:fill [255 215 64]] @calls)
        (should-contain [:text ["Fighter needs attention..." 10 110]] @calls)
        (should-contain [:text ["Round 17  Map: Comp" 10 126]] @calls)
        (should-contain [:text ["Dest 12,7" 114 126]] @calls)
        (should-contain [:text ["Prod: fighter 2r" 162 126]] @calls)
        (should-contain [:text ["[12,7] player carrier [5/8]" 10 142]] @calls)
        (should-contain [:text ["sentry" 10 158]] @calls))))

  (it "prefers an error banner and leaves lower inspector rows empty when there is no hover text"
    (let [calls (atom [])]
      (sa/write-state! :text-area-dimensions [0 100 240 64])
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
                    q/text (fn [& args] (swap! calls conj [:text args]))]
        (messages-render/draw-message-area)
        (should-contain [:fill [255 80 80]] @calls)
        (should-contain [:text ["Conquest Failed" 10 110]] @calls)
        (should-contain [:text ["PAUSED  Round 2" 10 126]] @calls)
        (should-not-contain [:text [nil 10 142]] @calls))))

  (it "shows attention-cell order context in the status center when there is no destination"
    (let [calls (atom [])]
      (sa/write-state! :text-area-dimensions [0 100 300 64])
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
                    q/text (fn [& args] (swap! calls conj [:text args]))]
        (messages-render/draw-message-area)
        (should-contain [:text ["Round 6" 10 126]] @calls)
        (should-contain [:text ["Flight 9,4" 110 126]] @calls)))))

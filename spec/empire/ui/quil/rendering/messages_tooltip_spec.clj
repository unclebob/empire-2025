(ns empire.ui.quil.rendering.messages-tooltip-spec
  (:require [empire.state.api :as sa]
            [empire.test.utils :refer [reset-all-atoms!]]
            [empire.ui.quil.rendering.messages :as messages-render]
            [quil.core :as q]
            [speclj.core :refer :all]))

(defn- hover-tooltip
  [mouse-x mouse-y round-message city-summary production-status]
  (with-redefs [q/text-width (fn [text] (* 8 (count text)))]
    (messages-render/hud-tooltip mouse-x mouse-y 0 100 300 round-message nil city-summary production-status)))

(defn- seed-tooltip-state!
  []
  (sa/write-state! :text-area-dimensions [0 100 300 80])
  (sa/write-state! :text-font :fake-font)
  (sa/write-state! :attention-message "")
  (sa/write-state! :warning-message "")
  (sa/write-state! :command-message "")
  (sa/write-state! :round-number 1)
  (sa/write-state! :paused false)
  (sa/write-state! :pause-requested false)
  (sa/write-state! :map-to-display :player-map)
  (sa/write-state! :destination nil)
  (sa/write-state! :production-status "A:3 F:0 T:0 D:0 S:0 P:0 C:0 B:0 Z:0 | 20%")
  (sa/write-state! :hover-message ""))

(describe "hud-tooltip"
  (it "explains recognized status tokens and ignores invalid hover positions"
    (let [cases [{:mouse-x 223
                  :mouse-y 110
                  :round-message "R1"
                  :city-summary "A3 | 20%"
                  :production-status "A:3 F:0 T:0 D:0 S:0 P:0 C:0 B:0 Z:0 | 20%"
                  :expected "3 armies"}
                 {:mouse-x 223
                  :mouse-y 120
                  :round-message "R1"
                  :city-summary "A3 | 20%"
                  :production-status "A:3 F:0 T:0 D:0 S:0 P:0 C:0 B:0 Z:0 | 20%"
                  :expected "3 armies"}
                 {:mouse-x 230
                  :mouse-y 110
                  :round-message "R1"
                  :city-summary "A2 F1 T1 +2 | 75%"
                  :production-status "A:2 F:1 T:1 D:1 S:0 P:0 C:1 B:0 Z:0 | 75%"
                  :expected "2 armies, 1 fighters, 1 transports, 1 destroyers, 1 carriers"}
                 {:mouse-x 263
                  :mouse-y 110
                  :round-message "R1"
                  :city-summary "A3 | 20%"
                  :production-status "A:3 F:0 T:0 D:0 S:0 P:0 C:0 B:0 Z:0 | 20%"
                  :expected "3 armies"}
                 {:mouse-x 18
                  :mouse-y 110
                  :round-message "BOGUS"
                  :city-summary nil
                  :production-status nil
                  :expected nil}
                 {:mouse-x 223
                  :mouse-y 170
                  :round-message "R1"
                  :city-summary "A3 | 20%"
                  :production-status "A:3 F:0 T:0 D:0 S:0 P:0 C:0 B:0 Z:0 | 20%"
                  :expected nil}]]
      (should= 6 (count cases))
      (should= (mapv :expected cases)
               (mapv (fn [{:keys [mouse-x mouse-y round-message city-summary production-status]}]
                       (hover-tooltip mouse-x mouse-y round-message city-summary production-status))
                     cases)))))

(describe "draw-message-area tooltip rendering"
  (before (reset-all-atoms!))

  (it "draws a tooltip box when hovering a recognized status token"
    (let [calls (atom [])]
      (seed-tooltip-state!)
      (with-redefs [q/no-stroke (fn [& _] nil)
                    q/rect (fn [& args] (swap! calls conj [:rect args]))
                    q/stroke (fn [& args] (swap! calls conj [:stroke args]))
                    q/line (fn [& _] nil)
                    q/text-font (fn [& _] nil)
                    q/fill (fn [& args] (swap! calls conj [:fill args]))
                    q/text-width (fn [text] (* 8 (count text)))
                    q/mouse-x (fn [] 223)
                    q/mouse-y (fn [] 110)
                    q/width (fn [] 400)
                    q/height (fn [] 300)
                    q/text (fn [& args] (swap! calls conj [:text args]))]
        (messages-render/draw-message-area)
        (should-contain [:stroke [70 64 40]] @calls)
        (should-contain [:text ["3 armies" 241 138]] @calls)))))

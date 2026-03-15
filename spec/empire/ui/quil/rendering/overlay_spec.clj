(ns empire.ui.quil.rendering.overlay-spec
  (:require [empire.state.api :as sa]
            [empire.test.utils :refer [reset-all-atoms!]]
            [empire.ui.quil.rendering.overlay :as overlay]
            [speclj.core :refer :all]))

(describe "update-hover-status"
  (before (reset-all-atoms!))

  (it "updates load-menu hover and hover message when menu is open and mouse is on the map"
    (sa/write-state! :load-menu-open true)
    (sa/write-state! :load-menu-files ["Bob.edn"])
    (sa/write-state! :map-to-display :player-map)
    (sa/write-state! :player-map :player)
    (sa/write-state! :computer-map :computer)
    (sa/write-state! :game-map :world)
    (sa/write-state! :production {:p :status})
    (with-redefs [quil.core/mouse-x (constantly 12)
                  quil.core/mouse-y (constantly 34)
                  quil.core/width (constantly 300)
                  quil.core/height (constantly 200)
                  empire.game.save-load/menu-geometry (fn [& _] :geom)
                  empire.game.save-load/hovered-file-index (fn [x y geom count]
                                                             (should= [12 34 :geom 1] [x y geom count])
                                                             0)
                  empire.game-mechanics.movement.map-utils/on-map? (constantly true)
                  empire.game-mechanics.movement.map-utils/determine-cell-coordinates (fn [_ _] [1 2])
                  empire.ui.util.rendering.display/resolve-display-map (fn [& _] :resolved)
                  empire.ui.util.rendering.display/compute-hover-message (fn [the-map production coords]
                                                                           (should= :resolved the-map)
                                                                           (should= {:p :status} production)
                                                                           (should= [1 2] coords)
                                                                           "hovered")]
      (overlay/update-hover-status)
      (should= 0 (sa/read-state :load-menu-hovered))
      (should= "hovered" (sa/read-state :hover-message)))))

  (it "clears hover message when the mouse is off the map"
    (sa/write-state! :load-menu-open false)
    (with-redefs [quil.core/mouse-x (constantly 500)
                  quil.core/mouse-y (constantly 500)
                  empire.game-mechanics.movement.map-utils/on-map? (constantly false)]
      (overlay/update-hover-status)
      (should= "" (sa/read-state :hover-message))))

(describe "draw-load-menu"
  (before (reset-all-atoms!))

  (it "renders the empty load menu message"
    (let [calls (atom [])]
      (sa/write-state! :load-menu-open true)
      (sa/write-state! :load-menu-files [])
      (sa/write-state! :load-menu-hovered nil)
      (sa/write-state! :text-font :font)
      (with-redefs [quil.core/width (constantly 300)
                    quil.core/height (constantly 200)
                    empire.game.save-load/menu-geometry (fn [& _] {:left 10 :top 20 :width 100 :height 80 :content-top 40})
                    quil.core/fill (fn [& xs] (swap! calls conj [:fill xs]))
                    quil.core/rect (fn [& xs] (swap! calls conj [:rect xs]))
                    quil.core/stroke (fn [& xs] (swap! calls conj [:stroke xs]))
                    quil.core/stroke-weight (fn [& xs] (swap! calls conj [:stroke-weight xs]))
                    quil.core/text-font (fn [& xs] (swap! calls conj [:text-font xs]))
                    quil.core/text (fn [& xs] (swap! calls conj [:text xs]))
                    quil.core/no-stroke (fn [& xs] (swap! calls conj [:no-stroke xs]))]
        (overlay/draw-load-menu)
        (should-contain [:text ["Load Game" 25 50]] @calls)
        (should-contain [:text ["No saved games found" 25 55]] @calls))))

  (it "renders hovered save names with inverse colors"
    (let [calls (atom [])]
      (sa/write-state! :load-menu-open true)
      (sa/write-state! :load-menu-files ["Bob.edn" "Alice.edn"])
      (sa/write-state! :load-menu-hovered 1)
      (sa/write-state! :text-font :font)
      (with-redefs [quil.core/width (constantly 300)
                    quil.core/height (constantly 200)
                    empire.game.save-load/menu-geometry (fn [& _] {:left 10 :top 20 :width 100 :height 100 :content-top 40})
                    quil.core/fill (fn [& xs] (swap! calls conj [:fill xs]))
                    quil.core/rect (fn [& xs] (swap! calls conj [:rect xs]))
                    quil.core/stroke (fn [& xs] (swap! calls conj [:stroke xs]))
                    quil.core/stroke-weight (fn [& xs] (swap! calls conj [:stroke-weight xs]))
                    quil.core/text-font (fn [& xs] (swap! calls conj [:text-font xs]))
                    quil.core/text (fn [& xs] (swap! calls conj [:text xs]))
                    quil.core/no-stroke (fn [& xs] (swap! calls conj [:no-stroke xs]))]
        (overlay/draw-load-menu)
        (should-contain [:text ["Bob.edn" 25 57]] @calls)
        (should-contain [:rect [10 65 100 25]] @calls)
        (should-contain [:text ["Alice.edn" 25 82]] @calls)))))

(describe "draw-save-menu"
  (before (reset-all-atoms!))

  (it "renders the save dialog and dims default text when active"
    (let [calls (atom [])]
      (sa/write-state! :save-menu-open true)
      (sa/write-state! :save-menu-input "Bob")
      (sa/write-state! :save-menu-default-active true)
      (sa/write-state! :text-font :font)
      (with-redefs [quil.core/width (constantly 300)
                    quil.core/height (constantly 200)
                    empire.game.save-load/menu-geometry (fn [& _] {:left 10 :top 20 :width 140 :height 100 :content-top 40})
                    quil.core/fill (fn [& xs] (swap! calls conj [:fill xs]))
                    quil.core/rect (fn [& xs] (swap! calls conj [:rect xs]))
                    quil.core/stroke (fn [& xs] (swap! calls conj [:stroke xs]))
                    quil.core/stroke-weight (fn [& xs] (swap! calls conj [:stroke-weight xs]))
                    quil.core/text-font (fn [& xs] (swap! calls conj [:text-font xs]))
                    quil.core/text (fn [& xs] (swap! calls conj [:text xs]))]
        (overlay/draw-save-menu)
        (should-contain [:text ["Save Game" 25 50]] @calls)
        (should-contain [:text ["Save file name (.edn optional):" 25 52]] @calls)
        (should-contain [:fill [120]] @calls)
        (should-contain [:text ["Bob" 31 76]] @calls)
        (should-contain [:text ["Enter=Save  Esc=Cancel" 25 102]] @calls)
        (should-contain [:text ["Backspace/Delete=Remove Last" 25 120]] @calls)))))

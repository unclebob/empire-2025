(ns empire.ui.quil.input-spec
  (:require [empire.ui.quil.input :as input]
            [speclj.core :refer :all]))

(describe "mouse->cell"
  (it "returns nil when the mouse is off the map"
    (with-redefs [quil.core/mouse-x (constantly 500)
                  quil.core/mouse-y (constantly 600)
                  empire.game-mechanics.movement.map-utils/on-map? (constantly false)
                  empire.game-mechanics.movement.map-utils/determine-cell-coordinates (fn [& _]
                                                                                         (throw (ex-info "should not run" {})))]
      (should-be-nil (input/mouse->cell))))

  (it "converts mouse coordinates to a map cell when on the map"
    (with-redefs [quil.core/mouse-x (constantly 12)
                  quil.core/mouse-y (constantly 34)
                  empire.game-mechanics.movement.map-utils/on-map? (fn [x y]
                                                                     (should= [12 34] [x y])
                                                                     true)
                  empire.game-mechanics.movement.map-utils/determine-cell-coordinates (fn [x y]
                                                                                        (should= [12 34] [x y])
                                                                                        [1 2])]
      (should= [1 2] (input/mouse->cell)))))

(describe "key-down"
  (it "forwards the key and current mouse position to dispatch"
    (let [calls (atom [])]
      (with-redefs [quil.core/mouse-x (constantly 12)
                    quil.core/mouse-y (constantly 34)
                    empire.ui.util.input.dispatch/key-down (fn [k x y]
                                                             (swap! calls conj [k x y]))]
        (input/key-down :a)
        (should= [[:a 12 34]] @calls)))))

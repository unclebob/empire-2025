(ns empire.ui.util.input.dispatch-clearing-spec
  (:require [empire.game-mechanics.movement.map-utils :as map-utils]
            [empire.state.api :as sa]
            [empire.test.utils :refer [reset-all-atoms!]]
            [empire.ui.util.input.dispatch :as dispatch]
            [empire.ui.util.input.dispatch-mouse :as mouse]
            [speclj.core :refer :all]))

(describe "zone clearing on player action"
  (before (reset-all-atoms!))

  (it "clears warning-message on key-down"
    (sa/write-state! :warning-message "Can't move into water.")
    (sa/write-state! :command-message "Marching orders set to 5,12")
    (with-redefs [map-utils/on-map? (fn [_ _] false)
                  dispatch/dispatch-key (fn [_ _] nil)]
      (dispatch/key-down :w 50 50))
    (should= "" (sa/read-state :warning-message))
    (should= "" (sa/read-state :command-message)))

  (it "clears warning-message on mouse-down"
    (sa/write-state! :warning-message "Can't move into water.")
    (sa/write-state! :command-message "Marching orders set to 5,12")
    (with-redefs [mouse/mouse-down (fn [_ _ _] nil)]
      (dispatch/mouse-down 100 200 :left))
    (should= "" (sa/read-state :warning-message))
    (should= "" (sa/read-state :command-message))))

(ns empire.atoms-helpers-spec
  (:require [speclj.core :refer :all]
            [empire.atoms :as atoms]
            [empire.atoms-helpers :as helpers]
            [empire.test-utils :refer [reset-all-atoms! build-test-map]]))

(describe "atoms-helpers"
  (before (reset-all-atoms!))

  (describe "get-cell"
    (it "returns the cell at coordinates"
      (reset! atoms/game-map (build-test-map ["#~"
                                               "+#"]))
      (should= :land (:type (helpers/get-cell [0 0])))
      (should= :sea (:type (helpers/get-cell [0 1])))
      (should= :city (:type (helpers/get-cell [1 0])))))

  (describe "set-cell-contents!"
    (it "sets contents on a cell"
      (reset! atoms/game-map (build-test-map ["#"]))
      (helpers/set-cell-contents! [0 0] {:type :army :owner :player})
      (should= {:type :army :owner :player} (:contents (get-in @atoms/game-map [0 0])))))

  (describe "clear-cell-contents!"
    (it "removes contents from a cell"
      (reset! atoms/game-map (build-test-map ["#"]))
      (swap! atoms/game-map assoc-in [0 0 :contents] {:type :army})
      (helpers/clear-cell-contents! [0 0])
      (should-be-nil (:contents (get-in @atoms/game-map [0 0])))))

  (describe "get-unit"
    (it "returns the unit at coordinates"
      (reset! atoms/game-map (build-test-map ["#"]))
      (swap! atoms/game-map assoc-in [0 0 :contents] {:type :army :owner :player})
      (should= {:type :army :owner :player} (helpers/get-unit [0 0])))

    (it "returns nil when no unit"
      (reset! atoms/game-map (build-test-map ["#"]))
      (should-be-nil (helpers/get-unit [0 0]))))

  (describe "set-unit-field!"
    (it "sets a field on the unit"
      (reset! atoms/game-map (build-test-map ["#"]))
      (swap! atoms/game-map assoc-in [0 0 :contents] {:type :army :mode :awake})
      (helpers/set-unit-field! [0 0] :mode :sentry)
      (should= :sentry (:mode (:contents (get-in @atoms/game-map [0 0]))))))

  (describe "set-message!"
    (it "sets the main message"
      (helpers/set-message! "Test message")
      (should= "Test message" @atoms/message)))

  (describe "clear-message!"
    (it "clears the main message"
      (reset! atoms/message "Something")
      (helpers/clear-message!)
      (should= "" @atoms/message)))

  (describe "waiting-for-input?"
    (it "returns the waiting-for-input state"
      (reset! atoms/waiting-for-input true)
      (should (helpers/waiting-for-input?))
      (reset! atoms/waiting-for-input false)
      (should-not (helpers/waiting-for-input?))))

  (describe "set-waiting-for-input!"
    (it "sets the waiting-for-input flag"
      (helpers/set-waiting-for-input! true)
      (should @atoms/waiting-for-input)
      (helpers/set-waiting-for-input! false)
      (should-not @atoms/waiting-for-input)))

  (describe "paused?"
    (it "returns the paused state"
      (reset! atoms/paused true)
      (should (helpers/paused?))
      (reset! atoms/paused false)
      (should-not (helpers/paused?))))

  (describe "current-attention-coords"
    (it "returns first attention cell"
      (reset! atoms/cells-needing-attention [[5 3] [2 1]])
      (should= [5 3] (helpers/current-attention-coords))))

  (describe "map-dimensions"
    (it "returns height and width"
      (reset! atoms/game-map (build-test-map ["###"
                                               "###"]))
      (should= [2 3] (helpers/map-dimensions)))))

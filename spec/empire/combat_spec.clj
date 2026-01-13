(ns empire.combat-spec
  (:require [speclj.core :refer :all]
            [empire.combat :as combat]
            [empire.atoms :as atoms]
            [empire.config :as config]))

(describe "hostile-city?"
  (it "returns true for free city"
    (reset! atoms/game-map [[{:type :city :city-status :free}]])
    (should (combat/hostile-city? [0 0])))

  (it "returns true for computer city"
    (reset! atoms/game-map [[{:type :city :city-status :computer}]])
    (should (combat/hostile-city? [0 0])))

  (it "returns false for player city"
    (reset! atoms/game-map [[{:type :city :city-status :player}]])
    (should-not (combat/hostile-city? [0 0])))

  (it "returns false for non-city cells"
    (reset! atoms/game-map [[{:type :land}]])
    (should-not (combat/hostile-city? [0 0])))

  (it "returns false for sea cells"
    (reset! atoms/game-map [[{:type :sea}]])
    (should-not (combat/hostile-city? [0 0]))))

(describe "attempt-conquest"
  (with-stubs)

  (it "removes army from original cell on success"
    (with-redefs [rand (constantly 0.1)]
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :player}}
                               {:type :city :city-status :free}]])
      (combat/attempt-conquest [0 0] [0 1])
      (should= nil (:contents (get-in @atoms/game-map [0 0])))))

  (it "converts city to player on success"
    (with-redefs [rand (constantly 0.1)]
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :player}}
                               {:type :city :city-status :free}]])
      (combat/attempt-conquest [0 0] [0 1])
      (should= :player (:city-status (get-in @atoms/game-map [0 1])))))

  (it "removes army from original cell on failure"
    (with-redefs [rand (constantly 0.9)]
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :player}}
                               {:type :city :city-status :free}]])
      (reset! atoms/line3-message "")
      (combat/attempt-conquest [0 0] [0 1])
      (should= nil (:contents (get-in @atoms/game-map [0 0])))))

  (it "keeps city status on failure"
    (with-redefs [rand (constantly 0.9)]
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :player}}
                               {:type :city :city-status :free}]])
      (reset! atoms/line3-message "")
      (combat/attempt-conquest [0 0] [0 1])
      (should= :free (:city-status (get-in @atoms/game-map [0 1])))))

  (it "sets failure message on failed conquest"
    (with-redefs [rand (constantly 0.9)]
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :player}}
                               {:type :city :city-status :free}]])
      (reset! atoms/line3-message "")
      (combat/attempt-conquest [0 0] [0 1])
      (should= (:conquest-failed config/messages) @atoms/line3-message)))

  (it "returns true regardless of outcome"
    (with-redefs [rand (constantly 0.5)]
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :player}}
                               {:type :city :city-status :free}]])
      (should (combat/attempt-conquest [0 0] [0 1])))))

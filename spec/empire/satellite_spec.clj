(ns empire.satellite-spec
  (:require [speclj.core :refer :all]
            [empire.atoms :as atoms]
            [empire.satellite :refer :all]
            [empire.test-utils :refer [build-test-map set-test-unit reset-all-atoms! make-initial-test-map]]))

(describe "calculate-satellite-target"
  (before (reset-all-atoms!))
  (it "extends target to boundary in direction of travel"
    (reset! atoms/game-map (make-initial-test-map 5 5 {:type :land}))
    ;; From [1 1] toward [2 2] should extend to [4 4]
    (should= [4 4] (calculate-satellite-target [1 1] [2 2])))

  (it "extends target to right edge when moving east"
    (reset! atoms/game-map (make-initial-test-map 5 5 {:type :land}))
    (should= [2 4] (calculate-satellite-target [2 1] [2 2])))

  (it "extends target to bottom edge when moving south"
    (reset! atoms/game-map (make-initial-test-map 5 5 {:type :land}))
    (should= [4 2] (calculate-satellite-target [1 2] [2 2])))

  (it "extends target to top-left corner when moving northwest"
    (reset! atoms/game-map (make-initial-test-map 5 5 {:type :land}))
    (should= [0 0] (calculate-satellite-target [2 2] [1 1]))))

(describe "move-satellite"
  (before (reset-all-atoms!))
  (it "does not move without a target"
    (reset! atoms/game-map (build-test-map ["###"
                                             "#V#"
                                             "###"]))
    (set-test-unit atoms/game-map "V" :turns-remaining 50)
    (reset! atoms/player-map (make-initial-test-map 3 3 nil))
    (let [result (move-satellite [1 1])]
      (should= [1 1] result)
      (should (:contents (get-in @atoms/game-map [1 1])))
      (should-be-nil (:target (:contents (get-in @atoms/game-map [1 1]))))))

  (it "moves toward its target"
    (reset! atoms/game-map (build-test-map ["####"
                                             "#V##"
                                             "####"
                                             "####"]))
    (set-test-unit atoms/game-map "V" :target [3 3] :turns-remaining 50)
    (reset! atoms/player-map (make-initial-test-map 4 4 nil))
    (let [result (move-satellite [1 1])]
      (should= [2 2] result)
      (should (:contents (get-in @atoms/game-map [2 2])))
      (should-be-nil (:contents (get-in @atoms/game-map [1 1])))
      (should= [3 3] (:target (:contents (get-in @atoms/game-map [2 2]))))))

  (it "moves horizontally when target is directly east"
    (reset! atoms/game-map (build-test-map ["#####"
                                             "#V###"
                                             "#####"]))
    (set-test-unit atoms/game-map "V" :target [1 4] :turns-remaining 50)
    (reset! atoms/player-map (make-initial-test-map 3 5 nil))
    (let [result (move-satellite [1 1])]
      (should= [1 2] result)
      (should (:contents (get-in @atoms/game-map [1 2])))
      (should-be-nil (:contents (get-in @atoms/game-map [1 1])))))

  (it "moves vertically when target is directly south"
    (reset! atoms/game-map (build-test-map ["###"
                                             "#V#"
                                             "###"
                                             "###"
                                             "###"]))
    (set-test-unit atoms/game-map "V" :target [4 1] :turns-remaining 50)
    (reset! atoms/player-map (make-initial-test-map 5 3 nil))
    (let [result (move-satellite [1 1])]
      (should= [2 1] result)
      (should (:contents (get-in @atoms/game-map [2 1])))
      (should-be-nil (:contents (get-in @atoms/game-map [1 1])))))

  (it "gets new target on opposite boundary when reaching right edge"
    (reset! atoms/game-map (build-test-map ["###"
                                             "##V"
                                             "###"]))
    (set-test-unit atoms/game-map "V" :target [1 2] :turns-remaining 50)
    (reset! atoms/player-map (make-initial-test-map 3 3 nil))
    (move-satellite [1 2])
    (let [sat (:contents (get-in @atoms/game-map [1 2]))]
      (should sat)
      (should= 0 (second (:target sat)))))

  (it "gets new target on opposite boundary when reaching bottom edge"
    (reset! atoms/game-map (build-test-map ["###"
                                             "###"
                                             "#V#"]))
    (set-test-unit atoms/game-map "V" :target [2 1] :turns-remaining 50)
    (reset! atoms/player-map (make-initial-test-map 3 3 nil))
    (move-satellite [2 1])
    (let [sat (:contents (get-in @atoms/game-map [2 1]))]
      (should sat)
      (should= 0 (first (:target sat)))))

  (it "gets new target on one of opposite boundaries when at corner"
    (reset! atoms/game-map (build-test-map ["###"
                                             "###"
                                             "##V"]))
    (set-test-unit atoms/game-map "V" :target [2 2] :turns-remaining 50)
    (reset! atoms/player-map (make-initial-test-map 3 3 nil))
    (move-satellite [2 2])
    (let [sat (:contents (get-in @atoms/game-map [2 2]))
          [tx ty] (:target sat)]
      (should sat)
      (should (or (= tx 0) (= ty 0))))))

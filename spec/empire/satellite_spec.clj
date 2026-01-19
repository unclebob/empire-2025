(ns empire.satellite-spec
  (:require [speclj.core :refer :all]
            [empire.atoms :as atoms]
            [empire.satellite :refer :all]
            [empire.test-utils :refer [build-test-map set-test-unit reset-all-atoms! make-initial-test-map]]))

(describe "calculate-satellite-target"
  (before (reset-all-atoms!))
  (it "extends target to boundary in direction of travel"
    (reset! atoms/game-map (make-initial-test-map 10 10 {:type :land}))
    ;; From [2 2] toward [5 5] should extend to [9 9]
    (should= [9 9] (calculate-satellite-target [2 2] [5 5])))

  (it "extends target to right edge when moving east"
    (reset! atoms/game-map (make-initial-test-map 10 10 {:type :land}))
    (should= [5 9] (calculate-satellite-target [5 3] [5 5])))

  (it "extends target to bottom edge when moving south"
    (reset! atoms/game-map (make-initial-test-map 10 10 {:type :land}))
    (should= [9 5] (calculate-satellite-target [3 5] [5 5])))

  (it "extends target to top-left corner when moving northwest"
    (reset! atoms/game-map (make-initial-test-map 10 10 {:type :land}))
    (should= [0 0] (calculate-satellite-target [5 5] [3 3]))))

(describe "move-satellite"
  (before (reset-all-atoms!))
  (it "does not move without a target"
    (reset! atoms/game-map (build-test-map ["##########"
                                             "##########"
                                             "##########"
                                             "##########"
                                             "##########"
                                             "#####V####"
                                             "##########"
                                             "##########"
                                             "##########"
                                             "##########"]))
    (set-test-unit atoms/game-map "V" :turns-remaining 50)
    (reset! atoms/player-map (make-initial-test-map 10 10 nil))
    (let [result (move-satellite [5 5])]
      (should= [5 5] result)
      (should (:contents (get-in @atoms/game-map [5 5])))
      (should-be-nil (:target (:contents (get-in @atoms/game-map [5 5]))))))

  (it "moves toward its target"
    (reset! atoms/game-map (build-test-map ["##########"
                                             "##########"
                                             "##########"
                                             "##########"
                                             "##########"
                                             "#####V####"
                                             "##########"
                                             "##########"
                                             "##########"
                                             "##########"]))
    (set-test-unit atoms/game-map "V" :target [9 9] :turns-remaining 50)
    (reset! atoms/player-map (make-initial-test-map 10 10 nil))
    (let [result (move-satellite [5 5])]
      (should= [6 6] result)
      (should (:contents (get-in @atoms/game-map [6 6])))
      (should-be-nil (:contents (get-in @atoms/game-map [5 5])))
      (should= [9 9] (:target (:contents (get-in @atoms/game-map [6 6]))))))

  (it "moves horizontally when target is directly east"
    (reset! atoms/game-map (build-test-map ["##########"
                                             "##########"
                                             "##########"
                                             "##########"
                                             "##########"
                                             "###V######"
                                             "##########"
                                             "##########"
                                             "##########"
                                             "##########"]))
    (set-test-unit atoms/game-map "V" :target [5 9] :turns-remaining 50)
    (reset! atoms/player-map (make-initial-test-map 10 10 nil))
    (let [result (move-satellite [5 3])]
      (should= [5 4] result)
      (should (:contents (get-in @atoms/game-map [5 4])))
      (should-be-nil (:contents (get-in @atoms/game-map [5 3])))))

  (it "moves vertically when target is directly south"
    (reset! atoms/game-map (build-test-map ["##########"
                                             "##########"
                                             "##########"
                                             "#####V####"
                                             "##########"
                                             "##########"
                                             "##########"
                                             "##########"
                                             "##########"
                                             "##########"]))
    (set-test-unit atoms/game-map "V" :target [9 5] :turns-remaining 50)
    (reset! atoms/player-map (make-initial-test-map 10 10 nil))
    (let [result (move-satellite [3 5])]
      (should= [4 5] result)
      (should (:contents (get-in @atoms/game-map [4 5])))
      (should-be-nil (:contents (get-in @atoms/game-map [3 5])))))

  (it "gets new target on opposite boundary when reaching right edge"
    (reset! atoms/game-map (build-test-map ["##########"
                                             "##########"
                                             "##########"
                                             "##########"
                                             "##########"
                                             "#########V"
                                             "##########"
                                             "##########"
                                             "##########"
                                             "##########"]))
    (set-test-unit atoms/game-map "V" :target [5 9] :turns-remaining 50)
    (reset! atoms/player-map (make-initial-test-map 10 10 nil))
    (move-satellite [5 9])
    (let [sat (:contents (get-in @atoms/game-map [5 9]))]
      (should sat)
      (should= 0 (second (:target sat)))))

  (it "gets new target on opposite boundary when reaching bottom edge"
    (reset! atoms/game-map (build-test-map ["##########"
                                             "##########"
                                             "##########"
                                             "##########"
                                             "##########"
                                             "##########"
                                             "##########"
                                             "##########"
                                             "##########"
                                             "#####V####"]))
    (set-test-unit atoms/game-map "V" :target [9 5] :turns-remaining 50)
    (reset! atoms/player-map (make-initial-test-map 10 10 nil))
    (move-satellite [9 5])
    (let [sat (:contents (get-in @atoms/game-map [9 5]))]
      (should sat)
      (should= 0 (first (:target sat)))))

  (it "gets new target on one of opposite boundaries when at corner"
    (reset! atoms/game-map (build-test-map ["##########"
                                             "##########"
                                             "##########"
                                             "##########"
                                             "##########"
                                             "##########"
                                             "##########"
                                             "##########"
                                             "##########"
                                             "#########V"]))
    (set-test-unit atoms/game-map "V" :target [9 9] :turns-remaining 50)
    (reset! atoms/player-map (make-initial-test-map 10 10 nil))
    (move-satellite [9 9])
    (let [sat (:contents (get-in @atoms/game-map [9 9]))
          [tx ty] (:target sat)]
      (should sat)
      (should (or (= tx 0) (= ty 0))))))

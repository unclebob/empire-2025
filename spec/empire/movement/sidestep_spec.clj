(ns empire.movement.sidestep-spec
  (:require
    [empire.atoms :as atoms]
    [empire.game-loop :as game-loop]
    [empire.test-utils :refer [build-test-map set-test-unit get-test-unit reset-all-atoms! set-test-player-map! make-initial-test-map set-test-world! update-test-world!]]
    [speclj.core :refer :all]))

(describe "sidestep around friendly units"
  (before (reset-all-atoms!))
  (it "sidesteps diagonally around friendly unit and continues moving"
    (set-test-world! (build-test-map ["---------"
                                      "---------"
                                      "---------"
                                      "-----#---"
                                      "----AA###"
                                      "-----#---"
                                      "---------"
                                      "---------"
                                      "---------"]))
    (set-test-unit atoms/game-map "A1" :mode :moving :target [8 4] :steps-remaining 2)
    (set-test-unit atoms/game-map "A2" :mode :sentry)
    (set-test-player-map! (make-initial-test-map 9 9 nil))
    (let [moving-coords (:pos (get-test-unit atoms/game-map "A1"))
          blocking-coords (:pos (get-test-unit atoms/game-map "A2"))
          target-coords [6 4]]
      (game-loop/move-current-unit moving-coords)
      ;; Unit should have sidestepped and continued - now at [6 4] after sidestep+move
      (should (:contents (get-in @atoms/game-map target-coords)))
      ;; Original cell should be empty
      (should-be-nil (:contents (get-in @atoms/game-map moving-coords)))
      ;; Blocking army should still be there
      (should (:contents (get-in @atoms/game-map blocking-coords)))))

  (it "sidesteps orthogonally when diagonals blocked and continues moving"
    (set-test-world! (build-test-map ["---------"
                                      "---------"
                                      "---------"
                                      "-----#---"
                                      "----A----"
                                      "---#-A#--"
                                      "----##---"
                                      "---------"
                                      "---------"]))
    (set-test-unit atoms/game-map "A1" :mode :moving :target [6 6] :steps-remaining 2)
    (set-test-unit atoms/game-map "A2" :mode :sentry)
    (set-test-player-map! (make-initial-test-map 9 9 nil))
    (let [moving-coords (:pos (get-test-unit atoms/game-map "A1"))
          blocking-coords (:pos (get-test-unit atoms/game-map "A2"))]
      (game-loop/move-current-unit moving-coords)
      ;; Unit should have sidestepped and continued toward target
      (should-be-nil (:contents (get-in @atoms/game-map moving-coords)))
      ;; Blocking army should still be there
      (should (:contents (get-in @atoms/game-map blocking-coords)))
      ;; Unit should have progressed (could be at [6 4], [4 6], [6 5], or [5 6] depending on path)
      (should (or (:contents (get-in @atoms/game-map [6 4]))
                  (:contents (get-in @atoms/game-map [4 6]))
                  (:contents (get-in @atoms/game-map [6 5]))
                  (:contents (get-in @atoms/game-map [5 6]))))))

  (it "wakes when no valid sidestep exists"
    (set-test-world! (build-test-map ["---------"
                                      "---------"
                                      "---------"
                                      "---~~----"
                                      "----AA#-#"
                                      "---~~----"
                                      "---------"
                                      "---------"
                                      "---------"]))
    (set-test-unit atoms/game-map "A1" :mode :moving :target [8 4] :steps-remaining 1)
    (set-test-unit atoms/game-map "A2" :mode :sentry)
    (set-test-player-map! (make-initial-test-map 9 9 nil))
    (let [moving-coords (:pos (get-test-unit atoms/game-map "A1"))]
      (game-loop/move-current-unit moving-coords)
      ;; Unit should wake up at original position
      (let [unit (:contents (get-in @atoms/game-map moving-coords))]
        (should= :awake (:mode unit))
        (should= :somethings-in-the-way (:reason unit)))))

  (it "attacks (not sidesteps) when blocked by enemy unit"
    (set-test-world! (build-test-map ["---------"
                                      "---------"
                                      "---------"
                                      "-----#---"
                                      "----A##-#"
                                      "-----#---"
                                      "---------"
                                      "---------"
                                      "---------"]))
    (set-test-unit atoms/game-map "A" :hits 1 :mode :moving :target [8 4] :steps-remaining 1)
    (let [moving-coords (:pos (get-test-unit atoms/game-map "A"))
          enemy-coords [(inc (first moving-coords)) (second moving-coords)]]
      ;; Add blocking enemy army adjacent to moving unit
      (update-test-world! assoc-in enemy-coords {:type :land :contents {:type :army :owner :computer :mode :sentry :hits 1}})
      (set-test-player-map! (make-initial-test-map 9 9 nil))
      (with-redefs [rand (constantly 0.4)]
        (game-loop/move-current-unit moving-coords)
        ;; Unit should attack enemy, not sidestep. Attacker wins and moves to enemy cell.
        (should= nil (:contents (get-in @atoms/game-map moving-coords)))
        (should= :army (:type (:contents (get-in @atoms/game-map enemy-coords))))
        (should= :player (:owner (:contents (get-in @atoms/game-map enemy-coords)))))))

  (it "fighter sidesteps around friendly fighter and continues"
    (set-test-world! (build-test-map ["---------"
                                      "---------"
                                      "---------"
                                      "-----#---"
                                      "----FF###"
                                      "-----#---"
                                      "---------"
                                      "---------"
                                      "---------"]))
    (set-test-unit atoms/game-map "F1" :mode :moving :target [8 4] :fuel 20 :steps-remaining 2)
    (set-test-unit atoms/game-map "F2" :mode :sentry :fuel 10)
    (set-test-player-map! (make-initial-test-map 9 9 nil))
    (let [moving-coords (:pos (get-test-unit atoms/game-map "F1"))
          blocking-coords (:pos (get-test-unit atoms/game-map "F2"))
          target-coords [6 4]]
      (game-loop/move-current-unit moving-coords)
      ;; Fighter should have sidestepped and continued to [6 4]
      (should (:contents (get-in @atoms/game-map target-coords)))
      (should-be-nil (:contents (get-in @atoms/game-map moving-coords)))
      ;; Blocking fighter should still be there
      (should (:contents (get-in @atoms/game-map blocking-coords)))))

  (it "ship sidesteps around friendly ship and continues"
    (set-test-world! (build-test-map ["---------"
                                      "---------"
                                      "---------"
                                      "-----~---"
                                      "----DB~~~"
                                      "-----~---"
                                      "---------"
                                      "---------"
                                      "---------"]))
    (set-test-unit atoms/game-map "D" :mode :moving :target [8 4] :hits 3 :steps-remaining 2)
    (set-test-unit atoms/game-map "B" :mode :sentry :hits 10)
    (set-test-player-map! (make-initial-test-map 9 9 nil))
    (let [moving-coords (:pos (get-test-unit atoms/game-map "D"))
          blocking-coords (:pos (get-test-unit atoms/game-map "B"))
          target-coords [6 4]]
      (game-loop/move-current-unit moving-coords)
      ;; Ship should have sidestepped and continued to [6 4]
      (should (:contents (get-in @atoms/game-map target-coords)))
      (should-be-nil (:contents (get-in @atoms/game-map moving-coords)))
      ;; Blocking ship should still be there
      (should (:contents (get-in @atoms/game-map blocking-coords)))))

  (it "chooses sidestep that gets closer to target using 4-round look-ahead"
    (set-test-world! (build-test-map ["------------"
                                      "------------"
                                      "------------"
                                      "-----#~-----"
                                      "----AA####-#"
                                      "-----#------"
                                      "------------"
                                      "------------"
                                      "------------"
                                      "------------"
                                      "------------"
                                      "------------"]))
    (set-test-unit atoms/game-map "A1" :mode :moving :target [10 4] :steps-remaining 2)
    (set-test-unit atoms/game-map "A2" :mode :sentry)
    (set-test-player-map! (make-initial-test-map 12 12 nil))
    (let [moving-coords (:pos (get-test-unit atoms/game-map "A1"))
          blocking-coords (:pos (get-test-unit atoms/game-map "A2"))
          target-coords [6 4]]
      (game-loop/move-current-unit moving-coords)
      ;; Both sidesteps lead to [6 4] after sidestep+continuation
      (should (:contents (get-in @atoms/game-map target-coords)))
      (should-be-nil (:contents (get-in @atoms/game-map moving-coords)))
      ;; Blocking army should still be there
      (should (:contents (get-in @atoms/game-map blocking-coords)))))

  (it "wakes up when blocked by long line of friendly units (no progress possible)"
    (set-test-world! (build-test-map ["------------"
                                      "------------"
                                      "-----A------"
                                      "---#-A------"
                                      "----AA-----#"
                                      "---#-A------"
                                      "-----A------"
                                      "------------"
                                      "------------"
                                      "------------"
                                      "------------"
                                      "------------"]))
    ;; A1=[4,4] (moving), A2=[5,2], A3=[5,3], A4=[5,4], A5=[5,5], A6=[5,6]
    (set-test-unit atoms/game-map "A1" :mode :moving :target [10 4] :steps-remaining 1)
    (set-test-unit atoms/game-map "A2" :mode :sentry)
    (set-test-unit atoms/game-map "A3" :mode :sentry)
    (set-test-unit atoms/game-map "A4" :mode :sentry)
    (set-test-unit atoms/game-map "A5" :mode :sentry)
    (set-test-unit atoms/game-map "A6" :mode :sentry)
    (set-test-player-map! (make-initial-test-map 12 12 nil))
    (let [moving-coords (:pos (get-test-unit atoms/game-map "A1"))]
      (game-loop/move-current-unit moving-coords)
      ;; Unit should wake up since sidestepping doesn't get us closer
      (let [unit (:contents (get-in @atoms/game-map moving-coords))]
        (should= :awake (:mode unit))
        (should= :somethings-in-the-way (:reason unit)))))

  (it "does not sidestep outside map boundaries"
    (set-test-world! (build-test-map ["AA###"
                                      "~#---"
                                      "-----"
                                      "-----"
                                      "-----"]))
    (set-test-unit atoms/game-map "A1" :mode :moving :target [4 0] :steps-remaining 2)
    (set-test-unit atoms/game-map "A2" :mode :sentry)
    (set-test-player-map! (make-initial-test-map 5 5 nil))
    (let [moving-coords (:pos (get-test-unit atoms/game-map "A1"))
          target-coords [2 0]]
      (game-loop/move-current-unit moving-coords)
      ;; Unit should sidestep to [1 1] and continue to [2 0]
      (should (:contents (get-in @atoms/game-map target-coords)))
      (should-be-nil (:contents (get-in @atoms/game-map moving-coords))))))

(describe "sidestep around cities"
  (before (reset-all-atoms!))
  (it "army sidesteps around friendly city"
    (set-test-world! (build-test-map ["---------"
                                      "---------"
                                      "---------"
                                      "-----#---"
                                      "----AO###"
                                      "-----#---"
                                      "---------"
                                      "---------"
                                      "---------"]))
    (set-test-unit atoms/game-map "A" :mode :moving :target [8 4] :steps-remaining 2)
    (set-test-player-map! (make-initial-test-map 9 9 nil))
    (game-loop/move-current-unit [4 4])
    ;; Army should have sidestepped around friendly city and continued
    (should (:contents (get-in @atoms/game-map [6 4])))
    (should-be-nil (:contents (get-in @atoms/game-map [4 4]))))

  (it "army wakes when no sidestep around friendly city exists"
    (set-test-world! (build-test-map ["---------"
                                      "---------"
                                      "---------"
                                      "---~~----"
                                      "----AO---"
                                      "---~~----"
                                      "---------"
                                      "---------"
                                      "---------"]))
    (set-test-unit atoms/game-map "A" :mode :moving :target [8 4] :steps-remaining 1)
    (set-test-player-map! (make-initial-test-map 9 9 nil))
    (game-loop/move-current-unit [4 4])
    ;; Army should wake up since no sidestep exists
    (let [unit (:contents (get-in @atoms/game-map [4 4]))]
      (should= :awake (:mode unit))
      (should= :cant-move-into-city (:reason unit))))

  (it "fighter sidesteps around free city when not target"
    (set-test-world! (build-test-map ["---------"
                                      "---------"
                                      "---------"
                                      "-----#---"
                                      "----F+###"
                                      "-----#---"
                                      "---------"
                                      "---------"
                                      "---------"]))
    (set-test-unit atoms/game-map "F" :mode :moving :target [8 4] :fuel 20 :steps-remaining 2)
    (set-test-player-map! (make-initial-test-map 9 9 nil))
    (game-loop/move-current-unit [4 4])
    ;; Fighter should have sidestepped around city and continued
    (should (:contents (get-in @atoms/game-map [6 4])))
    (should-be-nil (:contents (get-in @atoms/game-map [4 4]))))

  (it "fighter sidesteps around player city when not target"
    (set-test-world! (build-test-map ["---------"
                                      "---------"
                                      "---------"
                                      "-----#---"
                                      "----FO###"
                                      "-----#---"
                                      "---------"
                                      "---------"
                                      "---------"]))
    (set-test-unit atoms/game-map "F" :mode :moving :target [8 4] :fuel 20 :steps-remaining 2)
    (set-test-player-map! (make-initial-test-map 9 9 nil))
    (game-loop/move-current-unit [4 4])
    ;; Fighter should have sidestepped around city and continued
    (should (:contents (get-in @atoms/game-map [6 4])))
    (should-be-nil (:contents (get-in @atoms/game-map [4 4]))))

  (it "fighter does not sidestep when city is target"
    (set-test-world! (build-test-map ["---------"
                                      "---------"
                                      "---------"
                                      "-----#---"
                                      "----FO---"
                                      "-----#---"
                                      "---------"
                                      "---------"
                                      "---------"]))
    (let [fighter-coords (:pos (get-test-unit atoms/game-map "F"))
          city-coords [(inc (first fighter-coords)) (second fighter-coords)]]
      (set-test-unit atoms/game-map "F" :mode :moving :target city-coords :fuel 20 :steps-remaining 2)
      (update-test-world! assoc-in (conj city-coords :fighter-count) 0)
      (set-test-player-map! (make-initial-test-map 9 9 nil))
      (game-loop/move-current-unit fighter-coords)
      ;; Fighter should land at target city, not sidestep
      (should= 1 (:fighter-count (get-in @atoms/game-map city-coords)))
      (should-be-nil (:contents (get-in @atoms/game-map fighter-coords)))))

  (it "fighter sidesteps around hostile city"
    (set-test-world! (build-test-map ["---------"
                                      "---------"
                                      "---------"
                                      "-----#---"
                                      "----FX###"
                                      "-----#---"
                                      "---------"
                                      "---------"
                                      "---------"]))
    (set-test-unit atoms/game-map "F" :mode :moving :target [8 4] :fuel 20 :steps-remaining 2)
    (set-test-player-map! (make-initial-test-map 9 9 nil))
    (game-loop/move-current-unit [4 4])
    ;; Fighter should have sidestepped around hostile city
    (should (:contents (get-in @atoms/game-map [6 4])))
    (should-be-nil (:contents (get-in @atoms/game-map [4 4])))))

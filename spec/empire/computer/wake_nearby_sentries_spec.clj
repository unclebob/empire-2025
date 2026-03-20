(ns empire.computer.wake-nearby-sentries-spec
  (:require [empire.computer.shared.action-resolution :as action-resolution]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-player-map! set-test-world! update-test-world!]]
            [speclj.core :refer :all]))

(describe "wake-nearby-sentries"
  (before (reset-all-atoms!))

  (it "wakes sentry armies within radius"
    (set-test-world! (build-test-map ["~a~"]))
    (update-test-world! assoc-in [1 0 :contents :mode] :sentry)
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (with-redefs [rand (constantly 0.3)]
      (let [woken (action-resolution/wake-nearby-sentries [0 0] 2)]
        (should= 1 woken)
        (should= :awake (:mode (:contents (get-in (test-utils/read-test-state :game-map) [1 0])))))))

  (it "does not wake armies beyond radius"
    (set-test-world! (build-test-map ["~..a"]))
    (update-test-world! assoc-in [3 0 :contents :mode] :sentry)
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (with-redefs [rand (constantly 0.3)]
      (let [woken (action-resolution/wake-nearby-sentries [0 0] 1)]
        (should= 0 woken)
        (should= :sentry (:mode (:contents (get-in (test-utils/read-test-state :game-map) [3 0])))))))

  (it "does not wake player armies"
    (set-test-world! (build-test-map ["~A~"]))
    (update-test-world! assoc-in [1 0 :contents :mode] :sentry)
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (with-redefs [rand (constantly 0.3)]
      (let [woken (action-resolution/wake-nearby-sentries [0 0] 2)]
        (should= 0 woken)
        (should= :sentry (:mode (:contents (get-in (test-utils/read-test-state :game-map) [1 0])))))))

  (it "does not wake non-sentry armies"
    (set-test-world! (build-test-map ["~a~"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (with-redefs [rand (constantly 0.3)]
      (let [woken (action-resolution/wake-nearby-sentries [0 0] 2)]
        (should= 0 woken))))

  (it "wakes sentry at exact chebyshev distance = radius"
    (set-test-world! (build-test-map ["~~#~~"
                                      "~~#~~"
                                      "a~#~#"
                                      "~~#~~"
                                      "~~#~~"]))
    (update-test-world! assoc-in [0 2 :contents :mode] :sentry)
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (with-redefs [rand (constantly 0.3)]
      (let [woken (action-resolution/wake-nearby-sentries [2 2] 2)]
        (should= 1 woken)
        (should= :awake (:mode (:contents (get-in (test-utils/read-test-state :game-map) [0 2])))))))

  (it "wakes sentries at row 0 and last row boundaries"
    (set-test-world! (build-test-map ["~~a~~"
                                      "~~#~~"
                                      "~~a~~"]))
    (update-test-world! assoc-in [2 0 :contents :mode] :sentry)
    (update-test-world! assoc-in [2 2 :contents :mode] :sentry)
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (with-redefs [rand (constantly 0.3)]
      (let [woken (action-resolution/wake-nearby-sentries [2 1] 1)]
        (should= 2 woken)
        (should= :awake (:mode (:contents (get-in (test-utils/read-test-state :game-map) [2 0]))))
        (should= :awake (:mode (:contents (get-in (test-utils/read-test-state :game-map) [2 2])))))))

  (it "sets direction pointing away from trigger with negative dc"
    (set-test-world! (build-test-map ["~~#~~"
                                      "~~#~~"
                                      "a~#~~"
                                      "~~#~~"
                                      "~~#~~"]))
    (update-test-world! assoc-in [0 2 :contents :mode] :sentry)
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (with-redefs [rand (constantly 0.3)]
      (action-resolution/wake-nearby-sentries [2 2] 2)
      (let [dir (:interior-explore-direction (:contents (get-in (test-utils/read-test-state :game-map) [0 2])))]
        (should= -1 (first dir)))))

  (it "sets direction pointing away from trigger with negative dr"
    (set-test-world! (build-test-map ["~~a~~"
                                      "~~~~~"
                                      "~~#~~"
                                      "~~~~~"
                                      "~~~~~"]))
    (update-test-world! assoc-in [2 0 :contents :mode] :sentry)
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (with-redefs [rand (constantly 0.3)]
      (action-resolution/wake-nearby-sentries [2 2] 2)
      (let [dir (:interior-explore-direction (:contents (get-in (test-utils/read-test-state :game-map) [2 0])))]
        (should= -1 (second dir)))))

  (it "uses random direction when dc is zero"
    (set-test-world! (build-test-map ["~~a~~"
                                      "~~~~~"
                                      "~~#~~"
                                      "~~~~~"
                                      "~~~~~"]))
    (update-test-world! assoc-in [2 0 :contents :mode] :sentry)
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (with-redefs [rand (constantly 0.3)]
      (action-resolution/wake-nearby-sentries [2 2] 2)
      (let [dir (:interior-explore-direction (:contents (get-in (test-utils/read-test-state :game-map) [2 0])))]
        (should= -1 (first dir)))))

  (it "uses random direction when dr is zero"
    (set-test-world! (build-test-map ["~~#~~"
                                      "~~#~~"
                                      "a~#~~"
                                      "~~#~~"
                                      "~~#~~"]))
    (update-test-world! assoc-in [0 2 :contents :mode] :sentry)
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (with-redefs [rand (constantly 0.3)]
      (action-resolution/wake-nearby-sentries [2 2] 2)
      (let [dir (:interior-explore-direction (:contents (get-in (test-utils/read-test-state :game-map) [0 2])))]
        (should= -1 (second dir)))))

  (it "random direction picks 1 when rand >= 0.5"
    (set-test-world! (build-test-map ["~~a~~"
                                      "~~~~~"
                                      "~~#~~"
                                      "~~~~~"
                                      "~~~~~"]))
    (update-test-world! assoc-in [2 0 :contents :mode] :sentry)
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (with-redefs [rand (constantly 0.7)]
      (action-resolution/wake-nearby-sentries [2 2] 2)
      (let [dir (:interior-explore-direction (:contents (get-in (test-utils/read-test-state :game-map) [2 0])))]
        (should= 1 (first dir)))))

  (it "wakes sentry at c range upper boundary"
    (set-test-world! (build-test-map ["~~~~"
                                      "~#~a"
                                      "~~~~"
                                      "~~~~"]))
    (update-test-world! assoc-in [3 1 :contents :mode] :sentry)
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (with-redefs [rand (constantly 0.3)]
      (let [woken (action-resolution/wake-nearby-sentries [1 1] 2)]
        (should= 1 woken)
        (should= :awake (:mode (:contents (get-in (test-utils/read-test-state :game-map) [3 1]))))))))

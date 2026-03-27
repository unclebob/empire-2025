(ns empire.ui.quil.headless-spec
  (:require [empire.ui.quil.headless :as headless]
            [empire.game.loop.core :as game-loop]
            [empire.state.api :as sa]
            [empire.test.utils :refer [reset-all-atoms!]]
            [speclj.core :refer :all]))

(describe "headless-progress-line"
  (it "reports explored percent and invasion state"
    (should= "Round 20 explored 50.0% invasion yes"
             (#'headless/headless-progress-line
              20
              [[{:type :sea} {:type :unexplored}]
               [{:type :land} {:type :unexplored}]]
              {:active? true}))))

(describe "run-headless!"
  (before (reset-all-atoms!))

  (it "prints progress every 20 rounds and stops at the requested round"
    (let [player-updates (atom 0)
          computer-updates (atom 0)
          advances (atom 0)]
      (sa/write-state! :computer-map [[{:type :sea} {:type :land}]
                                      [{:type :unexplored} {:type :unexplored}]])
      (sa/write-state! :major-invasion-state {:active? false})
      (with-redefs [empire.ui.quil.headless/install-seeded-random! (fn [] nil)
                    empire.ui.quil.headless/initialize-map! (fn [] nil)
                    empire.game.loop.core/update-player-map (fn [] (swap! player-updates inc))
                    empire.game.loop.core/update-computer-map (fn [] (swap! computer-updates inc))
                    empire.game.loop.core/advance-game-batch (fn []
                                                               (swap! advances inc)
                                                               (sa/update-state! :round-number inc)
                                                               (when (= 20 (sa/read-state :round-number))
                                                                 (sa/write-state! :major-invasion-state {:active? true})))]
        (should= "Round 20 explored 50.0% invasion yes\nRound 40 explored 50.0% invasion yes\n"
                 (with-out-str
                   (headless/run-headless! {:headless-rounds 40})))
        (should= 40 (sa/read-state :round-number))
        (should= 40 @player-updates)
        (should= 40 @computer-updates)
        (should= 40 @advances))))

  (it "prints a final report when game over stops the run between checkpoints"
    (sa/write-state! :computer-map [[{:type :sea} {:type :land}]
                                    [{:type :unexplored} {:type :unexplored}]])
    (sa/write-state! :major-invasion-state {:active? false})
    (with-redefs [empire.ui.quil.headless/install-seeded-random! (fn [] nil)
                  empire.ui.quil.headless/initialize-map! (fn [] nil)
                  empire.game.loop.core/update-player-map (fn [] nil)
                  empire.game.loop.core/update-computer-map (fn [] nil)
                  empire.game.loop.core/advance-game-batch (fn []
                                                             (sa/update-state! :round-number inc)
                                                             (when (= 37 (sa/read-state :round-number))
                                                               (sa/write-state! :paused true)))]
      (should= "Round 20 explored 50.0% invasion no\nRound 37 explored 50.0% invasion no\n"
               (with-out-str
                 (headless/run-headless! {:headless-rounds 60})))))

  (it "writes a debug dump when headless exits and dumping is enabled"
    (sa/write-state! :computer-map [[{:type :sea}]])
    (sa/write-state! :major-invasion-state {:active? false})
    (sa/write-state! :debug-dump-on-exit? true)
    (let [writes (atom [])]
      (with-redefs [empire.ui.quil.headless/install-seeded-random! (fn [] nil)
                    empire.ui.quil.headless/initialize-map! (fn [] nil)
                    empire.game.loop.core/update-player-map (fn [] nil)
                    empire.game.loop.core/update-computer-map (fn [] nil)
                    empire.game.loop.core/advance-game-batch (fn []
                                                               (sa/update-state! :round-number inc)
                                                               (when (= 2 (sa/read-state :round-number))
                                                                 (sa/write-state! :paused true)))
                    empire.game-mechanics.debug.dump/write-full-dump! (fn []
                                                                        (swap! writes conj :dump)
                                                                        "debug-headless.txt")]
        (should-contain "Debug log written: debug-headless.txt"
                        (with-out-str
                          (headless/run-headless! {:headless-rounds 10})))
        (should= [:dump] @writes)
        (should (sa/read-state :debug-dump-written?)))))

  (it "keeps major invasion probe stopping disabled while running headless"
    (let [observed-stop-flags (atom [])]
      (sa/write-state! :computer-map [[{:type :sea} {:type :land}]
                                      [{:type :unexplored} {:type :unexplored}]])
      (sa/write-state! :major-invasion-state {:active? false})
      (with-redefs [empire.ui.quil.headless/install-seeded-random! (fn [] nil)
                    empire.ui.quil.headless/initialize-map! (fn [] nil)
                    empire.game.loop.core/update-player-map (fn [] nil)
                    empire.game.loop.core/update-computer-map (fn [] nil)
                    empire.game.loop.core/advance-game-batch (fn []
                                                               (swap! observed-stop-flags conj
                                                                      (sa/read-state :headless-stop-on-major-invasion?))
                                                               (sa/update-state! :round-number inc))]
        (headless/run-headless! {:headless-rounds 3})
        (should= [false false false] @observed-stop-flags)
        (should-not (sa/read-state :headless-stop-on-major-invasion?))))))

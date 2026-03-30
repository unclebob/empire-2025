(ns empire.game-loop-round-start-spec
  (:require [empire.game-mechanics.debug.integrity :as integrity]
            [empire.game.loop.core :as game-loop]
            [empire.game.loop.control-decisions :as control-decisions]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-player-map! set-test-world!]]
            [speclj.core :refer :all]))

(describe "start-new-round"
  (before
    (reset-all-atoms!)
    (set-test-world! (build-test-map ["O"]))
    (set-test-player-map! (build-test-map ["#"]))
    (set-test-computer-map! (build-test-map ["#"]))
    (test-utils/set-test-state! :production {})
    (test-utils/set-test-state! :round-number 0)
    (test-utils/set-test-state! :handicap-rounds-remaining 0)
    (test-utils/set-test-state! :handicap-display-rounds nil)
    (test-utils/set-test-state! :player-items [])
    (test-utils/set-test-state! :waiting-for-input true)
    (test-utils/set-test-state! :attention-message "old message")
    (test-utils/set-test-state! :cells-needing-attention [[0 0]]))

  (it "increments round number"
    (game-loop/start-new-round)
    (should= 1 (test-utils/read-test-state :round-number)))

  (it "builds player items list"
    (game-loop/start-new-round)
    (should-contain [0 0] (test-utils/read-test-state :player-items)))

  (it "suppresses player items while handicap rounds remain"
    (test-utils/set-test-state! :handicap-rounds-remaining 2)
    (test-utils/set-test-state! :handicap-display-rounds 2)
    (game-loop/start-new-round)
    (should= [] (vec (test-utils/read-test-state :player-items))))

  (it "pauses and reports an impossible player-phase skip"
    (with-redefs [empire.game.loop.round-start/current-player-items (fn [_] [])]
      (game-loop/start-new-round)
      (should= true (test-utils/read-test-state :paused))
      (should= [[0 0]] (vec (test-utils/read-test-state :player-items)))
      (should-contain "Player phase skip detected" (test-utils/read-test-state :error-message))
      (should= Long/MAX_VALUE (test-utils/read-test-state :error-until)))))

  (it "resets waiting-for-input to false"
    (game-loop/start-new-round)
    (should= false (test-utils/read-test-state :waiting-for-input)))

  (it "clears message"
    (game-loop/start-new-round)
    (should= "" (test-utils/read-test-state :attention-message)))

  (it "clears cells-needing-attention"
    (game-loop/start-new-round)
    (should= [] (test-utils/read-test-state :cells-needing-attention)))

  (it "checks world integrity once per round"
    (let [calls (atom 0)]
      (with-redefs [integrity/check-world-integrity! (fn []
                                                       (swap! calls inc)
                                                       nil)]
        (game-loop/start-new-round)
        (should= 1 @calls))))

  (it "builds explicit round-start state"
    (should= {:player-items [[0 0]]
              :computer-items [[1 1]]
              :game-over nil
              :waiting-for-input false
              :attention-message ""
              :cells-needing-attention []}
             (control-decisions/round-start-state
              {:handicap-rounds-remaining 0
               :player-items [[0 0]]
               :computer-items [[1 1]]
               :game-over-check-enabled true})))

  (it "does not wake carrier fighters - they stay asleep until u is pressed"
    (set-test-world! (-> (build-test-map ["C"])
                         (assoc-in [0 0 :contents :fighter-count] 2)
                         (assoc-in [0 0 :contents :awake-fighters] 0)))
    (set-test-player-map! (build-test-map ["~"]))
    (set-test-computer-map! (build-test-map ["~"]))
    (game-loop/start-new-round)
    (let [carrier (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))]
      (should= 0 (:awake-fighters carrier 0))))

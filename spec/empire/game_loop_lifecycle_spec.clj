(ns empire.game-loop-lifecycle-spec
  (:require [empire.config.core :as config]
            [empire.game.loop.core :as game-loop]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map get-test-unit reset-all-atoms! set-test-computer-map! set-test-player-map! set-test-unit set-test-world!]]
            [speclj.core :refer :all]))

(describe "round lifecycle"
  (before (reset-all-atoms!))

  (context "item-processed"
    (it "resets waiting-for-input to false"
      (test-utils/set-test-state! :waiting-for-input true)
      (game-loop/item-processed)
      (should= false (test-utils/read-test-state :waiting-for-input)))

    (it "preserves attention-message"
      (test-utils/set-test-state! :attention-message "test message")
      (game-loop/item-processed)
      (should= "test message" (test-utils/read-test-state :attention-message)))

    (it "clears cells-needing-attention"
      (test-utils/set-test-state! :cells-needing-attention [[1 2] [3 4]])
      (game-loop/item-processed)
      (should= [] (test-utils/read-test-state :cells-needing-attention))))

  (context "build-player-items"
    (before
      (set-test-world! (build-test-map ["#O"
                                        "AX"])))

    (it "returns player city coordinates"
      (let [items (game-loop/build-player-items)]
        (should-contain [1 0] items)))

    (it "returns player unit coordinates"
      (let [items (game-loop/build-player-items)]
        (should-contain [0 1] items)))

    (it "does not return computer cities"
      (let [items (game-loop/build-player-items)]
        (should-not-contain [1 1] items)))

    (it "does not return empty land"
      (let [items (game-loop/build-player-items)]
        (should-not-contain [0 0] items))))

  (context "remove-dead-units"
    (before
      (set-test-world! (-> (build-test-map ["AF"
                                            "##"])
                           (assoc-in [0 0 :contents :hits] 0)
                           (assoc-in [1 0 :contents :hits] 1)))
      (set-test-player-map! (build-test-map ["##"
                                             "##"])))

    (it "removes units with hits <= 0"
      (game-loop/remove-dead-units)
      (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))))

    (it "keeps units with hits > 0"
      (game-loop/remove-dead-units)
      (should= {:type :fighter :owner :player :hits 1 :fuel 32}
               (:contents (get-in (test-utils/read-test-state :game-map) [1 0])))))

  (context "reset-steps-remaining"
    (before
      (set-test-world! (assoc-in (build-test-map ["AF"
                                                  "A#"])
                                 [0 1 :contents :owner] :computer)))

    (it "sets steps-remaining for player army"
      (game-loop/reset-steps-remaining)
      (should= (config/unit-speed :army)
               (:steps-remaining (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))))

    (it "sets steps-remaining for player fighter"
      (game-loop/reset-steps-remaining)
      (should= (config/unit-speed :fighter)
               (:steps-remaining (:contents (get-in (test-utils/read-test-state :game-map) [1 0])))))

    (it "does not set steps-remaining for computer units"
      (game-loop/reset-steps-remaining)
      (should-be-nil (:steps-remaining (:contents (get-in (test-utils/read-test-state :game-map) [0 1])))))

    (it "scales steps-remaining by damage for multi-hit ships"
      (set-test-world! (build-test-map ["D"]))
      (set-test-unit (test-utils/game-map-atom) "D" :hits 1)
      (game-loop/reset-steps-remaining)
      (should= 1 (:steps-remaining (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))))))

(describe "build-computer-items"
  (before (reset-all-atoms!))

  (it "returns computer city coordinates"
    (set-test-world! (build-test-map ["#O"
                                      "aX"]))
    (let [items (game-loop/build-computer-items)]
      (should-contain [1 1] items)))

  (it "returns computer unit coordinates"
    (set-test-world! (build-test-map ["#O"
                                      "aX"]))
    (let [items (game-loop/build-computer-items)]
      (should-contain [0 1] items)))

  (it "does not return player cities"
    (set-test-world! (build-test-map ["#O"
                                      "aX"]))
    (let [items (game-loop/build-computer-items)]
      (should-not-contain [1 0] items)))

  (it "does not return empty land"
    (set-test-world! (build-test-map ["#O"
                                      "aX"]))
    (let [items (game-loop/build-computer-items)]
      (should-not-contain [0 0] items))))

(describe "computer unit logging"
  (before (reset-all-atoms!))

  (it "formats one snapshot per computer unit with round and position"
    (set-test-world! (build-test-map ["ad"]))
    (test-utils/set-test-state! :round-number 7)
    (let [entries (#'game-loop/computer-unit-snapshots
                   (test-utils/read-test-world)
                   (test-utils/read-test-state :round-number))]
      (should= [{:round 7
                 :pos [0 0]
                 :unit {:type :army :owner :computer :hits 1}}
                {:round 7
                 :pos [1 0]
                 :unit {:type :destroyer :owner :computer :hits 3}}]
               entries)))

  (it "appends all computer units when a log file is configured"
    (set-test-world! (build-test-map ["ad"]))
    (test-utils/set-test-state! :round-number 3)
    (test-utils/set-test-state! :computer-unit-log-file "empire-units-test.log")
    (let [written (atom nil)]
      (with-redefs [spit (fn [path content & opts]
                           (reset! written {:path path :content content :opts opts}))]
        (#'game-loop/log-computer-units!)
        (should= "empire-units-test.log" (:path @written))
        (should-contain "{:round 3, :pos [0 0]" (:content @written))
        (should-contain "{:round 3, :pos [1 0]" (:content @written))
        (should-contain ":append true" (pr-str (:opts @written)))))))

(ns empire.game-loop-wake-spec
  (:require [empire.config.core :as config]
            [empire.game.loop.core :as game-loop]
            [empire.game-mechanics.containers.ops :as container-ops]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map make-initial-test-map reset-all-atoms! set-test-unit set-test-world!]]
            [speclj.core :refer :all]))

(describe "wake and sleep logic"
  (before (reset-all-atoms!))

  (context "wake-airport-fighters"
    (it "wakes one fighter in an empty player city airport"
      (set-test-world! (build-test-map ["O"]))
      (test-utils/update-test-world! assoc-in [0 0 :fighter-count] 4)
      (test-utils/update-test-world! assoc-in [0 0 :awake-fighters] 0)
      (game-loop/wake-airport-fighters)
      (should= 1 (:awake-fighters (get-in (test-utils/read-test-state :game-map) [0 0]))))

    (it "does not wake airport fighters in an occupied city"
      (set-test-world! (build-test-map ["O"]))
      (test-utils/update-test-world! assoc-in [0 0 :fighter-count] 4)
      (test-utils/update-test-world! assoc-in [0 0 :awake-fighters] 0)
      (test-utils/update-test-world! assoc-in [0 0 :contents] {:type :army :owner :player :mode :awake :hits 1})
      (game-loop/wake-airport-fighters)
      (should= 0 (:awake-fighters (get-in (test-utils/read-test-state :game-map) [0 0]))))

    (it "does not change a city that already has an awake airport fighter"
      (set-test-world! (build-test-map ["O"]))
      (test-utils/update-test-world! assoc-in [0 0 :fighter-count] 4)
      (test-utils/update-test-world! assoc-in [0 0 :awake-fighters] 1)
      (game-loop/wake-airport-fighters)
      (should= 1 (:awake-fighters (get-in (test-utils/read-test-state :game-map) [0 0]))))

    (it "launches one fighter when an empty player city has a flight-path"
      (set-test-world! (build-test-map ["O#"]))
      (test-utils/update-test-world! assoc-in [0 0 :fighter-count] 4)
      (test-utils/update-test-world! assoc-in [0 0 :awake-fighters] 0)
      (test-utils/update-test-world! assoc-in [0 0 :flight-path] [1 0])
      (let [launches (atom [])]
        (with-redefs [container-ops/launch-fighter-from-airport
                      (fn [coords target]
                        (swap! launches conj [coords target])
                        (test-utils/update-test-world! update-in [0 0 :fighter-count] dec)
                        [1 0])]
          (game-loop/wake-airport-fighters)
          (should= [[[0 0] [1 0]]] @launches)
          (let [city (get-in (test-utils/read-test-state :game-map) [0 0])]
            (should= 3 (:fighter-count city))
            (should= 0 (:awake-fighters city 0))
            (should= true (:flight-path-launched city))))))

    (it "does not wake a fighter when the city launches one on its flight-path"
      (set-test-world! (build-test-map ["O#"]))
      (test-utils/update-test-world! assoc-in [0 0 :fighter-count] 4)
      (test-utils/update-test-world! assoc-in [0 0 :awake-fighters] 0)
      (test-utils/update-test-world! assoc-in [0 0 :flight-path] [1 0])
        (with-redefs [container-ops/launch-fighter-from-airport
                      (fn [_ _]
                        (test-utils/update-test-world! update-in [0 0 :fighter-count] dec)
                        [1 0])]
          (game-loop/wake-airport-fighters)
          (should= 0 (:awake-fighters (get-in (test-utils/read-test-state :game-map) [0 0]))))))

  (context "wake-carrier-fighters"
    (it "wakes all fighters on player carriers"
      (set-test-world! (build-test-map ["C"]))
      (set-test-unit (test-utils/game-map-atom) "C" :fighter-count 3)
      (game-loop/wake-carrier-fighters)
      (should= 3 (:awake-fighters (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))))

    (it "ignores computer carriers"
      (set-test-world! (build-test-map ["c"]))
      (set-test-unit (test-utils/game-map-atom) "c" :fighter-count 3)
      (game-loop/wake-carrier-fighters)
      (should= 0 (:awake-fighters (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))))

    (it "ignores carriers with no fighters"
      (set-test-world! (build-test-map ["C"]))
      (set-test-unit (test-utils/game-map-atom) "C" :fighter-count 0)
      (game-loop/wake-carrier-fighters)
      (should= 0 (:awake-fighters (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))))))

  (context "consume-sentry-fighter-fuel"
    (it "decrements fuel for sentry fighters"
      (set-test-world! (build-test-map ["F"]))
      (set-test-unit (test-utils/game-map-atom) "F" :mode :sentry :fuel 20)
      (game-loop/consume-sentry-fighter-fuel)
      (should= 19 (:fuel (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))))

    (it "wakes fighter when fuel reaches 1"
      (set-test-world! (build-test-map ["F"]))
      (set-test-unit (test-utils/game-map-atom) "F" :mode :sentry :fuel 2)
      (game-loop/consume-sentry-fighter-fuel)
      (let [unit (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))]
        (should= 1 (:fuel unit))
        (should= :awake (:mode unit))
        (should= :fighter-out-of-fuel (:reason unit))))

    (it "sets hits to 0 when fuel reaches 0"
      (set-test-world! (build-test-map ["F"]))
      (set-test-unit (test-utils/game-map-atom) "F" :mode :sentry :fuel 1)
      (game-loop/consume-sentry-fighter-fuel)
      (should= 0 (:hits (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))))

    (it "sets error message when fighter crashes"
      (set-test-world! (build-test-map ["F"]))
      (set-test-unit (test-utils/game-map-atom) "F" :mode :sentry :fuel 1)
      (game-loop/consume-sentry-fighter-fuel)
      (should-contain (:fighter-crashed config/messages) (test-utils/read-test-state :error-message)))

    (it "does not affect moving fighters"
      (set-test-world! (build-test-map ["F"]))
      (set-test-unit (test-utils/game-map-atom) "F" :mode :moving :fuel 20)
      (game-loop/consume-sentry-fighter-fuel)
      (should= 20 (:fuel (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))))

    (it "wakes fighter with bingo when fuel is low and friendly city in range"
      (set-test-world! (build-test-map ["OF"]))
      (set-test-unit (test-utils/game-map-atom) "F" :mode :sentry :fuel 6)
      (game-loop/consume-sentry-fighter-fuel)
      (let [unit (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))]
        (should= 5 (:fuel unit))
        (should= :awake (:mode unit))
        (should= :fighter-bingo (:reason unit)))))

  (context "wake-sentries-seeing-enemy"
    (it "wakes sentry unit when enemy is adjacent"
      (set-test-world! (build-test-map ["Aa"]))
      (test-utils/set-test-player-map! (make-initial-test-map 1 2 nil))
      (set-test-unit (test-utils/game-map-atom) "A" :mode :sentry)
      (game-loop/wake-sentries-seeing-enemy)
      (let [unit (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))]
        (should= :awake (:mode unit))
        (should= :enemy-spotted (:reason unit))))

    (it "does not wake sentry when no enemy visible"
      (set-test-world! (build-test-map ["A#"]))
      (test-utils/set-test-player-map! (make-initial-test-map 1 2 nil))
      (set-test-unit (test-utils/game-map-atom) "A" :mode :sentry)
      (game-loop/wake-sentries-seeing-enemy)
      (let [unit (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))]
        (should= :sentry (:mode unit))))

    (it "does not wake awake units"
      (set-test-world! (build-test-map ["Aa"]))
      (test-utils/set-test-player-map! (make-initial-test-map 1 2 nil))
      (set-test-unit (test-utils/game-map-atom) "A" :mode :awake)
      (game-loop/wake-sentries-seeing-enemy)
      (let [unit (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))]
        (should= :awake (:mode unit))
        (should-be-nil (:reason unit))))

    (it "does not wake moving units"
      (set-test-world! (build-test-map ["Aa"]))
      (test-utils/set-test-player-map! (make-initial-test-map 1 2 nil))
      (set-test-unit (test-utils/game-map-atom) "A" :mode :moving :target [5 0])
      (game-loop/wake-sentries-seeing-enemy)
      (let [unit (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))]
        (should= :moving (:mode unit))))

    (it "wakes sentry naval units when enemy visible"
      (set-test-world! (build-test-map ["Ds"]))
      (test-utils/set-test-player-map! (make-initial-test-map 1 2 nil))
      (set-test-unit (test-utils/game-map-atom) "D" :mode :sentry)
      (game-loop/wake-sentries-seeing-enemy)
      (let [unit (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))]
        (should= :awake (:mode unit))
        (should= :enemy-spotted (:reason unit))))

    (it "does not wake computer sentry units"
      (set-test-world! (build-test-map ["aA"]))
      (test-utils/set-test-player-map! (make-initial-test-map 1 2 nil))
      (set-test-unit (test-utils/game-map-atom) "a" :mode :sentry)
      (game-loop/wake-sentries-seeing-enemy)
      (let [unit (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))]
        (should= :sentry (:mode unit))))))

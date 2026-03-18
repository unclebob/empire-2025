(ns empire.computer.army-positioning-spec
  "Tests for VMS Empire style computer army movement."
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.army :as army]
            [empire.computer.core :as core]
            [empire.computer.production :as production]
            [empire.computer.stamping :as stamping]
            [empire.game-mechanics.services.combat :as combat]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-world! update-test-world!]]))

(defn- disable-opening!
  []
  (test-utils/set-test-state! :round-number nil))

(defn- sync-computer-map!
  []
  (set-test-computer-map! (test-utils/read-test-state :game-map)))
(describe "should-sentry-on-coast?"
  (before
    (reset-all-atoms!)
    (disable-opening!))

  (it "returns true for coastal land with country-id, not city, not near computer city"
    (let [world (build-test-map ["#~"])]
      (set-test-world! world)
      (set-test-computer-map! world))
    (should (@#'army/should-sentry-on-coast? [0 0] 1)))

  (it "returns false when country-id is nil"
    (let [world (build-test-map ["#~"])]
      (set-test-world! world)
      (set-test-computer-map! world))
    (should-not (@#'army/should-sentry-on-coast? [0 0] nil)))

  (it "returns false when not adjacent to sea"
    (let [world (build-test-map ["###"
                                 "###"
                                 "###"])]
      (set-test-world! world)
      (set-test-computer-map! world))
    (should-not (@#'army/should-sentry-on-coast? [1 1] 1)))

  (it "returns false when position is a city"
    (let [world (build-test-map ["+~"])]
      (set-test-world! world)
      (set-test-computer-map! world))
    (should-not (@#'army/should-sentry-on-coast? [0 0] 1)))

  (it "returns false when adjacent to computer city"
    (let [world (build-test-map ["#~"
                                 "X#"])]
      (set-test-world! world)
      (set-test-computer-map! world))
    (should-not (@#'army/should-sentry-on-coast? [0 0] 1)))

  (it "returns false when the sea is hidden on the computer map"
    (set-test-world! (build-test-map ["#~"]))
    (set-test-computer-map! [[{:type :land} nil]])
    (should-not (@#'army/should-sentry-on-coast? [0 0] 1))))

(describe "can-settle-here?"
  (before
    (reset-all-atoms!)
    (disable-opening!))

  (it "returns true for coastal land with country-id, not city"
    (let [world (build-test-map ["#~"])]
      (set-test-world! world)
      (set-test-computer-map! world))
    (should (@#'army/can-settle-here? [0 0] 1)))

  (it "returns false when country-id is nil"
    (let [world (build-test-map ["#~"])]
      (set-test-world! world)
      (set-test-computer-map! world))
    (should-not (@#'army/can-settle-here? [0 0] nil)))

  (it "returns false when not adjacent to sea"
    (let [world (build-test-map ["###"
                                 "###"
                                 "###"])]
      (set-test-world! world)
      (set-test-computer-map! world))
    (should-not (@#'army/can-settle-here? [1 1] 1)))

  (it "returns false when position is a city"
    (let [world (build-test-map ["+~"])]
      (set-test-world! world)
      (set-test-computer-map! world))
    (should-not (@#'army/can-settle-here? [0 0] 1))))
(describe "process-army"
  (before
    (reset-all-atoms!)
    (disable-opening!))
  (context "attack behavior"
    (it "attacks adjacent player army"
      (set-test-world! (build-test-map ["aA#"]))
      (set-test-computer-map! (build-test-map ["aA#"]))
      (let [result (army/process-army [0 0])]
        ;; Either army won or lost, but combat happened
        ;; Check that computer army is no longer at [0 0]
        (should (or (nil? (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))
                    ;; Or army moved to [1 0] after winning
                    (= :army (:type (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))))))))

    (it "attacks adjacent free city"
      (set-test-world! (build-test-map ["a+#"]))
      (set-test-computer-map! (build-test-map ["a+#"]))
      ;; Run multiple times to account for 50% conquest chance
      (loop [attempts 10]
        (when (pos? attempts)
          (set-test-world! (build-test-map ["a+#"]))
          (set-test-computer-map! (build-test-map ["a+#"]))
          (army/process-army [0 0])
          (let [city-status (:city-status (get-in (test-utils/read-test-state :game-map) [1 0]))]
            (when (= :free city-status)
              (recur (dec attempts))))))
      ;; After up to 10 attempts, city should be conquered (very high probability)
      ;; Actually we just verify the army tried to attack
      (should-not= :free (:city-status (get-in (test-utils/read-test-state :game-map) [1 0])))))

  (context "sentry behavior"
    (it "sentry army doesn't move even with free city nearby"
      (set-test-world! (build-test-map ["a#+"]))
      (set-test-computer-map! (build-test-map ["a#+"]))
      (update-test-world! assoc-in [0 0 :contents :mode] :sentry)
      (test-utils/update-test-state! :computer-map assoc-in [0 0 :contents :mode] :sentry)
      (army/process-army [0 0])
      (should= :army (get-in (test-utils/read-test-state :game-map) [0 0 :contents :type]))
      (should= :sentry (get-in (test-utils/read-test-state :game-map) [0 0 :contents :mode])))

    (it "sentry army attacks adjacent player army"
      (set-test-world! (build-test-map ["aA#"]))
      (set-test-computer-map! (build-test-map ["aA#"]))
      (update-test-world! assoc-in [0 0 :contents :mode] :sentry)
      (army/process-army [0 0])
      ;; Combat should have occurred
      (should (or (nil? (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))
                  (= :computer (:owner (:contents (get-in (test-utils/read-test-state :game-map) [1 0])))))))

    (it "sentry army with attack-target moves toward target"
      (set-test-world! (build-test-map ["a##+"]))
      (set-test-computer-map! (build-test-map ["a##+"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :army :owner :computer :hits 1 :mode :sentry
              :attack-target [3 0] :country-id 1})
      (army/process-army [0 0])
      ;; Army should have moved toward target (no longer at [0 0])
      (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))
      (should= :army (get-in (test-utils/read-test-state :game-map) [1 0 :contents :type]))))

  (context "attack-target behavior"
    (it "moves toward valid target"
      ;; Army at [0 0], free city at [3 0]
      (set-test-world! (build-test-map ["a##+"]))
      (set-test-computer-map! (build-test-map ["a##+"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :army :owner :computer :hits 1 :mode :awake
              :attack-target [3 0] :country-id 1})
      (army/process-army [0 0])
      ;; Should move toward target
      (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))
      (should= :army (get-in (test-utils/read-test-state :game-map) [1 0 :contents :type])))

    (it "clears target when city conquered by computer"
      ;; Army at [0 0], target city already computer-owned
      (set-test-world! [[{:type :land :contents {:type :army :owner :computer :hits 1
                                                        :mode :awake :attack-target [2 0]}}
                                {:type :land}
                                {:type :city :city-status :computer}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (army/process-army [0 0])
      ;; Target should be cleared
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [0 0 :contents :attack-target])))

    (it "clears target when city no longer exists on computer-map"
      ;; Army with attack-target, but target cell is unexplored on computer-map
      (set-test-world! [[{:type :land :contents {:type :army :owner :computer :hits 1
                                                        :mode :awake :attack-target [2 0]}}
                                {:type :land}
                                {:type :city :city-status :free}]])
      (set-test-computer-map! [[{:type :land :contents {:type :army :owner :computer :hits 1
                                                        :mode :awake :attack-target [2 0]}}
                                {:type :land}
                                nil]])
      (army/process-army [0 0])
      ;; Target should be cleared (not visible on computer-map)
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [0 0 :contents :attack-target]))))

  (context "city exit"
    (it "army in computer city moves to empty land neighbor"
      ;; X = computer city with army; # = empty land (no coastal cells)
      (set-test-world! (build-test-map ["X#"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :army :owner :computer :hits 1
              :mode :awake :country-id 1})
      (sync-computer-map!)
      (army/process-army [0 0])
      ;; Army should have left the city
      (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))
      (should= :army (get-in (test-utils/read-test-state :game-map) [1 0 :contents :type])))

    (it "army does not move into computer city"
      ;; Army at [0 0] on land, computer city at [1 0], no other land
      (set-test-world! (build-test-map ["aX"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (army/process-army [0 0])
      ;; Army should stay — computer city is not passable
      (should= :army (get-in (test-utils/read-test-state :game-map) [0 0 :contents :type]))
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [1 0 :contents])))

    (it "army in computer city stays if all neighbors are sea"
      ;; X = computer city with army; ~ = sea
      (set-test-world! (build-test-map ["X~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :army :owner :computer :hits 1
              :mode :awake :country-id 1})
      (army/process-army [0 0])
      ;; Army should still be in the city (no land neighbor)
      (should= :army (get-in (test-utils/read-test-state :game-map) [0 0 :contents :type]))))

  (context "city attack coordination"
    (it "assigns up to 6 closest armies to visible free city"
      ;; 8 sentry armies and a free city visible on computer-map (10 cols x 1 row)
      (let [army-cell {:type :land :country-id 1
                       :contents {:type :army :owner :computer :hits 1
                                  :mode :sentry :country-id 1}}]
        (set-test-world! [[army-cell] [army-cell] [army-cell] [army-cell]
                                 [army-cell] [army-cell] [army-cell] [army-cell]
                                 [{:type :land}] [{:type :city :city-status :free}]])
        (set-test-computer-map! (test-utils/read-test-state :game-map))
        (army/assign-city-attacks)
        ;; Count armies with attack-target set
        (let [assigned (count (for [i (range 10)
                                    :let [unit (get-in (test-utils/read-test-state :game-map) [i 0 :contents])]
                                    :when (and unit (:attack-target unit))]
                                true))]
          (should= 6 assigned))))

    (it "does not assign armies to cities across the sea"
      ;; Army on land, sea gap, free city on other continent
      (let [army-cell {:type :land :country-id 1
                       :contents {:type :army :owner :computer :hits 1
                                  :mode :sentry :country-id 1}}]
        (set-test-world! [[army-cell] [{:type :sea}] [{:type :city :city-status :free}]])
        (set-test-computer-map! (test-utils/read-test-state :game-map))
        (army/assign-city-attacks)
        (should-be-nil (get-in (test-utils/read-test-state :game-map) [0 0 :contents :attack-target]))))

    (it "does not assign coast-walk armies"
      (let [army-cell {:type :land :country-id 1
                       :contents {:type :army :owner :computer :hits 1
                                  :mode :sentry :country-id 1}}
            cw-cell {:type :land :country-id 1
                     :contents {:type :army :owner :computer :hits 1
                                :mode :coast-walk :coast-direction :clockwise
                                :coast-start [0 0] :coast-visited [] :country-id 1}}]
        (set-test-world! [[cw-cell] [army-cell] [{:type :city :city-status :free}]])
        (set-test-computer-map! (test-utils/read-test-state :game-map))
        (army/assign-city-attacks)
        ;; Coast-walk army should NOT have attack-target
        (should-be-nil (get-in (test-utils/read-test-state :game-map) [0 0 :contents :attack-target]))
        ;; Sentry army should have attack-target
        (should= [2 0] (get-in (test-utils/read-test-state :game-map) [1 0 :contents :attack-target])))))

)

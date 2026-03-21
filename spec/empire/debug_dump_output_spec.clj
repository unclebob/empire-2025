(ns empire.debug-dump-output-spec
  (:require [empire.game-mechanics.debug.dump :as debug-dump]
            [empire.game-mechanics.debug.logging :as debug-logging]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-player-map! set-test-world!]]
            [speclj.core :refer :all]))

(describe "debug dump output helpers"
  (it "writes the formatted dump to the generated filename"
    (let [writes (atom [])]
      (with-redefs [debug-dump/generate-dump-filename (fn [] "debug-test.txt")
                    debug-dump/format-dump (fn [start end]
                                             (str "dump " start " " end))
                    spit (fn [filename content]
                           (swap! writes conj [filename content]))]
        (should= "debug-test.txt" (debug-dump/write-dump! [1 2] [3 4]))
        (should= [["debug-test.txt" "dump [1 2] [3 4]"]]
                 @writes))))

  (it "writes a full dump using the current map size"
    (test-utils/set-test-state! :map-size [3 4])
    (with-redefs [debug-dump/write-dump! (fn [start end]
                                           [start end])]
      (should= [[0 0] [2 3]]
               (debug-dump/write-full-dump!))))

  (it "formats movement entries"
    (let [basic (#'debug-dump/format-movement-entry {:unit-type :army :from [1 2] :to [1 3] :mode :moving :event :move :reason nil})
          with-event (#'debug-dump/format-movement-entry {:unit-type :army :from [0 0] :to [0 1] :mode :explore :event :wake :reason nil})
          with-reason (#'debug-dump/format-movement-entry {:unit-type :fighter :from [3 4] :to [3 5] :mode :moving :event :blocked :reason :steps-exhausted})]
      (should-contain "army" basic)
      (should-contain "moving" basic)
      (should-contain "wake" with-event)
      (should-contain "explore" with-event)
      (should-contain "blocked" with-reason)
      (should-contain "steps-exhausted" with-reason))))

(describe "format-dump"
  (before (reset-all-atoms!))

  (it "contains header and global state sections"
    (test-utils/set-test-state! :round-number 5)
    (test-utils/set-test-state! :waiting-for-input true)
    (set-test-world! (build-test-map ["##" "##"]))
    (set-test-player-map! (build-test-map ["##" "##"]))
    (set-test-computer-map! (build-test-map ["##" "##"]))
    (let [result (debug-dump/format-dump [0 0] [1 1])]
      (should-contain "Empire Debug Dump" result)
      (should-contain "Round: 5" result)
      (should-contain "Global State" result)
      (should-contain "waiting-for-input: true" result)))

  (it "contains map and production sections"
    (set-test-world! (build-test-map ["##" "##"]))
    (set-test-player-map! (build-test-map ["##" "##"]))
    (set-test-computer-map! (build-test-map ["##" "##"]))
    (let [result (debug-dump/format-dump [0 0] [1 1])]
      (should-contain "Map Data" result)
      (should-contain "game-map" result)
      (should-contain "player-map" result)
      (should-contain "computer-map" result)
      (should-contain "Production State" result)))

  (it "shows empty actions and production sections when no state is present"
    (set-test-world! (build-test-map ["##" "##"]))
    (set-test-player-map! (build-test-map ["##" "##"]))
    (set-test-computer-map! (build-test-map ["##" "##"]))
    (test-utils/set-test-state! :action-log [])
    (test-utils/set-test-state! :production {})
    (let [result (debug-dump/format-dump [0 0] [1 1])]
      (should-contain "(none)" result)
      (should-contain "(no production)" result)))

  (it "contains recent actions and event extras"
    (set-test-world! (build-test-map ["##" "##"]))
    (set-test-player-map! (build-test-map ["##" "##"]))
    (set-test-computer-map! (build-test-map ["##" "##"]))
    (debug-logging/log-action! [:test-action])
    (test-utils/set-test-state! :round-number 10)
    (test-utils/set-test-state! :computer-event-log [{:round 10 :event :army-move :pos [1 1] :to [1 2]}])
    (let [result (debug-dump/format-dump [0 0] [1 1])]
      (should-contain "Recent Actions" result)
      (should-contain "test-action" result)
      (should-contain "army-move" result)
      (should-contain ":to [1 2]" result)))

  (it "contains transport reservations and load manifest details"
    (set-test-world! [[{:type :sea
                        :contents {:type :transport
                                   :owner :computer
                                   :transport-mission :loading
                                   :hold-sail-to-load-since-round 17
                                   :load-plan-failure {:reasons [:no-manifest]}
                                   :load-manifest [41 42]
                                   :sail-path [[1 0] [2 0]]
                                   :load-target-cell [3 1]}}]])
    (set-test-player-map! [[{:type :sea}]])
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (test-utils/set-test-state! :transport-load-reservations
                                {7 {:coastal-cell [3 1]
                                    :army-ids #{41 42}}})
    (let [result (debug-dump/format-dump [0 0] [0 0])]
      (should-contain "Transport Load Reservations" result)
      (should-contain "7 {:coastal-cell [3 1], :army-ids #{41 42}}" result)
      (should-contain "hold-since:17" result)
      (should-contain "load-plan-failure:{:reasons [:no-manifest]}" result)
      (should-contain "load-manifest:[41 42]" result)
      (should-contain "sail-path:[[1 0] [2 0]]" result)
      (should-contain "load-target:[3 1]" result)))

  (it "contains major invasion targets and routing paths"
    (set-test-world! (build-test-map ["##" "##"]))
    (set-test-player-map! (build-test-map ["##" "##"]))
    (set-test-computer-map! (build-test-map ["##" "##"]))
    (test-utils/set-test-state! :major-invasion-state
                                {:active? true
                                 :decision :ready
                                 :failure-reason nil
                                 :next-review-round 99
                                 :started-round 12
                                 :first-landing-round 18
                                 :detection-points #{[9 9] [8 8]}
                                 :sea-reachable-detection-points #{[8 8]}
                                 :target-land-set #{[10 10]}
                                 :kamikazee-army-targets [{:pos [11 11] :seen-round 12}]
                                 :kamikazee-root-city [1 1]
                                 :kamikazee-forward-carrier [3 3]
                                 :kamikazee-bridge-carriers #{[2 2]}
                                 :kamikazee-terminal-sites #{[1 1] [3 3]}
                                 :kamikazee-city-next-hops {[0 0] [1 1]}
                                 :kamikazee-carrier-next-hops {[2 2] [1 1]}})
    (let [result (debug-dump/format-dump [0 0] [1 1])]
      (should-contain "Major Invasion State" result)
      (should-contain "detection-points: ([8 8] [9 9])" result)
      (should-contain "kamikazee-army-targets: [[11 11]]" result)
      (should-contain "city-paths:" result)
      (should-contain "[0 0] -> [1 1]" result)
      (should-contain "carrier-paths:" result)
      (should-contain "[2 2] -> [1 1]" result)))

  (it "contains kamikazee fighter targets and airport aggregates in region"
    (let [world (build-test-map ["f#"
                                 "##"])]
      (set-test-world! world)
      (set-test-player-map! world)
      (set-test-computer-map! world)
      (test-utils/update-test-world! assoc-in [0 0 :contents]
                                     {:type :fighter
                                      :owner :computer
                                      :hits 1
                                      :fuel 17
                                      :major-invasion true
                                      :kamikazee true
                                      :kamikazee-stage :hunt
                                      :major-invasion-target [9 9]
                                      :kamikazee-targets [[8 8] [9 9]]
                                      :kamikazee-route [[1 0] [1 1]]
                                      :kamikazee-terminal-site [1 1]
                                      :kamikazee-wait-site [0 1]
                                      :kamikazee-hunt-resume-pos [0 0]})
      (let [fighter-result (debug-dump/format-dump [0 0] [1 1])]
        (should-contain "Kamikazee Fighters In Region" fighter-result)
        (should-contain "[0 0] fuel:17 stage::hunt major-target:[9 9]" fighter-result)
        (should-contain "targets: [[8 8] [9 9]]" fighter-result)
        (should-contain "route: [[1 0] [1 1]]" fighter-result)
        (should-contain "terminal-site: [1 1] wait-site: [0 1] resume_pos" (clojure.string/replace fighter-result "resume-pos" "resume_pos"))))
    (let [world (build-test-map ["X#"
                                 "##"])]
      (set-test-world! world)
      (set-test-player-map! world)
      (set-test-computer-map! world)
      (test-utils/update-test-world! assoc-in [0 0 :fighter-count] 3)
      (test-utils/update-test-world! assoc-in [0 0 :awake-fighters] 2)
      (test-utils/update-test-world! assoc-in [0 0 :kamikazee-fighter-count] 2)
      (test-utils/update-test-world! assoc-in [0 0 :awake-kamikazee-fighters] 1)
      (let [airport-result (debug-dump/format-dump [0 0] [1 1])]
        (should-contain "Kamikazee Airports In Region" airport-result)
        (should-contain "[0 0] fighters:3 awake:2 kamikazees:2 awake-kamikazees:1" airport-result)))))

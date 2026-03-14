(ns empire.debug-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.game-mechanics.debug.dump :as debug-dump]
            [empire.game-mechanics.debug.logging :as debug-logging]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-player-map! set-test-computer-map! set-test-world!]]
            [clojure.string :as str]))

(describe "format-cell handles nil contents fields"
  (before (reset-all-atoms!))

  (it "does not crash when contents has nil type or owner"
    (let [cell {:type :sea :contents {:type nil :owner nil}}
          result (debug-dump/format-cell [0 0] cell)]
      (should-contain "contents:" result)))

  (it "formats cell with valid contents"
    (let [cell {:type :sea :contents {:type :destroyer :owner :computer :hits 3}}
          result (debug-dump/format-cell [0 0] cell)]
      (should-contain "type:destroyer" result)
      (should-contain "owner:computer" result))))

(describe "format-cell"
  (it "formats nil cell"
    (should= "[3,5] nil" (debug-dump/format-cell [3 5] nil)))

  (it "formats land cell"
    (should-contain ":land" (debug-dump/format-cell [0 0] {:type :land})))

  (it "formats city cell with status"
    (let [result (debug-dump/format-cell [1 2] {:type :city :city-status :player})]
      (should-contain ":city" result)
      (should-contain "city-status:player" result)))

  (it "formats cell with unit contents including optional fields"
    (let [cell {:type :sea :contents {:type :fighter :owner :player
                                      :mode :sentry :hits 1 :fuel 20}}
          result (debug-dump/format-cell [0 0] cell)]
      (should-contain "type:fighter" result)
      (should-contain "owner:player" result)
      (should-contain "mode::sentry" result)
      (should-contain "fuel:20" result)))

  (it "formats cell with kamikazee routing fields"
    (let [cell {:type :land
                :contents {:type :fighter
                           :owner :computer
                           :kamikazee true
                           :kamikazee-stage :hunt
                           :major-invasion-target [9 9]
                           :kamikazee-route [[2 3] [4 5]]
                           :kamikazee-targets [[7 8]]
                           :kamikazee-terminal-site [4 5]}}
          result (debug-dump/format-cell [0 0] cell)]
      (should-contain "kamikazee:true" result)
      (should-contain "k-stage::hunt" result)
      (should-contain "mi-target:[9 9]" result)
      (should-contain "k-route:[[2 3] [4 5]]" result)
      (should-contain "k-targets:[[7 8]]" result)))

  (it "includes coordinate prefix"
    (let [result (debug-dump/format-cell [7 12] {:type :sea})]
      (should-contain "[7,12]" result))))

(describe "log-player-movement!"
  (before (reset-all-atoms!))

  (it "appends movement entry to log"
    (debug-logging/log-player-movement! :army [1 2] [1 3] :moving :move nil)
    (should= 1 (count (test-utils/read-test-state :player-movement-log)))
    (let [entry (first (test-utils/read-test-state :player-movement-log))]
      (should= :army (:unit-type entry))
      (should= [1 2] (:from entry))
      (should= [1 3] (:to entry))
      (should= :move (:event entry))))

  (it "includes round number"
    (test-utils/set-test-state! :round-number 5)
    (debug-logging/log-player-movement! :army [0 0] [0 1] :explore :move nil)
    (should= 5 (:round (first (test-utils/read-test-state :player-movement-log)))))

  (it "keeps exactly 500 entries without truncating"
    (dotimes [_ 500]
      (debug-logging/log-player-movement! :army [0 0] [0 1] :moving :move nil))
    (should= 500 (count (test-utils/read-test-state :player-movement-log))))

  (it "truncates to 500 when exceeding limit"
    (dotimes [_ 501]
      (debug-logging/log-player-movement! :army [0 0] [0 1] :moving :move nil))
    (should= 500 (count (test-utils/read-test-state :player-movement-log))))

  (it "includes wake reason"
    (debug-logging/log-player-movement! :army [0 0] [0 1] :explore :wake :steps-exhausted)
    (should= :steps-exhausted (:reason (first (test-utils/read-test-state :player-movement-log))))))

(describe "log-action!"
  (before (reset-all-atoms!))

  (it "appends action to log"
    (debug-logging/log-action! [:move :army [4 6] [4 7]])
    (should= 1 (count (test-utils/read-test-state :action-log)))
    (should= [:move :army [4 6] [4 7]] (:action (first (test-utils/read-test-state :action-log)))))

  (it "includes timestamp"
    (debug-logging/log-action! [:test])
    (should (number? (:timestamp (first (test-utils/read-test-state :action-log))))))

  (it "caps log at 100 entries"
    (dotimes [i 110]
      (debug-logging/log-action! [:action i]))
    (should= 100 (count (test-utils/read-test-state :action-log)))))

(describe "log-computer-event!"
  (before (reset-all-atoms!))

  (it "appends computer event entry"
    (debug-logging/log-computer-event! :army-move [1 2] {:to [1 3]})
    (should= 1 (count (test-utils/read-test-state :computer-event-log)))
    (let [entry (first (test-utils/read-test-state :computer-event-log))]
      (should= :army-move (:event entry))
      (should= [1 2] (:pos entry))
      (should= [1 3] (:to entry))))

  (it "caps computer event log at 2000 entries"
    (dotimes [i 2001]
      (debug-logging/log-computer-event! :tick [0 i] {:n i}))
    (should= 2000 (count (test-utils/read-test-state :computer-event-log)))))

(describe "dump-region"
  (before (reset-all-atoms!))

  (it "extracts cells from coordinate range"
    (set-test-world! (build-test-map ["###"
                                      "###"
                                      "###"]))
    (set-test-player-map! (build-test-map ["###"
                                               "###"
                                               "###"]))
    (set-test-computer-map! (build-test-map ["###"
                                                 "###"
                                                 "###"]))
    (let [result (debug-dump/dump-region [0 0] [1 1])]
      (should (map? (:game-map result)))
      (should (map? (:player-map result)))
      (should (map? (:computer-map result)))
      ;; Should have 4 cells in range [0 0] to [1 1]
      (should= 4 (count (:game-map result)))))

  (it "handles empty maps gracefully"
    (set-test-world! [[nil nil] [nil nil]])
    (set-test-player-map! [[nil nil] [nil nil]])
    (set-test-computer-map! [[nil nil] [nil nil]])
    (let [result (debug-dump/dump-region [0 0] [1 1])]
      (should= 0 (count (:game-map result))))))

(describe "screen-coords-to-cell-range"
  (before (reset-all-atoms!))

  (it "converts screen coords to cell range"
    (test-utils/set-test-state! :map-screen-dimensions [100 100])
    (set-test-world! (build-test-map ["#####"
                                      "#####"
                                      "#####"
                                      "#####"
                                      "#####"]))
    (let [[[sr sc] [er ec]] (debug-dump/screen-coords-to-cell-range [0 0] [99 99])]
      (should= 0 sr)
      (should= 0 sc)
      (should= 4 er)
      (should= 4 ec)))

  (it "normalizes reversed coordinates"
    (test-utils/set-test-state! :map-screen-dimensions [100 100])
    (set-test-world! (build-test-map ["#####"
                                      "#####"
                                      "#####"
                                      "#####"
                                      "#####"]))
    (let [[[sr sc] [er ec]] (debug-dump/screen-coords-to-cell-range [99 99] [0 0])]
      (should= 0 sr)
      (should= 0 sc)
      (should= 4 er)
      (should= 4 ec)))

  (it "clamps to map bounds"
    (test-utils/set-test-state! :map-screen-dimensions [100 100])
    (set-test-world! (build-test-map ["###"
                                      "###"
                                      "###"]))
    (let [[[sr sc] [er ec]] (debug-dump/screen-coords-to-cell-range [0 0] [200 200])]
      (should (>= sr 0))
      (should (>= sc 0))
      (should (<= er 2))
      (should (<= ec 2)))))

(describe "generate-dump-filename"
  (it "generates filename with debug prefix"
    (let [filename (debug-dump/generate-dump-filename)]
      (should (str/starts-with? filename "debug-"))
      (should (str/ends-with? filename ".txt"))))

  (it "contains date pattern"
    (let [filename (debug-dump/generate-dump-filename)]
      ;; Should match debug-YYYY-MM-DD-HHMMSS.txt
      (should (re-find #"debug-\d{4}-\d{2}-\d{2}-\d{6}\.txt" filename)))))

(describe "format-movement-entry"
  (it "formats basic move entry"
    (let [entry {:unit-type :army :from [1 2] :to [1 3] :mode :moving :event :move :reason nil}
          result (#'debug-dump/format-movement-entry entry)]
      (should-contain "army" result)
      (should-contain "[1 2]" result)
      (should-contain "[1 3]" result)
      (should-contain "moving" result)
      (should-not-contain "move" (subs result (+ 4 (.indexOf result "moving"))))))

  (it "formats entry with non-move event"
    (let [entry {:unit-type :army :from [0 0] :to [0 1] :mode :explore :event :wake :reason nil}
          result (#'debug-dump/format-movement-entry entry)]
      (should-contain "wake" result)
      (should-contain "explore" result)))

  (it "formats entry with reason"
    (let [entry {:unit-type :fighter :from [3 4] :to [3 5] :mode :moving :event :blocked :reason :steps-exhausted}
          result (#'debug-dump/format-movement-entry entry)]
      (should-contain "blocked" result)
      (should-contain "steps-exhausted" result))))

(describe "format-dump"
  (before (reset-all-atoms!))

  (it "contains header section with round number"
    (test-utils/set-test-state! :round-number 5)
    (set-test-world! (build-test-map ["##" "##"]))
    (set-test-player-map! (build-test-map ["##" "##"]))
    (set-test-computer-map! (build-test-map ["##" "##"]))
    (let [result (debug-dump/format-dump [0 0] [1 1])]
      (should-contain "Empire Debug Dump" result)
      (should-contain "Round: 5" result)))

  (it "contains global state section"
    (test-utils/set-test-state! :round-number 3)
    (test-utils/set-test-state! :waiting-for-input true)
    (set-test-world! (build-test-map ["##" "##"]))
    (set-test-player-map! (build-test-map ["##" "##"]))
    (set-test-computer-map! (build-test-map ["##" "##"]))
    (let [result (debug-dump/format-dump [0 0] [1 1])]
      (should-contain "Global State" result)
      (should-contain "waiting-for-input: true" result)))

  (it "contains map data section"
    (set-test-world! (build-test-map ["##" "##"]))
    (set-test-player-map! (build-test-map ["##" "##"]))
    (set-test-computer-map! (build-test-map ["##" "##"]))
    (let [result (debug-dump/format-dump [0 0] [1 1])]
      (should-contain "Map Data" result)
      (should-contain "game-map" result)
      (should-contain "player-map" result)
      (should-contain "computer-map" result)))

  (it "contains production state section"
    (set-test-world! (build-test-map ["##" "##"]))
    (set-test-player-map! (build-test-map ["##" "##"]))
    (set-test-computer-map! (build-test-map ["##" "##"]))
    (let [result (debug-dump/format-dump [0 0] [1 1])]
      (should-contain "Production State" result)))

  (it "contains recent actions section"
    (set-test-world! (build-test-map ["##" "##"]))
    (set-test-player-map! (build-test-map ["##" "##"]))
    (set-test-computer-map! (build-test-map ["##" "##"]))
    (debug-logging/log-action! [:test-action])
    (let [result (debug-dump/format-dump [0 0] [1 1])]
      (should-contain "Recent Actions" result)
      (should-contain "test-action" result)))

  (it "formats computer event extras in dump output"
    (set-test-world! (build-test-map ["##" "##"]))
    (set-test-player-map! (build-test-map ["##" "##"]))
    (set-test-computer-map! (build-test-map ["##" "##"]))
    (test-utils/set-test-state! :round-number 10)
    (test-utils/set-test-state! :computer-event-log [{:round 10 :event :army-move :pos [1 1] :to [1 2]}])
    (let [result (debug-dump/format-dump [0 0] [1 1])]
      (should-contain "army-move" result)
      (should-contain ":to [1 2]" result)))

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

  (it "contains kamikazee fighter targets and paths for fighters in region"
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
      (let [result (debug-dump/format-dump [0 0] [1 1])]
        (should-contain "Kamikazee Fighters In Region" result)
        (should-contain "[0 0] fuel:17 stage::hunt major-target:[9 9]" result)
        (should-contain "targets: [[8 8] [9 9]]" result)
        (should-contain "route: [[1 0] [1 1]]" result)
        (should-contain "terminal-site: [1 1] wait-site: [0 1] resume-pos: [0 0]" result)))))

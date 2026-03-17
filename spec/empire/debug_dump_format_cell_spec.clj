(ns empire.debug-dump-format-cell-spec
  (:require [clojure.string :as str]
            [empire.game-mechanics.debug.dump :as debug-dump]
            [speclj.core :refer :all]))

(describe "debug dump cell formatting"
  (it "does not crash when contents has nil type or owner"
    (let [cell {:type :sea :contents {:type nil :owner nil}}
          result (debug-dump/format-cell [0 0] cell)]
      (should-contain "contents:" result)))

  (it "formats cell with valid contents"
    (let [cell {:type :sea :contents {:type :destroyer :owner :computer :hits 3}}
          result (debug-dump/format-cell [0 0] cell)]
      (should-contain "type:destroyer" result)
      (should-contain "owner:computer" result)))

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
      (should-contain "[7,12]" result)))

  (it "formats dump filenames with the debug prefix and date pattern"
    (let [filename (debug-dump/generate-dump-filename)]
      (should (str/starts-with? filename "debug-"))
      (should (str/ends-with? filename ".txt"))
      (should (re-find #"debug-\d{4}-\d{2}-\d{2}-\d{6}\.txt" filename)))))

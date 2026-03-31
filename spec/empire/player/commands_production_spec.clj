(ns empire.player.commands-production-spec
  (:require [empire.player.commands :as commands]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-world! update-test-world!]]
            [speclj.core :refer :all]))

(defn- setup-unit-attention
  [coords]
  (test-utils/set-test-state! :cells-needing-attention [coords])
  (test-utils/set-test-state! :player-items (list coords)))

(describe "handle-key - city production"
  (before (reset-all-atoms!))

  (context "basic production keys"
    (it "sets army production on player city when :a pressed"
      (set-test-world! (build-test-map ["O"]))
      (setup-unit-attention [0 0])
      (commands/handle-key :a)
      (should= :army (:item (get (test-utils/read-test-state :production) [0 0]))))

    (it "sets fighter production on player city when :f pressed"
      (set-test-world! (build-test-map ["O"]))
      (setup-unit-attention [0 0])
      (commands/handle-key :f)
      (should= :fighter (:item (get (test-utils/read-test-state :production) [0 0]))))

    (it "sets production to :none when :x pressed on player city"
      (set-test-world! (build-test-map ["O"]))
      (setup-unit-attention [0 0])
      (commands/handle-key :x)
      (should= :none (get (test-utils/read-test-state :production) [0 0])))

    (it "advances player-items when :space pressed on player city"
      (set-test-world! (build-test-map ["O"]))
      (setup-unit-attention [0 0])
      (commands/handle-key :space)
      (should (empty? (test-utils/read-test-state :player-items)))))

  (context "coastal city restrictions"
    (it "shows error for naval production on non-coastal city"
      (set-test-world! (build-test-map ["###"
                                        "#O#"
                                        "###"]))
      (setup-unit-attention [1 1])
      (commands/handle-key :t)
      (should-not (get (test-utils/read-test-state :production) [1 1]))
      (should-contain "coastal" (test-utils/read-test-state :warning-message)))

    (it "allows naval production on coastal city"
      (set-test-world! (build-test-map ["~##"
                                        "#O#"
                                        "###"]))
      (setup-unit-attention [1 1])
      (commands/handle-key :t)
      (should= :transport (:item (get (test-utils/read-test-state :production) [1 1])))))

  (context "city with active unit"
    (it "does not handle production key when city has active unit"
      (set-test-world! (build-test-map ["O"]))
      (update-test-world! assoc-in [0 0 :contents]
                         {:type :army :mode :awake :owner :player :hits 1})
      (setup-unit-attention [0 0])
      (let [result (commands/handle-key :a)]
        (should-not (get (test-utils/read-test-state :production) [0 0]))))

    (it "sets production for a city with airport fighters when the city itself needs production"
      (set-test-world! (build-test-map ["O"]))
      (update-test-world! assoc-in [0 0 :fighter-count] 5)
      (update-test-world! assoc-in [0 0 :awake-fighters] 4)
      (setup-unit-attention [0 0])
      (commands/handle-key :f)
      (should= :fighter (:item (get (test-utils/read-test-state :production) [0 0])))))

  (context "production variants"
    (it "sets destroyer production"
      (set-test-world! (build-test-map ["~O"]))
      (setup-unit-attention [1 0])
      (commands/handle-key :d)
      (should= :destroyer (:item (get (test-utils/read-test-state :production) [1 0]))))

    (it "sets submarine production"
      (set-test-world! (build-test-map ["~O"]))
      (setup-unit-attention [1 0])
      (commands/handle-key :s)
      (should= :submarine (:item (get (test-utils/read-test-state :production) [1 0]))))

    (it "sets carrier production"
      (set-test-world! (build-test-map ["~O"]))
      (setup-unit-attention [1 0])
      (commands/handle-key :c)
      (should= :carrier (:item (get (test-utils/read-test-state :production) [1 0]))))

    (it "sets battleship production"
      (set-test-world! (build-test-map ["~O"]))
      (setup-unit-attention [1 0])
      (commands/handle-key :b)
      (should= :battleship (:item (get (test-utils/read-test-state :production) [1 0]))))

    (it "sets patrol-boat production"
      (set-test-world! (build-test-map ["~O"]))
      (setup-unit-attention [1 0])
      (commands/handle-key :p)
      (should= :patrol-boat (:item (get (test-utils/read-test-state :production) [1 0]))))

    (it "sets satellite production"
      (set-test-world! (build-test-map ["O"]))
      (setup-unit-attention [0 0])
      (commands/handle-key :z)
      (should= :satellite (:item (get (test-utils/read-test-state :production) [0 0]))))))

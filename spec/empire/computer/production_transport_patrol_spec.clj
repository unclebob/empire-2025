(ns empire.computer.production-transport-patrol-spec
  "Tests for VMS Empire style computer production."
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.production :as production]
            [empire.computer.ship :as ship]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-world! update-test-world!]]))

(defn- disable-opening!
  []
  (test-utils/set-test-state! :round-number nil))
(describe "transport and patrol boat production"
  (before
    (reset-all-atoms!)
    (disable-opening!))

  (context "transport waiting-armies production"

    (it "produces transport when armies await pickup and existing transport is full"
      ;; Coastal city, country-id 1, 6+ armies, 1 full transport
      (set-test-world! (build-test-map ["~X#aaaaaaa~t"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [1 0 :country-id] 1)
      (doseq [col (range 3 10)]
        (update-test-world! assoc-in [col 0 :country-id] 1)
        (update-test-world! assoc-in [col 0 :contents :country-id] 1))
      (update-test-world! assoc-in [11 0 :contents :country-id] 1)
      (update-test-world! assoc-in [11 0 :contents :transport-id] 1)
      (update-test-world! assoc-in [11 0 :contents :army-count] 6)
      (update-test-world! assoc-in [11 0 :contents :transport-mission] :unloading)
      (production/rebuild-country-stats!)
      (should= :transport (production/decide-production [1 0])))

    (it "does not produce transport when existing transport has room"
      ;; Coastal city, country-id 1, 6+ armies, 1 non-full transport
      (set-test-world! (build-test-map ["~X#aaaaaaa~t"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [1 0 :country-id] 1)
      (doseq [col (range 3 10)]
        (update-test-world! assoc-in [col 0 :country-id] 1)
        (update-test-world! assoc-in [col 0 :contents :country-id] 1))
      (update-test-world! assoc-in [11 0 :contents :country-id] 1)
      (update-test-world! assoc-in [11 0 :contents :transport-id] 1)
      (update-test-world! assoc-in [11 0 :contents :army-count] 3)
      (update-test-world! assoc-in [11 0 :contents :transport-mission] :loading)
      (production/rebuild-country-stats!)
      (should-not= :transport (production/decide-production [1 0]))))

  (context "patrol boat 4-cap and post-4 switch"

    (it "produces patrol boat when country has fewer than 4"
      ;; 2-row: coastal city, armies fill coastal cells, transport+escort, 0 patrol boats
      (set-test-world! (build-test-map ["~Xaatd"
                                               "~~~~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [1 0 :country-id] 1)
      (doseq [col [2 3]]
        (update-test-world! assoc-in [col 0 :country-id] 1)
        (update-test-world! assoc-in [col 0 :contents :country-id] 1))
      (update-test-world! assoc-in [4 0 :contents :country-id] 1)
      (update-test-world! assoc-in [4 0 :contents :transport-id] 1)
      (update-test-world! assoc-in [4 0 :contents :escort-destroyer-id] 1)
      (production/rebuild-country-stats!)
      (should= :patrol-boat (production/decide-production [1 0])))

    (it "does not produce patrol boat when country has 4"
      ;; 2-row: armies fill coastal cells, 4 patrol boats
      (set-test-world! (build-test-map ["~Xaatd~pppp"
                                               "~~~~~~~~~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [1 0 :country-id] 1)
      (doseq [col [2 3]]
        (update-test-world! assoc-in [col 0 :country-id] 1)
        (update-test-world! assoc-in [col 0 :contents :country-id] 1))
      (update-test-world! assoc-in [4 0 :contents :country-id] 1)
      (update-test-world! assoc-in [4 0 :contents :transport-id] 1)
      (update-test-world! assoc-in [4 0 :contents :escort-destroyer-id] 1)
      (doseq [col [7 8 9 10]]
        (update-test-world! assoc-in [col 0 :contents :country-id] 1))
      (production/rebuild-country-stats!)
      (should-not= :patrol-boat (production/decide-production [1 0]))))

  (context "patrol boat production"

    (it "produces patrol boat when country has none"
      ;; 2-row: coastal armies fill all coastal cells, transport+escort, 0 patrol boats
      (set-test-world! (build-test-map ["~Xaatd"
                                               "~~~~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [1 0 :country-id] 1)
      (doseq [col [2 3]]
        (update-test-world! assoc-in [col 0 :country-id] 1)
        (update-test-world! assoc-in [col 0 :contents :country-id] 1))
      (update-test-world! assoc-in [4 0 :contents :country-id] 1)
      (update-test-world! assoc-in [4 0 :contents :transport-id] 1)
      (update-test-world! assoc-in [4 0 :contents :escort-destroyer-id] 1)
      (production/rebuild-country-stats!)
      (should= :patrol-boat (production/decide-production [1 0])))

    (it "does not produce patrol boat when country already has 4"
      ;; 2-row: same but with 4 patrol boats
      (set-test-world! (build-test-map ["~Xaatd~pppp"
                                               "~~~~~~~~~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [1 0 :country-id] 1)
      (doseq [col [2 3]]
        (update-test-world! assoc-in [col 0 :country-id] 1)
        (update-test-world! assoc-in [col 0 :contents :country-id] 1))
      (update-test-world! assoc-in [4 0 :contents :country-id] 1)
      (update-test-world! assoc-in [4 0 :contents :transport-id] 1)
      (update-test-world! assoc-in [4 0 :contents :escort-destroyer-id] 1)
      (doseq [col [7 8 9 10]]
        (update-test-world! assoc-in [col 0 :contents :country-id] 1))
      (production/rebuild-country-stats!)
      (should-not= :patrol-boat (production/decide-production [1 0]))))

  (context "transport full boundary (L164)"

    (it "considers transport with exactly 6 armies as full"
      ;; L164: >= -> > would miss the boundary case of exactly 6
      (set-test-world! (build-test-map ["~Xaaaaaaa~t"
                                               "~~~~~~~~~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [1 0 :country-id] 1)
      (doseq [col (range 2 9)]
        (update-test-world! assoc-in [col 0 :country-id] 1)
        (update-test-world! assoc-in [col 0 :contents :country-id] 1))
      (update-test-world! assoc-in [10 0 :contents :country-id] 1)
      (update-test-world! assoc-in [10 0 :contents :transport-id] 1)
      (update-test-world! assoc-in [10 0 :contents :army-count] 6)
      (production/rebuild-country-stats!)
      (should= :transport (production/decide-production [1 0])))

    (it "considers transport with 0 army-count as not full"
      ;; L164: 0 -> 1 default would make transport with no army-count appear to have 1
      (set-test-world! (build-test-map ["~Xaaaaaaa~t"
                                               "~~~~~~~~~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [1 0 :country-id] 1)
      (doseq [col (range 2 9)]
        (update-test-world! assoc-in [col 0 :country-id] 1)
        (update-test-world! assoc-in [col 0 :contents :country-id] 1))
      (update-test-world! assoc-in [10 0 :contents :country-id] 1)
      (update-test-world! assoc-in [10 0 :contents :transport-id] 1)
      ;; Transport with no army-count should be considered not full (default 0)
      (production/rebuild-country-stats!)
      (should-not= :transport (production/decide-production [1 0]))))

  (context "should-rotate-transport? (L270)"

    (it "rotates when same city was last transport producer"
      ;; L270: = -> not= would invert the check
      ;; Place enough armies for transport priority
      (set-test-world! (build-test-map ["~X~Xaaaaaaa"
                                               "~~~~~~~~~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [1 0 :country-id] 1)
      (update-test-world! assoc-in [3 0 :country-id] 1)
      (doseq [col (range 4 11)]
        (update-test-world! assoc-in [col 0 :country-id] 1)
        (update-test-world! assoc-in [col 0 :contents :country-id] 1))
      (test-utils/set-test-state! :last-transport-city {1 [1 0]})
      (production/rebuild-country-stats!)
      ;; Should NOT produce transport at [1,0] because rotation says skip
      (should-not= :transport (production/decide-production [1 0])))))
(run-specs)

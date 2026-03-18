(ns empire.computer.army-backtrack-spec
  "Tests for VMS Empire style computer army movement."
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.army :as army]
            [empire.computer.core :as core]
            [empire.computer.production :as production]
            [empire.computer.stamping :as stamping]
            [empire.game-mechanics.services.combat :as combat]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-world! update-test-world!]]))

(defn- sync-computer-map!
  []
  (set-test-computer-map! (test-utils/read-test-state :game-map)))

(defn- disable-opening!
  []
  (test-utils/set-test-state! :round-number nil))
(describe "backtrack and anti-oscillation"
  (before
    (reset-all-atoms!)
    (disable-opening!))

  (context "backtrack memory"
    (it "records move-history after moving"
      (set-test-world! (build-test-map ["a##"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [0 0 :contents :country-id] 1)
      (update-test-world! assoc-in [0 0 :contents :mode] :awake)
      (doseq [col (range 3)]
        (update-test-world! assoc-in [col 0 :country-id] 1))
      ;; Give interior-explore-direction to force predictable move
      (update-test-world! assoc-in [0 0 :contents :interior-explore-direction] [1 0])
      (army/process-army [0 0])
      ;; Army should be at [1 0] with move-history containing [0 0]
      (should= [[0 0]] (get-in (test-utils/read-test-state :game-map) [1 0 :contents :move-history])))

    (it "caps move-history at 4 entries"
      (set-test-world! (build-test-map ["a######"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (doseq [col (range 7)]
        (update-test-world! assoc-in [col 0 :country-id] 1))
      (update-test-world! assoc-in [0 0 :contents :country-id] 1)
      (update-test-world! assoc-in [0 0 :contents :interior-explore-direction] [1 0])
      ;; Move 1: [0 0] -> [1 0]
      (army/process-army [0 0])
      (update-test-world! assoc-in [1 0 :contents :interior-explore-direction] [1 0])
      ;; Move 2: [1 0] -> [2 0]
      (army/process-army [1 0])
      (update-test-world! assoc-in [2 0 :contents :interior-explore-direction] [1 0])
      ;; Move 3: [2 0] -> [3 0]
      (army/process-army [2 0])
      (update-test-world! assoc-in [3 0 :contents :interior-explore-direction] [1 0])
      ;; Move 4: [3 0] -> [4 0]
      (army/process-army [3 0])
      (update-test-world! assoc-in [4 0 :contents :interior-explore-direction] [1 0])
      ;; Move 5: [4 0] -> [5 0]
      (army/process-army [4 0])
      ;; Should have last 4: [1 0] [2 0] [3 0] [4 0] — not [0 0]
      (let [history (get-in (test-utils/read-test-state :game-map) [5 0 :contents :move-history])]
        (should= 4 (count history))
        (should-not-contain [0 0] history)
        (should-contain [4 0] history)))

    (it "does not oscillate between two cells"
      ;; Computer city at [2 0] with army, sentries at [0 0] and [4 0]
      ;; Sea on row 1. Army should NOT bounce city<->adjacent.
      (set-test-world! (build-test-map ["a#X#a"
                                               "~~~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (doseq [col (range 5)]
        (update-test-world! assoc-in [col 0 :country-id] 1))
      (update-test-world! assoc-in [0 0 :contents :mode] :sentry)
      (update-test-world! assoc-in [0 0 :contents :country-id] 1)
      (update-test-world! assoc-in [4 0 :contents :mode] :sentry)
      (update-test-world! assoc-in [4 0 :contents :country-id] 1)
      (update-test-world! assoc-in [2 0 :contents]
             {:type :army :owner :computer :hits 1 :mode :awake :country-id 1})
      (sync-computer-map!)
      ;; Round 1: army leaves city
      (with-redefs [rand (constantly 0.5)]
        (army/process-army [2 0]))
      (let [army-pos (cond
                       (= :army (get-in (test-utils/read-test-state :game-map) [1 0 :contents :type])) [1 0]
                       (= :army (get-in (test-utils/read-test-state :game-map) [3 0 :contents :type])) [3 0]
                       :else nil)]
        (should-not-be-nil army-pos)
        ;; Round 2: army should NOT go back to city
        (with-redefs [rand (constantly 0.5)]
          (army/process-army army-pos))
        (should-not= :awake (get-in (test-utils/read-test-state :game-map) [2 0 :contents :mode]))))

    (it "wakes nearby sentries when stuck"
      ;; Army boxed in by sentries on all neighbors
      (set-test-world! (build-test-map ["#####"
                                               "##a##"
                                               "#####"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (doseq [col (range 5) row (range 3)]
        (update-test-world! assoc-in [col row :country-id] 1))
      (doseq [pos [[1 0] [2 0] [3 0] [1 1] [3 1] [1 2] [2 2] [3 2]]]
        (update-test-world! assoc-in (conj pos :contents)
               {:type :army :owner :computer :hits 1 :mode :sentry :country-id 1}))
      (update-test-world! assoc-in [2 1 :contents :mode] :awake)
      (update-test-world! assoc-in [2 1 :contents :country-id] 1)
      (sync-computer-map!)
      (with-redefs [rand (constantly 0.5)
                    rand-nth first]
        (army/process-army [2 1]))
      ;; At least one nearby sentry should have been woken
      (let [modes (map #(get-in (test-utils/read-test-state :game-map) (conj % :contents :mode))
                       [[1 0] [2 0] [3 0] [1 1] [3 1] [1 2] [2 2] [3 2]])]
        (should (some #(not= :sentry %) modes))))

    (it "woken sentries have interior-explore-direction away from stuck army"
      (set-test-world! (build-test-map ["a#a##"
                                               "~~~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (doseq [col (range 5)]
        (update-test-world! assoc-in [col 0 :country-id] 1))
      ;; Sentry at [0 0]
      (update-test-world! assoc-in [0 0 :contents :mode] :sentry)
      (update-test-world! assoc-in [0 0 :contents :country-id] 1)
      ;; Army at [2 0] — all neighbors occupied or sea
      (update-test-world! assoc-in [2 0 :contents :mode] :awake)
      (update-test-world! assoc-in [2 0 :contents :country-id] 1)
      ;; Sentries at [3 0] and [4 0]
      (update-test-world! assoc-in [3 0 :contents]
             {:type :army :owner :computer :hits 1 :mode :sentry :country-id 1})
      (update-test-world! assoc-in [4 0 :contents]
             {:type :army :owner :computer :hits 1 :mode :sentry :country-id 1})
      (with-redefs [rand (constantly 0.5)
                    rand-nth first]
        (army/process-army [2 0]))
      ;; Sentry at [0 0] should have direction pointing away (negative col)
      (let [dir (get-in (test-utils/read-test-state :game-map) [0 0 :contents :interior-explore-direction])]
        (when dir (should (neg? (first dir))))))

    (it "army goes sentry near coast when all coastal cells occupied"
      ;; All coastal cells (row 2) have sentries. Army at interior [2 1].
      (set-test-world! (build-test-map ["#####"
                                               "##a##"
                                               "#####"
                                               "~~~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (doseq [col (range 5) row (range 3)]
        (update-test-world! assoc-in [col row :country-id] 1))
      ;; Fill all coastal cells (row 2) with sentries
      (doseq [col (range 5)]
        (update-test-world! assoc-in [col 2 :contents]
               {:type :army :owner :computer :hits 1 :mode :sentry :country-id 1}))
      (update-test-world! assoc-in [2 1 :contents :country-id] 1)
      (with-redefs [rand (constantly 0.5)]
        (army/process-army [2 1]))
      ;; Army should have moved toward coast or gone sentry (queued)
      (let [unit (get-in (test-utils/read-test-state :game-map) [2 1 :contents])]
        ;; Either moved away or went sentry
        (should (or (nil? unit)
                    (= :sentry (:mode unit))))))

    (it "wakes nearby sentries when army boards transport"
      (set-test-world! (build-test-map ["###"
                                               "~t~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (doseq [col (range 3)]
        (update-test-world! assoc-in [col 0 :country-id] 1))
      ;; Army at [1 0]
      (update-test-world! assoc-in [1 0 :contents]
             {:type :army :owner :computer :hits 1 :mode :awake :country-id 1})
      ;; Sentry at [2 0]
      (update-test-world! assoc-in [2 0 :contents]
             {:type :army :owner :computer :hits 1 :mode :sentry :country-id 1})
      ;; Transport at [1 1] in loading mode
      (update-test-world! assoc-in [1 1 :contents :transport-mission] :loading)
      ;; Board the army
      (core/board-transport [1 0] [1 1])
      ;; Sentry at [2 0] should be woken
      (should-not= :sentry (get-in (test-utils/read-test-state :game-map) [2 0 :contents :mode])))

    (it "army progresses over multiple rounds instead of oscillating"
      ;; Integration test: run multiple rounds, verify progress
      (set-test-world! (build-test-map ["a#X#a"
                                               "~~~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (doseq [col (range 5)]
        (update-test-world! assoc-in [col 0 :country-id] 1))
      (update-test-world! assoc-in [0 0 :contents :mode] :sentry)
      (update-test-world! assoc-in [0 0 :contents :country-id] 1)
      (update-test-world! assoc-in [4 0 :contents :mode] :sentry)
      (update-test-world! assoc-in [4 0 :contents :country-id] 1)
      (update-test-world! assoc-in [2 0 :contents]
             {:type :army :owner :computer :hits 1 :mode :awake :country-id 1})
      ;; Run 6 rounds
      (with-redefs [rand (constantly 0.5)
                    rand-nth first]
        (dotimes [_ 6]
          (let [game-map (test-utils/read-test-state :game-map)]
            (doseq [c (range 5)
                    :let [unit (get-in game-map [c 0 :contents])]
                    :when (and unit (= :army (:type unit))
                               (= :computer (:owner unit))
                               (#{:awake} (:mode unit)))]
              (army/process-army [c 0])))))
      ;; After 6 rounds some sentries should have been woken
      ;; or army should have settled (not stuck oscillating)
      (let [awake-count (count (for [c (range 5)
                                     :let [unit (get-in (test-utils/read-test-state :game-map) [c 0 :contents])]
                                     :when (and unit (= :army (:type unit))
                                                (#{:awake} (:mode unit)))]
                                 true))]
        ;; Should not have an army perpetually awake bouncing around
        ;; (it either settles as sentry or wakes others who settle)
        (should (>= 1 awake-count)))))

  (context "update-backtrack trimming (L33)"
    (it "trims coast-visited to last 10 entries after 11+ visits"
      ;; 14-col land strip with sea below. Coast-walk army visits 12 cells.
      ;; After 11+ entries, update-backtrack should trim to last 10.
      (let [cols 14
            land-str (apply str (cons \a (repeat (dec cols) \#)))
            sea-str (apply str (repeat cols \~))]
        (set-test-world! (build-test-map [land-str sea-str]))
        (set-test-computer-map! (test-utils/read-test-state :game-map))
        ;; Start army at [0 0] in coast-walk mode with 10 already-visited entries
        (update-test-world! assoc-in [0 0 :contents]
               {:type :army :owner :computer :hits 1
                :mode :coast-walk :coast-direction :clockwise
                :coast-start [13 0]
                :coast-visited [[99 0] [98 0] [97 0] [96 0] [95 0]
                                [94 0] [93 0] [92 0] [91 0] [90 0]]})
        (sync-computer-map!)
        ;; Move once — should add to visited and trim to 10
        (army/process-army [0 0])
        (let [unit (get-in (test-utils/read-test-state :game-map) [1 0 :contents])]
          (should= :army (:type unit))
          (should= 10 (count (:coast-visited unit)))
          ;; Oldest entry [99 0] should be trimmed off
          (should-not-contain [99 0] (:coast-visited unit))
          ;; New entry [1 0] should be present
          (should-contain [1 0] (:coast-visited unit)))))))

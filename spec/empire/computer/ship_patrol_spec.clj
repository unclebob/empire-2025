(ns empire.computer.ship-patrol-spec
  (:require [speclj.core :refer :all]
            [empire.computer.ship-patrol :as patrol]
            [empire.computer.ship-core :as ship-core]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [build-test-map reset-all-atoms!]]))

(describe "ship-patrol"
  (before (reset-all-atoms!))

  (context "generate-random-sea-walk dead end (L132)"
    (it "returns partial path when walk hits dead end after one step"
      ;; Patrol boat at [0,0], empty sea at [1,0], land at [2,0].
      ;; Walk steps to [1,0]. From [1,0]: [0,0] has contents (patrol boat),
      ;; [2,0] is land. No empty passable neighbors -> dead end with path [[1,0]].
      (let [generate-random-sea-walk #'empire.computer.ship-patrol/generate-random-sea-walk]
        (reset! atoms/game-map [[{:type :sea :contents {:type :patrol-boat :owner :computer :hits 1}}]
                                 [{:type :sea}]
                                 [{:type :land}]])
        (with-redefs [rand-nth first]
          (let [path (generate-random-sea-walk [0 0] 5)]
            (should-not-be-nil path)
            (should= [[1 0]] path))))))

  (context "process-patrol-boat blocked step (L183)"
    (it "continues loop when patrol-boat-step returns nil"
      ;; Patrol boat surrounded by occupied cells — step returns nil
      ;; but loop should still decrement and eventually return
      (reset! atoms/game-map [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3}}
                                {:type :sea :contents {:type :patrol-boat :owner :computer :hits 1
                                                       :patrol-mode :crawling}}
                                {:type :sea :contents {:type :destroyer :owner :computer :hits 3}}]])
      (reset! atoms/computer-map @atoms/game-map)
      ;; No land adjacent, so crawl finds no coastal cells -> returns nil
      ;; patrol-boat-step returns nil, so process-patrol-boat decrements and loops
      (let [result (patrol/process-patrol-boat [0 1])]
        ;; Should return current pos (stayed in place)
        (should= [0 1] result)))))

(ns empire.dead-code-spec
  "Runs a headless game under cloverage to find uncovered functions.
   Usage: clj -M:dead-code"
  (:require [empire.ui.quil.core :as quil-core]
            [empire.state.api :as sa]
            [empire.config.core :as config]
            [empire.game.initialization :as init]
            [empire.test.utils :refer [reset-all-atoms!]]
            [speclj.core :refer :all]))

(describe "dead code detection via headless run"
  (it "runs 50 headless rounds to exercise production code"
    (reset-all-atoms!)
    (#'quil-core/initialize-startup-state!
     {:cols 100 :rows 60 :seed 42 :handicap 50
      :production-limits {} :log-enabled false :debug-dump-enabled false}
     42)
    (let [rng (java.util.Random. 42)]
      (alter-var-root #'clojure.core/rand
                      (constantly (fn ([] (.nextDouble rng)) ([n] (* n (.nextDouble rng))))))
      (alter-var-root #'clojure.core/rand-int
                      (constantly (fn [n] (.nextInt rng (int n))))))
    (sa/write-state! :game-over-check-enabled true)
    (with-out-str
      (#'quil-core/run-headless! {:headless-rounds 50}))
    (should (>= (sa/read-state :round-number) 50))))

(ns empire.adapters.state.atoms-spec
  (:require [speclj.core :refer :all]
            [empire.adapters.state.atoms :as adapter]
            [empire.application.ports :as ports]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [build-test-map reset-all-atoms! set-test-world!]]))

(describe "atom world store adapter"
  (before (reset-all-atoms!))

  (it "loads world from atoms/game-map"
    (set-test-world! (build-test-map ["#"]))
    (let [store (adapter/world-store)]
      (should= @atoms/game-map (ports/load-world store))))

  (it "saves world into atoms/game-map"
    (let [store (adapter/world-store)
          world (build-test-map ["~~"])]
      (ports/save-world! store world)
      (should= world @atoms/game-map))))

(run-specs)

(ns empire.adapters.state.atoms-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.adapters.state.atoms :as adapter]
            [empire.application.ports.world-store :as ports]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-world!]]))

(describe "atom world store adapter"
  (before (reset-all-atoms!))

  (it "loads world from (test-utils/game-map-atom)"
    (set-test-world! (build-test-map ["#"]))
    (let [store (adapter/world-store)]
      (should= (test-utils/read-test-state :game-map) (ports/load-world store))))

  (it "saves world into (test-utils/game-map-atom)"
    (let [store (adapter/world-store)
          world (build-test-map ["~~"])]
      (ports/save-world! store world)
      (should= world (test-utils/read-test-state :game-map)))))

(run-specs)

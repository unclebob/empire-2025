(ns empire.computer.kamikazee-targets-spec
  (:require [empire.computer.threat-response.kamikazee :as kamikazee]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-world! set-test-computer-map!]]
            [speclj.core :refer :all]))

(describe "kamikazee target refresh"
  (before (reset-all-atoms!))

  (it "writes ordered army targets onto each live kamikazee fighter"
    (let [world (build-test-map ["f~~"
                                 "~A~"
                                 "~~A"])
          state-atom (atom {:kamikazee-army-targets [{:pos [1 1] :seen-round 1}
                                                     {:pos [2 2] :seen-round 3}]})]
      (set-test-world! world)
      (set-test-computer-map! world)
      (test-utils/set-kamikazee-fighter! [0 0] {:kamikazee-stage :hunt})
      (kamikazee/refresh-army-targets! {:current-world test-utils/read-test-world
                                        :update-game-map! test-utils/update-test-world!
                                        :load-major-invasion-state #(deref state-atom)
                                        :update-major-invasion-state! #(swap! state-atom %)
                                        :read-runtime-state (fn [_] 4)})
      (should= [[2 2] [1 1]]
               (get-in (test-utils/read-test-state :game-map) [0 0 :contents :kamikazee-targets]))))

  (it "drops dead army targets from invasion state before writing fighter targets"
    (let [world (build-test-map ["f~~"
                                 "~A~"
                                 "~~~"])
          state-atom (atom {:kamikazee-army-targets [{:pos [1 1] :seen-round 1}
                                                     {:pos [2 2] :seen-round 3}]})]
      (set-test-world! world)
      (set-test-computer-map! world)
      (test-utils/set-kamikazee-fighter! [0 0] {:kamikazee-stage :hunt})
      (kamikazee/refresh-army-targets! {:current-world test-utils/read-test-world
                                        :update-game-map! test-utils/update-test-world!
                                        :load-major-invasion-state #(deref state-atom)
                                        :update-major-invasion-state! #(swap! state-atom %)
                                        :read-runtime-state (fn [_] 4)})
      (should= [{:pos [1 1] :seen-round 1}]
               (:kamikazee-army-targets @state-atom))
      (should= [[1 1]]
               (get-in (test-utils/read-test-state :game-map) [0 0 :contents :kamikazee-targets])))))

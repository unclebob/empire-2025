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
               (get-in (test-utils/read-test-state :game-map) [0 0 :contents :kamikazee-targets]))))

  (it "records a new army target at the front and refreshes fighter targets"
    (let [state (atom {:kamikazee-army-targets [{:pos [1 1] :seen-round 2}]})
          refresh-calls (atom 0)]
      (with-redefs [empire.computer.threat-response.kamikazee-targets/refresh-army-targets!
                    (fn [_] (swap! refresh-calls inc))]
        (empire.computer.threat-response.kamikazee-targets/record-army-target!
         {:read-runtime-state (fn [_] 7)
          :update-major-invasion-state! (fn [f] (swap! state f))}
         [3 3]))
      (should= [{:pos [3 3] :seen-round 7}
                {:pos [1 1] :seen-round 2}]
               (:kamikazee-army-targets @state))
      (should= 1 @refresh-calls)))

  (it "prefers terminal support targets before sea-reachable or detection points"
    (should= [[4 4]]
             (vec (empire.computer.threat-response.kamikazee-targets/fighter-support-targets
                   {:kamikazee-terminal-sites [[4 4]]
                    :sea-reachable-detection-points [[3 3]]
                    :detection-points [[2 2]]}))))

  (it "chooses the nearest major target and returns nil when no targets exist"
    (let [world (build-test-map ["A~~"
                                 "~~A"])]
      (should= [0 0]
               (empire.computer.threat-response.kamikazee-targets/choose-major-target
                {:kamikazee-army-targets [{:pos [0 0] :seen-round 1}
                                          {:pos [2 1] :seen-round 2}]}
                world
                [0 1]))
      (should-be-nil
       (empire.computer.threat-response.kamikazee-targets/choose-army-target
        {:kamikazee-army-targets []}
        1
        world)))))

(ns empire.computer.army-sovereignty-process-spec
  "Tests for VMS Empire style computer army movement."
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.army :as army]
            [empire.computer.core :as core]
            [empire.computer.production :as production]
            [empire.computer.stamping :as stamping]
            [empire.game-mechanics.services.combat :as combat]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-world! update-test-world!]]))

(defn- disable-opening!
  []
  (test-utils/set-test-state! :round-number nil))
(describe "process-army"
  (before
    (reset-all-atoms!)
    (disable-opening!))

  (context "ignores non-computer units"
    (it "returns nil for player army"
      (set-test-world! (build-test-map ["A#"]))
      (should-be-nil (army/process-army [0 0])))

    (it "returns nil for empty cell"
      (set-test-world! (build-test-map ["##"]))
      (should-be-nil (army/process-army [0 0]))))

  (context "country sovereignty"
    (it "army is blocked by foreign territory"
      ;; Army with country-id 1 at [0 0], target land has country-id 2
      (set-test-world! [[{:type :land :contents {:type :army :owner :computer :hits 1
                                                        :country-id 1 :mode :random-explore
                                                        :random-explore-direction [0 1]}}
                                {:type :land :country-id 2}
                                {:type :land :country-id 2}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (army/process-army [0 0])
      ;; Army should not have moved into foreign territory
      (should= :army (get-in (test-utils/read-test-state :game-map) [0 0 :contents :type])))

    (it "army passes through own territory"
      (set-test-world! [[{:type :land :contents {:type :army :owner :computer :hits 1
                                                        :country-id 1 :mode :random-explore
                                                        :random-explore-direction [0 1]}}
                                {:type :land :country-id 1}
                                {:type :land :country-id 1}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (army/process-army [0 0])
      ;; Army should have moved into own territory
      (should= :army (get-in (test-utils/read-test-state :game-map) [0 1 :contents :type])))

    (it "army passes through unclaimed land"
      (set-test-world! [[{:type :land :contents {:type :army :owner :computer :hits 1
                                                        :country-id 1 :mode :random-explore
                                                        :random-explore-direction [0 1]}}
                                {:type :land}
                                {:type :land}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (army/process-army [0 0])
      ;; Army should have moved into unclaimed land
      (should= :army (get-in (test-utils/read-test-state :game-map) [0 1 :contents :type])))

    (it "army with no country-id passes through any territory"
      (set-test-world! [[{:type :land :contents {:type :army :owner :computer :hits 1
                                                        :mode :random-explore
                                                        :random-explore-direction [0 1]}}
                                {:type :land :country-id 2}
                                {:type :land :country-id 2}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (army/process-army [0 0])
      ;; Army without country-id should move freely
      (should= :army (get-in (test-utils/read-test-state :game-map) [0 1 :contents :type])))

    (it "army can approach cities in foreign territory"
      (set-test-world! [[{:type :land :contents {:type :army :owner :computer :hits 1 :country-id 1}}
                                {:type :city :city-status :free :country-id 2}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (army/process-army [0 0])
      ;; Army should attack the city despite foreign country-id
      ;; (army is removed after conquest attempt either way)
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [0 0 :contents])))

    (it "coast-walk terminates at sovereignty boundary"
      ;; Coast-walking army hits foreign territory - no valid candidates
      (set-test-world! [[{:type :land :contents {:type :army :owner :computer :hits 1
                                                       :country-id 1
                                                       :mode :coast-walk :coast-direction :clockwise
                                                       :coast-start [0 0] :coast-visited [[0 0]]}}
                                {:type :land :country-id 2}
                                {:type :land :country-id 2}]
                               [{:type :sea} {:type :sea} {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (army/process-army [0 0])
      ;; Should terminate coast-walk and switch to sentry
      (let [unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
        (should= :sentry (:mode unit))
        (should-be-nil (:coast-direction unit))))

    (it "army can still attack adjacent enemy across sovereignty border"
      ;; Army with country-id 1 adjacent to player army in foreign territory
      (set-test-world! [[{:type :land :contents {:type :army :owner :computer :hits 1 :country-id 1}}
                                {:type :land :country-id 2 :contents {:type :army :owner :player :hits 1}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (army/process-army [0 0])
      ;; Combat should have occurred - computer army no longer at [0 0]
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [0 0 :contents]))))

  (context "coastal fill behavior"
    (it "goes sentry on coastal cell when no cities to target"
      ;; Army at [1 0] on coastal cell (adjacent to sea at row 1)
      ;; Fully explored, no player/free cities
      (set-test-world! (build-test-map ["####"
                                               "~~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (doseq [col (range 4)]
        (update-test-world! assoc-in [col 0 :country-id] 1))
      (update-test-world! assoc-in [1 0 :contents]
             {:type :army :owner :computer :hits 1 :mode :awake :country-id 1})
      (with-redefs [rand (constantly 0.5)]
        (army/process-army [1 0]))
      ;; Army should go sentry - already on coastal cell, nothing else to do
      (should= :sentry (get-in (test-utils/read-test-state :game-map) [1 0 :contents :mode])))

    (it "moves toward unoccupied coastal cell from interior"
      ;; Army at [1 0] (interior), coastal cells at row 2
      ;; ####
      ;; ####
      ;; ####
      ;; ~~~~
      (set-test-world! (build-test-map ["####"
                                               "####"
                                               "####"
                                               "~~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (doseq [col (range 4) row (range 3)]
        (update-test-world! assoc-in [col row :country-id] 1))
      (update-test-world! assoc-in [1 0 :contents]
             {:type :army :owner :computer :hits 1 :mode :awake :country-id 1})
      (with-redefs [rand (constantly 0.5)]
        (army/process-army [1 0]))
      ;; Army should have moved toward coastal cell (row 2)
      (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [1 0])))
      (should= :army (get-in (test-utils/read-test-state :game-map) [1 1 :contents :type])))

)

  (context "interior exploration"
    (it "interior explorer continues walking in its direction"
      ;; Army at [1 1] with interior-explore-direction already set
      (set-test-world! (build-test-map ["####"
                                               "####"
                                               "####"
                                               "~~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (doseq [col (range 4) row (range 3)]
        (update-test-world! assoc-in [col row :country-id] 1))
      (update-test-world! assoc-in [1 1 :contents]
             {:type :army :owner :computer :hits 1 :mode :awake :country-id 1
              :interior-explore-direction [0 -1]})
      (army/process-army [1 1])
      ;; Army should have moved north to [1 0]
      (let [unit (get-in (test-utils/read-test-state :game-map) [1 0 :contents])]
        (should= :army (:type unit))
        (should= [0 -1] (:interior-explore-direction unit))))

    (it "interior explorer clears direction when reaching coast"
      ;; Army at [1 1] heading south [0 1] toward sea
      (set-test-world! (build-test-map ["####"
                                               "####"
                                               "####"
                                               "~~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (doseq [col (range 4) row (range 3)]
        (update-test-world! assoc-in [col row :country-id] 1))
      (update-test-world! assoc-in [1 1 :contents]
             {:type :army :owner :computer :hits 1 :mode :awake :country-id 1
              :interior-explore-direction [0 1]})
      (army/process-army [1 1])
      ;; Army at [1 2] (coastal) should have direction cleared
      (let [unit (get-in (test-utils/read-test-state :game-map) [1 2 :contents])]
        (should= :army (:type unit))
        (should-be-nil (:interior-explore-direction unit))))

    (it "interior explorer clears direction when blocked"
      ;; Army at [0 0] heading north [0 -1] — would go off map
      (set-test-world! (build-test-map ["####"
                                               "####"
                                               "####"
                                               "~~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (doseq [col (range 4) row (range 3)]
        (update-test-world! assoc-in [col row :country-id] 1))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :army :owner :computer :hits 1 :mode :awake :country-id 1
              :interior-explore-direction [0 -1]})
      (army/process-army [0 0])
      ;; Army should still be at [0 0] but direction cleared
      (let [unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
        (should= :army (:type unit))
        (should-be-nil (:interior-explore-direction unit))))))


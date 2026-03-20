(ns empire.computer.kamikazee-assignment-spec
  (:require [speclj.core :refer :all]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-world! set-test-computer-map! set-test-state! update-test-world!]]
            [empire.test.utils :as test-utils]
            [empire.computer.threat-response-impl :as threat-response]
            [empire.computer.production :as production]))

(describe "major invasion kamikazee assignment"
  (before (reset-all-atoms!))

  (it "marks fighters as kamikazee on major invasion assignment"
    (let [gm (build-test-map ["f~t~O"
                              "~~~~~"])]
      (set-test-world! gm)
      (set-test-computer-map! gm)
      (update-test-world! assoc-in [2 0 :contents :transport-mission] :sailing)
      (update-test-world! assoc-in [2 0 :contents :army-count] 4)
      (threat-response/handle-detection! [4 0] (get-in (test-utils/read-test-state :game-map) [4 0]))
      (threat-response/on-round-start!)
      (let [fighter (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
        (should= true (:kamikazee fighter))
        (should= true (:major-invasion fighter)))))

  (it "records newly detected player armies as kamikazee targets"
    (let [gm (build-test-map ["f~t~O~~"
                              "~~~~~p~"
                              "######A"])]
      (set-test-world! gm)
      (set-test-computer-map! gm)
      (update-test-world! assoc-in [2 0 :contents :transport-mission] :sailing)
      (update-test-world! assoc-in [2 0 :contents :army-count] 4)
      (threat-response/handle-detection! [4 0] (get-in (test-utils/read-test-state :game-map) [4 0]))
      (threat-response/on-round-start!)
      (threat-response/handle-detection! [6 2] (get-in (test-utils/read-test-state :game-map) [6 2]))
      (let [fighter (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
        (should= [[6 2]] (:kamikazee-targets fighter)))))

  (it "forces fighter production during major invasion while a loaded transport remains"
    (let [gm (build-test-map ["X~t~O"
                              "X####"])]
      (set-test-world! gm)
      (set-test-computer-map! gm)
      (update-test-world! assoc-in [0 0 :country-id] 1)
      (update-test-world! assoc-in [0 1 :country-id] 2)
      (update-test-world! assoc-in [2 0 :contents :transport-mission] :sailing)
      (update-test-world! assoc-in [2 0 :contents :army-count] 4)
      (threat-response/handle-detection! [4 0] (get-in (test-utils/read-test-state :game-map) [4 0]))
      (threat-response/on-round-start!)
      (production/rebuild-country-stats!)
      (should= :fighter (production/decide-production [0 0]))
      (should= :fighter (production/decide-production [0 1]))))

  (it "keeps a fixed invasion carrier in place during ship processing"
    (let [world (build-test-map ["XO~~~~"
                                 "~~c~~~"
                                 "~~~~~~"])]
      (set-test-world! world)
      (set-test-computer-map! world)
      (set-test-state! :major-invasion-state
                       {:active? true
                        :kamikazee-bridge-carriers #{[2 1]}})
      (update-test-world! update-in [2 1 :contents]
                          merge
                          {:major-invasion true
                           :mode :sentry
                           :major-invasion-target [2 1]})
      (threat-response/process-ship-threat [2 1] :carrier (get-in (test-utils/read-test-state :game-map) [2 1 :contents]))
      (should= :carrier (get-in (test-utils/read-test-state :game-map) [2 1 :contents :type]))
      (should= nil (get-in (test-utils/read-test-state :game-map) [1 1 :contents]))
      (should= nil (get-in (test-utils/read-test-state :game-map) [2 0 :contents]))
      (should= nil (get-in (test-utils/read-test-state :game-map) [2 2 :contents])))))

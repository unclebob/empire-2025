(ns empire.computer.kamikazee-spec
  (:require [speclj.core :refer :all]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-world! set-test-computer-map! update-test-world!]]
            [empire.test.utils :as test-utils]
            [empire.computer.threat-response :as threat-response]
            [empire.computer.threat-response.kamikazee :as kamikazee]
            [empire.computer.production :as production]))

(describe "major invasion kamikazee"
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

  (it "prefers newer army detections when choosing targets"
    (let [world (build-test-map ["A~A"])]
      (with-redefs [rand-nth first]
        (should= [2 0]
                 (kamikazee/choose-army-target
                  {:kamikazee-army-targets [{:pos [0 0] :seen-round 1}
                                            {:pos [2 0] :seen-round 5}]}
                  5
                  world))))))

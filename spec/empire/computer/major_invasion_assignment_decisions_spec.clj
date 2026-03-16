(ns empire.computer.major-invasion-assignment-decisions-spec
  (:require [speclj.core :refer :all]
            [empire.computer.threat-response.major-invasion-assignment-decisions :as decisions]))

(describe "major-invasion-assignment-decisions"
  (it "classifies unit type into an assignment action"
    (should= :carrier
             (decisions/assignment-action {:type :carrier
                                           :major-invasion-ship-types #{:carrier :destroyer}})))

  (it "clears transient threat and kamikazee fields for fighter assignment"
    (let [assignment (decisions/fighter-assignment {:major-target [9 9]
                                                    :targets [[9 9]]
                                                    :plan {:route [[5 5]]
                                                           :terminal-site [5 5]}})]
      (should= :route (:kamikazee-stage assignment))
      (should (some #{:threat-mission} (:clear-keys assignment)))))

  (it "keeps carrier on sentry when supporting a launch target"
    (should= {:major-invasion true
              :mode :sentry
              :major-invasion-target [4 4]}
             (decisions/carrier-assignment {:support-target [4 4]
                                            :ship-target [7 7]})))

  (it "omits nil coast target for army embark assignment"
    (should= {:mode :move-to-coast-for-invasion}
             (decisions/army-coast-assignment nil))))

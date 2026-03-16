(ns empire.computer.production-selection-decisions-spec
  (:require [empire.computer.production-selection-decisions :as sut]
            [speclj.core :refer :all]))

(describe "production selection decisions"
  (it "chooses the first available country production"
    (should= :transport
             (sut/country-production-choice {:transport :transport
                                             :army :army
                                             :fighter :fighter})))

  (it "chooses the first available global production"
    (should= :carrier
             (sut/global-production-choice {:carrier :carrier
                                            :capital-ship :battleship
                                            :satellite :satellite})))

  (it "builds a process-city action for setting production"
    (should= {:reset-lake-production? false
              :set-production? :army
              :unit-type :army}
             (sut/process-city-action {:reset-lake-production? false
                                       :current-production nil
                                       :unit-type :army}))))

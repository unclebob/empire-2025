(ns empire.computer.production-utility-spec
  "Tests for VMS Empire style computer production."
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.production :as production]
            [empire.computer.ship :as ship]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-world! update-test-world!]]))
(describe "utility functions"
  (before (reset-all-atoms!))

  (context "city-is-coastal?"

    (it "returns true when city has adjacent sea"
      (set-test-world! (build-test-map ["~X#"]))
      (should (production/city-is-coastal? [1 0])))

    (it "returns false when city has no adjacent sea"
      (set-test-world! (build-test-map ["#X#"]))
      (should-not (production/city-is-coastal? [1 0]))))

  (context "count-computer-units"

    (it "counts computer units by type"
      (set-test-world! (build-test-map ["aad"]))
      (let [counts (production/count-computer-units)]
        (should= 2 (get counts :army))
        (should= 1 (get counts :destroyer))))

    (it "ignores player units"
      (set-test-world! (build-test-map ["aAD"]))
      (let [counts (production/count-computer-units)]
        (should= 1 (get counts :army))
        (should-be-nil (get counts :destroyer)))))

  (context "count-computer-cities"

    (it "counts computer cities"
      (set-test-world! (build-test-map ["X#X~O"]))
      (should= 2 (production/count-computer-cities)))

    (it "ignores player and free cities"
      (set-test-world! (build-test-map ["O+X"]))
      (should= 1 (production/count-computer-cities))))

  (context "count-country-armies default army-count (L79)"

    (it "counts 0 armies aboard transport with no :army-count key"
      ;; Transport with no :army-count key should default to 0, not 1
      (set-test-world! (build-test-map ["~t"]))
      (update-test-world! assoc-in [1 0 :contents :country-id] 1)
      (update-test-world! assoc-in [1 0 :contents :army-count] nil)
      (update-test-world! update-in [1 0 :contents] dissoc :army-count)
      (production/rebuild-country-stats!)
      (should= 0 (production/count-country-armies 1)))))

;; ===== 2. production decisions =====

(run-specs)

(ns empire.computer.early-game.role-policy-two-coast-spec
  (:require [empire.computer.early-game.role-policy-two-coast :as policy]
            [speclj.core :refer :all]))

(describe "two coast role policy"
  (it "uses fixed counts for zero and one inland support cities"
    (should= {:CA 1 :CF 0 :CT 1 :CP 0}
             (policy/two-coast-role-counts 0 false))
    (should= {:CA 1 :CF 1 :CT 1 :CP 0}
             (policy/two-coast-role-counts 1 true)))

  (it "uses the strong default for larger inland support counts"
    (should= {:CA 2 :CF 1 :CT 1 :CP 1}
             (policy/two-coast-role-counts 3 true)))

  (it "uses the non-strong default for larger inland support counts"
    (should= {:CA 3 :CF 1 :CT 1 :CP 0}
             (policy/two-coast-role-counts 3 false))))

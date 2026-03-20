(ns empire.computer.transport.sailing-support-spec
  (:require [empire.computer.transport.sailing-support :as support]
            [speclj.core :refer :all]))

(describe "transport sailing support"
  (it "returns no intermediate cells when origin already equals target"
    (should= []
             (support/between-cells [2 2] [2 2])))

  (it "recognizes sea and unexplored corridor cells"
    (should (support/sea-or-unexplored? {:type :sea}))
    (should (support/sea-or-unexplored? {:type :unexplored}))
    (should-not (support/sea-or-unexplored? {:type :land}))))


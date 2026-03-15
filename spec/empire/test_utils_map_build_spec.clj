(ns empire.test-utils-map-build-spec
  (:require [speclj.core :refer :all]
            [empire.test.utils :refer :all]))
(describe "build-test-map"
  (it "returns a vector"
    (should (vector? (build-test-map ["~"]))))

  (it "converts ~ to sea cell"
    (should= [[{:type :sea}]] (build-test-map ["~"])))

  (it "converts # to land cell"
    (should= [[{:type :land}]] (build-test-map ["#"])))

  (it "converts = to labeled sea cell"
    (should= [[{:type :sea :label "="}]] (build-test-map ["="])))

  (it "converts % to labeled land cell"
    (should= [[{:type :land :label "%"}]] (build-test-map ["%"])))

  (it "converts + to free city"
    (should= [[{:type :city :city-status :free}]] (build-test-map ["+"])))

  (it "converts O to player city"
    (should= [[{:type :city :city-status :player}]] (build-test-map ["O"])))

  (it "converts X to computer city"
    (should= [[{:type :city :city-status :computer}]] (build-test-map ["X"])))

  (it "converts * to land with waypoint marker"
    (should= [[{:type :land :waypoint true}]] (build-test-map ["*"])))

  (it "converts - to unexplored (nil)"
    (should= [[nil]] (build-test-map ["-"])))

  (it "converts A to army on land"
    (should= [[{:type :land :contents {:type :army :owner :player :hits 1}}]]
             (build-test-map ["A"])))

  (it "converts T to transport on sea"
    (should= [[{:type :sea :contents {:type :transport :owner :player :hits 1 :army-count 0 :awake-armies 0 :been-to-sea true}}]]
             (build-test-map ["T"])))

  (it "converts D to destroyer on sea"
    (should= [[{:type :sea :contents {:type :destroyer :owner :player :hits 3}}]]
             (build-test-map ["D"])))

  (it "converts P to patrol-boat on sea"
    (should= [[{:type :sea :contents {:type :patrol-boat :owner :player :hits 1}}]]
             (build-test-map ["P"])))

  (it "converts C to carrier on sea"
    (should= [[{:type :sea :contents {:type :carrier :owner :player :hits 8 :fighter-count 0 :awake-fighters 0}}]]
             (build-test-map ["C"])))

  (it "converts B to battleship on sea"
    (should= [[{:type :sea :contents {:type :battleship :owner :player :hits 10}}]]
             (build-test-map ["B"])))

  (it "converts S to submarine on sea"
    (should= [[{:type :sea :contents {:type :submarine :owner :player :hits 2}}]]
             (build-test-map ["S"])))

  (it "converts F to fighter over land"
    (should= [[{:type :land :contents {:type :fighter :owner :player :hits 1 :fuel 32}}]]
             (build-test-map ["F"])))

  (it "converts J to fighter over sea"
    (should= [[{:type :sea :contents {:type :fighter :owner :player :hits 1 :fuel 32}}]]
             (build-test-map ["J"])))

  (it "converts V to satellite over land"
    (should= [[{:type :land :contents {:type :satellite :owner :player :hits 1 :turns-remaining 50}}]]
             (build-test-map ["V"])))

  (it "builds multi-cell rows"
    (should= [[{:type :land}] [{:type :land}] [{:type :sea}] [{:type :sea}]]
             (build-test-map ["##~~"])))

  (it "builds multi-row maps"
    (should= [[{:type :land} {:type :sea}]
              [{:type :sea} {:type :land}]]
             (build-test-map ["#~" "~#"])))

  (it "throws on unknown character"
    (should-throw (build-test-map ["!"])))

  ;; Enemy unit conversions (lowercase)
  (it "converts a to enemy army on land"
    (should= [[{:type :land :contents {:type :army :owner :computer :hits 1}}]]
             (build-test-map ["a"])))

  (it "converts t to enemy transport on sea"
    (should= [[{:type :sea :contents {:type :transport :owner :computer :hits 1 :army-count 0 :awake-armies 0 :been-to-sea true}}]]
             (build-test-map ["t"])))

  (it "converts d to enemy destroyer on sea"
    (should= [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3}}]]
             (build-test-map ["d"])))

  (it "converts p to enemy patrol-boat on sea"
    (should= [[{:type :sea :contents {:type :patrol-boat :owner :computer :hits 1}}]]
             (build-test-map ["p"])))

  (it "converts c to enemy carrier on sea"
    (should= [[{:type :sea :contents {:type :carrier :owner :computer :hits 8 :fighter-count 0 :awake-fighters 0}}]]
             (build-test-map ["c"])))

  (it "converts b to enemy battleship on sea"
    (should= [[{:type :sea :contents {:type :battleship :owner :computer :hits 10}}]]
             (build-test-map ["b"])))

  (it "converts s to enemy submarine on sea"
    (should= [[{:type :sea :contents {:type :submarine :owner :computer :hits 2}}]]
             (build-test-map ["s"])))

  (it "converts f to enemy fighter over land"
    (should= [[{:type :land :contents {:type :fighter :owner :computer :hits 1 :fuel 32}}]]
             (build-test-map ["f"])))

  (it "converts j to enemy fighter over sea"
    (should= [[{:type :sea :contents {:type :fighter :owner :computer :hits 1 :fuel 32}}]]
             (build-test-map ["j"])))

  (it "converts v to enemy satellite over land"
    (should= [[{:type :land :contents {:type :satellite :owner :computer :hits 1 :turns-remaining 50}}]]
             (build-test-map ["v"])))

  (it "builds map with mixed player and enemy units"
    (should= [[{:type :sea :contents {:type :transport :owner :player :hits 1 :army-count 0 :awake-armies 0 :been-to-sea true}}]
              [{:type :sea :contents {:type :transport :owner :computer :hits 1 :army-count 0 :awake-armies 0 :been-to-sea true}}]]
             (build-test-map ["Tt"]))))


(ns empire.test-utils-spec
  (:require [speclj.core :refer :all]
            [empire.test-utils :refer :all]))

(describe "build-test-map"
  (it "returns an atom"
    (should (instance? clojure.lang.Atom (build-test-map ["s"]))))

  (it "converts s to sea cell"
    (should= [[{:type :sea}]] @(build-test-map ["s"])))

  (it "converts L to land cell"
    (should= [[{:type :land}]] @(build-test-map ["L"])))

  (it "converts + to free city"
    (should= [[{:type :city :city-status :free}]] @(build-test-map ["+"])))

  (it "converts O to player city"
    (should= [[{:type :city :city-status :player}]] @(build-test-map ["O"])))

  (it "converts * to land with waypoint marker"
    (should= [[{:type :land :waypoint true}]] @(build-test-map ["*"])))

  (it "converts . to unexplored (nil)"
    (should= [[nil]] @(build-test-map ["."])))

  (it "converts A to army on land"
    (should= [[{:type :land :contents {:type :army :owner :player}}]]
             @(build-test-map ["A"])))

  (it "converts T to transport on sea"
    (should= [[{:type :sea :contents {:type :transport :owner :player}}]]
             @(build-test-map ["T"])))

  (it "converts D to destroyer on sea"
    (should= [[{:type :sea :contents {:type :destroyer :owner :player}}]]
             @(build-test-map ["D"])))

  (it "converts P to patrol-boat on sea"
    (should= [[{:type :sea :contents {:type :patrol-boat :owner :player}}]]
             @(build-test-map ["P"])))

  (it "converts C to carrier on sea"
    (should= [[{:type :sea :contents {:type :carrier :owner :player}}]]
             @(build-test-map ["C"])))

  (it "converts B to battleship on sea"
    (should= [[{:type :sea :contents {:type :battleship :owner :player}}]]
             @(build-test-map ["B"])))

  (it "converts S to submarine on sea"
    (should= [[{:type :sea :contents {:type :submarine :owner :player}}]]
             @(build-test-map ["S"])))

  (it "converts F to fighter over land"
    (should= [[{:type :land :contents {:type :fighter :owner :player}}]]
             @(build-test-map ["F"])))

  (it "converts J to fighter over sea"
    (should= [[{:type :sea :contents {:type :fighter :owner :player}}]]
             @(build-test-map ["J"])))

  (it "builds multi-cell rows"
    (should= [[{:type :land} {:type :land} {:type :sea} {:type :sea}]]
             @(build-test-map ["LLss"])))

  (it "builds multi-row maps"
    (should= [[{:type :land} {:type :sea}]
              [{:type :sea} {:type :land}]]
             @(build-test-map ["Ls" "sL"])))

  (it "throws on unknown character"
    (should-throw (build-test-map ["x"]))))

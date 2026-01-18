(ns empire.test-utils-spec
  (:require [speclj.core :refer :all]
            [empire.test-utils :refer :all]))

(describe "build-test-map"
  (it "returns an atom"
    (should (instance? clojure.lang.Atom (build-test-map ["~"]))))

  (it "converts ~ to sea cell"
    (should= [[{:type :sea}]] @(build-test-map ["~"])))

  (it "converts # to land cell"
    (should= [[{:type :land}]] @(build-test-map ["#"])))

  (it "converts + to free city"
    (should= [[{:type :city :city-status :free}]] @(build-test-map ["+"])))

  (it "converts O to player city"
    (should= [[{:type :city :city-status :player}]] @(build-test-map ["O"])))

  (it "converts X to computer city"
    (should= [[{:type :city :city-status :computer}]] @(build-test-map ["X"])))

  (it "converts * to land with waypoint marker"
    (should= [[{:type :land :waypoint true}]] @(build-test-map ["*"])))

  (it "converts - to unexplored (nil)"
    (should= [[nil]] @(build-test-map ["-"])))

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

  (it "converts V to satellite over land"
    (should= [[{:type :land :contents {:type :satellite :owner :player}}]]
             @(build-test-map ["V"])))

  (it "builds multi-cell rows"
    (should= [[{:type :land} {:type :land} {:type :sea} {:type :sea}]]
             @(build-test-map ["##~~"])))

  (it "builds multi-row maps"
    (should= [[{:type :land} {:type :sea}]
              [{:type :sea} {:type :land}]]
             @(build-test-map ["#~" "~#"])))

  (it "throws on unknown character"
    (should-throw (build-test-map ["x"]))))

(describe "set-test-unit"
  (it "sets a single key-value on the first unit"
    (let [gm (build-test-map ["T"])]
      (set-test-unit gm "T" :mode :sentry)
      (should= :sentry (get-in @gm [0 0 :contents :mode]))))

  (it "sets multiple key-values on a unit"
    (let [gm (build-test-map ["T"])]
      (set-test-unit gm "T" :mode :coastline-follow :army-count 2 :fuel 50)
      (should= :coastline-follow (get-in @gm [0 0 :contents :mode]))
      (should= 2 (get-in @gm [0 0 :contents :army-count]))
      (should= 50 (get-in @gm [0 0 :contents :fuel]))))

  (it "finds unit in multi-row map"
    (let [gm (build-test-map ["##"
                              "#T"])]
      (set-test-unit gm "T" :mode :awake)
      (should= :awake (get-in @gm [1 1 :contents :mode]))))

  (it "finds second unit with T2 notation"
    (let [gm (build-test-map ["T~T"])]
      (set-test-unit gm "T2" :mode :sentry)
      (should= nil (get-in @gm [0 0 :contents :mode]))
      (should= :sentry (get-in @gm [0 2 :contents :mode]))))

  (it "finds army with A notation"
    (let [gm (build-test-map ["A"])]
      (set-test-unit gm "A" :mode :moving :hits 1)
      (should= :moving (get-in @gm [0 0 :contents :mode]))
      (should= 1 (get-in @gm [0 0 :contents :hits]))))

  (it "throws when unit not found"
    (let [gm (build-test-map ["~~"])]
      (should-throw (set-test-unit gm "T" :mode :awake)))))

(describe "get-test-unit"
  (it "returns nil when unit not found"
    (let [gm (build-test-map ["~~"])]
      (should= nil (get-test-unit gm "T"))))

  (it "returns position and unit for first matching unit"
    (let [gm (build-test-map ["T"])]
      (set-test-unit gm "T" :mode :awake)
      (let [result (get-test-unit gm "T")]
        (should= [0 0] (:pos result))
        (should= :transport (:type (:unit result)))
        (should= :awake (:mode (:unit result))))))

  (it "finds unit in multi-row map"
    (let [gm (build-test-map ["##"
                              "#T"])]
      (let [result (get-test-unit gm "T")]
        (should= [1 1] (:pos result))
        (should= :transport (:type (:unit result))))))

  (it "finds second unit with T2 notation"
    (let [gm (build-test-map ["T~T"])]
      (set-test-unit gm "T1" :mode :sentry)
      (set-test-unit gm "T2" :mode :awake)
      (let [result (get-test-unit gm "T2")]
        (should= [0 2] (:pos result))
        (should= :awake (:mode (:unit result))))))

  (it "filters by mode when specified"
    (let [gm (build-test-map ["TT"])]
      (set-test-unit gm "T1" :mode :sentry)
      (set-test-unit gm "T2" :mode :awake)
      (let [result (get-test-unit gm "T" :mode :awake)]
        (should= [0 1] (:pos result))
        (should= :awake (:mode (:unit result))))))

  (it "returns nil when no unit matches filter"
    (let [gm (build-test-map ["T"])]
      (set-test-unit gm "T" :mode :sentry)
      (should= nil (get-test-unit gm "T" :mode :awake))))

  (it "filters by multiple criteria"
    (let [gm (build-test-map ["TTT"])]
      (set-test-unit gm "T1" :mode :sentry :hits 1)
      (set-test-unit gm "T2" :mode :awake :hits 1)
      (set-test-unit gm "T3" :mode :awake :hits 3)
      (let [result (get-test-unit gm "T" :mode :awake :hits 3)]
        (should= [0 2] (:pos result))
        (should= 3 (:hits (:unit result))))))

  (it "works with different unit types"
    (let [gm (build-test-map ["V"])]
      (set-test-unit gm "V" :target [5 5])
      (let [result (get-test-unit gm "V")]
        (should= [0 0] (:pos result))
        (should= :satellite (:type (:unit result)))
        (should= [5 5] (:target (:unit result)))))))

(describe "get-test-city"
  (it "returns nil when city not found"
    (let [gm (build-test-map ["~~"])]
      (should= nil (get-test-city gm "O"))))

  (it "returns position and cell for player city"
    (let [gm (build-test-map ["O"])]
      (let [result (get-test-city gm "O")]
        (should= [0 0] (:pos result))
        (should= :city (:type (:cell result)))
        (should= :player (:city-status (:cell result))))))

  (it "returns position and cell for computer city"
    (let [gm (build-test-map ["X"])]
      (let [result (get-test-city gm "X")]
        (should= [0 0] (:pos result))
        (should= :computer (:city-status (:cell result))))))

  (it "returns position and cell for free city"
    (let [gm (build-test-map ["+"])]
      (let [result (get-test-city gm "+")]
        (should= [0 0] (:pos result))
        (should= :free (:city-status (:cell result))))))

  (it "finds city in multi-row map"
    (let [gm (build-test-map ["##"
                              "#O"])]
      (let [result (get-test-city gm "O")]
        (should= [1 1] (:pos result)))))

  (it "finds second city with O2 notation"
    (let [gm (build-test-map ["O~O"])]
      (let [result (get-test-city gm "O2")]
        (should= [0 2] (:pos result)))))

  (it "distinguishes between city types"
    (let [gm (build-test-map ["O+X"])]
      (should= [0 0] (:pos (get-test-city gm "O")))
      (should= [0 1] (:pos (get-test-city gm "+")))
      (should= [0 2] (:pos (get-test-city gm "X")))))

  (it "returns nil for wrong city type"
    (let [gm (build-test-map ["O"])]
      (should= nil (get-test-city gm "X"))
      (should= nil (get-test-city gm "+")))))

(ns empire.test-utils-unit-access-spec
  (:require [speclj.core :refer :all]
            [empire.test.utils :refer :all]))
(describe "set-test-unit"
  (it "sets a single key-value on the first unit"
    (let [gm (atom (build-test-map ["T"]))]
      (set-test-unit gm "T" :mode :sentry)
      (should= :sentry (get-in @gm [0 0 :contents :mode]))))

  (it "sets multiple key-values on a unit"
    (let [gm (atom (build-test-map ["T"]))]
      (set-test-unit gm "T" :mode :coastline-follow :army-count 2 :fuel 50)
      (should= :coastline-follow (get-in @gm [0 0 :contents :mode]))
      (should= 2 (get-in @gm [0 0 :contents :army-count]))
      (should= 50 (get-in @gm [0 0 :contents :fuel]))))

  (it "finds unit in multi-row map"
    (let [gm (atom (build-test-map ["##"
                              "#T"]))]
      (set-test-unit gm "T" :mode :awake)
      (should= :awake (get-in @gm [1 1 :contents :mode]))))

  (it "finds second unit with T2 notation"
    (let [gm (atom (build-test-map ["T~T"]))]
      (set-test-unit gm "T2" :mode :sentry)
      (should= nil (get-in @gm [0 0 :contents :mode]))
      (should= :sentry (get-in @gm [2 0 :contents :mode]))))

  (it "finds army with A notation"
    (let [gm (atom (build-test-map ["A"]))]
      (set-test-unit gm "A" :mode :moving :hits 1)
      (should= :moving (get-in @gm [0 0 :contents :mode]))
      (should= 1 (get-in @gm [0 0 :contents :hits]))))

  (it "throws when unit not found"
    (let [gm (atom (build-test-map ["~~"]))]
      (should-throw (set-test-unit gm "T" :mode :awake))))

  ;; Enemy unit tests
  (it "sets properties on enemy unit with lowercase spec"
    (let [gm (atom (build-test-map ["t"]))]
      (set-test-unit gm "t" :mode :sentry :hits 2)
      (should= :sentry (get-in @gm [0 0 :contents :mode]))
      (should= 2 (get-in @gm [0 0 :contents :hits]))))

  (it "finds second enemy unit with t2 notation"
    (let [gm (atom (build-test-map ["t~t"]))]
      (set-test-unit gm "t2" :mode :awake)
      (should= nil (get-in @gm [0 0 :contents :mode]))
      (should= :awake (get-in @gm [2 0 :contents :mode]))))

  (it "distinguishes player and enemy units by case"
    (let [gm (atom (build-test-map ["Tt"]))]
      (set-test-unit gm "T" :mode :sentry)
      (set-test-unit gm "t" :mode :awake)
      (should= :sentry (get-in @gm [0 0 :contents :mode]))
      (should= :awake (get-in @gm [1 0 :contents :mode]))))

  (it "throws when enemy unit not found using lowercase spec"
    (let [gm (atom (build-test-map ["T"]))]
      (should-throw (set-test-unit gm "t" :mode :awake)))))

(describe "get-test-unit"
  (it "returns nil when unit not found"
    (let [gm (atom (build-test-map ["~~"]))]
      (should= nil (get-test-unit gm "T"))))

  (it "returns position and unit for first matching unit"
    (let [gm (atom (build-test-map ["T"]))]
      (set-test-unit gm "T" :mode :awake)
      (let [result (get-test-unit gm "T")]
        (should= [0 0] (:pos result))
        (should= :transport (:type (:unit result)))
        (should= :awake (:mode (:unit result))))))

  (it "finds unit in multi-row map"
    (let [gm (atom (build-test-map ["##"
                              "#T"]))]
      (let [result (get-test-unit gm "T")]
        (should= [1 1] (:pos result))
        (should= :transport (:type (:unit result))))))

  (it "finds second unit with T2 notation"
    (let [gm (atom (build-test-map ["T~T"]))]
      (set-test-unit gm "T1" :mode :sentry)
      (set-test-unit gm "T2" :mode :awake)
      (let [result (get-test-unit gm "T2")]
        (should= [2 0] (:pos result))
        (should= :awake (:mode (:unit result))))))

  (it "filters by mode when specified"
    (let [gm (atom (build-test-map ["TT"]))]
      (set-test-unit gm "T1" :mode :sentry)
      (set-test-unit gm "T2" :mode :awake)
      (let [result (get-test-unit gm "T" :mode :awake)]
        (should= [1 0] (:pos result))
        (should= :awake (:mode (:unit result))))))

  (it "returns nil when no unit matches filter"
    (let [gm (atom (build-test-map ["T"]))]
      (set-test-unit gm "T" :mode :sentry)
      (should= nil (get-test-unit gm "T" :mode :awake))))

  (it "filters by multiple criteria"
    (let [gm (atom (build-test-map ["TTT"]))]
      (set-test-unit gm "T1" :mode :sentry :hits 1)
      (set-test-unit gm "T2" :mode :awake :hits 1)
      (set-test-unit gm "T3" :mode :awake :hits 3)
      (let [result (get-test-unit gm "T" :mode :awake :hits 3)]
        (should= [2 0] (:pos result))
        (should= 3 (:hits (:unit result))))))

  (it "works with different unit types"
    (let [gm (atom (build-test-map ["V"]))]
      (set-test-unit gm "V" :target [5 5])
      (let [result (get-test-unit gm "V")]
        (should= [0 0] (:pos result))
        (should= :satellite (:type (:unit result)))
        (should= [5 5] (:target (:unit result))))))

  ;; Enemy unit tests
  (it "returns nil when enemy unit not found"
    (let [gm (atom (build-test-map ["T"]))]
      (should= nil (get-test-unit gm "t"))))

  (it "finds enemy unit with lowercase spec"
    (let [gm (atom (build-test-map ["t"]))]
      (set-test-unit gm "t" :mode :awake)
      (let [result (get-test-unit gm "t")]
        (should= [0 0] (:pos result))
        (should= :transport (:type (:unit result)))
        (should= :computer (:owner (:unit result)))
        (should= :awake (:mode (:unit result))))))

  (it "finds second enemy unit with t2 notation"
    (let [gm (atom (build-test-map ["t~t"]))]
      (set-test-unit gm "t2" :mode :awake)
      (let [result (get-test-unit gm "t2")]
        (should= [2 0] (:pos result))
        (should= :awake (:mode (:unit result))))))

  (it "distinguishes player and enemy units by case"
    (let [gm (atom (build-test-map ["Tt"]))]
      (set-test-unit gm "T" :mode :sentry)
      (set-test-unit gm "t" :mode :awake)
      (should= [0 0] (:pos (get-test-unit gm "T")))
      (should= :player (:owner (:unit (get-test-unit gm "T"))))
      (should= [1 0] (:pos (get-test-unit gm "t")))
      (should= :computer (:owner (:unit (get-test-unit gm "t"))))))

  (it "filters enemy units by mode"
    (let [gm (atom (build-test-map ["tt"]))]
      (set-test-unit gm "t1" :mode :sentry)
      (set-test-unit gm "t2" :mode :awake)
      (let [result (get-test-unit gm "t" :mode :awake)]
        (should= [1 0] (:pos result))))))

(describe "get-test-city"
  (it "returns nil when city not found"
    (let [gm (atom (build-test-map ["~~"]))]
      (should= nil (get-test-city gm "O"))))

  (it "returns position and cell for player city"
    (let [gm (atom (build-test-map ["O"]))]
      (let [result (get-test-city gm "O")]
        (should= [0 0] (:pos result))
        (should= :city (:type (:cell result)))
        (should= :player (:city-status (:cell result))))))

  (it "returns position and cell for computer city"
    (let [gm (atom (build-test-map ["X"]))]
      (let [result (get-test-city gm "X")]
        (should= [0 0] (:pos result))
        (should= :computer (:city-status (:cell result))))))

  (it "returns position and cell for free city"
    (let [gm (atom (build-test-map ["+"]))]
      (let [result (get-test-city gm "+")]
        (should= [0 0] (:pos result))
        (should= :free (:city-status (:cell result))))))

  (it "finds city in multi-row map"
    (let [gm (atom (build-test-map ["##"
                              "#O"]))]
      (let [result (get-test-city gm "O")]
        (should= [1 1] (:pos result)))))

  (it "finds second city with O2 notation"
    (let [gm (atom (build-test-map ["O~O"]))]
      (let [result (get-test-city gm "O2")]
        (should= [2 0] (:pos result)))))

  (it "distinguishes between city types"
    (let [gm (atom (build-test-map ["O+X"]))]
      (should= [0 0] (:pos (get-test-city gm "O")))
      (should= [1 0] (:pos (get-test-city gm "+")))
      (should= [2 0] (:pos (get-test-city gm "X")))))

  (it "returns nil for wrong city type"
    (let [gm (atom (build-test-map ["O"]))]
      (should= nil (get-test-city gm "X"))
      (should= nil (get-test-city gm "+")))))

(describe "get-test-cell"
  (it "finds first = cell"
    (let [gm (atom (build-test-map ["~=~"]))]
      (let [result (get-test-cell gm "=")]
        (should= [1 0] (:pos result))
        (should= :sea (:type (:cell result)))
        (should= "=" (:label (:cell result))))))

  (it "finds second = cell with =2"
    (let [gm (atom (build-test-map ["=~=" "~~~"]))]
      (let [result (get-test-cell gm "=2")]
        (should= [2 0] (:pos result))
        (should= :sea (:type (:cell result))))))

  (it "finds first % cell"
    (let [gm (atom (build-test-map ["%#%"]))]
      (let [result (get-test-cell gm "%")]
        (should= [0 0] (:pos result))
        (should= :land (:type (:cell result)))
        (should= "%" (:label (:cell result))))))

  (it "finds second % cell with %2"
    (let [gm (atom (build-test-map ["%#%"]))]
      (let [result (get-test-cell gm "%2")]
        (should= [2 0] (:pos result))
        (should= :land (:type (:cell result))))))

  (it "finds = cell in multi-column map"
    (let [gm (atom (build-test-map ["~~" "~="]))]
      (let [result (get-test-cell gm "=")]
        (should= [1 1] (:pos result)))))

  (it "returns nil for missing label"
    (let [gm (atom (build-test-map ["~~~"]))]
      (should= nil (get-test-cell gm "="))))

  (it "returns nil when index exceeds count"
    (let [gm (atom (build-test-map ["=~~"]))]
      (should= nil (get-test-cell gm "=2")))))


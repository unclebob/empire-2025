(ns empire.computer.transport-loading-spec
  (:require [speclj.core :refer :all]
            [empire.computer.transport-loading :as loading]
            [empire.computer.transport-core :as tc]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [build-test-map reset-all-atoms!]]))

(describe "transport-loading"
  (before (reset-all-atoms!))

  (context "has-nearby-loadable-armies? (L26)"
    (it "returns false when no armies nearby (L39)"
      (reset! atoms/game-map (build-test-map ["~~~"
                                              "~t~"
                                              "~~~"]))
      (let [transport {:type :transport :owner :computer}]
        (should= false (loading/has-nearby-loadable-armies? [1 1] transport 3))))

    (it "returns true when loadable army adjacent"
      (reset! atoms/game-map (build-test-map ["a~~"
                                              "~t~"
                                              "~~~"]))
      (let [transport {:type :transport :owner :computer}]
        (should (loading/has-nearby-loadable-armies? [1 1] transport 3))))

    (it "finds army via BFS within depth (L44, L56)"
      ;; Army at [4 0], coastal BFS from [0 1] needs 3 hops along coast
      (reset! atoms/game-map [[{:type :land}
                                {:type :sea}
                                {:type :sea}
                                {:type :sea}
                                {:type :land :contents {:type :army :owner :computer}}]
                               [{:type :land}
                                {:type :land}
                                {:type :land}
                                {:type :land}
                                {:type :land}]])
      (let [transport {:type :transport :owner :computer}]
        (should (loading/has-nearby-loadable-armies? [0 1] transport 5))))

    (it "returns false when army beyond max-depth (L44)"
      (reset! atoms/game-map [[{:type :land}
                                {:type :sea}
                                {:type :sea}
                                {:type :sea}
                                {:type :land :contents {:type :army :owner :computer}}]
                               [{:type :land}
                                {:type :land}
                                {:type :land}
                                {:type :land}
                                {:type :land}]])
      (let [transport {:type :transport :owner :computer}]
        (should= false (loading/has-nearby-loadable-armies? [0 1] transport 1))))

    (it "BFS traverses through computer-occupied sea cells (L53)"
      ;; Army at [0 3], computer ship at [0 2] blocks unless BFS allows it
      (reset! atoms/game-map [[{:type :land}
                                {:type :sea}
                                {:type :sea :contents {:type :destroyer :owner :computer}}
                                {:type :land :contents {:type :army :owner :computer}}]
                               [{:type :land}
                                {:type :land}
                                {:type :land}
                                {:type :land}]])
      (let [transport {:type :transport :owner :computer}]
        (should (loading/has-nearby-loadable-armies? [0 1] transport 3))))

    (it "filters armies matching unload-event-id (L24)"
      ;; Army has same unload-event-id as transport — should be filtered
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :computer
                                                        :unload-event-id 42}}
                                {:type :sea}]])
      (let [transport {:type :transport :owner :computer :unload-event-id 42}]
        (should= false (loading/has-nearby-loadable-armies? [0 1] transport 1)))))

  (context "load-adjacent-armies (L59)"
    (it "increments army-count from zero default (L87)"
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :computer :hits 1}}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :army-count 0}}]])
      (should= 1 (loading/load-adjacent-armies [0 1]))
      (should= 1 (get-in @atoms/game-map [0 1 :contents :army-count]))))

  (context "loading-stale? (L135)"
    (it "returns true when loading exceeds max rounds (L138)"
      (reset! atoms/round-number 20)
      (should (loading/loading-stale? {:loading-since 5})))

    (it "returns false when loading is recent"
      (reset! atoms/round-number 12)
      (should-not (loading/loading-stale? {:loading-since 5})))

    (it "returns falsy when loading-since is nil"
      (reset! atoms/round-number 20)
      (should-not (loading/loading-stale? {})))))

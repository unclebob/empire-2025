(ns empire.computer.transport-loading-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.transport-loading :as loading]
            [empire.computer.transport-core :as tc]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-world!]]))

(describe "transport-loading"
  (before (reset-all-atoms!))

  (context "has-nearby-loadable-armies? (L26)"
    (it "returns false when no armies nearby (L39)"
      (set-test-world! (build-test-map ["~~~"
                                        "~t~"
                                        "~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [transport {:type :transport :owner :computer}]
        (should= false (loading/has-nearby-loadable-armies? [1 1] transport 3))))

    (it "returns true when loadable army adjacent"
      (set-test-world! (build-test-map ["a~~"
                                        "~t~"
                                        "~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [transport {:type :transport :owner :computer}]
        (should (loading/has-nearby-loadable-armies? [1 1] transport 3))))

    (it "finds army via BFS within depth (L44, L56)"
      ;; Army at [4 0], coastal BFS from [0 1] needs 3 hops along coast
      (set-test-world! [[{:type :land}
                         {:type :sea}
                         {:type :sea}
                         {:type :sea}
                         {:type :land :contents {:type :army :owner :computer}}]
                        [{:type :land}
                         {:type :land}
                         {:type :land}
                         {:type :land}
                         {:type :land}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [transport {:type :transport :owner :computer}]
        (should (loading/has-nearby-loadable-armies? [0 1] transport 5))))

    (it "returns false when army beyond max-depth (L44)"
      (set-test-world! [[{:type :land}
                         {:type :sea}
                         {:type :sea}
                         {:type :sea}
                         {:type :land :contents {:type :army :owner :computer}}]
                        [{:type :land}
                         {:type :land}
                         {:type :land}
                         {:type :land}
                         {:type :land}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [transport {:type :transport :owner :computer}]
        (should= false (loading/has-nearby-loadable-armies? [0 1] transport 1))))

    (it "BFS traverses through computer-occupied sea cells (L53)"
      ;; Army at [0 3], computer ship at [0 2] blocks unless BFS allows it
      (set-test-world! [[{:type :land}
                         {:type :sea}
                         {:type :sea :contents {:type :destroyer :owner :computer}}
                         {:type :land :contents {:type :army :owner :computer}}]
                        [{:type :land}
                         {:type :land}
                         {:type :land}
                         {:type :land}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [transport {:type :transport :owner :computer}]
        (should (loading/has-nearby-loadable-armies? [0 1] transport 3))))

    (it "filters armies matching unload-event-id (L24)"
      ;; Army has same unload-event-id as transport — should be filtered
      (set-test-world! [[{:type :land :contents {:type :army :owner :computer
                                                  :unload-event-id 42}}
                         {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [transport {:type :transport :owner :computer :unload-event-id 42}]
        (should= false (loading/has-nearby-loadable-armies? [0 1] transport 1))))

    (it "ignores loadable armies reachable only via coast known on game-map"
      (set-test-world! [[{:type :land}
                         {:type :sea}
                         {:type :sea}
                         {:type :land :contents {:type :army :owner :computer}}]
                        [{:type :land}
                         {:type :land}
                         {:type :land}
                         {:type :land}]])
      (set-test-computer-map! [[{:type :land}
                                {:type :sea}
                                {:type :sea}
                                nil]
                               [{:type :land}
                                {:type :land}
                                {:type :land}
                                nil]])
      (let [transport {:type :transport :owner :computer}]
        (should= false (loading/has-nearby-loadable-armies? [0 1] transport 3)))))

  (context "load-adjacent-armies (L59)"
    (it "increments army-count from zero default (L87)"
      (set-test-world! [[{:type :land :contents {:type :army :owner :computer :hits 1}}
                         {:type :sea :contents {:type :transport :owner :computer
                                                :army-count 0}}]])
      (should= 1 (loading/load-adjacent-armies [0 1]))
      (should= 1 (get-in (test-utils/read-test-state :game-map) [0 1 :contents :army-count])))

    (it "loads invasion-bound adjacent armies even from recently unloaded country"
      (test-utils/set-test-state! :round-number 20)
      (set-test-world! [[{:type :land :contents {:type :army :owner :computer :hits 1
                                                 :country-id 130
                                                 :mode :move-to-coast-for-invasion}}
                         {:type :sea :contents {:type :transport :owner :computer
                                                :army-count 0
                                                :unloaded-countries {130 15}}}]])
      (should= 1 (loading/load-adjacent-armies [0 1]))
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [0 0 :contents]))
      (should= 1 (get-in (test-utils/read-test-state :game-map) [0 1 :contents :army-count]))))

  (context "loading-stale? (L135)"
    (it "returns true when loading exceeds max rounds (L138)"
      (test-utils/set-test-state! :round-number 20)
      (should (loading/loading-stale? {:loading-since 5})))

    (it "returns false when loading is recent"
      (test-utils/set-test-state! :round-number 12)
      (should-not (loading/loading-stale? {:loading-since 5})))

    (it "returns falsy when loading-since is nil"
      (test-utils/set-test-state! :round-number 20)
      (should-not (loading/loading-stale? {})))))

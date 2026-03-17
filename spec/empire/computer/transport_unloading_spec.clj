(ns empire.computer.transport-unloading-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.transport-unloading :as unloading]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-world! set-test-computer-map! update-test-world!]]))

(describe "transport-unloading"
  (before (reset-all-atoms!))

  (context "has-nearby-unloadable-land? (L71)"
    (it "returns false when no unloadable land within depth"
      ;; Transport surrounded by sea, no land at all
      (set-test-world! (build-test-map ["~~~"
                                        "~t~"
                                        "~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [transport {:type :transport :owner :computer}]
        (should= false (unloading/has-nearby-unloadable-land? [1 1] transport 3))))

    (it "returns true when unloadable land is adjacent"
      ;; Transport adjacent to empty land
      (set-test-world! (build-test-map ["#~~"
                                        "~t~"
                                        "~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [transport {:type :transport :owner :computer}]
        (should (unloading/has-nearby-unloadable-land? [1 1] transport 3))))

    (it "returns true when unloadable land is within depth via BFS (L89, L94)"
      ;; Transport at [0 1] with country-id 1. Adjacent land at [0 0] has country-id 1 (excluded).
      ;; Unloadable land (no country-id) at [3 0], reachable via 2 BFS hops along coast.
      ;; Depth 3 should find it.
      (set-test-world! [[{:type :land :country-id 1}
                         {:type :sea}
                         {:type :sea}
                         {:type :land}]
                        [{:type :land :country-id 1}
                         {:type :land :country-id 1}
                         {:type :land :country-id 1}
                         {:type :land}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [transport {:type :transport :owner :computer :country-id 1}]
        (should (unloading/has-nearby-unloadable-land? [0 1] transport 3))))

    (it "BFS traverses through computer-occupied sea cells (L66)"
      ;; Transport at [0 1], country-id 1 land at [0 0] (excluded).
      ;; Computer ship at [0 2] blocks unless BFS allows computer-owned sea.
      ;; Unloadable land at [0 3].
      (set-test-world! [[{:type :land :country-id 1}
                         {:type :sea}
                         {:type :sea :contents {:type :destroyer :owner :computer}}
                         {:type :land}]
                        [{:type :land :country-id 1}
                         {:type :land :country-id 1}
                         {:type :land :country-id 1}
                         {:type :land}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [transport {:type :transport :owner :computer :country-id 1}]
        (should (unloading/has-nearby-unloadable-land? [0 1] transport 3))))

    (it "returns false when land is beyond max-depth (L89)"
      ;; Same map but depth limit too small to reach unexcluded land
      (set-test-world! [[{:type :land :country-id 1}
                         {:type :sea}
                         {:type :sea}
                         {:type :land}]
                        [{:type :land :country-id 1}
                         {:type :land :country-id 1}
                         {:type :land :country-id 1}
                         {:type :land}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [transport {:type :transport :owner :computer :country-id 1}]
        (should= false (unloading/has-nearby-unloadable-land? [0 1] transport 0))))

    (it "ignores unloadable land visible only on game-map"
      (set-test-world! [[{:type :land :country-id 1}
                         {:type :sea}
                         {:type :sea}
                         {:type :land}]
                        [{:type :land :country-id 1}
                         {:type :land :country-id 1}
                         {:type :land :country-id 1}
                         {:type :land}]])
      (set-test-computer-map! [[{:type :land :country-id 1}
                                {:type :sea}
                                {:type :sea}
                                nil]
                               [{:type :land :country-id 1}
                                {:type :land :country-id 1}
                                {:type :land :country-id 1}
                                nil]])
      (let [transport {:type :transport :owner :computer :country-id 1}]
        (should= false (unloading/has-nearby-unloadable-land? [0 1] transport 3))))

    (it "does not exclude a visible unload target based on hidden pickup country ownership"
      (set-test-world! [[{:type :land :country-id 9}
                         {:type :sea}
                         {:type :sea}
                         {:type :land :country-id 9}]
                        [{:type :sea}
                         {:type :sea}
                         {:type :sea}
                         {:type :sea}]])
      (set-test-computer-map! [[{:type :land}
                                {:type :sea}
                                {:type :sea}
                                {:type :land :country-id 9}]
                               [{:type :sea}
                                {:type :sea}
                                {:type :sea}
                                {:type :sea}]])
      (let [transport {:type :transport :owner :computer :pickup-continent-pos [0 0]}]
        (should (unloading/has-nearby-unloadable-land? [0 1] transport 3)))))

  (context "unload-armies (L146)"
    (it "unloads armies onto adjacent empty land (L159)"
      (set-test-world! [[{:type :land}
                         {:type :sea :contents {:type :transport :owner :computer
                                                :army-count 2}}
                         {:type :land}]])
      (should (unloading/unload-armies [0 1] nil))
      (should= :army (get-in (test-utils/read-test-state :game-map) [0 0 :contents :type]))
      (should= :army (get-in (test-utils/read-test-state :game-map) [0 2 :contents :type]))
      (should= 0 (get-in (test-utils/read-test-state :game-map) [0 1 :contents :army-count])))

    (it "returns nil when no adjacent empty land"
      (set-test-world! [[{:type :sea}
                         {:type :sea :contents {:type :transport :owner :computer
                                                :army-count 2}}
                         {:type :sea}]])
      (should-be-nil (unloading/unload-armies [0 1] nil)))

    (it "never-reload transport transitions to sailing after full unload"
      (set-test-world! [[{:type :land}
                         {:type :sea :contents {:type :transport :owner :computer
                                                :transport-mission :unloading
                                                :army-count 1
                                                :never-reload? true}}
                         {:type :sea}]])
      (should (unloading/unload-armies [0 1] nil))
      (should= 0 (get-in (test-utils/read-test-state :game-map) [0 1 :contents :army-count]))
      (should= :sailing (get-in (test-utils/read-test-state :game-map) [0 1 :contents :transport-mission]))
      (should= true (get-in (test-utils/read-test-state :game-map) [0 1 :contents :never-reload?]))))

  (context "pickup-continent exclusion"
    (it "does not unload onto the same pickup landmass even when country-ids differ"
      (set-test-world! (build-test-map ["###"
                                        "#t#"
                                        "###"]))
      (set-test-computer-map! (build-test-map ["###"
                                               "#t#"
                                               "###"]))
      (update-test-world! assoc-in [1 1 :contents]
                         {:type :transport :owner :computer
                          :army-count 3
                          :country-id 1
                          :pickup-continent-pos [0 0]})
      ;; Simulate drifted country-ids on the same landmass.
      (doseq [p [[0 1] [1 0] [1 2] [2 1]]]
        (update-test-world! assoc-in (conj p :country-id) 15))
      (should-be-nil (unloading/try-opportunistic-unload [1 1]))
      (should= 3 (get-in (test-utils/read-test-state :game-map) [1 1 :contents :army-count])))))

(ns empire.computer.transport-unloading-spec
  (:require [speclj.core :refer :all]
            [empire.computer.transport-unloading :as unloading]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [build-test-map reset-all-atoms!]]))

(describe "transport-unloading"
  (before (reset-all-atoms!))

  (context "has-nearby-unloadable-land? (L71)"
    (it "returns false when no unloadable land within depth"
      ;; Transport surrounded by sea, no land at all
      (reset! atoms/game-map (build-test-map ["~~~"
                                              "~t~"
                                              "~~~"]))
      (let [transport {:type :transport :owner :computer}]
        (should= false (unloading/has-nearby-unloadable-land? [1 1] transport 3))))

    (it "returns true when unloadable land is adjacent"
      ;; Transport adjacent to empty land
      (reset! atoms/game-map (build-test-map ["#~~"
                                              "~t~"
                                              "~~~"]))
      (let [transport {:type :transport :owner :computer}]
        (should (unloading/has-nearby-unloadable-land? [1 1] transport 3))))

    (it "returns true when unloadable land is within depth via BFS (L89, L94)"
      ;; Transport at [0 1] with country-id 1. Adjacent land at [0 0] has country-id 1 (excluded).
      ;; Unloadable land (no country-id) at [3 0], reachable via 2 BFS hops along coast.
      ;; Depth 3 should find it.
      (reset! atoms/game-map [[{:type :land :country-id 1}
                                {:type :sea}
                                {:type :sea}
                                {:type :land}]
                               [{:type :land :country-id 1}
                                {:type :land :country-id 1}
                                {:type :land :country-id 1}
                                {:type :land}]])
      (let [transport {:type :transport :owner :computer :country-id 1}]
        (should (unloading/has-nearby-unloadable-land? [0 1] transport 3))))

    (it "BFS traverses through computer-occupied sea cells (L66)"
      ;; Transport at [0 1], country-id 1 land at [0 0] (excluded).
      ;; Computer ship at [0 2] blocks unless BFS allows computer-owned sea.
      ;; Unloadable land at [0 3].
      (reset! atoms/game-map [[{:type :land :country-id 1}
                                {:type :sea}
                                {:type :sea :contents {:type :destroyer :owner :computer}}
                                {:type :land}]
                               [{:type :land :country-id 1}
                                {:type :land :country-id 1}
                                {:type :land :country-id 1}
                                {:type :land}]])
      (let [transport {:type :transport :owner :computer :country-id 1}]
        (should (unloading/has-nearby-unloadable-land? [0 1] transport 3))))

    (it "returns false when land is beyond max-depth (L89)"
      ;; Same map but depth limit too small to reach unexcluded land
      (reset! atoms/game-map [[{:type :land :country-id 1}
                                {:type :sea}
                                {:type :sea}
                                {:type :land}]
                               [{:type :land :country-id 1}
                                {:type :land :country-id 1}
                                {:type :land :country-id 1}
                                {:type :land}]])
      (let [transport {:type :transport :owner :computer :country-id 1}]
        (should= false (unloading/has-nearby-unloadable-land? [0 1] transport 0)))))

  (context "unload-armies (L146)"
    (it "unloads armies onto adjacent empty land (L159)"
      (reset! atoms/game-map [[{:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :army-count 2}}
                                {:type :land}]])
      (should (unloading/unload-armies [0 1] nil))
      (should= :army (get-in @atoms/game-map [0 0 :contents :type]))
      (should= :army (get-in @atoms/game-map [0 2 :contents :type]))
      (should= 0 (get-in @atoms/game-map [0 1 :contents :army-count])))

    (it "returns nil when no adjacent empty land"
      (reset! atoms/game-map [[{:type :sea}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :army-count 2}}
                                {:type :sea}]])
      (should-be-nil (unloading/unload-armies [0 1] nil)))))

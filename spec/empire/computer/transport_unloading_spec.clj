(ns empire.computer.transport.unloading-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.transport.unloading :as unloading]
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

    (it "uses the coastal index to find nearby unloadable land"
      (set-test-world! [[{:type :sea}
                         {:type :sea}
                         {:type :sea}
                         {:type :land}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (test-utils/set-test-state! :coastal-index {:coastal-sea-cells #{[0 2]}})
      (let [transport {:type :transport :owner :computer}]
        (should (unloading/has-nearby-unloadable-land? [0 0] transport 2))))

    (it "does not treat adjacent computer cities as unloadable"
      (set-test-world! [[{:type :city :city-status :computer}
                         {:type :sea :contents {:type :transport :owner :computer}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [transport {:type :transport :owner :computer}]
        (should= false (unloading/has-nearby-unloadable-land? [0 1] transport 3))))

    (it "treats adjacent player cities as unloadable"
      (set-test-world! [[{:type :city :city-status :player}
                         {:type :sea :contents {:type :transport :owner :computer}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [transport {:type :transport :owner :computer}]
        (should (unloading/has-nearby-unloadable-land? [0 1] transport 3))))

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
                         {:type :city :city-status :player}]
                        [{:type :sea}
                         {:type :sea}
                         {:type :sea}
                         {:type :sea}]])
      (set-test-computer-map! [[{:type :land}
                                {:type :sea}
                                {:type :sea}
                                {:type :city :city-status :player}]
                               [{:type :sea}
                                {:type :sea}
                                {:type :sea}
                                {:type :sea}]])
      (let [transport {:type :transport :owner :computer}]
        (should (unloading/has-nearby-unloadable-land? [0 1] transport 3)))))

  (context "unload-armies (L146)"
    (it "unloads armies onto adjacent empty land (L159)"
      (set-test-world! [[{:type :land}
                         {:type :sea :contents {:type :transport :owner :computer
                                                :army-count 2}}
                         {:type :land}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (should (unloading/unload-armies [0 1] nil))
      (should= :army (get-in (test-utils/read-test-state :game-map) [0 0 :contents :type]))
      (should= :army (get-in (test-utils/read-test-state :game-map) [0 2 :contents :type]))
      (should= 0 (get-in (test-utils/read-test-state :game-map) [0 1 :contents :army-count])))

    (it "creates distinct computer-unit-ids for each unloaded army"
      (test-utils/set-test-state! :next-computer-unit-id 41)
      (set-test-world! [[{:type :land}
                         {:type :sea :contents {:type :transport :owner :computer
                                                :transport-mission :unloading
                                                :army-count 2}}
                         {:type :land}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (unloading/unload-armies [0 1] nil)
      (let [left-id (get-in (test-utils/read-test-state :game-map) [0 0 :contents :computer-unit-id])
            right-id (get-in (test-utils/read-test-state :game-map) [0 2 :contents :computer-unit-id])]
        (should= 41 left-id)
        (should= 42 right-id)
        (should-not= left-id right-id)))

    (it "records transport unload events with transport metadata"
      (set-test-world! [[{:type :land}
                         {:type :sea :contents {:type :transport :owner :computer
                                                :transport-id 18
                                                :produced-at [0 0]
                                                :transport-mission :unloading
                                                :major-invasion true
                                                :invasion-target [9 9]
                                                :major-invasion-target [8 8]
                                                :army-count 1}}
                         {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (unloading/unload-armies [0 1] nil)
      (let [entry (first (test-utils/read-test-state :computer-event-log))]
        (should= :transport-unload-army (:event entry))
        (should= 18 (:transport-id entry))
        (should= :unloading (:transport-mission entry))
        (should= true (:major-invasion entry))
        (should= 1 (:army-count-before entry))
        (should= [9 9] (:invasion-target entry))
        (should= [8 8] (:major-invasion-target entry))
        (should= [0 0] (:continent-id entry))
        (should= false (:foreign-continent? entry))
        (should= false (:first-landing-on-continent? entry))))

    (it "records first foreign-continent landing when unloading onto a new continent"
      (set-test-world! [[{:type :city :city-status :computer}
                         {:type :sea}
                         {:type :sea :contents {:type :transport :owner :computer
                                                :transport-id 18
                                                :produced-at [0 0]
                                                :transport-mission :unloading
                                                :army-count 1}}
                         {:type :land}]
                        [{:type :sea}
                         {:type :sea}
                         {:type :sea}
                         {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (unloading/unload-armies [0 2] nil)
      (let [entries (test-utils/read-test-state :computer-event-log)
            landing-entry (first (filter #(= :transport-foreign-continent-landing (:event %))
                                         entries))
            unload-entry (first (filter #(= :transport-unload-army (:event %))
                                        entries))]
        (should= [0 3] (:to landing-entry))
        (should= 18 (:transport-id landing-entry))
        (should= [0 3] (:continent-id landing-entry))
        (should= true (:foreign-continent? landing-entry))
        (should= true (:first-landing-on-continent? landing-entry))
        (should= [0 3] (:continent-id unload-entry))
        (should= true (:foreign-continent? unload-entry))
        (should= true (:first-landing-on-continent? unload-entry)))))

    (it "starts an unloading hold when armies remain after unloading"
      (set-test-world! [[{:type :land}
                         {:type :sea :contents {:type :transport :owner :computer
                                                :transport-mission :unloading
                                                :army-count 2}}
                         {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (test-utils/set-test-state! :round-number 30)
      (unloading/unload-armies [0 1] nil)
      (should= 1 (get-in (test-utils/read-test-state :game-map) [0 1 :contents :army-count]))
      (should= 30 (get-in (test-utils/read-test-state :game-map) [0 1 :contents :unloading-hold-since-round])))

    (it "does not unload onto adjacent computer cities"
      (set-test-world! [[{:type :city :city-status :computer}
                         {:type :sea :contents {:type :transport :owner :computer
                                                :army-count 1}}
                         {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (should-be-nil (unloading/unload-armies [0 1] nil))
      (should= 1 (get-in (test-utils/read-test-state :game-map) [0 1 :contents :army-count])))

    (it "unloads onto adjacent player cities"
      (set-test-world! [[{:type :city :city-status :player}
                         {:type :sea :contents {:type :transport :owner :computer
                                                :army-count 1}}
                         {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (should (unloading/unload-armies [0 1] nil))
      (should= :army (get-in (test-utils/read-test-state :game-map) [0 0 :contents :type]))
      (should= 0 (get-in (test-utils/read-test-state :game-map) [0 1 :contents :army-count])))

    (it "returns nil when no adjacent empty land"
      (set-test-world! [[{:type :sea}
                         {:type :sea :contents {:type :transport :owner :computer
                                                :army-count 2}}
                         {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (should-be-nil (unloading/unload-armies [0 1] nil)))

    (it "never-reload transport transitions to sail-to-load after full unload"
      (set-test-world! [[{:type :land}
                         {:type :sea :contents {:type :transport :owner :computer
                                                :transport-mission :unloading
                                                :army-count 1
                                                :never-reload? true}}
                         {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (should (unloading/unload-armies [0 1] nil))
      (should= 0 (get-in (test-utils/read-test-state :game-map) [0 1 :contents :army-count]))
      (should= :sail-to-load (get-in (test-utils/read-test-state :game-map) [0 1 :contents :transport-mission]))
      (should= true (get-in (test-utils/read-test-state :game-map) [0 1 :contents :never-reload?]))))

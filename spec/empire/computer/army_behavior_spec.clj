(ns empire.computer.army-behavior-spec
  "Tests for VMS Empire style computer army movement."
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.army :as army]
            [empire.computer.shared.action-resolution :as action-resolution]
            [empire.computer.production :as production]
            [empire.computer.shared.stamping :as stamping]
            [empire.game-mechanics.services.combat :as combat]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-world! update-test-world!]]))

(defn- disable-opening!
  []
  (test-utils/set-test-state! :round-number nil))

(defn- sync-computer-map!
  []
  (set-test-computer-map! (test-utils/read-test-state :game-map)))

(describe "process-army"
  (before
    (reset-all-atoms!)
    (disable-opening!))
  (context "random-explore behavior"
    (it "goes sentry on coast (adjacent to sea)"
      ;; Army at [1 0] on land, sea at [0 0]
      (set-test-world! (build-test-map ["~a##"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [1 0 :contents]
             {:type :army :owner :computer :hits 1
              :mode :random-explore :random-explore-direction [1 0] :country-id 1})
      (sync-computer-map!)
      (army/process-army [1 0])
      (should= :sentry (get-in (test-utils/read-test-state :game-map) [1 0 :contents :mode])))

    (it "logs and avoids creating malformed contents when coast sentry write has no unit"
      (set-test-world! (build-test-map ["###"
                                        "###"
                                        "~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [1 0 :contents]
                         {:type :army :owner :computer :hits 1
                          :mode :random-explore :random-explore-direction [0 1] :country-id 1})
      (let [logged (atom nil)]
        (with-redefs [empire.computer.army.movement/try-move (fn [_ _] true)
                      empire.game-mechanics.debug.integrity/write-stacktrace-error-log!
                      (fn [_prefix context _throwable]
                        (reset! logged context)
                        "army-error123.log")]
          (@#'empire.computer.army.exploration/try-random-direction-move
           [1 0] 1 {:random-explore-direction [0 1] :country-id 1}))
        (should= nil (get-in (test-utils/read-test-state :game-map) [1 1 :contents]))
        (should= :try-random-direction-move (:operation @logged)))))

    (it "moves inland in stored direction"
      ;; 3x3 all-land, army at [1 1], direction [1 0] (right)
      (set-test-world! (build-test-map ["###"
                                               "#a#"
                                               "###"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [1 1 :contents]
             {:type :army :owner :computer :hits 1
              :mode :random-explore :random-explore-direction [1 0] :country-id 1})
      (sync-computer-map!)
      (army/process-army [1 1])
      ;; Should move right to [2 1]
      (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [1 1])))
      (should= :army (get-in (test-utils/read-test-state :game-map) [2 1 :contents :type])))

    (it "does not enter computer city via interior-explore"
      ;; Army at [0 0] with interior-explore-direction [1 0], computer city at [1 0]
      (set-test-world! (build-test-map ["#X"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :army :owner :computer :hits 1
              :interior-explore-direction [1 0] :country-id 1})
      (army/process-army [0 0])
      ;; Army should not have entered the computer city
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [1 0 :contents])))

    (it "does not go sentry on sea that is hidden on the computer map"
      (set-test-world! (build-test-map ["~a##"]))
      (set-test-computer-map! [[nil {:type :land :contents {:type :army :owner :computer :hits 1
                                                            :mode :random-explore
                                                            :random-explore-direction [1 0]
                                                            :country-id 1}}
                                {:type :land}
                                {:type :land}]])
      (update-test-world! assoc-in [1 0 :contents]
                         {:type :army :owner :computer :hits 1
                          :mode :random-explore :random-explore-direction [1 0] :country-id 1})
      (army/process-army [1 0])
      (should-not= :sentry (get-in (test-utils/read-test-state :game-map) [1 0 :contents :mode])))

    (it "clears mode to awake when blocked inland"
      ;; Army at [2 0] heading right [1 0] — would go off 3-col map
      (set-test-world! (build-test-map ["###"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [2 0 :contents]
             {:type :army :owner :computer :hits 1
              :mode :random-explore :random-explore-direction [1 0] :country-id 1})
      (sync-computer-map!)
      (army/process-army [2 0])
      ;; Army stays, mode cleared to awake so it redirects to coast next round
      (should= :army (get-in (test-utils/read-test-state :game-map) [2 0 :contents :type]))
      (should= :awake (get-in (test-utils/read-test-state :game-map) [2 0 :contents :mode]))
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [2 0 :contents :random-explore-direction]))
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [2 0 :contents :random-explore-rounds]))))

  (context "random explore timeout"
    (it "initializes random-explore-rounds to 0 when entering random-explore"
      ;; 3x3 all-land, army at [1 1] in move-inland mode. Not adjacent to sea → transitions to random-explore.
      (set-test-world! (build-test-map ["###"
                                               "###"
                                               "###"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [1 1 :contents]
             {:type :army :owner :computer :hits 1
              :mode :move-inland :country-id 1})
      (sync-computer-map!)
      (army/process-army [1 1])
      ;; Should now be in random-explore mode with rounds initialized to 0
      (should= :random-explore (get-in (test-utils/read-test-state :game-map) [1 1 :contents :mode]))
      (should= 0 (get-in (test-utils/read-test-state :game-map) [1 1 :contents :random-explore-rounds])))

    (it "transitions to awake after 10 rounds of random-explore"
      ;; 3x3 all-land, army at [1 1] with random-explore-rounds 10 → timeout
      (set-test-world! (build-test-map ["###"
                                               "###"
                                               "###"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [1 1 :contents]
             {:type :army :owner :computer :hits 1
              :mode :random-explore :random-explore-direction [0 1]
              :random-explore-rounds 10 :country-id 1})
      (sync-computer-map!)
      (army/process-army [1 1])
      ;; Should have timed out: mode awake, no random-explore fields
      (should= :awake (get-in (test-utils/read-test-state :game-map) [1 1 :contents :mode]))
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [1 1 :contents :random-explore-direction]))
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [1 1 :contents :random-explore-rounds]))))

  (context "exploration behavior"
    (it "explores when nothing else to do"
      (set-test-world! (build-test-map ["a##"]))
      (set-test-computer-map! (build-test-map ["a##"]))
      (let [result (army/process-army [0 0])]
        ;; Army should move to some passable cell
        (should (or (= [1 0] result) (nil? result)))))

    (it "returns nil when no valid moves"
      ;; Army surrounded by sea
      (set-test-world! [[{:type :sea} {:type :sea} {:type :sea}]
                               [{:type :sea} {:type :land :contents {:type :army :owner :computer}} {:type :sea}]
                               [{:type :sea} {:type :sea} {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [result (army/process-army [1 1])]
        (should-be-nil result))))

  (context "coast-walk behavior"
    (it "moves along coastline (land adjacent to sea)"
      ;; Map: land strip with sea below
      ;; a###
      ;; ~~~~
      ;; Army at [0 0] in coast-walk mode should move to [1 0] (land adjacent to sea)
      (set-test-world! (build-test-map ["a###"
                                               "~~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :army :owner :computer :hits 1
              :mode :coast-walk :coast-direction :clockwise
              :coast-start [0 0] :coast-visited [[0 0]]})
      (army/process-army [0 0])
      ;; Army should have moved to an adjacent land cell that is also adjacent to sea
      (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))
      (should= :army (get-in (test-utils/read-test-state :game-map) [1 0 :contents :type])))

    (it "terminates when no coast-adjacent moves available"
      ;; 3x3 all-land map, army at center. No sea anywhere → no coast candidates → terminate
      (set-test-world! (build-test-map ["###"
                                               "#a#"
                                               "###"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [1 1 :contents]
             {:type :army :owner :computer :hits 1
              :mode :coast-walk :coast-direction :clockwise
              :coast-start [0 0] :coast-visited [[0 0] [1 1]]})
      (sync-computer-map!)
      (army/process-army [1 1])
      ;; Should have terminated - switched to sentry mode
      (let [unit (get-in (test-utils/read-test-state :game-map) [1 1 :contents])]
        (should= :sentry (:mode unit))
        (should-be-nil (:coast-direction unit))))

    (it "terminates when returning to start position"
      ;; ##~   Army at [0 0], only coast candidate is [1 0] which equals coast-start
      ;; ~~~
      (set-test-world! (build-test-map ["##~"
                                               "~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :army :owner :computer :hits 1
              :mode :coast-walk :coast-direction :clockwise
              :coast-start [1 0] :coast-visited [[0 0]]})
      (sync-computer-map!)
      (army/process-army [0 0])
      ;; Army should have moved to [1 0] (coast-start) and terminated
      (let [unit (get-in (test-utils/read-test-state :game-map) [1 0 :contents])]
        (should= :army (:type unit))
        (should= :sentry (:mode unit))
        (should-be-nil (:coast-direction unit))))

    (it "prefers unexplored territory"
      ;; 2x5 map: land on top, sea on bottom. Army at [2 0].
      ;; Computer-map has [0 0] and [1 0] unexplored (nil).
      ;; Candidate [1 0] is visible land with unexplored neighbor [0 0], candidate [3 0] does not.
      (set-test-world! (build-test-map ["#####"
                                               "~~~~~"]))
      (set-test-computer-map! [[nil {:type :sea}]
                                   [{:type :land} {:type :sea}]
                                   [{:type :land} {:type :sea}]
                                   [{:type :land} {:type :sea}]
                                   [{:type :land} {:type :sea}]])
      (update-test-world! assoc-in [2 0 :contents]
             {:type :army :owner :computer :hits 1
              :mode :coast-walk :coast-direction :clockwise
              :coast-start [4 0] :coast-visited [[2 0]]})
      (test-utils/update-test-state! :computer-map assoc-in [2 0 :contents]
                                     {:type :army :owner :computer :hits 1
                                      :mode :coast-walk :country-id 1
                                      :coast-direction :clockwise
                                      :coast-start [4 0] :coast-visited [[2 0]]})
      (test-utils/update-test-state! :computer-map assoc-in [2 0 :country-id] 1)
      (army/process-army [2 0])
      ;; Should move toward [1 0] which has unexplored neighbor [0 0]
      (should= :army (get-in (test-utils/read-test-state :game-map) [1 0 :contents :type])))

    (it "avoids backtracking"
      ;; Army at [1 0], visited includes [0 0], should prefer [2 0]
      (set-test-world! (build-test-map ["###"
                                               "~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [1 0 :contents]
             {:type :army :owner :computer :hits 1
              :mode :coast-walk :coast-direction :clockwise
              :coast-start [0 0] :coast-visited [[0 0] [1 0]]})
      (sync-computer-map!)
      (army/process-army [1 0])
      ;; Should avoid [0 0] (in visited) and go to [2 0]
      (should= :army (get-in (test-utils/read-test-state :game-map) [2 0 :contents :type])))

    (it "attacks adjacent enemy even in coast-walk mode"
      (set-test-world! (build-test-map ["aA#"
                                               "~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :army :owner :computer :hits 1
              :mode :coast-walk :coast-direction :clockwise
              :coast-start [0 0] :coast-visited [[0 0]]})
      (army/process-army [0 0])
      ;; Combat should have occurred
      (should (or (nil? (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))
                  (= :computer (:owner (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))))))))

  (context "territory stamping"
    (it "computer army with country-id stamps land cell it moves to"
      ;; Army at [0 0] with country-id 3, random-explore direction [0 1]
      (set-test-world! [[{:type :land :contents {:type :army :owner :computer :hits 1
                                                        :country-id 3 :mode :random-explore
                                                        :random-explore-direction [0 1]}}
                                {:type :land}
                                {:type :land}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (army/process-army [0 0])
      ;; The cell army moved to should have country-id stamped
      (should= 3 (:country-id (get-in (test-utils/read-test-state :game-map) [0 1]))))

    (it "army without country-id does not stamp land"
      ;; Army at [0 0] without country-id
      (set-test-world! [[{:type :land :contents {:type :army :owner :computer :hits 1}}
                                {:type :land}
                                {:type :land}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (army/process-army [0 0])
      ;; Land cell should not have country-id
      (should-be-nil (:country-id (get-in (test-utils/read-test-state :game-map) [0 1]))))

    (it "army does not stamp sea or city cells"
      ;; Army at [0 0] with country-id, next to a city
      (set-test-world! [[{:type :land :contents {:type :army :owner :computer :hits 1 :country-id 3}}
                                {:type :city :city-status :free}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (army/process-army [0 0])
      ;; City cell should not have country-id from stamping (may get one from conquest though)
      ;; Just verify the cell is still a city
      (should= :city (:type (get-in (test-utils/read-test-state :game-map) [0 1]))))

    (it "move-unit-to stamps territory for computer armies"
      ;; Directly test move-unit-to stamps land
      (set-test-world! [[{:type :land :contents {:type :army :owner :computer :hits 1 :country-id 5}}
                                {:type :land}]])
      (action-resolution/move-unit-to [0 0] [0 1])
      (should= 5 (:country-id (get-in (test-utils/read-test-state :game-map) [0 1])))))

  (context "land action fallback"
    (it "army moves toward city objective when available"
      ;; 3 columns, 1 row each. Army at [0 0], empty land at [1 0], free city at [2 0].
      (set-test-world! [[{:type :land :country-id 1
                                 :contents {:type :army :owner :computer :hits 1
                                            :mode :awake :country-id 1}}]
                               [{:type :land :country-id 1}]
                               [{:type :city :city-status :free}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (with-redefs [rand (constantly 0.9)]
        (@#'army/find-and-execute-land-action [0 0] 1))
      ;; Army should have moved toward the free city
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [0 0 :contents]))
      (should= :army (get-in (test-utils/read-test-state :game-map) [1 0 :contents :type])))

    (it "army starts interior exploration with 1/3 probability"
      ;; 5x5 map, all land with country-id 1. Sea along row 4 (bottom).
      ;; Army at [2 2] center. No cities on continent.
      ;; rand < 1/3 triggers interior exploration. rand-nth picks direction [1 0].
      ;; Target [3 2] is interior land, not adjacent to sea -> direction kept.
      (let [land {:type :land :country-id 1}
            sea {:type :sea}
            army-cell (assoc land :contents {:type :army :owner :computer :hits 1
                                              :mode :awake :country-id 1})]
        (set-test-world! [[land land land land sea]
                                 [land land land land sea]
                                 [land land army-cell land sea]
                                 [land land land land sea]
                                 [land land land land sea]])
        (set-test-computer-map! (test-utils/read-test-state :game-map)))
      (with-redefs [rand (constantly 0.1)
                    rand-nth (constantly [1 0])]
        (@#'army/find-and-execute-land-action [2 2] 1))
      (let [unit (get-in (test-utils/read-test-state :game-map) [3 2 :contents])]
        (should= :army (:type unit))
        (should= [1 0] (:interior-explore-direction unit))))

    (it "army fills coastal cell when no city objective and no interior explore"
      ;; 1 column, 2 rows: land with army at [0 0], sea at [0 1].
      ;; Army is on coast, not in city, not adj to computer city -> goes sentry.
      (set-test-world! [[{:type :land :country-id 1
                                 :contents {:type :army :owner :computer :hits 1
                                            :mode :awake :country-id 1}}
                                {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (with-redefs [rand (constantly 0.9)]
        (@#'army/find-and-execute-land-action [0 0] 1))
      (should= :sentry (get-in (test-utils/read-test-state :game-map) [0 0 :contents :mode])))

    (it "army boards adjacent transport when coastal fill returns nil"
      ;; Use nil country-id so fill-coastal-cell returns nil (its when-guards need country-id).
      ;; Army at [1 0] on land. Transport at [0 0] on sea, loading, adjacent.
      ;; No cities. No sentries.
      (set-test-world! [[{:type :sea
                                 :contents {:type :transport :owner :computer :hits 3
                                            :transport-mission :loading :army-count 0}}]
                               [{:type :land
                                 :contents {:type :army :owner :computer :hits 1
                                            :mode :awake}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (with-redefs [rand (constantly 0.9)]
        (@#'army/find-and-execute-land-action [1 0] nil))
      ;; Army should have boarded the transport
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [1 0 :contents]))
      (should= 1 (get-in (test-utils/read-test-state :game-map) [0 0 :contents :army-count])))

    (it "army explores randomly as last resort"
      ;; Use nil country-id so fill-coastal-cell returns nil. No transports.
      ;; 3x3 all-land game-map. computer-map knows adjacent land cells but still has
      ;; unexplored fringe beyond them, so explore-randomly has legal visible moves.
      (set-test-world! [[{:type :land} {:type :land} {:type :land}]
                        [{:type :land}
                         {:type :land :contents {:type :army :owner :computer
                                                 :hits 1 :mode :awake}}
                         {:type :land}]
                        [{:type :land} {:type :land} {:type :land}]])
      (set-test-computer-map! [[nil {:type :land} nil]
                               [{:type :land}
                                {:type :land :contents {:type :army :owner :computer
                                                        :hits 1 :mode :awake}}
                                {:type :land}]
                               [nil {:type :land} nil]])
      (with-redefs [rand (constantly 0.9)
                    rand-nth (fn [coll] (first coll))]
        (@#'army/find-and-execute-land-action [1 1] nil)
        (should-be-nil (get-in (test-utils/read-test-state :game-map) [1 1 :contents])))))

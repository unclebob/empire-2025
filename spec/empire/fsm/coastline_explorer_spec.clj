(ns empire.fsm.coastline-explorer-spec
  (:require [speclj.core :refer :all]
            [empire.fsm.coastline-explorer :as explorer]
            [empire.fsm.context :as context]
            [empire.fsm.engine :as engine]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [reset-all-atoms! build-test-map]]))

(describe "Coastline Explorer FSM"

  (describe "free city detection and event reporting"

    (before
      (reset-all-atoms!)
      ;; Map with free port city (adjacent to sea)
      ;; ~~~
      ;; ~+#   (+ = free port city at [1,1])
      ;; ~##
      (reset! atoms/game-map (build-test-map ["~~~"
                                               "~+#"
                                               "~##"]))
      (reset! atoms/computer-map (build-test-map ["~~~"
                                                   "~+#"
                                                   "~##"])))

    (it "detects adjacent free port city"
      (let [pos [1 2]  ; land at [1,2] adjacent to port city at [1,1]
            city-pos (explorer/find-adjacent-free-city pos)]
        (should= [1 1] city-pos)))

    (it "returns nil when no adjacent free city"
      ;; Use larger map where [3 3] is truly not adjacent to city at [1 1]
      (reset! atoms/game-map (build-test-map ["~~~~"
                                               "~+##"
                                               "~###"
                                               "~###"]))
      (let [pos [3 3]  ; interior land, not adjacent to city at [1 1]
            city-pos (explorer/find-adjacent-free-city pos)]
        (should-be-nil city-pos)))

    (it "seek-coast-action returns event when adjacent to free city"
      (let [explorer-data (explorer/create-explorer-data [1 2])
            entity {:fsm (:fsm explorer-data)
                    :fsm-state :seeking-coast
                    :fsm-data (assoc (:fsm-data explorer-data) :position [1 2])
                    :event-queue []}
            ctx (context/build-context entity)
            result (explorer/seek-coast-action-public ctx)]
        (should-contain :events result)
        ;; Should have cells-discovered and free-city-found events
        (let [city-events (filter #(= :free-city-found (:type %)) (:events result))]
          (should= 1 (count city-events))
          (should= [1 1] (get-in (first city-events) [:data :coords])))))

    (it "follow-coast-action returns event when adjacent to free city"
      (reset! atoms/game-map (build-test-map ["~~~"
                                               "~+#"
                                               "~##"]))
      (reset! atoms/computer-map (build-test-map ["~~~"
                                                   "~+#"
                                                   "~##"]))
      (let [explorer-data (explorer/create-explorer-data [2 1])
            entity {:fsm (:fsm explorer-data)
                    :fsm-state :following-coast
                    :fsm-data (assoc (:fsm-data explorer-data)
                                     :position [2 1]
                                     :found-coast true)
                    :event-queue []}
            ctx (context/build-context entity)
            result (explorer/follow-coast-action-public ctx)]
        (should-contain :events result)
        (let [city-events (filter #(= :free-city-found (:type %)) (:events result))]
          (should= 1 (count city-events))
          (should= [1 1] (get-in (first city-events) [:data :coords]))))))

  (describe "free inland city detection"

    (before
      (reset-all-atoms!)
      ;; Map with free inland city (not adjacent to sea)
      ;; ####
      ;; #+##   (+ = free inland city at [1,1])
      ;; ####
      (reset! atoms/game-map (build-test-map ["####"
                                               "#+##"
                                               "####"]))
      (reset! atoms/computer-map (build-test-map ["####"
                                                   "#+##"
                                                   "####"])))

    (it "detects adjacent free inland city"
      (let [pos [1 2]  ; land at [1,2] adjacent to inland city at [1,1]
            city-pos (explorer/find-adjacent-free-city pos)]
        (should= [1 1] city-pos))))

  (describe "port city detection for skirting"

    (before
      (reset-all-atoms!)
      ;; Map where port city blocks coastal path
      ;; ~~~~
      ;; ~X##   (X = computer port city at [1,1] blocking coast)
      ;; ~###
      ;; ~~~~
      (reset! atoms/game-map (build-test-map ["~~~~"
                                               "~X##"
                                               "~###"
                                               "~~~~"]))
      (reset! atoms/computer-map (build-test-map ["~~~~"
                                                   "~X##"
                                                   "~###"
                                                   "~~~~"])))

    (it "finds adjacent port city"
      (let [pos [2 1]  ; coastal land adjacent to port city
            port-city (explorer/find-adjacent-port-city pos)]
        (should= [1 1] port-city)))

    (it "returns nil when no adjacent port city"
      (let [pos [2 3]  ; not adjacent to any city
            port-city (explorer/find-adjacent-port-city pos)]
        (should-be-nil port-city)))

    (it "at-port-city? guard returns true when port city blocks path"
      (let [explorer-data (explorer/create-explorer-data [2 1])
            entity {:fsm (:fsm explorer-data)
                    :fsm-state :following-coast
                    :fsm-data (assoc (:fsm-data explorer-data)
                                     :position [2 1]
                                     :found-coast true)
                    :event-queue []}
            ctx (context/build-context entity)]
        (should (explorer/at-port-city? ctx))))

    (it "at-port-city? guard returns false when no port city blocks"
      ;; Position [2 3] is not adjacent to city at [1 1]
      (let [explorer-data (explorer/create-explorer-data [2 3])
            entity {:fsm (:fsm explorer-data)
                    :fsm-state :following-coast
                    :fsm-data (assoc (:fsm-data explorer-data)
                                     :position [2 3]
                                     :found-coast true)
                    :event-queue []}
            ctx (context/build-context entity)]
        (should-not (explorer/at-port-city? ctx)))))

  (describe "city skirting behavior"

    (before
      (reset-all-atoms!)
      ;; Map for skirting test
      ;; ~~~~~
      ;; ~X###   (X = computer port city at [1,1])
      ;; ~####
      ;; ~~~~~
      (reset! atoms/game-map (build-test-map ["~~~~~"
                                               "~X###"
                                               "~####"
                                               "~~~~~"]))
      (reset! atoms/computer-map (build-test-map ["~~~~~"
                                                   "~X###"
                                                   "~####"
                                                   "~~~~~"])))

    (it "skirt-city-action records city being skirted"
      (let [explorer-data (explorer/create-explorer-data [2 1])
            entity {:fsm (:fsm explorer-data)
                    :fsm-state :skirting-city
                    :fsm-data (assoc (:fsm-data explorer-data)
                                     :position [2 1]
                                     :found-coast true)
                    :event-queue []}
            ctx (context/build-context entity)
            result (explorer/skirt-city-action-public ctx)]
        (should-not-be-nil (:city-being-skirted result))
        (should= [1 1] (:city-being-skirted result))))

    (it "skirt-city-action returns move that stays adjacent to city"
      (let [explorer-data (explorer/create-explorer-data [2 1])
            entity {:fsm (:fsm explorer-data)
                    :fsm-state :skirting-city
                    :fsm-data (assoc (:fsm-data explorer-data)
                                     :position [2 1]
                                     :found-coast true
                                     :city-being-skirted [1 1])
                    :event-queue []}
            ctx (context/build-context entity)
            result (explorer/skirt-city-action-public ctx)
            next-pos (:move-to result)]
        (should-not-be-nil next-pos)
        ;; Next position should be adjacent to the city [1,1]
        (let [[r c] next-pos
              [cr cc] [1 1]]
          (should (<= (Math/abs (- r cr)) 1))
          (should (<= (Math/abs (- c cc)) 1)))))

    (it "back-on-coast? returns true when on coast past the city"
      (let [explorer-data (explorer/create-explorer-data [1 2])
            entity {:fsm (:fsm explorer-data)
                    :fsm-state :skirting-city
                    :fsm-data (assoc (:fsm-data explorer-data)
                                     :position [1 2]
                                     :found-coast true
                                     :city-being-skirted [1 1]
                                     :skirt-start-pos [2 1])
                    :event-queue []}
            ctx (context/build-context entity)]
        (should (explorer/back-on-coast? ctx))))

    (it "back-on-coast? returns false when still adjacent to skirted city and at start"
      (let [explorer-data (explorer/create-explorer-data [2 1])
            entity {:fsm (:fsm explorer-data)
                    :fsm-state :skirting-city
                    :fsm-data (assoc (:fsm-data explorer-data)
                                     :position [2 1]
                                     :found-coast true
                                     :city-being-skirted [1 1]
                                     :skirt-start-pos [2 1])
                    :event-queue []}
            ctx (context/build-context entity)]
        (should-not (explorer/back-on-coast? ctx))))

    (it "pick-skirting-move always chooses city-adjacent moves over non-adjacent"
      ;; Map where unit at [2,2] has both city-adjacent and non-adjacent moves
      ;; ~~~~~~
      ;; ~X####   X = computer city at [1,1]
      ;; ~#####   Unit at [2,2] - adjacent to city: [1,2], [2,1], [3,1]
      ;; ~#####                 - NOT adjacent: [2,3], [3,2], [3,3]
      ;; ~~~~~~
      (reset! atoms/game-map (build-test-map ["~~~~~~"
                                               "~X####"
                                               "~#####"
                                               "~#####"
                                               "~~~~~~"]))
      (reset! atoms/computer-map (build-test-map ["~~~~~~"
                                                   "~X####"
                                                   "~#####"
                                                   "~#####"
                                                   "~~~~~~"]))
      (let [pos [2 2]
            city-pos [1 1]
            all-moves [[1 2] [1 3] [2 1] [2 3] [3 1] [3 2] [3 3]]
            recent-moves []
            result (explorer/pick-skirting-move pos all-moves recent-moves city-pos @atoms/computer-map)]
        ;; Result must be adjacent to city [1,1]
        (should-not-be-nil result)
        (should (explorer/adjacent-to? result city-pos))))

    (it "pick-skirting-move returns nil when only non-adjacent moves available"
      ;; When no city-adjacent moves exist, unit should be stuck rather than move away
      ;; ~~~~~~
      ;; ~X####   X = computer city at [1,1]
      ;; ~#####   Unit at [3,3] - only non-adjacent moves available
      ;; ~#####
      ;; ~~~~~~
      (reset! atoms/game-map (build-test-map ["~~~~~~"
                                               "~X####"
                                               "~#####"
                                               "~#####"
                                               "~~~~~~"]))
      (reset! atoms/computer-map (build-test-map ["~~~~~~"
                                                   "~X####"
                                                   "~#####"
                                                   "~#####"
                                                   "~~~~~~"]))
      (let [pos [3 3]
            city-pos [1 1]
            ;; Only moves NOT adjacent to city at [1,1]
            all-moves [[2 3] [2 4] [3 2] [3 4] [4 2] [4 3] [4 4]]
            recent-moves []
            result (explorer/pick-skirting-move pos all-moves recent-moves city-pos @atoms/computer-map)]
        ;; Should return nil - no valid skirting move
        (should-be-nil result))))

  (describe "FSM transitions"

    (before
      (reset-all-atoms!)
      (reset! atoms/game-map (build-test-map ["~~~~~"
                                               "~X###"
                                               "~####"
                                               "~~~~~"]))
      (reset! atoms/computer-map (build-test-map ["~~~~~"
                                                   "~X###"
                                                   "~####"
                                                   "~~~~~"])))

    (it "transitions from following-coast to skirting-city when port city blocks"
      (let [explorer-data (explorer/create-explorer-data [2 1])
            entity {:fsm (:fsm explorer-data)
                    :fsm-state :following-coast
                    :fsm-data (assoc (:fsm-data explorer-data)
                                     :position [2 1]
                                     :found-coast true)
                    :event-queue []}
            ctx (context/build-context entity)
            updated (engine/step entity ctx)]
        (should= :skirting-city (:fsm-state updated))))

    (it "transitions from skirting-city to following-coast when back on coast"
      (let [explorer-data (explorer/create-explorer-data [1 2])
            entity {:fsm (:fsm explorer-data)
                    :fsm-state :skirting-city
                    :fsm-data (assoc (:fsm-data explorer-data)
                                     :position [1 2]
                                     :found-coast true
                                     :city-being-skirted [1 1]
                                     :skirt-start-pos [2 1])
                    :event-queue []}
            ctx (context/build-context entity)
            updated (engine/step entity ctx)]
        (should= :following-coast (:fsm-state updated)))))

  (describe "engine event handling"

    (before
      (reset-all-atoms!)
      (reset! atoms/game-map (build-test-map ["~~~"
                                               "~+#"
                                               "~##"]))
      (reset! atoms/computer-map (build-test-map ["~~~"
                                                   "~+#"
                                                   "~##"])))

    (it "engine merges action events into entity event-queue"
      (let [explorer-data (explorer/create-explorer-data [2 1])
            entity {:fsm (:fsm explorer-data)
                    :fsm-state :following-coast
                    :fsm-data (assoc (:fsm-data explorer-data)
                                     :position [2 1]
                                     :found-coast true)
                    :event-queue []}
            ctx (context/build-context entity)
            updated (engine/step entity ctx)]
        (should (seq (:event-queue updated)))
        (let [city-event (first (filter #(= :free-city-found (:type %))
                                        (:event-queue updated)))]
          (should-not-be-nil city-event)
          (should= [1 1] (get-in city-event [:data :coords]))))))

  (describe "cell discovery reporting"

    (before
      (reset-all-atoms!)
      ;; Map with coastal and landlocked cells
      ;; ~~~~
      ;; ~###   [1,1] coastal, [1,2] landlocked, [1,3] coastal
      ;; ~###   [2,1] coastal, [2,2] landlocked, [2,3] coastal
      ;; ~~~~
      (reset! atoms/game-map (build-test-map ["~~~~"
                                               "~###"
                                               "~###"
                                               "~~~~"]))
      (reset! atoms/computer-map (build-test-map ["~~~~"
                                                   "~###"
                                                   "~###"
                                                   "~~~~"])))

    (it "follow-coast-action emits cells-discovered event for current position"
      (let [explorer-data (explorer/create-explorer-data [1 1])
            entity {:fsm (:fsm explorer-data)
                    :fsm-state :following-coast
                    :fsm-data (assoc (:fsm-data explorer-data)
                                     :position [1 1]
                                     :found-coast true)
                    :event-queue []}
            ctx (context/build-context entity)
            result (explorer/follow-coast-action-public ctx)]
        (should-contain :events result)
        (let [discovery-event (first (filter #(= :cells-discovered (:type %))
                                             (:events result)))]
          (should-not-be-nil discovery-event)
          (should= [{:pos [1 1] :terrain :coastal}]
                   (get-in discovery-event [:data :cells])))))

    (it "reports landlocked cells correctly"
      ;; Need a larger map where [2,2] is truly landlocked (no adjacent sea)
      (reset! atoms/game-map (build-test-map ["~~~~~~"
                                               "~#####"
                                               "~#####"
                                               "~#####"
                                               "~~~~~~"]))
      (reset! atoms/computer-map (build-test-map ["~~~~~~"
                                                   "~#####"
                                                   "~#####"
                                                   "~#####"
                                                   "~~~~~~"]))
      (let [explorer-data (explorer/create-explorer-data [2 2])
            entity {:fsm (:fsm explorer-data)
                    :fsm-state :following-coast
                    :fsm-data (assoc (:fsm-data explorer-data)
                                     :position [2 2]
                                     :found-coast true)
                    :event-queue []}
            ctx (context/build-context entity)
            result (explorer/follow-coast-action-public ctx)]
        (should-contain :events result)
        (let [discovery-event (first (filter #(= :cells-discovered (:type %))
                                             (:events result)))]
          (should-not-be-nil discovery-event)
          (should= [{:pos [2 2] :terrain :landlocked}]
                   (get-in discovery-event [:data :cells])))))

    (it "seek-coast-action emits cells-discovered event"
      ;; Set up inland position
      (reset! atoms/game-map (build-test-map ["~~~~~"
                                               "~####"
                                               "~####"
                                               "~~~~~"]))
      (reset! atoms/computer-map (build-test-map ["~~~~~"
                                                   "~####"
                                                   "~####"
                                                   "~~~~~"]))
      (let [explorer-data (explorer/create-explorer-data [2 2])
            entity {:fsm (:fsm explorer-data)
                    :fsm-state :seeking-coast
                    :fsm-data (assoc (:fsm-data explorer-data)
                                     :position [2 2]
                                     :explore-direction [0 1])
                    :event-queue []}
            ctx (context/build-context entity)
            result (explorer/seek-coast-action-public ctx)]
        (should-contain :events result)
        (let [discovery-event (first (filter #(= :cells-discovered (:type %))
                                             (:events result)))]
          (should-not-be-nil discovery-event)))))

  (describe "pick-following-move with explore-direction"

    (before
      (reset-all-atoms!)
      ;; Long coastline with unexplored on both sides
      ;; ~~~~~~~~~~
      ;; ~########~
      ;; ~~~~~~~~~~
      (reset! atoms/game-map (build-test-map ["~~~~~~~~~~"
                                               "~########~"
                                               "~~~~~~~~~~"]))
      ;; Everything explored except edges
      (reset! atoms/computer-map (build-test-map ["~~~~~~~~~~"
                                                   "~########~"
                                                   "~~~~~~~~~~"])))

    (it "uses explore-direction on first move when recent-moves has only starting position"
      (let [pos [1 5]
            all-moves [[1 4] [1 6]]  ; can go left or right along coast
            recent-moves [[1 5]]     ; only starting position
            explore-direction [0 1]  ; prefer moving right (increasing column)
            result (explorer/pick-following-move pos all-moves recent-moves
                                                  @atoms/computer-map explore-direction)]
        ;; Should pick [1 6] because explore-direction points right
        (should= [1 6] result)))

    (it "picks move in opposite direction when explore-direction points left"
      (let [pos [1 5]
            all-moves [[1 4] [1 6]]
            recent-moves [[1 5]]
            explore-direction [0 -1]  ; prefer moving left (decreasing column)
            result (explorer/pick-following-move pos all-moves recent-moves
                                                  @atoms/computer-map explore-direction)]
        ;; Should pick [1 4] because explore-direction points left
        (should= [1 4] result)))

    (it "falls back to unexplored-based selection after first move"
      (let [pos [1 5]
            all-moves [[1 4] [1 6]]
            recent-moves [[1 5] [1 6]]  ; has history, not first move
            explore-direction [0 1]     ; would prefer right, but not first move
            result (explorer/pick-following-move pos all-moves recent-moves
                                                  @atoms/computer-map explore-direction)]
        ;; Should pick based on unexplored neighbors, not explore-direction
        ;; Both have same unexplored count, but [1 4] is not in recent-moves
        (should= [1 4] result))))

  (describe "moving-to-start state"

    (before
      (reset-all-atoms!)
      ;; Map with land for pathfinding
      ;; ~~~~~~
      ;; ~#####
      ;; ~#####
      ;; ~~~~~~
      (reset! atoms/game-map (build-test-map ["~~~~~~"
                                               "~#####"
                                               "~#####"
                                               "~~~~~~"]))
      (reset! atoms/computer-map (build-test-map ["~~~~~~"
                                                   "~#####"
                                                   "~#####"
                                                   "~~~~~~"])))

    (it "create-explorer-data with target sets moving-to-start state"
      (let [pos [1 1]
            target [1 4]
            data (explorer/create-explorer-data pos target)]
        (should= :moving-to-start (:fsm-state data))
        (should= target (get-in data [:fsm-data :destination]))))

    (it "create-explorer-data without target uses normal state selection"
      (let [pos [1 1]  ; coastal position
            data (explorer/create-explorer-data pos)]
        (should= :following-coast (:fsm-state data))
        (should-be-nil (get-in data [:fsm-data :destination]))))

    (it "create-explorer-data at target position starts exploration immediately"
      (let [pos [1 4]
            target [1 4]  ; already at target
            data (explorer/create-explorer-data pos target)]
        ;; Should skip moving-to-start since already there
        (should= :following-coast (:fsm-state data))))

    (it "move-to-start-action returns next step toward destination"
      (let [data (explorer/create-explorer-data [1 1] [1 4])
            entity (assoc data :event-queue [])
            ctx (context/build-context entity)
            result (explorer/move-to-start-action-public ctx)]
        (should-not-be-nil (:move-to result))
        ;; Should move toward [1 4], so next pos should be [1 2]
        (should= [1 2] (:move-to result))))

    (it "transitions to following-coast when reaching coastal destination"
      (let [;; Unit one step away from coastal destination
            data (explorer/create-explorer-data [1 3] [1 4])
            entity (-> data
                       (assoc :event-queue [])
                       (assoc-in [:fsm-data :position] [1 3]))
            ctx (context/build-context entity)
            updated (engine/step entity ctx)]
        ;; After moving to [1 4] (coastal), should transition to following-coast
        (should= [1 4] (get-in updated [:fsm-data :move-to]))
        ;; State transitions on next step when at destination
        (let [entity2 (-> updated
                          (assoc-in [:fsm-data :position] [1 4]))
              ctx2 (context/build-context entity2)
              updated2 (engine/step entity2 ctx2)]
          (should= :following-coast (:fsm-state updated2)))))

    (it "transitions to seeking-coast when reaching non-coastal destination"
      ;; Larger map with interior destination
      (reset! atoms/game-map (build-test-map ["~~~~~~~"
                                               "~#####~"
                                               "~#####~"
                                               "~#####~"
                                               "~~~~~~~"]))
      (reset! atoms/computer-map (build-test-map ["~~~~~~~"
                                                   "~#####~"
                                                   "~#####~"
                                                   "~#####~"
                                                   "~~~~~~~"]))
      (let [;; Unit at interior position, destination is also interior
            data (explorer/create-explorer-data [2 2] [2 4])
            entity (-> data
                       (assoc :event-queue [])
                       (assoc-in [:fsm-data :position] [2 4]))  ; simulate being at destination
            ctx (context/build-context entity)
            updated (engine/step entity ctx)]
        ;; Should transition to seeking-coast since [2 4] is not coastal
        (should= :seeking-coast (:fsm-state updated))))))

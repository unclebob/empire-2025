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
        (should= 1 (count (:events result)))
        (should= :free-city-found (:type (first (:events result))))
        (should= [1 1] (get-in (first (:events result)) [:data :coords]))))

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
        (should-not (explorer/back-on-coast? ctx)))))

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
          (should= [1 1] (get-in city-event [:data :coords])))))))

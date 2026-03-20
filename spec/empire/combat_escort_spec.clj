(ns empire.game-mechanics.services.combat-escort-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.game-mechanics.services.combat :as combat]
            [empire.config.core :as config]
            [empire.test.utils :refer [build-test-map get-test-city get-test-unit set-test-contents! set-test-unit reset-all-atoms! set-test-computer-map!
                                       set-test-player-map! set-test-world! update-test-world!]]
            [empire.config.units.dispatcher :as dispatcher]
            [empire.game-mechanics.containers.helpers :as uc]
            [empire.computer.production :as comp-production]
            [empire.player.production :as production]))
(describe "conquer-city-contents"
  (before (reset-all-atoms!))

  (it "removes army from conquered city (L27)"
    (set-test-world! (build-test-map ["X"]))
    (set-test-contents! [0 0]
                        {:type :army :owner :computer :mode :sentry :hits 1})
    (combat/conquer-city-contents [0 0] :player)
    (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))))

  (it "leaves satellite unchanged (L31)"
    (set-test-world! (build-test-map ["X"]))
    (set-test-contents! [0 0]
                        {:type :satellite :owner :computer :mode :sentry :hits 1})
    (combat/conquer-city-contents [0 0] :player)
    (should= :satellite (:type (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))))

  (it "flips fighter ownership and wakes it (L24)"
    (set-test-world! (build-test-map ["X"]))
    (set-test-contents! [0 0]
                        {:type :fighter :owner :computer :mode :moving :hits 1
                         :target [5 5] :reason :patrol})
    (combat/conquer-city-contents [0 0] :player)
    (let [f (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))]
      (should= :player (:owner f))
      (should= :awake (:mode f))
      (should-be-nil (:target f))
      (should-be-nil (:reason f))))

  (it "flips transport and clears cargo (L41, L42)"
    (set-test-world! (build-test-map ["X"]))
    (update-test-world! assoc-in [0 0 :contents]
           {:type :transport :owner :computer :mode :moving :hits 1
            :army-count 3 :awake-armies 2})
    (combat/conquer-city-contents [0 0] :player)
    (let [t (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))]
      (should= :player (:owner t))
      (should= 0 (:army-count t))
      (should= 0 (:awake-armies t))))

  (it "flips carrier and clears cargo (L43, L44)"
    (set-test-world! (build-test-map ["X"]))
    (update-test-world! assoc-in [0 0 :contents]
           {:type :carrier :owner :computer :mode :moving :hits 8
            :fighter-count 4 :awake-fighters 2})
    (combat/conquer-city-contents [0 0] :player)
    (let [c (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))]
      (should= :player (:owner c))
      (should= 0 (:fighter-count c))
      (should= 0 (:awake-fighters c))))

  (it "clears standing orders (L49)"
    (set-test-world! (build-test-map ["X"]))
    (update-test-world! assoc-in [0 0 :marching-orders] [1 1])
    (update-test-world! assoc-in [0 0 :flight-path] [[1 0] [2 0]])
    (combat/conquer-city-contents [0 0] :player)
    (should-be-nil (:marching-orders (get-in (test-utils/read-test-state :game-map) [0 0])))
    (should-be-nil (:flight-path (get-in (test-utils/read-test-state :game-map) [0 0])))))

(describe "dead-escort-destroyer?"
  (it "true for destroyer with escort-transport-id"
    (should (combat/dead-escort-destroyer? {:type :destroyer :escort-transport-id 42})))
  (it "false for destroyer without escort-transport-id"
    (should-not (combat/dead-escort-destroyer? {:type :destroyer})))
  (it "false for non-destroyer"
    (should-not (combat/dead-escort-destroyer? {:type :transport :escort-transport-id 42}))))

(describe "dead-escort-transport?"
  (it "true for transport with escort-destroyer-id"
    (should (combat/dead-escort-transport? {:type :transport :escort-destroyer-id 7})))
  (it "false for transport without escort-destroyer-id"
    (should-not (combat/dead-escort-transport? {:type :transport})))
  (it "false for non-transport"
    (should-not (combat/dead-escort-transport? {:type :destroyer :escort-destroyer-id 7}))))

(describe "escort death handling"
  (before (reset-all-atoms!))

  (context "clear-escort-on-death"
    (it "clears transport escort-destroyer-id when destroyer dies (L193)"
      (set-test-world! (build-test-map ["~"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :transport :owner :computer :mode :moving :hits 1
              :transport-id 42 :escort-destroyer-id 7})
      (combat/clear-escort-on-death {:type :destroyer :escort-transport-id 42})
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [0 0 :contents :escort-destroyer-id])))

    (it "sets destroyer to seeking when transport dies (L202)"
      (set-test-world! (build-test-map ["~"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :destroyer :owner :computer :mode :moving :hits 3
              :destroyer-id 7 :escort-transport-id 42 :escort-mode :escorting})
      (combat/clear-escort-on-death {:type :transport :escort-destroyer-id 7})
      (let [d (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
        (should= :seeking (:escort-mode d))
        (should-be-nil (:escort-transport-id d))))

    (it "clears carrier group-battleship-id when battleship dies (L156)"
      (set-test-world! (build-test-map ["~"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :carrier :owner :computer :mode :moving :hits 8
              :carrier-id 10 :group-battleship-id 5})
      (combat/clear-escort-on-death {:type :battleship :escort-carrier-id 10 :escort-id 5})
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [0 0 :contents :group-battleship-id])))

    (it "removes submarine from carrier group-submarine-ids (L156)"
      (set-test-world! (build-test-map ["~"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :carrier :owner :computer :mode :moving :hits 8
              :carrier-id 10 :group-submarine-ids [5 7]})
      (combat/clear-escort-on-death {:type :submarine :escort-carrier-id 10 :escort-id 5})
      (should= [7] (get-in (test-utils/read-test-state :game-map) [0 0 :contents :group-submarine-ids])))

    (it "releases all carrier escorts to seeking when carrier dies (L170, L184)"
      (set-test-world! (build-test-map ["~~"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :battleship :owner :computer :mode :moving :hits 10
              :escort-carrier-id 10 :escort-mode :escorting :orbit-angle 45})
      (update-test-world! assoc-in [1 0 :contents]
             {:type :submarine :owner :computer :mode :moving :hits 2
              :escort-carrier-id 10 :escort-mode :escorting :orbit-angle 90})
      (combat/clear-escort-on-death {:type :carrier :carrier-id 10})
      (let [b (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
        (should= :seeking (:escort-mode b))
        (should-be-nil (:escort-carrier-id b)))
      (let [s (get-in (test-utils/read-test-state :game-map) [1 0 :contents])]
        (should= :seeking (:escort-mode s))
        (should-be-nil (:escort-carrier-id s))))))

(describe "attempt-attack advanced"
  (before (reset-all-atoms!))

  (context "drown-excess-cargo (L219)"
    (it "drowns excess fighters when carrier takes damage"
      (set-test-world! (build-test-map ["~~"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :submarine :owner :player :mode :awake :hits 2})
      (update-test-world! assoc-in [1 0 :contents]
             {:type :carrier :owner :computer :mode :sentry :hits 8
              :fighter-count 6 :awake-fighters 3})
      ;; carrier hits sub (1dmg), sub hits carrier (3dmg), carrier hits sub (dies)
      (let [rolls (atom [0.6 0.4 0.6])]
        (with-redefs [rand (fn [] (let [v (first @rolls)] (swap! rolls rest) v))]
          (combat/apply-combat-result! (combat/attempt-attack (test-utils/read-test-state :game-map) [0 0] [1 0]))
          (let [survivor (get-in (test-utils/read-test-state :game-map) [1 0 :contents])]
            (should= 5 (:hits survivor))
            (should= 5 (:fighter-count survivor))
            (should= 3 (:awake-fighters survivor))))))

    (it "does not drown cargo when within capacity (L223)"
      (set-test-world! (build-test-map ["~~"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :submarine :owner :player :mode :awake :hits 2})
      (update-test-world! assoc-in [1 0 :contents]
             {:type :carrier :owner :computer :mode :sentry :hits 8
              :fighter-count 3 :awake-fighters 2})
      ;; Same combat: carrier survives with 5 hits, capacity 5
      (let [rolls (atom [0.6 0.4 0.6])]
        (with-redefs [rand (fn [] (let [v (first @rolls)] (swap! rolls rest) v))]
          (combat/apply-combat-result! (combat/attempt-attack (test-utils/read-test-state :game-map) [0 0] [1 0]))
          (let [survivor (get-in (test-utils/read-test-state :game-map) [1 0 :contents])]
            (should= 3 (:fighter-count survivor))
            (should= 2 (:awake-fighters survivor)))))))

  (context "dead unit identification (L245)"
    (it "clears dead attacker escort when attacker loses"
      (set-test-world! (build-test-map ["~~~"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :destroyer :owner :player :mode :awake :hits 1
              :destroyer-id 7 :escort-transport-id 42 :escort-mode :escorting})
      (update-test-world! assoc-in [1 0 :contents]
             {:type :battleship :owner :computer :mode :sentry :hits 10})
      (update-test-world! assoc-in [2 0 :contents]
             {:type :transport :owner :player :mode :moving :hits 1
              :transport-id 42 :escort-destroyer-id 7})
      (with-redefs [rand (constantly 0.6)]
        (combat/apply-combat-result! (combat/attempt-attack (test-utils/read-test-state :game-map) [0 0] [1 0]))
        (should-be-nil (get-in (test-utils/read-test-state :game-map) [2 0 :contents :escort-destroyer-id]))))

    (it "clears dead defender escort when attacker wins"
      (set-test-world! (build-test-map ["~~~"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :battleship :owner :player :mode :awake :hits 10})
      (update-test-world! assoc-in [1 0 :contents]
             {:type :destroyer :owner :computer :mode :sentry :hits 1
              :destroyer-id 7 :escort-transport-id 42 :escort-mode :escorting})
      (update-test-world! assoc-in [2 0 :contents]
             {:type :transport :owner :computer :mode :moving :hits 1
              :transport-id 42 :escort-destroyer-id 7})
      (with-redefs [rand (constantly 0.4)]
        (combat/apply-combat-result! (combat/attempt-attack (test-utils/read-test-state :game-map) [0 0] [1 0]))
        (should-be-nil (get-in (test-utils/read-test-state :game-map) [2 0 :contents :escort-destroyer-id]))))))

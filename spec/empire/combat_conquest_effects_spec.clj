(ns empire.combat-conquest-effects-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.game-mechanics.services.combat :as combat]
            [empire.config.core :as config]
            [empire.test.utils :refer [build-test-map get-test-city get-test-unit set-test-unit reset-all-atoms! set-test-computer-map! set-test-world! update-test-world!]]
            [empire.config.units.dispatcher :as dispatcher]
            [empire.game-mechanics.containers.helpers :as uc]
            [empire.computer.shared.action-resolution :as action-resolution]
            [empire.computer.production :as comp-production]
            [empire.player.production :as production]))
(describe "post-combat effects"
  (before (reset-all-atoms!))

  (context "conquer-city-contents"
    (it "flips a fighter at the city to new owner"
      (set-test-world! (build-test-map ["X"]))
      ;; Place computer fighter on the city
      (update-test-world! assoc-in [0 0 :contents]
             {:type :fighter :owner :computer :mode :moving :hits 1 :fuel 20 :target [5 5]})
      (combat/conquer-city-contents [0 0] :player)
      (let [unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
        (should= :player (:owner unit))
        (should= :fighter (:type unit))
        (should= :awake (:mode unit))
        (should-be-nil (:target unit))))

    (it "flips a destroyer at the city to new owner"
      (set-test-world! (build-test-map ["X"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :destroyer :owner :computer :mode :moving :hits 3 :target [5 5]})
      (combat/conquer-city-contents [0 0] :player)
      (let [unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
        (should= :player (:owner unit))
        (should= :destroyer (:type unit))
        (should= :awake (:mode unit))
        (should-be-nil (:target unit))))

    (it "flips a patrol-boat at the city to new owner"
      (set-test-world! (build-test-map ["X"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :patrol-boat :owner :computer :mode :sentry :hits 1 :target [3 3]})
      (combat/conquer-city-contents [0 0] :player)
      (let [unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
        (should= :player (:owner unit))
        (should= :patrol-boat (:type unit))
        (should= :awake (:mode unit))
        (should-be-nil (:target unit))))

    (it "flips a submarine at the city to new owner"
      (set-test-world! (build-test-map ["X"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :submarine :owner :computer :mode :moving :hits 2 :target [4 4]})
      (combat/conquer-city-contents [0 0] :player)
      (let [unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
        (should= :player (:owner unit))
        (should= :submarine (:type unit))
        (should= :awake (:mode unit))
        (should-be-nil (:target unit))))

    (it "flips a battleship at the city to new owner"
      (set-test-world! (build-test-map ["X"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :battleship :owner :computer :mode :moving :hits 10 :target [7 7]})
      (combat/conquer-city-contents [0 0] :player)
      (let [unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
        (should= :player (:owner unit))
        (should= :battleship (:type unit))
        (should= :awake (:mode unit))
        (should-be-nil (:target unit))))

    (it "clears :reason on flipped units"
      (set-test-world! (build-test-map ["X"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :fighter :owner :computer :mode :moving :hits 1 :fuel 20
              :target [5 5] :reason :some-reason})
      (combat/conquer-city-contents [0 0] :player)
      (let [unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
        (should-be-nil (:reason unit))))

    (it "kills army standing on the city"
      (set-test-world! (build-test-map ["X"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :army :owner :computer :mode :awake :hits 1})
      (combat/conquer-city-contents [0 0] :player)
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [0 0 :contents])))

    (it "kills armies inside a transport and flips the transport"
      (set-test-world! (build-test-map ["X"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :transport :owner :computer :mode :sentry :hits 1
              :army-count 4 :awake-armies 2})
      (combat/conquer-city-contents [0 0] :player)
      (let [unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
        (should= :player (:owner unit))
        (should= :transport (:type unit))
        (should= :awake (:mode unit))
        (should= 0 (:army-count unit))
        (should= 0 (:awake-armies unit))))

    (it "kills fighters inside a carrier and flips the carrier"
      (set-test-world! (build-test-map ["X"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :carrier :owner :computer :mode :sentry :hits 8
              :fighter-count 5 :awake-fighters 3})
      (combat/conquer-city-contents [0 0] :player)
      (let [unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
        (should= :player (:owner unit))
        (should= :carrier (:type unit))
        (should= :awake (:mode unit))
        (should= 0 (:fighter-count unit))
        (should= 0 (:awake-fighters unit))))

    (it "leaves satellites unchanged"
      (set-test-world! (build-test-map ["X"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :satellite :owner :computer :mode :moving :hits 1 :turns-remaining 30 :target [5 5]})
      (combat/conquer-city-contents [0 0] :player)
      (let [unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
        (should= :computer (:owner unit))
        (should= :satellite (:type unit))
        (should= :moving (:mode unit))))

    (it "clears city production on conquest"
      (set-test-world! (build-test-map ["X"]))
      (test-utils/update-test-state! :production assoc [0 0] {:item :army :remaining-rounds 3})
      (combat/conquer-city-contents [0 0] :player)
      (should-be-nil (get (test-utils/read-test-state :production) [0 0])))

    (it "clears marching-orders and flight-path on conquest"
      (set-test-world! (build-test-map ["X"]))
      (update-test-world! assoc-in [0 0 :marching-orders] [10 10])
      (update-test-world! assoc-in [0 0 :flight-path] [20 20])
      (combat/conquer-city-contents [0 0] :player)
      (let [cell (get-in (test-utils/read-test-state :game-map) [0 0])]
        (should-be-nil (:marching-orders cell))
        (should-be-nil (:flight-path cell))))

    (it "preserves airport fighter count for new owner"
      (set-test-world! (build-test-map ["X"]))
      (update-test-world! assoc-in [0 0 :fighter-count] 3)
      (update-test-world! assoc-in [0 0 :awake-fighters] 1)
      (combat/conquer-city-contents [0 0] :player)
      (let [cell (get-in (test-utils/read-test-state :game-map) [0 0])]
        (should= 3 (:fighter-count cell))
        (should= 1 (:awake-fighters cell))))

    (it "preserves shipyard ships for new owner"
      (set-test-world! (build-test-map ["X"]))
      (update-test-world! assoc-in [0 0 :shipyard]
             [{:type :destroyer :hits 2} {:type :submarine :hits 1}])
      (combat/conquer-city-contents [0 0] :player)
      (let [cell (get-in (test-utils/read-test-state :game-map) [0 0])]
        (should= 2 (count (:shipyard cell)))
        (should= :destroyer (:type (first (:shipyard cell))))))

    (it "player conquest calls conquer-city-contents"
      (with-redefs [rand (constantly 0.1)]
        (set-test-world! (build-test-map ["AX"]))
        ;; Place a computer destroyer on the city
        (update-test-world! assoc-in [1 0 :contents]
               {:type :destroyer :owner :computer :mode :sentry :hits 3})
        (test-utils/update-test-state! :production assoc [1 0] {:item :fighter :remaining-rounds 5})
        (combat/apply-combat-result! (combat/attempt-conquest (test-utils/read-test-state :game-map) [0 0] [1 0]))
        ;; City should be player-owned
        (should= :player (get-in (test-utils/read-test-state :game-map) [1 0 :city-status]))
        ;; Destroyer should be flipped to player
        (let [unit (get-in (test-utils/read-test-state :game-map) [1 0 :contents])]
          (should= :player (:owner unit))
          (should= :destroyer (:type unit)))
        ;; Production should be cleared
        (should-be-nil (get (test-utils/read-test-state :production) [1 0]))))

    (it "computer conquest applies same logic"
      (with-redefs [rand (constantly 0.1)]
        (set-test-world! (build-test-map ["aO"]))
        (set-test-computer-map! (test-utils/read-test-state :game-map))
        ;; Place a player destroyer on the city
        (update-test-world! assoc-in [1 0 :contents]
               {:type :destroyer :owner :player :mode :sentry :hits 3})
        (test-utils/update-test-state! :production assoc [1 0] {:item :army :remaining-rounds 2})
        (let [attempt-conquest (requiring-resolve 'empire.computer.shared.action-resolution/attempt-conquest-computer)]
          (attempt-conquest [0 0] [1 0])
          ;; City should be computer-owned
          (should= :computer (get-in (test-utils/read-test-state :game-map) [1 0 :city-status]))
          ;; Destroyer should be flipped to computer
          (let [unit (get-in (test-utils/read-test-state :game-map) [1 0 :contents])]
            (should= :computer (:owner unit))
            (should= :destroyer (:type unit)))
          ;; Old production should be replaced with auto-produced army
          (should= :army (:item (get (test-utils/read-test-state :production) [1 0])))))))

  (context "country-id on conquest"
    (it "computer army with country-id assigns it to conquered city"
      (with-redefs [rand (constantly 0.1)]
        (set-test-world! (build-test-map ["aO"]))
        (set-test-computer-map! (test-utils/read-test-state :game-map))
        (update-test-world! assoc-in [0 0 :contents :country-id] 3)
        (let [attempt-conquest (requiring-resolve 'empire.computer.shared.action-resolution/attempt-conquest-computer)]
          (attempt-conquest [0 0] [1 0])
          (should= 3 (:country-id (get-in (test-utils/read-test-state :game-map) [1 0])))))))

  ;; unload-event-id minting tests removed — country-id is now minted at sail time
  ;; via mint-unload-country-id, so armies always have country-id at unload.

)
(run-specs)

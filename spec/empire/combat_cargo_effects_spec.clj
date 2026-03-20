(ns empire.combat-cargo-effects-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.game-mechanics.services.combat :as combat]
            [empire.config.core :as config]
            [empire.test.utils :refer [build-test-map get-test-city get-test-unit set-test-unit reset-all-atoms! set-test-computer-map! set-test-world! update-test-world!]]
            [empire.config.units.dispatcher :as dispatcher]
            [empire.game-mechanics.containers.helpers :as uc]
            [empire.computer.core :as computer-core]
            [empire.computer.production :as comp-production]
            [empire.player.production :as production]))
(describe "post-combat effects"
  (before (reset-all-atoms!))

  (context "cargo drowning after combat"
    (it "drowns excess fighters when carrier wins with reduced capacity"
      ;; Carrier at 8 hits with 6 fighters attacks army. Carrier wins but takes hits.
      ;; Carrier ends at 4/8 hits -> capacity 4, so 2 fighters drown.
      (set-test-world! (build-test-map ["Ca"]))
      (set-test-unit (test-utils/game-map-atom) "C" :hits 8 :fighter-count 6 :awake-fighters 0)
      (set-test-unit (test-utils/game-map-atom) "a" :hits 1)
      ;; Rolls: 0.6(a hits C:7), 0.6(a hits C:6), 0.6(a hits C:5), 0.6(a hits C:4), 0.4(C hits a:0)
      (let [rolls (atom [0.6 0.6 0.6 0.6 0.4])]
        (with-redefs [rand (fn [] (let [v (first @rolls)] (swap! rolls rest) v))]
          (combat/apply-combat-result! (combat/attempt-attack (test-utils/read-test-state :game-map) [0 0] [1 0]))
          (let [survivor (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))]
            (should= :carrier (:type survivor))
            (should= 4 (:hits survivor))
            (should= 4 (:fighter-count survivor))))))

    (it "does not drown when cargo within capacity"
      ;; Carrier at 8 hits with 3 fighters attacks army. Carrier wins with 4 hits.
      ;; Capacity 4 >= 3 fighters, no drowning.
      (set-test-world! (build-test-map ["Ca"]))
      (set-test-unit (test-utils/game-map-atom) "C" :hits 8 :fighter-count 3 :awake-fighters 0)
      (set-test-unit (test-utils/game-map-atom) "a" :hits 1)
      (let [rolls (atom [0.6 0.6 0.6 0.6 0.4])]
        (with-redefs [rand (fn [] (let [v (first @rolls)] (swap! rolls rest) v))]
          (combat/apply-combat-result! (combat/attempt-attack (test-utils/read-test-state :game-map) [0 0] [1 0]))
          (let [survivor (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))]
            (should= 3 (:fighter-count survivor))))))

    (it "caps awake-fighters at new fighter-count after drowning"
      (set-test-world! (build-test-map ["Ca"]))
      (set-test-unit (test-utils/game-map-atom) "C" :hits 8 :fighter-count 6 :awake-fighters 5)
      (set-test-unit (test-utils/game-map-atom) "a" :hits 1)
      ;; Carrier ends at 4 hits -> capacity 4
      (let [rolls (atom [0.6 0.6 0.6 0.6 0.4])]
        (with-redefs [rand (fn [] (let [v (first @rolls)] (swap! rolls rest) v))]
          (combat/apply-combat-result! (combat/attempt-attack (test-utils/read-test-state :game-map) [0 0] [1 0]))
          (let [survivor (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))]
            (should= 4 (:fighter-count survivor))
            (should (<= (:awake-fighters survivor) 4))))))

    (it "handles carrier with missing cargo keys (defaults to 0)"
      ;; Carrier with no :fighter-count or :awake-fighters keys
      (set-test-world! (build-test-map ["Ca"]))
      (set-test-unit (test-utils/game-map-atom) "C" :hits 8)
      (update-test-world! update-in [0 0 :contents] dissoc :fighter-count :awake-fighters)
      (set-test-unit (test-utils/game-map-atom) "a" :hits 1)
      (let [rolls (atom [0.6 0.6 0.6 0.6 0.4])]
        (with-redefs [rand (fn [] (let [v (first @rolls)] (swap! rolls rest) v))]
          (combat/apply-combat-result! (combat/attempt-attack (test-utils/read-test-state :game-map) [0 0] [1 0]))
          (let [survivor (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))]
            (should= :carrier (:type survivor))
            ;; Should not crash — 0 cargo, nothing to drown
            (should= 4 (:hits survivor))))))

    (it "drowns cargo when defending carrier takes damage"
      ;; Computer army attacks player carrier. Carrier wins but takes damage.
      (set-test-world! (build-test-map ["aC"]))
      (set-test-unit (test-utils/game-map-atom) "C" :hits 8 :fighter-count 6 :awake-fighters 0)
      (set-test-unit (test-utils/game-map-atom) "a" :hits 1)
      ;; Rolls: 0.6(C hits a:0) -> army dies immediately, carrier unhurt
      ;; Need carrier to take damage. Army attacks carrier.
      ;; Rolls: 0.6(C hits a, damage 1) -> a dies. But carrier takes no damage.
      ;; Let me use a scenario where the attacker damages the defender before dying.
      ;; Roll 0.4: attacker hits defender (army deals 1 damage to carrier -> 7 hits)
      ;; Roll 0.6: defender (carrier) hits attacker (1 damage -> army dies)
      ;; Carrier at 7 hits, capacity 7 >= 6, no drowning.
      ;; Need more damage. Use a stronger attacker.
      (set-test-world! (build-test-map ["sC"]))
      (set-test-unit (test-utils/game-map-atom) "C" :hits 8 :fighter-count 7 :awake-fighters 0)
      (set-test-unit (test-utils/game-map-atom) "s" :hits 2)
      ;; Rolls: 0.4(sub hits C:5), 0.4(sub hits C:2), 0.6(C hits sub:1), 0.6(C hits sub:0)
      (let [rolls (atom [0.4 0.4 0.6 0.6])]
        (with-redefs [rand (fn [] (let [v (first @rolls)] (swap! rolls rest) v))]
          (combat/apply-combat-result! (combat/attempt-attack (test-utils/read-test-state :game-map) [0 0] [1 0]))
          ;; Carrier won (defender), now at 2/8 hits -> capacity 2
          ;; 7 fighters should be reduced to 2
          (let [survivor (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))]
            (should= :carrier (:type survivor))
            (should= 2 (:hits survivor))
            (should= 2 (:fighter-count survivor)))))))

  (context "clear-escort-on-death"
    (it "dead destroyer clears transport's escort-destroyer-id"
      ;; Destroyer at [0,0] paired with transport at [2,0]. Enemy army kills destroyer.
      (set-test-world! (build-test-map ["D#Ta"]))
      (set-test-unit (test-utils/game-map-atom) "D" :hits 3
                     :escort-transport-id 42 :escort-id 99)
      (update-test-world! assoc-in [2 0 :contents]
             {:type :transport :owner :player :mode :sentry :hits 1
              :transport-id 42 :escort-destroyer-id 99 :army-count 0 :awake-armies 0})
      (set-test-unit (test-utils/game-map-atom) "a" :hits 1)
      ;; Destroyer attacks army. Roll 0.6 = defender hits, 0.6 again, 0.6 again => destroyer dies
      (with-redefs [rand (constantly 0.6)]
        (combat/apply-combat-result! (combat/attempt-attack (test-utils/read-test-state :game-map) [0 0] [3 0]))
        (let [transport (:contents (get-in (test-utils/read-test-state :game-map) [2 0]))]
          (should= :transport (:type transport))
          (should-be-nil (:escort-destroyer-id transport)))))

    (it "dead transport sets paired destroyer to seeking"
      ;; Transport at [0,0] paired with destroyer at [2,0]. Enemy sub kills transport.
      (set-test-world! (build-test-map ["T#Ds"]))
      (set-test-unit (test-utils/game-map-atom) "T" :hits 1
                     :escort-destroyer-id 77 :army-count 0 :awake-armies 0)
      (update-test-world! assoc-in [2 0 :contents]
             {:type :destroyer :owner :player :mode :moving :hits 3
              :destroyer-id 77 :escort-transport-id 42 :escort-mode :escorting})
      (set-test-unit (test-utils/game-map-atom) "s" :hits 2)
      ;; Transport attacks sub. Roll 0.6 = sub hits transport (3 damage), transport dies.
      (with-redefs [rand (constantly 0.6)]
        (combat/apply-combat-result! (combat/attempt-attack (test-utils/read-test-state :game-map) [0 0] [3 0]))
        (let [destroyer (:contents (get-in (test-utils/read-test-state :game-map) [2 0]))]
          (should= :destroyer (:type destroyer))
          (should= :seeking (:escort-mode destroyer))
          (should-be-nil (:escort-transport-id destroyer))))))

  (context "clear-carrier-group-on-death"
    (it "dead battleship clears carrier's group-battleship-id"
      ;; Battleship at [0,0] in carrier group. Carrier at [2,0]. Enemy sub kills battleship.
      (set-test-world! (build-test-map ["B#Cs"]))
      (set-test-unit (test-utils/game-map-atom) "B" :hits 10
                     :escort-carrier-id 55 :escort-id 88)
      (update-test-world! assoc-in [2 0 :contents]
             {:type :carrier :owner :player :mode :sentry :hits 8
              :carrier-id 55 :group-battleship-id 88
              :fighter-count 0 :awake-fighters 0})
      (set-test-unit (test-utils/game-map-atom) "s" :hits 2)
      ;; Battleship attacks sub. Sub hits battleship 5 times (2 damage each = 10 total), battleship dies.
      (with-redefs [rand (constantly 0.6)]
        (combat/apply-combat-result! (combat/attempt-attack (test-utils/read-test-state :game-map) [0 0] [3 0]))
        (let [carrier (:contents (get-in (test-utils/read-test-state :game-map) [2 0]))]
          (should= :carrier (:type carrier))
          (should-be-nil (:group-battleship-id carrier)))))

    (it "dead submarine removes from carrier's group-submarine-ids"
      ;; Submarine at [0,0] in carrier group. Carrier at [2,0]. Enemy destroyer kills submarine.
      (set-test-world! (build-test-map ["S#Cd"]))
      (set-test-unit (test-utils/game-map-atom) "S" :hits 2
                     :escort-carrier-id 55 :escort-id 77)
      (update-test-world! assoc-in [2 0 :contents]
             {:type :carrier :owner :player :mode :sentry :hits 8
              :carrier-id 55 :group-submarine-ids [77 99]
              :fighter-count 0 :awake-fighters 0})
      (set-test-unit (test-utils/game-map-atom) "d" :hits 3)
      ;; Submarine attacks destroyer. Roll 0.6 = defender hits (1 damage each).
      ;; Sub has 2 hits, takes 1 damage per hit -> 2 rounds to die.
      (with-redefs [rand (constantly 0.6)]
        (combat/apply-combat-result! (combat/attempt-attack (test-utils/read-test-state :game-map) [0 0] [3 0]))
        (let [carrier (:contents (get-in (test-utils/read-test-state :game-map) [2 0]))]
          (should= :carrier (:type carrier))
          (should= [99] (:group-submarine-ids carrier)))))

    (it "dead carrier releases escorts to seeking"
      ;; Carrier at [0,0] with submarine escort at [2,0]. Enemy sub kills carrier.
      (set-test-world! (build-test-map ["C#Ss"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :carrier :owner :player :mode :sentry :hits 8
              :carrier-id 55 :fighter-count 0 :awake-fighters 0})
      (update-test-world! assoc-in [2 0 :contents]
             {:type :submarine :owner :player :mode :moving :hits 2
              :escort-carrier-id 55 :escort-mode :escorting :orbit-angle 0.5})
      (set-test-unit (test-utils/game-map-atom) "s" :hits 2)
      ;; Carrier attacks sub. Roll 0.6 = sub hits carrier (3 damage each). 3 hits => 8-9=-1 dead after 3.
      ;; Actually sub has strength 3, so: 0.6 -> sub hits carrier (3 dmg, 5 left),
      ;; 0.6 -> sub hits carrier (3 dmg, 2 left), 0.6 -> sub hits carrier (3 dmg, -1) carrier dies
      (with-redefs [rand (constantly 0.6)]
        (combat/apply-combat-result! (combat/attempt-attack (test-utils/read-test-state :game-map) [0 0] [3 0]))
        (let [escort (:contents (get-in (test-utils/read-test-state :game-map) [2 0]))]
          (should= :submarine (:type escort))
          (should= :seeking (:escort-mode escort))
          (should-be-nil (:escort-carrier-id escort))
          (should-be-nil (:orbit-angle escort))))))

  (context "auto-produce armies on conquest"
    (it "conquered city starts producing armies"
      (with-redefs [rand (constantly 0.1)]
        (set-test-world! (build-test-map ["aO"]))
        (set-test-computer-map! (test-utils/read-test-state :game-map))
        (update-test-world! assoc-in [0 0 :contents :country-id] 3)
        (computer-core/attempt-conquest-computer [0 0] [1 0])
        ;; City should be computer-owned
        (should= :computer (get-in (test-utils/read-test-state :game-map) [1 0 :city-status]))
        ;; Production should be set to :army
        (let [prod (get (test-utils/read-test-state :production) [1 0])]
          (should-not-be-nil prod)
          (should= :army (:item prod)))))

    (it "conquered city produces armies when no other city in country is producing"
      (with-redefs [rand (constantly 0.1)]
        ;; Build a wider map with room for 20 armies and the conquering army + city
        ;; With coastal-fill system, army count alone doesn't block production;
        ;; only duplicate production in the same country blocks it
        (set-test-world! (build-test-map ["aaaaaaaaaaaaaaaaaaaaO"
                                                "a####################"]))
        (set-test-computer-map! (test-utils/read-test-state :game-map))
        ;; Give all 21 armies the same country-id 3
        (doseq [col (range 20)]
          (update-test-world! assoc-in [col 0 :contents :country-id] 3))
        (update-test-world! assoc-in [0 1 :contents :country-id] 3)
        ;; Army at [0 0] conquers city at [20 0]
        (computer-core/attempt-conquest-computer [0 0] [20 0])
        ;; City should be computer-owned
        (should= :computer (get-in (test-utils/read-test-state :game-map) [20 0 :city-status]))
        ;; Production SHOULD be set (no other city in country 3 producing armies)
        (let [prod (get (test-utils/read-test-state :production) [20 0])]
          (should-not-be-nil prod)
          (should= :army (:item prod)))))

    (it "conquered city does not produce armies when another city in country is already producing"
      (with-redefs [rand (constantly 0.1)]
        (set-test-world! (build-test-map ["a#XO"]))
        (update-test-world! assoc-in [0 0 :contents :country-id] 3)
        ;; Existing computer city at [2 0] in country 3, already producing armies
        (update-test-world! assoc-in [2 0 :country-id] 3)
        (set-test-computer-map! (test-utils/read-test-state :game-map))
        (test-utils/update-test-state! :production assoc [2 0] {:item :army :remaining-rounds 3})
        ;; Army at [0 0] conquers city at [3 0]
        (computer-core/attempt-conquest-computer [0 0] [3 0])
        ;; City should be computer-owned
        (should= :computer (get-in (test-utils/read-test-state :game-map) [3 0 :city-status]))
        ;; Production should NOT be set because another city in country 3 is already producing armies
        (should-be-nil (get (test-utils/read-test-state :production) [3 0]))))))

(run-specs)

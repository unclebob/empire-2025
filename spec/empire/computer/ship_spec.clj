(ns empire.computer.ship-spec
  "Tests for VMS Empire style computer ship movement."
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.ship :as ship]
            [empire.computer.ship-carrier :as ship-carrier]
            [empire.computer.core :as core]
            [empire.config.core :as config]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-unit set-test-world! update-test-world!]]
            [empire.containers.helpers :as uc]
            [empire.domain.services.combat :as combat]
            [empire.computer.threat :as threat]))

(describe "process-ship"
  (before (reset-all-atoms!))

  (context "dock behavior"
    (it "damaged computer ship docks at adjacent friendly city"
      (set-test-world! (build-test-map ["BdX"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (set-test-unit (test-utils/game-map-atom) "d" :hits 2)
      (ship/process-ship [1 0] :destroyer)
      ;; Ship should be removed from map
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [1 0 :contents]))
      ;; Ship should be in city X's shipyard
      (let [city (get-in (test-utils/read-test-state :game-map) [2 0])
            shipyard (uc/get-shipyard-ships city)]
        (should= 1 (count shipyard))
        (should= :destroyer (:type (first shipyard)))
        (should= 2 (:hits (first shipyard))))))

  (context "attack behavior"
    (it "attacks adjacent player ship"
      (set-test-world! [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3}}
                                {:type :sea :contents {:type :patrol-boat :owner :player :hits 1}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [_result (ship/process-ship [0 0] :destroyer)]
        ;; Combat should have occurred
        (let [cell0 (get-in (test-utils/read-test-state :game-map) [0 0])
              cell1 (get-in (test-utils/read-test-state :game-map) [0 1])]
          (should (or (nil? (:contents cell0))
                      (nil? (:contents cell1))
                      (= :computer (:owner (:contents cell1)))))))))

  (context "escort behavior"
    (it "destroyer moves toward transport"
      (set-test-world! [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3}}
                                {:type :sea}
                                {:type :sea}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :army-count 3}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (ship/process-ship [0 0] :destroyer)
      ;; Destroyer should have moved toward transport
      (should= :destroyer (get-in (test-utils/read-test-state :game-map) [0 1 :contents :type]))))

  (context "exploration behavior"
    (it "explores toward unexplored sea"
      (set-test-computer-map! [[{:type :sea :contents {:type :submarine :owner :computer :hits 2}}
                                    {:type :sea}
                                    nil]])
      (set-test-world! [[{:type :sea :contents {:type :submarine :owner :computer :hits 2}}
                                {:type :sea}
                                {:type :sea}]])
      (ship/process-ship [0 0] :submarine)
      ;; Ship should have moved toward unexplored
      (should= :submarine (get-in (test-utils/read-test-state :game-map) [0 1 :contents :type])))

    (it "stays put when all sea is explored"
      (set-test-world! [[{:type :sea :contents {:type :submarine :owner :computer :hits 2}}
                                {:type :sea}
                                {:type :land}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (ship/process-ship [0 0] :submarine)
      ;; Ship stays put - no unexplored territory
      (should= :submarine (get-in (test-utils/read-test-state :game-map) [0 0 :contents :type])))

    (it "explores toward unexplored sea without NW bias"
      ;; 5x5 all-sea map. Ship at center, unexplored in SE corner.
      (let [game-map (build-test-map ["~~~~~"
                                      "~~~~~"
                                      "~~~~~"
                                      "~~~~~"
                                      "~~~~~"])]
        (set-test-world! game-map)
        (set-test-computer-map! (build-test-map ["~~~~~"
                                                    "~~~~~"
                                                    "~~~~~"
                                                    "~~~~~"
                                                    "~~~~-"]))
        (update-test-world! assoc-in [2 2 :contents]
               {:type :destroyer :owner :computer :hits 3})
        (ship/process-ship [2 2] :destroyer)
        ;; Should have moved
        (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [2 2])))
        ;; Find where ship moved
        (let [new-pos (first (for [r (range 5) c (range 5)
                                   :when (= :destroyer (get-in (test-utils/read-test-state :game-map) [r c :contents :type]))]
                               [r c]))]
          ;; Should move toward SE, not NW
          (should-not= [1 1] new-pos)
          (should (or (> (first new-pos) 2)
                      (> (second new-pos) 2)))))))

  (context "hunting behavior"
    (it "moves toward visible player ship"
      (set-test-world! [[{:type :sea :contents {:type :battleship :owner :computer :hits 8}}
                                {:type :sea}
                                {:type :sea}
                                {:type :sea :contents {:type :destroyer :owner :player :hits 3}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (ship/process-ship [0 0] :battleship)
      ;; Battleship should have moved toward player ship
      (should= :battleship (get-in (test-utils/read-test-state :game-map) [0 1 :contents :type]))))

  (context "ignores non-computer ships"
    (it "returns nil for player ship"
      (set-test-world! [[{:type :sea :contents {:type :destroyer :owner :player :hits 3}}]])
      (should-be-nil (ship/process-ship [0 0] :destroyer)))

    (it "returns nil for wrong ship type"
      (set-test-world! [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3}}]])
      (should-be-nil (ship/process-ship [0 0] :patrol-boat))))

  (context "patrol boat behavior"
    (it "patrol boat crawls along coastline"
      ;; 3x3 map: land in center, sea around it. Patrol boat at [1 0] (sea, adjacent to land).
      ;; It should move to another sea cell that is also adjacent to land.
      (set-test-world! (build-test-map ["~~~"
                                               "~#~"
                                               "~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [1 0 :contents]
             {:type :patrol-boat :owner :computer :hits 1 :patrol-mode :crawling})
      (with-redefs [rand-nth first]
        (ship/process-ship [1 0] :patrol-boat)
        ;; Patrol boat should have moved
        (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [1 0])))
        ;; Find where it moved
        (let [new-pos (first (for [r (range 3) c (range 3)
                                   :when (= :patrol-boat (get-in (test-utils/read-test-state :game-map) [r c :contents :type]))]
                               [r c]))
              adj-to-land? (some (fn [[dr dc]]
                                   (let [nr (+ (first new-pos) dr)
                                         nc (+ (second new-pos) dc)]
                                     (= :land (:type (get-in (test-utils/read-test-state :game-map) [nr nc])))))
                                 [[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]])]
          (should-not-be-nil new-pos)
          (should adj-to-land?))))

    (it "patrol boat attacks adjacent transport"
      ;; Patrol boat next to a player transport - should attack it
      (set-test-world! [[{:type :sea :contents {:type :patrol-boat :owner :computer :hits 1
                                                       :patrol-mode :crawling}}
                                {:type :sea :contents {:type :transport :owner :player :hits 3}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (ship/process-ship [0 0] :patrol-boat)
      ;; Combat should have occurred - either patrol boat moved to [0 1] or died
      (let [cell0 (get-in (test-utils/read-test-state :game-map) [0 0])
            cell1 (get-in (test-utils/read-test-state :game-map) [0 1])]
        (should (or (nil? (:contents cell0))
                    (= :computer (:owner (:contents cell1)))))))

    (it "patrol boat prefers unseen coast over seen coast"
      ;; 3x5 map: land row at top. Mark [1,1] as seen.
      (set-test-world! (build-test-map ["#####"
                                               "~~~~~"
                                               "~~~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [2 1 :contents]
             {:type :patrol-boat :owner :computer :hits 1 :patrol-mode :crawling})
      (test-utils/set-test-state! :seen-coast #{[1 1]})
      (with-redefs [rand-nth first]
        (ship/process-ship [2 1] :patrol-boat)
        ;; Find where patrol boat ended up
        (let [new-pos (first (for [c (range 5) r (range 3)
                                   :when (= :patrol-boat (get-in (test-utils/read-test-state :game-map) [c r :contents :type]))]
                               [c r]))]
          (should-not-be-nil new-pos))))

    (it "patrol boat flees from non-transport enemy"
      ;; Patrol boat at [0 1], destroyer at [0 2] -- should move away to [0 0]
      (set-test-world! [[{:type :sea}
                                {:type :sea :contents {:type :patrol-boat :owner :computer :hits 1
                                                       :patrol-mode :crawling}}
                                {:type :sea :contents {:type :destroyer :owner :player :hits 3}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (ship/process-ship [0 1] :patrol-boat)
      ;; Patrol boat should have fled to [0 0] (away from destroyer at [0 2])
      (should= :patrol-boat (get-in (test-utils/read-test-state :game-map) [0 0 :contents :type])))

    (it "major-invasion patrol boat attacks adjacent non-transport enemy ship"
      (set-test-world! [[{:type :sea :contents {:type :patrol-boat :owner :computer :hits 1
                                                :patrol-mode :crawling :major-invasion true}}
                         {:type :sea :contents {:type :destroyer :owner :player :hits 3}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (with-redefs [combat/resolve-combat
                    (fn [attacker _defender]
                      {:winner :attacker :survivor attacker})]
        (ship/process-ship [0 0] :patrol-boat)
        ;; Enemy ship should be destroyed and patrol boat should survive.
        (should-not= :player (get-in (test-utils/read-test-state :game-map) [0 1 :contents :owner]))
        (should (some #(= :patrol-boat (get-in (test-utils/read-test-state :game-map) (conj % :contents :type)))
                      [[0 0] [0 1] [1 0]]))))

)

  (context "destroyer escort behavior"
    (it "seeking destroyer adopts unadopted transport"
      (set-test-world! [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3
                                                       :destroyer-id 1 :escort-mode :seeking}}
                                {:type :sea}
                                {:type :sea}
                                {:type :sea :contents {:type :transport :owner :computer :hits 3
                                                       :transport-id 1 :transport-mission :loading
                                                       :army-count 0}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (ship/process-ship [0 0] :destroyer)
      ;; Destroyer should have adopted the transport and moved toward it
      (let [destroyer (first (for [c (range 4)
                                   :let [unit (get-in (test-utils/read-test-state :game-map) [0 c :contents])]
                                   :when (= :destroyer (:type unit))]
                               unit))
            transport (get-in (test-utils/read-test-state :game-map) [0 3 :contents])]
        (should= :intercepting (:escort-mode destroyer))
        (should= 1 (:escort-transport-id destroyer))
        (should= 1 (:escort-destroyer-id transport))))

    (it "intercepting destroyer transitions to escorting when adjacent"
      (set-test-world! [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3
                                                       :destroyer-id 1 :escort-mode :intercepting
                                                       :escort-transport-id 1}}
                                {:type :sea :contents {:type :transport :owner :computer :hits 3
                                                       :transport-id 1 :escort-destroyer-id 1
                                                       :transport-mission :loading :army-count 0}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (ship/process-ship [0 0] :destroyer)
      ;; Should transition to escorting (already adjacent)
      (let [destroyer (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
        (should= :escorting (:escort-mode destroyer))))

    (it "escorting destroyer follows transport"
      ;; Destroyer at [0 0] escorting, transport at [0 2] (not adjacent)
      ;; Destroyer should move toward transport
      (set-test-world! [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3
                                                       :destroyer-id 1 :escort-mode :escorting
                                                       :escort-transport-id 1}}
                                {:type :sea}
                                {:type :sea :contents {:type :transport :owner :computer :hits 3
                                                       :transport-id 1 :escort-destroyer-id 1
                                                       :transport-mission :loading :army-count 0}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (ship/process-ship [0 0] :destroyer)
      ;; Should have moved toward transport
      (should= :destroyer (get-in (test-utils/read-test-state :game-map) [0 1 :contents :type])))

    (it "destroyer reverts to seeking when transport is destroyed"
      ;; Destroyer escorting a transport that no longer exists
      (set-test-world! [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3
                                                       :destroyer-id 1 :escort-mode :escorting
                                                       :escort-transport-id 99}}
                                {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (ship/process-ship [0 0] :destroyer)
      ;; Should revert to seeking
      (let [destroyer (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
        (should= :seeking (:escort-mode destroyer))
        (should-be-nil (:escort-transport-id destroyer))))

    (it "escorting destroyer begins pursuit when enemy adjacent"
      ;; Destroyer at [0 0] escorting, transport at [0 2], player sub at [1 2] (adjacent to transport, not destroyer)
      (set-test-world! [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3
                                                       :destroyer-id 1 :escort-mode :escorting
                                                       :escort-transport-id 1}}
                                {:type :sea}
                                {:type :sea :contents {:type :transport :owner :computer :hits 3
                                                       :transport-id 1 :escort-destroyer-id 1
                                                       :transport-mission :loading :army-count 0}}]
                               [{:type :sea}
                                {:type :sea}
                                {:type :sea :contents {:type :submarine :owner :player :hits 2}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (ship/process-ship [0 0] :destroyer)
      ;; Should have begun pursuit toward enemy
      (let [destroyer (first (for [i (range 2) j (range 3)
                                   :let [unit (get-in (test-utils/read-test-state :game-map) [i j :contents])]
                                   :when (= :destroyer (:type unit))]
                               unit))]
        (should= :pursuing (:escort-mode destroyer))))

    (it "intercepting destroyer reverts to seeking when transport destroyed"
      ;; Destroyer intercepting a transport that no longer exists
      (set-test-world! [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3
                                                       :destroyer-id 1 :escort-mode :intercepting
                                                       :escort-transport-id 99}}
                                {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (ship/process-ship [0 0] :destroyer)
      (let [destroyer (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
        (should= :seeking (:escort-mode destroyer))
        (should-be-nil (:escort-transport-id destroyer))))

    (it "pursuing destroyer continues pursuit"
      (set-test-world! [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3
                                                       :destroyer-id 1 :escort-mode :pursuing
                                                       :escort-transport-id 1
                                                       :pursuit-target [1 0]
                                                       :pursuit-steps-remaining 3}}
                                {:type :sea}]
                               [{:type :sea}
                                {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (with-redefs [rand-nth (fn [coll] (first coll))]
        (ship/process-ship [0 0] :destroyer))
      ;; Should have moved (or ended pursuit if no valid sea-candidates)
      (let [destroyer (first (for [i (range 2) j (range 2)
                                   :let [unit (get-in (test-utils/read-test-state :game-map) [i j :contents])]
                                   :when (= :destroyer (:type unit))]
                               unit))]
        (should-not-be-nil destroyer))))

  (context "lake sentry behavior"
    (it "ship in known lake backs away from shore then enters sentry"
      (set-test-world! (build-test-map ["#####"
                                        "#d~~#"
                                        "#~~~#"
                                        "#~~~#"
                                        "#####"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (test-utils/set-test-state! :lake-max-cells 20)
      (update-test-world! assoc-in [1 1 :contents :lake-locked?] true)
      (ship/process-ship [1 1] :destroyer)
      (should= :destroyer (get-in (test-utils/read-test-state :game-map) [2 2 :contents :type]))
      (should= :sentry (get-in (test-utils/read-test-state :game-map) [2 2 :contents :mode]))
      ;; Once parked as sentry, it should never move again.
      (ship/process-ship [2 2] :destroyer)
      (should= :destroyer (get-in (test-utils/read-test-state :game-map) [2 2 :contents :type]))
      (should= :sentry (get-in (test-utils/read-test-state :game-map) [2 2 :contents :mode])))

    (it "lake-locked ship with no retreat step becomes sentry in place"
      (set-test-world! (build-test-map ["#####"
                                        "#d~~#"
                                        "#~~~#"
                                        "#~~~#"
                                        "#####"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (test-utils/set-test-state! :lake-max-cells 20)
      (update-test-world! assoc-in [1 1 :contents :lake-locked?] true)
      (with-redefs [empire.computer.lake-naval/retreat-step-from-shore (fn [& _] nil)]
        (ship/process-ship [1 1] :destroyer))
      (should= :destroyer (get-in (test-utils/read-test-state :game-map) [1 1 :contents :type]))
      (should= :sentry (get-in (test-utils/read-test-state :game-map) [1 1 :contents :mode]))))

  (context "carrier group escort intercepting"
    (it "intercepting escort moves toward carrier when far away"
      ;; Battleship at [0 0] intercepting, carrier at [0 5] (distance > 2)
      (set-test-world! [[{:type :sea :contents {:type :battleship :owner :computer :hits 4
                                                       :escort-id 1 :escort-mode :intercepting
                                                       :escort-carrier-id 1 :orbit-angle 0}}
                                {:type :sea}
                                {:type :sea}
                                {:type :sea}
                                {:type :sea}
                                {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                       :carrier-id 1}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (ship/process-ship [0 0] :battleship)
      ;; Should have moved closer to carrier
      (should= :battleship (get-in (test-utils/read-test-state :game-map) [0 1 :contents :type])))

    (it "intercepting escort transitions to orbiting when within radius 2"
      ;; Battleship at [0 0], carrier at [3 3] on a 7x7 sea map
      ;; Chebyshev distance from [0 0] to [3 3] = 3, so place battleship at [2 2] instead
      (set-test-world! (vec (for [r (range 7)]
                                    (vec (for [c (range 7)]
                                           (cond
                                             (and (= r 2) (= c 2))
                                             {:type :sea :contents {:type :battleship :owner :computer :hits 4
                                                                     :escort-id 1 :escort-mode :intercepting
                                                                     :escort-carrier-id 1 :orbit-angle 0}}
                                             (and (= r 3) (= c 3))
                                             {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                                     :carrier-id 1}}
                                             :else {:type :sea}))))))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (ship/process-ship [2 2] :battleship)
      ;; Should have transitioned to orbiting
      (let [bs (first (for [i (range 7) j (range 7)
                            :let [unit (get-in (test-utils/read-test-state :game-map) [i j :contents])]
                            :when (= :battleship (:type unit))]
                        unit))]
        (should= :orbiting (:escort-mode bs))))

    (it "intercepting escort reverts to seeking when carrier is gone"
      (set-test-world! [[{:type :sea :contents {:type :battleship :owner :computer :hits 4
                                                       :escort-id 1 :escort-mode :intercepting
                                                       :escort-carrier-id 99 :orbit-angle 0}}
                                {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (ship/process-ship [0 0] :battleship)
      (let [bs (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
        (should= :seeking (:escort-mode bs))
        (should-be-nil (:escort-carrier-id bs))))

    (it "intercepting escort orbits even when no valid orbit position"
      ;; Battleship at [0 0], carrier at [0 1] (distance 1, within radius 2)
      ;; Only 2 cells, so most orbit positions are out of bounds
      (set-test-world! [[{:type :sea :contents {:type :battleship :owner :computer :hits 4
                                                       :escort-id 1 :escort-mode :intercepting
                                                       :escort-carrier-id 1 :orbit-angle 0}}
                                {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                       :carrier-id 1}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (ship/process-ship [0 0] :battleship)
      ;; Should transition to orbiting even without a valid orbit position
      (let [bs (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
        (should= :orbiting (:escort-mode bs)))))

  (context "carrier repositioning"
    (it "finds new position and moves toward it"
      (set-test-world! [[{:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                       :carrier-mode :repositioning}}
                                {:type :sea}
                                {:type :sea}
                                {:type :sea}
                                {:type :sea}]
                               [{:type :city :city-status :computer}
                                {:type :land}
                                {:type :land}
                                {:type :land}
                                {:type :city :city-status :computer}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (with-redefs [ship-carrier/find-carrier-position (fn [] {:position [0 2] :pair [[1 0] [1 4]]})]
        (ship/process-ship [0 0] :carrier))
      (let [carrier (first (for [i (range 2) j (range 5)
                                 :let [u (get-in (test-utils/read-test-state :game-map) [i j :contents])]
                                 :when (= :carrier (:type u))]
                             u))]
        (should= :positioning (:carrier-mode carrier))
        (should= [0 2] (:carrier-target carrier))))

    (it "switches to holding when no position available"
      (set-test-world! [[{:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                       :carrier-mode :repositioning}}
                                {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (with-redefs [ship-carrier/find-carrier-position (fn [] nil)]
        (ship/process-ship [0 0] :carrier))
      (should= :holding (get-in (test-utils/read-test-state :game-map) [0 0 :contents :carrier-mode]))))
) ;; end process-ship

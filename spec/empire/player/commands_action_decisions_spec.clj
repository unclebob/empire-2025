(ns empire.player.commands-action-decisions-spec
  (:require [empire.player.commands-action-decisions :as sut]
            [speclj.core :refer :all]))

(describe "player command action decisions"
  (it "classifies city production and movement context actions"
    (should= {:action :reject-production :item :destroyer}
             (sut/city-production-action {:naval? true :coastal? false :item :destroyer}))
    (should= {:action :set-production :item :army}
             (sut/city-production-action {:naval? false :coastal? false :item :army}))
    (should= :launch-airport-fighter (sut/movement-context-action :airport-fighter))
    (should= :standard-unit-movement (sut/movement-context-action :standard-unit)))

  (it "returns a fighter skip fuel action"
    (should= {:action :skip-and-burn-fuel
              :fuel 24
              :reason "Skipping this round. Fuel: 24"}
             (sut/space-key-action {:type :fighter :fuel 32})))

  (it "returns a reject action for an ineligible coastline unit"
    (should= :reject
             (:action (sut/look-around-action [[{:type :sea}]]
                                              [0 0]
                                              {:type :transport}))))

  (it "returns a conquest click action for an adjacent hostile city"
    (should= {:action :attempt-conquest
              :target [1 1]}
             (sut/click-action [[{:type :land} {:type :land}]
                                [{:type :land} {:type :city :city-status :computer}]]
                               [0 0]
                               [1 1]
                               :normal
                               {:type :army})))

  (it "allows movement targeting a distant hostile unit"
    (should= {:action :set-unit-movement
              :target [2 0]}
             (sut/click-action [[{:type :land}]
                                [{:type :land}]
                                [{:type :land :contents {:type :army :owner :computer}}]]
                               [0 0]
                               [2 0]
                               :normal
                               {:type :army :owner :player})))

  (it "disembarks army aboard with target on distant click"
    (should= {:action :disembark-army-with-target
              :target [1 0]
              :extended-target [2 0]}
             (sut/click-action [[{:type :sea}]
                                [{:type :land}]
                                [{:type :land}]]
                               [0 0]
                               [2 0]
                               :army-aboard
                               {:type :army :owner :player})))

  (it "attacks adjacent hostile unit for army aboard transport click"
    (should= {:action :coastal-army-attack
              :target [1 0]}
             (sut/click-action [[{:type :sea}]
                                [{:type :land
                                  :contents {:type :army :owner :computer}}]]
                               [0 0]
                               [1 0]
                               :army-aboard
                               {:type :army :owner :player})))

  (it "rejects movement targeting a distant friendly unit"
    (should= {:action :reject
              :message "Something's in the way."}
             (sut/click-action [[{:type :land}]
                                [{:type :land}]
                                [{:type :land :contents {:type :army :owner :player}}]]
                               [0 0]
                               [2 0]
                               :normal
                               {:type :army :owner :player})))

  (context "unload-key-action"
    (it "returns wake-armies-on-transport when transport has armies"
      (should= {:action :wake-armies-on-transport}
               (sut/unload-key-action {:type :transport :army-count 2}
                                      {:type :sea}
                                      nil)))

    (it "returns wake-fighters-on-carrier when carrier has fighters"
      (should= {:action :wake-fighters-on-carrier}
               (sut/unload-key-action {:type :carrier :fighter-count 1}
                                      {:type :sea}
                                      nil)))

    (it "returns wake-fighters-on-airport when city has fighters"
      (should= {:action :wake-fighters-on-airport}
               (sut/unload-key-action nil
                                      {:type :city :fighter-count 2}
                                      nil)))

    (it "returns nil when city has no fighters"
      (should-be-nil (sut/unload-key-action nil
                                            {:type :city :fighter-count 0}
                                            nil)))

    (it "returns nil when no applicable condition"
      (should-be-nil (sut/unload-key-action nil {:type :land} nil))))

  (context "sentry-key-action"
    (it "returns sleep-armies-on-transport for army aboard transport"
      (should= {:action :sleep-armies-on-transport}
               (sut/sentry-key-action {:type :sea}
                                      {:type :army :mode :sentry :aboard-transport true})))

    (it "returns sleep-fighters-on-carrier for carrier fighter"
      (should= {:action :sleep-fighters-on-carrier}
               (sut/sentry-key-action {:type :sea}
                                      {:type :fighter :mode :sentry :from-carrier true})))

    (it "returns sleep-fighters-on-airport for airport fighter"
      (should= {:action :sleep-fighters-on-airport}
               (sut/sentry-key-action {:type :city}
                                      {:type :fighter :mode :awake :from-airport true})))

    (it "returns set-sentry-mode for a unit on non-city land"
      (should= {:action :set-sentry-mode}
               (sut/sentry-key-action {:type :land}
                                      {:type :army :mode :awake})))

    (it "returns nil for a regular unit at a city"
      (should-be-nil (sut/sentry-key-action {:type :city}
                                            {:type :army :mode :awake})))))

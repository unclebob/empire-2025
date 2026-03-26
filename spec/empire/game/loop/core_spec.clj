(ns empire.game.loop.core-spec
  (:require [empire.game.loop.core :as game-loop]
            [empire.game.loop.monitor :as monitor]
            [empire.state.api :as sa]
            [empire.test.utils :refer [reset-all-atoms!]]
            [empire.test.utils :as test-utils]
            [speclj.core :refer :all]))

(describe "start-new-round-timed"
  (before (reset-all-atoms!))

  (it "returns a vector of phase name/ms pairs"
    (let [calls (atom [])]
      (with-redefs [empire.state.api/update-state! (fn [k f] (swap! calls conj [:update k]))
                    empire.state.api/write-state! (fn [k v] (swap! calls conj [:write k]))
                    empire.state.api/read-state (fn [k] (case k
                                                          :handicap-rounds-remaining 0
                                                          :game-over-check-enabled true
                                                          :player-map {}
                                                          nil))
                    empire.state.api/current-world (fn [] [[]])
                    empire.game-mechanics.movement.pathfinding/clear-path-cache (fn [])
                    empire.game-mechanics.movement.pathfinding-bfs/clear-bfs-caches (fn [])
                    empire.computer.land-objectives/clear-continent-cache! (fn [])
                    empire.game-mechanics.unit-stamping/backfill-missing-computer-unit-ids! (fn [])
                    empire.game.loop.round-setup/move-satellites (fn [])
                    empire.game.loop.round-setup/consume-sentry-fighter-fuel (fn [])
                    empire.game.loop.round-setup/wake-sentries-seeing-enemy (fn [])
                    empire.game.loop.round-setup/remove-dead-units (fn [])
                    empire.game.loop.round-setup/mark-lake-locked-ships (fn [])
                    empire.game.loop.round-setup/evacuate-lake-patrol-boats (fn [])
                    empire.player.production/update-production (fn [])
                    empire.game.loop.round-setup/repair-damaged-ships (fn [])
                    empire.game.loop.round-setup/reset-steps-remaining (fn [])
                    empire.game.loop.round-setup/wake-airport-fighters (fn [])
                    empire.computer.threat-response-impl/on-round-start! (fn [])
                    empire.game-mechanics.debug.logging/begin-computer-unit-log-round! (fn [])
                    empire.game.loop.core/build-player-items (fn [] [])
                    empire.game.loop.core/build-computer-items (fn [] [])
                    empire.game.loop.control-decisions/round-start-state (fn [_] {:player-items [] :computer-items [] :waiting-for-input false :attention-message nil :cells-needing-attention []})
                    empire.computer.production/rebuild-country-stats! (fn [])
                    empire.computer.army/assign-city-attacks (fn [])
                    empire.computer.army/assign-transport-staging (fn [])
                    empire.game.production-status/format-production-status (fn [_ _] "")
                    empire.game-mechanics.debug.integrity/check-world-integrity! (fn [])]
        (let [phases (game-loop/start-new-round-timed)]
          (should (vector? phases))
          (should (pos? (count phases)))
          (should (every? (fn [[k v]] (and (keyword? k) (number? v))) phases))))))

  (it "includes expected phase names"
    (with-redefs [empire.state.api/update-state! (fn [k f])
                  empire.state.api/write-state! (fn [k v])
                  empire.state.api/read-state (fn [k] (case k
                                                        :handicap-rounds-remaining 0
                                                        :game-over-check-enabled true
                                                        :player-map {}
                                                        nil))
                  empire.state.api/current-world (fn [] [[]])
                  empire.game-mechanics.movement.pathfinding/clear-path-cache (fn [])
                  empire.game-mechanics.movement.pathfinding-bfs/clear-bfs-caches (fn [])
                  empire.computer.land-objectives/clear-continent-cache! (fn [])
                  empire.game-mechanics.unit-stamping/backfill-missing-computer-unit-ids! (fn [])
                  empire.game.loop.round-setup/move-satellites (fn [])
                  empire.game.loop.round-setup/consume-sentry-fighter-fuel (fn [])
                  empire.game.loop.round-setup/wake-sentries-seeing-enemy (fn [])
                  empire.game.loop.round-setup/remove-dead-units (fn [])
                  empire.game.loop.round-setup/mark-lake-locked-ships (fn [])
                  empire.game.loop.round-setup/evacuate-lake-patrol-boats (fn [])
                  empire.player.production/update-production (fn [])
                  empire.game.loop.round-setup/repair-damaged-ships (fn [])
                  empire.game.loop.round-setup/reset-steps-remaining (fn [])
                  empire.game.loop.round-setup/wake-airport-fighters (fn [])
                  empire.computer.threat-response-impl/on-round-start! (fn [])
                  empire.game-mechanics.debug.logging/begin-computer-unit-log-round! (fn [])
                  empire.game.loop.core/build-player-items (fn [] [])
                  empire.game.loop.core/build-computer-items (fn [] [])
                  empire.game.loop.control-decisions/round-start-state (fn [_] {:player-items [] :computer-items [] :waiting-for-input false :attention-message nil :cells-needing-attention []})
                  empire.computer.production/rebuild-country-stats! (fn [])
                  empire.computer.army/assign-city-attacks (fn [])
                  empire.computer.army/assign-transport-staging (fn [])
                  empire.game.production-status/format-production-status (fn [_ _] "")
                  empire.game-mechanics.debug.integrity/check-world-integrity! (fn [])]
      (let [phases (game-loop/start-new-round-timed)
            phase-names (set (map first phases))]
        (should-contain :clear-path-cache phase-names)
        (should-contain :move-satellites phase-names)
        (should-contain :threat-response phase-names)
        (should-contain :update-production phase-names)
        (should-contain :build-round-state phase-names)
        (should-contain :integrity-check phase-names)))))

(describe "*monitor-phase-sink*"
  (before (reset-all-atoms!))

  (it "captures timed phase data when bound during advance-game"
    (let [sink (atom [])
          round-started (atom false)]
      (sa/write-state! :round-number 1)
      (with-redefs [empire.state.api/update-state! (fn [k f] (when (= k :round-number) (sa/write-state! k (f (sa/read-state k)))))
                    empire.game.loop.core/start-new-round (fn [] (reset! round-started true))
                    empire.game.loop.core/start-new-round-timed (fn [] (reset! round-started true) [[:test-phase 42]])]
        (binding [monitor/*phase-sink* sink]
          (#'game-loop/apply-advance-game-action! :new-round))
        (should= [[:test-phase 42]] @sink)
        (should @round-started))))

  (it "uses normal start-new-round when not bound"
    (let [round-started (atom false)]
      (sa/write-state! :round-number 1)
      (with-redefs [empire.game.loop.core/start-new-round (fn [] (reset! round-started true))
                    empire.game.loop.core/start-new-round-timed (fn [] (throw (ex-info "should not be called" {})))]
        (#'game-loop/apply-advance-game-action! :new-round)
        (should @round-started)))))

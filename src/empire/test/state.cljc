(ns empire.test.state
  (:require [empire.state.api :as sa]
            [empire.state.world :as world]
            [empire.state.computer :as computer-state]
            [empire.state.player :as player-state]
            [empire.state.ui :as ui-state]
            [empire.computer.land-objectives :as land-objectives]
            [empire.game-mechanics.movement.pathfinding :as pathfinding]
            [empire.game-mechanics.movement.pathfinding-bfs :as pathfinding-bfs]
            [empire.game-mechanics.visibility :as visibility]
            [empire.computer.fighter.movement-impl :as fighter-movement-impl]
            [empire.computer.fighter.flight-decisions :as flight-decisions]
            [empire.computer.fighter.exploration :as fighter-exploration]
            [empire.computer.production.stats :as production-stats]
            [empire.computer.production.decisions :as production-decisions]
            [empire.computer.ship.carrier :as carrier]
            [empire.computer.early-game.theater :as theater]
            [empire.computer.shared.transport-query :as transport-query]
            [empire.notifications :as notifications]))

(defn read-test-state
  [k]
  (sa/read-state k))

(defn set-test-state!
  [k v]
  (sa/write-state! k v))

(defn update-test-state!
  [k f & args]
  (set-test-state! k (apply f (read-test-state k) args)))

(defn read-test-world
  []
  (sa/current-world))

(defn game-map-atom
  []
  :game-map)

(defn player-map-atom
  []
  :player-map)

(defn computer-map-atom
  []
  :computer-map)

(defn set-test-world!
  [world]
  (sa/write-state! :game-map world))

(defn set-test-cell!
  [pos cell]
  (sa/update-world! assoc-in pos cell))

(defn set-test-contents!
  [pos unit]
  (sa/update-world! assoc-in (conj pos :contents) unit))

(defn clear-test-contents!
  [pos]
  (sa/update-world! assoc-in (conj pos :contents) nil))

(defn update-test-world!
  [f & args]
  (apply sa/update-world! f args))

(defn set-test-player-map!
  [player-map]
  (set-test-state! :player-map player-map))

(defn update-test-player-map!
  [f & args]
  (apply update-test-state! :player-map f args))

(defn set-test-computer-map!
  [computer-map]
  (set-test-state! :computer-map computer-map))

(defn update-test-computer-map!
  [f & args]
  (apply update-test-state! :computer-map f args))

(defn mission-ctx []
  {:current-world read-test-world
   :update-game-map! update-test-world!
   :load-major-invasion-state #(read-test-state :major-invasion-state)})

(defn set-major-invasion-state!
  [state]
  (set-test-state! :major-invasion-state state))

(defn set-kamikazee-fighter!
  [pos attrs]
  (update-test-world! update-in (conj pos :contents)
                      merge
                      {:type :fighter
                       :owner :computer
                       :hits 1
                       :major-invasion true
                       :kamikazee true}
                      attrs))

(defn seed-airport-kamikazees!
  [city-pos total awake]
  (update-test-world! assoc-in (conj city-pos :fighter-count) total)
  (update-test-world! assoc-in (conj city-pos :awake-fighters) awake)
  (update-test-world! assoc-in (conj city-pos :kamikazee-fighter-count) total)
  (update-test-world! assoc-in (conj city-pos :awake-kamikazee-fighters) awake))

(defn reset-all-atoms! []
  (reset! world/state world/defaults)
  (reset! computer-state/state computer-state/defaults)
  (reset! player-state/state player-state/defaults)
  (reset! ui-state/state ui-state/defaults)
  (sa/write-state! :game-over-check-enabled false)
  (sa/write-state! :integrity-check-enabled false)
  (pathfinding/clear-path-cache)
  (pathfinding-bfs/clear-bfs-caches)
  (land-objectives/clear-continent-cache!)
  (fighter-movement-impl/clear-refueling-cache!)
  (flight-decisions/clear-active-targets-cache!)
  (fighter-exploration/clear-unexplored-distance-cache!)
  (production-stats/clear-asset-cache!)
  (production-decisions/clear-produced-transport-cache!)
  (carrier/clear-carrier-caches!)
  (theater/clear-theater-caches!)
  (transport-query/clear-loading-transport-cache!)
  (visibility/drain-detections!)
  (notifications/reset-alert-port!))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T15:07:08.213438-05:00", :module-hash "-463890779", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "-769166858"} {:id "defn/read-test-state", :kind "defn", :line 21, :end-line nil, :hash "589015934"} {:id "defn/set-test-state!", :kind "defn", :line 25, :end-line nil, :hash "870665925"} {:id "defn/update-test-state!", :kind "defn", :line 29, :end-line nil, :hash "-1050730755"} {:id "defn/read-test-world", :kind "defn", :line 33, :end-line nil, :hash "-1610409745"} {:id "defn/game-map-atom", :kind "defn", :line 37, :end-line nil, :hash "-1480579774"} {:id "defn/player-map-atom", :kind "defn", :line 41, :end-line nil, :hash "176409169"} {:id "defn/computer-map-atom", :kind "defn", :line 45, :end-line nil, :hash "-2115588914"} {:id "defn/set-test-world!", :kind "defn", :line 49, :end-line nil, :hash "-1130383465"} {:id "defn/set-test-cell!", :kind "defn", :line 53, :end-line nil, :hash "-1048778534"} {:id "defn/set-test-contents!", :kind "defn", :line 57, :end-line nil, :hash "-1933207427"} {:id "defn/clear-test-contents!", :kind "defn", :line 61, :end-line nil, :hash "-1032794746"} {:id "defn/update-test-world!", :kind "defn", :line 65, :end-line nil, :hash "1015896768"} {:id "defn/set-test-player-map!", :kind "defn", :line 69, :end-line nil, :hash "1917677314"} {:id "defn/update-test-player-map!", :kind "defn", :line 73, :end-line nil, :hash "2047748625"} {:id "defn/set-test-computer-map!", :kind "defn", :line 77, :end-line nil, :hash "-1539947896"} {:id "defn/update-test-computer-map!", :kind "defn", :line 81, :end-line nil, :hash "1539668718"} {:id "defn/mission-ctx", :kind "defn", :line 85, :end-line nil, :hash "-2036099439"} {:id "defn/set-major-invasion-state!", :kind "defn", :line 90, :end-line nil, :hash "-1341449945"} {:id "defn/set-kamikazee-fighter!", :kind "defn", :line 94, :end-line nil, :hash "86384771"} {:id "defn/seed-airport-kamikazees!", :kind "defn", :line 105, :end-line nil, :hash "-2138266696"} {:id "defn/reset-all-atoms!", :kind "defn", :line 112, :end-line nil, :hash "733756113"}]}
;; clj-mutate-manifest-end

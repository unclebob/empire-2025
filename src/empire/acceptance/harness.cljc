;; mutation-tested: no
(ns empire.acceptance.harness
  "Acceptance harness adapter used by generated acceptance specs.
   Keeps scenario execution paths behind a stable API."
  (:require [empire.atoms :as atoms]
            [empire.computer.fighter :as computer-fighter]
            [empire.computer.production :as computer-production]
            [empire.computer.ship :as computer-ship]
            [empire.computer.transport :as computer-transport]
            [empire.game-loop :as game-loop]
            [empire.game-loop.item-processing :as item-processing]
            [empire.movement.visibility :as visibility]
            [empire.test-utils :as test-utils]
            [empire.ui.quil.input :as quil-input]
            [empire.ui.util.input.dispatch :as input]
            [quil.core :as q]))

(defn read-state
  [k]
  (case k
    :round-number @atoms/round-number
    :waiting-for-input @atoms/waiting-for-input
    :paused @atoms/paused
    :player-items @atoms/player-items
    :computer-items @atoms/computer-items
    :cells-needing-attention @atoms/cells-needing-attention
    :game-map @atoms/game-map
    :player-map @atoms/player-map
    :computer-map @atoms/computer-map
    :last-key @atoms/last-key
    :production @atoms/production
    :attention-message @atoms/attention-message
    :turn-message @atoms/turn-message
    :error-message @atoms/error-message
    :map-to-display @atoms/map-to-display
    :load-menu-open @atoms/load-menu-open
    :destination @atoms/destination))

(defn set-last-key!
  [v]
  (reset! atoms/last-key v))

(defn build-test-map
  [rows]
  (test-utils/build-test-map rows))

(defn set-test-world!
  [m]
  (test-utils/set-test-world! m))

(defn update-test-world!
  [f & args]
  (apply test-utils/update-test-world! f args))

(defn reset-all-atoms!
  []
  (test-utils/reset-all-atoms!))

(defn message-matches?
  [message template]
  (test-utils/message-matches? message template))

(defn make-initial-test-map
  [rows cols fill]
  (test-utils/make-initial-test-map rows cols fill))

(defn visibility-mask
  [m]
  (test-utils/visibility-mask m))

(defn territory-mask
  [m]
  (test-utils/territory-mask m))

(defn build-territory-expected
  [rows]
  (test-utils/build-territory-expected rows))

(defn set-unit!
  [unit-spec & kvs]
  (apply test-utils/set-test-unit atoms/game-map unit-spec kvs))

(defn get-unit
  [unit-spec & {:as filters}]
  (apply test-utils/get-test-unit (read-state :game-map) unit-spec (mapcat identity filters)))

(defn get-city
  [city-spec]
  (test-utils/get-test-city (read-state :game-map) city-spec))

(defn get-cell
  [cell-spec]
  (test-utils/get-test-cell (read-state :game-map) cell-spec))

(defn cell-at
  ([coords]
   (get-in (read-state :game-map) coords))
  ([map-key coords]
   (get-in (read-state map-key) coords)))

(defn shipyard-at
  [coords]
  (:shipyard (cell-at coords) []))

(defn count-computer-armies
  []
  (let [gm (read-state :game-map)]
    (count (for [i (range (count gm))
                 j (range (count (first gm)))
                 :let [cell (get-in gm [i j])]
                 :when (and (:contents cell)
                            (= :army (:type (:contents cell)))
                            (= :computer (:owner (:contents cell))))]
             true))))

(defn set-state!
  [k v]
  (case k
    :round-number (reset! atoms/round-number v)
    :waiting-for-input (reset! atoms/waiting-for-input v)
    :paused (reset! atoms/paused v)
    :player-items (reset! atoms/player-items v)
    :computer-items (reset! atoms/computer-items v)
    :cells-needing-attention (reset! atoms/cells-needing-attention v)
    :game-map (reset! atoms/game-map v)
    :player-map (reset! atoms/player-map v)
    :computer-map (reset! atoms/computer-map v)
    :last-key (reset! atoms/last-key v)
    :map-screen-dimensions (reset! atoms/map-screen-dimensions v)
    :production (reset! atoms/production v)
    :destination (reset! atoms/destination v)
    :game-over-check-enabled (reset! atoms/game-over-check-enabled v)
    :pause-requested (reset! atoms/pause-requested v)
    :map-to-display (reset! atoms/map-to-display v)
    :load-menu-open (reset! atoms/load-menu-open v)
    (throw (ex-info (str "Unsupported harness set-state! key: " k) {:key k}))))

(defn update-state!
  [k f & args]
  (case k
    :production (apply swap! atoms/production f args)
    :player-map (apply swap! atoms/player-map f args)
    :computer-map (apply swap! atoms/computer-map f args)
    :player-items (apply swap! atoms/player-items f args)
    :game-map (apply swap! atoms/game-map f args)
    (throw (ex-info (str "Unsupported harness update-state! key: " k) {:key k}))))

(defn handle-key!
  [k]
  (input/handle-key k))

(defn key-down!
  [k]
  (with-redefs [q/mouse-x (constantly 0)
                q/mouse-y (constantly 0)]
    (set-last-key! nil)
    (quil-input/key-down k)))

(defn key-down-at!
  [k mouse-x mouse-y]
  (with-redefs [q/mouse-x (constantly mouse-x)
                q/mouse-y (constantly mouse-y)]
    (set-last-key! nil)
    (quil-input/key-down k)))

(defn start-new-round!
  []
  (game-loop/start-new-round))

(defn advance-game!
  []
  (game-loop/advance-game))

(defn process-player-items-batch!
  []
  (item-processing/process-player-items-batch))

(defn update-player-map!
  []
  (game-loop/update-player-map))

(defn update-cell-visibility!
  [pos owner unit]
  (visibility/update-cell-visibility pos owner unit))

(defn evaluate-computer-production!
  [city-pos]
  (computer-production/rebuild-country-stats!)
  (computer-production/process-computer-city city-pos))

(defn process-computer-transport!
  [pos]
  (computer-transport/process-transport pos))

(defn process-computer-fighter!
  [pos unit]
  (computer-fighter/process-fighter pos unit))

(defn process-computer-ship!
  [pos ship-type]
  (computer-ship/process-ship pos ship-type))

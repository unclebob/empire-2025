;; mutation-tested: no
(ns empire.acceptance.harness
  "Acceptance harness adapter used by generated acceptance specs.
   Keeps scenario execution paths behind a stable API."
  (:require [empire.application.state :as app-state]
            [empire.application.state-access :as sa]
            [empire.computer.fighter :as computer-fighter]
            [empire.computer.production :as computer-production]
            [empire.computer.ship :as computer-ship]
            [empire.computer.transport :as computer-transport]
            [empire.game-loop :as game-loop]
            [empire.game-loop.item-processing :as item-processing]
            [empire.movement.visibility :as visibility]
            [empire.test-utils :as test-utils]
            [empire.ui.quil.input :as quil-input]
            [empire.ui.util.input.dispatch :as input-dispatch]
            [quil.core :as q]))

(def ^:private readable-keys
  #{:round-number
    :waiting-for-input
    :paused
    :player-items
    :computer-items
    :cells-needing-attention
    :game-map
    :player-map
    :computer-map
    :last-key
    :production
    :attention-message
    :turn-message
    :error-message
    :map-to-display
    :load-menu-open
    :destination})

(defn read-state
  [k]
  (when (contains? readable-keys k)
    (if (= k :game-map)
      (sa/current-world)
      (sa/read-state k))))

(defn set-last-key!
  [v]
  (sa/write-state! :last-key v))

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
  (apply test-utils/set-test-unit :game-map unit-spec kvs))

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

(def ^:private writable-keys
  #{:round-number
    :waiting-for-input
    :paused
    :player-items
    :computer-items
    :cells-needing-attention
    :game-map
    :player-map
    :computer-map
    :last-key
    :map-screen-dimensions
    :production
    :destination
    :game-over-check-enabled
    :pause-requested
    :map-to-display
    :load-menu-open})

(defn set-state!
  [k v]
  (if (contains? writable-keys k)
    (if (= k :game-map)
      (app-state/set-world! (sa/state-ctx) v)
      (sa/write-state! k v))
    (throw (ex-info (str "Unsupported harness set-state! key: " k) {:key k}))))

(defn update-state!
  [k f & args]
  (if (contains? #{:production :player-map :computer-map :player-items :game-map} k)
    (if (= k :game-map)
      (apply app-state/update-world! (sa/state-ctx) f args)
      (apply sa/update-state! k f args))
    (throw (ex-info (str "Unsupported harness update-state! key: " k) {:key k}))))

(defn handle-key!
  [k]
  (input-dispatch/handle-key k))

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

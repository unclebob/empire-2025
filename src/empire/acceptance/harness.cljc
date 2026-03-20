;; mutation-tested: no
(ns empire.acceptance.harness
  "Acceptance harness adapter used by generated acceptance specs.
   Keeps scenario execution paths behind a stable API."
  (:require [empire.state.api :as sa]
            [empire.computer.fighter :as computer-fighter]
            [empire.computer.production :as computer-production]
            [empire.computer.ship :as computer-ship]
            [empire.computer.threat-response :as threat-response]
            [empire.computer.transport :as computer-transport]
            [empire.game.loop.core :as game-loop]
            [empire.game.loop.item-processing :as item-processing]
            [empire.game-mechanics.visibility :as visibility]
            [empire.test.utils :as test-utils]
            [empire.ui.util.input.dispatch :as input-dispatch]))

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
      (sa/write-state! :game-map v)
      (sa/write-state! k v))
    (throw (ex-info (str "Unsupported harness set-state! key: " k) {:key k}))))

(defn update-state!
  [k f & args]
  (if (contains? #{:production :player-map :computer-map :player-items :game-map} k)
    (if (= k :game-map)
      (apply sa/update-world! f args)
      (apply sa/update-state! k f args))
    (throw (ex-info (str "Unsupported harness update-state! key: " k) {:key k}))))

(defn handle-key!
  [k]
  (input-dispatch/handle-key k))

(defn key-down!
  [k]
  (set-last-key! nil)
  (input-dispatch/key-down k 0 0))

(defn key-down-at!
  [k mouse-x mouse-y]
  (set-last-key! nil)
  (input-dispatch/key-down k mouse-x mouse-y))

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
  (visibility/update-cell-visibility pos owner unit)
  (doseq [{:keys [pos cell]} (visibility/drain-detections!)]
    (threat-response/handle-detection! pos cell)))

(defn- reveal-computer-unit!
  [pos unit]
  (when (= :computer (:owner unit))
    (update-cell-visibility! pos :computer unit)))

(defn evaluate-computer-production!
  [city-pos]
  (computer-production/rebuild-country-stats!)
  (computer-production/process-computer-city city-pos))

(defn process-computer-transport!
  [pos]
  (when-let [unit (get-in (read-state :game-map) (conj pos :contents))]
    (reveal-computer-unit! pos unit))
  (computer-transport/process-transport pos))

(defn process-computer-fighter!
  [pos unit]
  (reveal-computer-unit! pos unit)
  (computer-fighter/process-fighter pos unit))

(defn process-computer-ship!
  [pos ship-type]
  (when-let [unit (get-in (read-state :game-map) (conj pos :contents))]
    (reveal-computer-unit! pos unit))
  (computer-ship/process-ship pos ship-type))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T11:56:33.656512-05:00", :module-hash "863288378", :forms [{:id "form/0/ns", :kind "ns", :line 2, :end-line 15, :hash "-1804880830"} {:id "def/readable-keys", :kind "def", :line 17, :end-line 34, :hash "-58068059"} {:id "defn/read-state", :kind "defn", :line 36, :end-line 41, :hash "-1124712834"} {:id "defn/set-last-key!", :kind "defn", :line 43, :end-line 45, :hash "-567587308"} {:id "defn/build-test-map", :kind "defn", :line 47, :end-line 49, :hash "375291745"} {:id "defn/set-test-world!", :kind "defn", :line 51, :end-line 53, :hash "-2021757230"} {:id "defn/update-test-world!", :kind "defn", :line 55, :end-line 57, :hash "541232387"} {:id "defn/reset-all-atoms!", :kind "defn", :line 59, :end-line 61, :hash "155223220"} {:id "defn/message-matches?", :kind "defn", :line 63, :end-line 65, :hash "-62899664"} {:id "defn/make-initial-test-map", :kind "defn", :line 67, :end-line 69, :hash "-110603750"} {:id "defn/visibility-mask", :kind "defn", :line 71, :end-line 73, :hash "2012904224"} {:id "defn/territory-mask", :kind "defn", :line 75, :end-line 77, :hash "1743460580"} {:id "defn/build-territory-expected", :kind "defn", :line 79, :end-line 81, :hash "1400558220"} {:id "defn/set-unit!", :kind "defn", :line 83, :end-line 85, :hash "-1662455137"} {:id "defn/get-unit", :kind "defn", :line 87, :end-line 89, :hash "-855372"} {:id "defn/get-city", :kind "defn", :line 91, :end-line 93, :hash "281639867"} {:id "defn/get-cell", :kind "defn", :line 95, :end-line 97, :hash "1213554020"} {:id "defn/cell-at", :kind "defn", :line 99, :end-line 103, :hash "2113880460"} {:id "defn/shipyard-at", :kind "defn", :line 105, :end-line 107, :hash "-1708539557"} {:id "defn/count-computer-armies", :kind "defn", :line 109, :end-line 118, :hash "1602521464"} {:id "def/writable-keys", :kind "def", :line 120, :end-line 137, :hash "368788818"} {:id "defn/set-state!", :kind "defn", :line 139, :end-line 145, :hash "-1674188680"} {:id "defn/update-state!", :kind "defn", :line 147, :end-line 153, :hash "-566378212"} {:id "defn/handle-key!", :kind "defn", :line 155, :end-line 157, :hash "697960671"} {:id "defn/key-down!", :kind "defn", :line 159, :end-line 162, :hash "1505852578"} {:id "defn/key-down-at!", :kind "defn", :line 164, :end-line 167, :hash "-500216165"} {:id "defn/start-new-round!", :kind "defn", :line 169, :end-line 171, :hash "-2039120726"} {:id "defn/advance-game!", :kind "defn", :line 173, :end-line 175, :hash "15459666"} {:id "defn/process-player-items-batch!", :kind "defn", :line 177, :end-line 179, :hash "1381578895"} {:id "defn/update-player-map!", :kind "defn", :line 181, :end-line 183, :hash "-506561392"} {:id "defn/update-cell-visibility!", :kind "defn", :line 185, :end-line 189, :hash "-1432544303"} {:id "defn/evaluate-computer-production!", :kind "defn", :line 191, :end-line 194, :hash "1586618979"} {:id "defn/process-computer-transport!", :kind "defn", :line 196, :end-line 198, :hash "-1213804184"} {:id "defn/process-computer-fighter!", :kind "defn", :line 200, :end-line 202, :hash "303727768"} {:id "defn/process-computer-ship!", :kind "defn", :line 204, :end-line 206, :hash "-811080827"}]}
;; clj-mutate-manifest-end

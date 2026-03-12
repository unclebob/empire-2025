(ns empire.ui.util.input.actions.production
  (:require [empire.game-mechanics.movement.movement-state :as movement-state]
            [empire.state.api :as sa]
            [empire.config.core :as config]
            [empire.game-mechanics.movement.map-utils :as map-utils]
            [empire.player.production :as player-production]
            [empire.ui.util.input.actions.helpers :as helpers]
            [empire.config.units.dispatcher :as dispatcher]))

(defn- try-set-production [coords item]
  (let [[x y] coords
        coastal? (map-utils/on-coast? x y)
        naval? (dispatcher/naval-units item)]
    (if (and naval? (not coastal?))
        (helpers/set-error-message! (format "Must be coastal city to produce %s." (name item)) config/error-message-duration)
      (do
        (player-production/set-city-production coords item)
        (helpers/item-processed!)))
    true))

(defn handle-city-production-key [k coords cell]
  (when (and (= (:type cell) :city)
             (= (:city-status cell) :player)
             (not (movement-state/get-active-unit cell)))
    (cond
      (= k :space) (do (sa/update-state! :player-items rest)
                       (helpers/item-processed!)
                       true)
      (= k :x) (do (sa/update-state! :production assoc coords :none)
                   (helpers/item-processed!)
                   true)
      (config/key->production-item k) (try-set-production coords (config/key->production-item k)))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T12:03:29.398883-05:00", :module-hash "610205501", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 8, :hash "351098827"} {:id "defn-/try-set-production", :kind "defn-", :line 10, :end-line 19, :hash "-1709960315"} {:id "defn/handle-city-production-key", :kind "defn", :line 21, :end-line 32, :hash "1566189338"}]}
;; clj-mutate-manifest-end

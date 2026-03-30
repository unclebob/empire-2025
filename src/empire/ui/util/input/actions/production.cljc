(ns empire.ui.util.input.actions.production
  (:require [empire.game-mechanics.movement.movement-state :as movement-state]
            [empire.player.command-decisions :as decisions]
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

(defn handle-city-production-decision [decision coords cell]
  (when (and (= (:type cell) :city)
             (= (:city-status cell) :player)
             (not (movement-state/get-active-unit cell coords)))
    (case (:action decision)
      :skip (do (sa/update-state! :player-items rest)
                (helpers/item-processed!)
                true)
      :clear-production (do (sa/update-state! :production assoc coords :none)
                            (helpers/item-processed!)
                            true)
      :set-production (try-set-production coords (:item decision))
      nil)))

(defn handle-city-production-key [k coords cell]
  (when-let [decision (decisions/city-key-action k cell)]
    (handle-city-production-decision decision coords cell)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T02:52:11.863432-05:00", :module-hash "-1263929012", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 9, :hash "168350232"} {:id "defn-/try-set-production", :kind "defn-", :line 11, :end-line 20, :hash "-1709960315"} {:id "defn/handle-city-production-decision", :kind "defn", :line 22, :end-line 34, :hash "1363216546"} {:id "defn/handle-city-production-key", :kind "defn", :line 36, :end-line 38, :hash "280111766"}]}
;; clj-mutate-manifest-end

;; mutation-tested: no
(ns empire.application.player-movement-services
  (:require [empire.movement.coastline :as coastline]
            [empire.movement.explore :as explore]))

(defn move-explore-unit
  [coords]
  (explore/move-explore-unit coords))

(defn set-explore-mode
  [coords]
  (explore/set-explore-mode coords))

(defn move-coastline-unit
  [coords]
  (coastline/move-coastline-unit coords))

(defn coastline-follow-eligible?
  [active-unit near-coast?]
  (coastline/coastline-follow-eligible? active-unit near-coast?))

(defn coastline-follow-rejection-reason
  [active-unit near-coast?]
  (coastline/coastline-follow-rejection-reason active-unit near-coast?))

(defn set-coastline-follow-mode
  [coords]
  (coastline/set-coastline-follow-mode coords))

(ns empire.computer.land-objectives
  (:require [empire.computer.shared.land-objectives :as shared]))

(def continent-cache shared/continent-cache)

(def clear-continent-cache! shared/clear-continent-cache!)

(def flood-fill-continent shared/flood-fill-continent)

(def continent-id shared/continent-id)

(def continent-id-for-pos shared/continent-id-for-pos)

(def city-status-key shared/city-status-key)

(def unit-owner-key shared/unit-owner-key)

(def scan-continent shared/scan-continent)

(def has-land-objective? shared/has-land-objective?)

(def find-all-objectives-on-continent shared/find-all-objectives-on-continent)

(def find-nearest-on-continent shared/find-nearest-on-continent)

(def find-unexplored-on-continent shared/find-unexplored-on-continent)

(def find-free-city-on-continent shared/find-free-city-on-continent)

(def find-player-city-on-continent shared/find-player-city-on-continent)

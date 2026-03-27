(ns empire.computer.transport.sailing-regular
  (:require [empire.computer.transport.sailing-regular.follow :as follow]
            [empire.computer.transport.sailing-regular.missions :as missions]
            [empire.computer.transport.sailing-regular.transitions :as transitions]
            [empire.state.api :as sa]))

(def enter-leave-city! transitions/enter-leave-city!)
(def enter-sail-to-load! transitions/enter-sail-to-load!)
(def enter-sail-to-unload! transitions/enter-sail-to-unload!)
(def launch-from-city-to-sea transitions/launch-from-city-to-sea)
(def transition-to-loading! transitions/transition-to-loading!)
(def handle-launch-and-follow! transitions/handle-launch-and-follow!)

(def sail-follow-path follow/sail-follow-path)
(def blocked-follow-result follow/blocked-follow-result)
(def blocked-follow? follow/blocked-follow?)
(def follow-path-action follow/follow-path-action)
(def follow-unload-sail-path follow/follow-unload-sail-path)
(def follow-load-sail-path follow/follow-load-sail-path)
(def replan-sail-path! follow/replan-sail-path!)
(def compute-and-follow-path! follow/compute-and-follow-path!)
(def compute-and-follow-load-target-path! follow/compute-and-follow-load-target-path!)

(def claimed-land? missions/claimed-land?)
(def process-sail-to-load-mission missions/process-sail-to-load-mission)
(def process-sail-to-unload-mission missions/process-sail-to-unload-mission)
(def process-hold-sail-to-load-mission missions/process-hold-sail-to-load-mission)
(def process-leave-city-mission missions/process-leave-city-mission)
(def process-sailing-default missions/process-sailing-default)

(defn process-sailing-mission
  [pos]
  (let [transport (get-in (sa/read-state :computer-map) (conj pos :contents))
        mission (:transport-mission transport)]
    (case mission
      :sailing (missions/process-sailing-default pos transport)
      :leave-city (missions/process-leave-city-mission pos transport)
      :hold-sail-to-load (missions/process-hold-sail-to-load-mission pos transport)
      :sail-to-load (missions/process-sail-to-load-mission pos transport)
      :sail-to-unload (missions/process-sail-to-unload-mission pos transport)
      (when-let [sail-path (:sail-path transport)]
        (follow/follow-path-action pos sail-path)))))

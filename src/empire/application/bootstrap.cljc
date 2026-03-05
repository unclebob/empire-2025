;; mutation-tested: no
(ns empire.application.bootstrap
  "Composition root wiring for application boundary implementations."
  (:require [empire.adapters.state.atoms :as atoms-adapter]
            [empire.adapters.state.runtime :as runtime-adapter]
            [empire.application.ports.runtime-state :as runtime-ports]
            [empire.application.ports.world-store :as world-ports]
            [empire.application.runtime :as app-runtime]
            [empire.computer.threat-response :as threat-response]
            [empire.computer.production :as computer-production]
            [empire.config :as config]
[empire.movement.adapter :as movement-adapter]
            [empire.movement.context :as movement-context]
            [empire.movement.pathfinding :as pathfinding]
            [empire.movement.pathfinding-bfs.context :as bfs-context]
            [empire.units.impl.dispatcher]))

(defn- build-default-state-ctx
  []
  (let [store (atoms-adapter/world-store)
        rt-store (runtime-adapter/runtime-state-store)
        movement-port (movement-adapter/movement-port)]
    {:world-store store
     :load-world #(world-ports/load-world store)
     :save-world! #(world-ports/save-world! store %)
     :load-major-invasion-state #(runtime-ports/load-major-invasion-state rt-store)
     :save-major-invasion-state! #(runtime-ports/save-major-invasion-state! rt-store %)
     :read-runtime-state #(runtime-ports/read-runtime-state rt-store %)
     :write-runtime-state! #(runtime-ports/write-runtime-state! rt-store %1 %2)
     :on-same-continent? #(runtime-ports/on-same-continent? rt-store %1 %2)
     :merge-continents! #(runtime-ports/merge-continents! rt-store %1 %2)
     :rebuild-refueling-caches! #(runtime-ports/rebuild-refueling-caches! rt-store)
     :rebuild-country-stats! (fn [] nil)
     :handle-detection! threat-response/handle-detection!
     :country-coastal-explored? (fn [country-id]
                                  (computer-production/country-coastal-cells-explored? country-id))
     :country-city-producing-armies? (fn [city-pos country-id]
                                       (computer-production/country-city-producing-armies?
                                         city-pos
                                         country-id))
     :set-city-production! (fn [coords item]
                             (let [current (or (runtime-ports/read-runtime-state rt-store :production) {})]
                               (runtime-ports/write-runtime-state! rt-store :production
                                                                  (assoc current
                                                                         coords
                                                                         {:item item
                                                                          :remaining-rounds (config/item-cost item)}))))
     :process-fighter-threat threat-response/process-fighter-threat
     :process-ship-threat threat-response/process-ship-threat
     :movement-port movement-port
     :check-invariants (fn [_world] nil)}))

(app-runtime/set-ctx-factory! build-default-state-ctx)

(defn initialize-default-services!
  "Loads app impl namespaces so their defmethod implementations are registered."
  []
  (let [ctx (app-runtime/default-state-ctx)]
    (movement-context/set-state-ctx! ctx)
    (pathfinding/set-world-loader! (:load-world ctx))
    (bfs-context/set-world-loader! (:load-world ctx))
    (bfs-context/set-runtime-reader! (:read-runtime-state ctx)))
  true)

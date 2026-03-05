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
            [empire.application.city-production :as city-production]
[empire.movement.adapter :as movement-adapter]))

(defn- build-default-state-ctx
  []
  (let [store (atoms-adapter/world-store)
        rt-store (runtime-adapter/runtime-state-store)]
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
     :country-coastal-explored? #(computer-production/country-coastal-cells-explored? %)
     :country-city-producing-armies? #(computer-production/country-city-producing-armies? %1 %2)
     :set-city-production! city-production/set-city-production
     :process-fighter-threat threat-response/process-fighter-threat
     :process-ship-threat threat-response/process-ship-threat
     :unit-state-port (movement-adapter/unit-state-port)
     :execution-port (movement-adapter/execution-port)
     :pathfinding-port (movement-adapter/pathfinding-port)
     :check-invariants (fn [_world] nil)}))

(app-runtime/set-ctx-factory! build-default-state-ctx)

(defn initialize-default-services!
  "Loads app impl namespaces so their defmethod implementations are registered."
  []
  (app-runtime/default-state-ctx)
  true)


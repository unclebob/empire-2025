(ns empire.computer.transport.mission-handlers
  (:require [empire.computer.transport.mission-handlers.invasion :as invasion]
            [empire.computer.transport.mission-handlers.loading-mission :as loading-mission]
            [empire.computer.transport.mission-handlers.unloading-mission :as unloading-mission]
            [empire.computer.transport.mission-handlers.lake :as lake]))

(def load-for-invasion-start! invasion/load-for-invasion-start!)
(def process-find-armies-for-invasion invasion/process-find-armies-for-invasion)
(def process-load-for-invasion-with-armies invasion/process-load-for-invasion-with-armies)
(def process-load-for-invasion-empty invasion/process-load-for-invasion-empty)
(def process-load-for-invasion invasion/process-load-for-invasion)

(def process-loading-mission loading-mission/process-loading-mission)

(def process-unloading-mission unloading-mission/process-unloading-mission)

(def park-lake-transport-if-empty lake/park-lake-transport-if-empty)
(def process-land-locked-mission lake/process-land-locked-mission)
(def fix-idle-mission lake/fix-idle-mission)
(def maybe-handle-lake-transport lake/maybe-handle-lake-transport)

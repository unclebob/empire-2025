(ns empire.computer.transport.sailing
  "Facade namespace for transport sailing and invading mission handlers."
  (:require [empire.computer.transport.sailing-invasion :as invasion]
            [empire.computer.transport.sailing-regular :as regular]
            [empire.computer.transport.sailing-support :as support]))

(def compute-sail-path support/compute-sail-path)
(def process-sailing-mission regular/process-sailing-mission)
(def process-invading-mission invasion/process-invading-mission)
(def enter-sail-to-load! regular/enter-sail-to-load!)
(def enter-leave-city! regular/enter-leave-city!)
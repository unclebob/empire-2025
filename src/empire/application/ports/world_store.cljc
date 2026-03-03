(ns empire.application.ports.world-store)

(defprotocol WorldStorePort
  (load-world [store] "Load current world state.")
  (save-world! [store world] "Persist world state and return it.")
  (world-atom [store] "Expose underlying world atom for legacy call sites requiring derefable map."))

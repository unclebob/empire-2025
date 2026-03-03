(ns empire.application.ports.runtime-state)

(defprotocol MajorInvasionStorePort
  (load-major-invasion-state [store] "Load major invasion coordinator state.")
  (save-major-invasion-state! [store state] "Persist major invasion state and return it."))

(defprotocol RuntimeStatePort
  (read-runtime-state [store k] "Read runtime state by key.")
  (write-runtime-state! [store k v] "Write runtime state by key and return value.")
  (on-same-continent? [store country-a country-b] "True when both countries are on the same continent.")
  (merge-continents! [store stamp-id existing-cid] "Merge continent groups.")
  (rebuild-refueling-caches! [store] "Rebuild cached city/carrier refueling positions."))

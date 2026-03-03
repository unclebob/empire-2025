(ns empire.application.ports.persistence)

(defprotocol PersistencePort
  (list-saves [persistence dir-path] "List save files from dir-path newest-first.")
  (save-state! [persistence dir-path data] "Save data to dir-path and return filename.")
  (load-state [persistence dir-path filename] "Load and return state data map."))

(ns empire.movement.service)

(def ^:private services* (atom nil))

(defn set-movement-services!
  "Sets the active movement service implementation. Intended for composition/init and tests."
  [services]
  (reset! services* services))

(defn reset-movement-services!
  "Clears the active movement services."
  []
  (reset! services* nil))

(defn current-services []
  (or @services*
      (throw (ex-info "Movement services not initialized"
                      {:hint "Call empire.movement.bootstrap/initialize-default-services! at startup or test setup"}))))

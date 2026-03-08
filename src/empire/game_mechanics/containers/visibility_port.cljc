(ns empire.game-mechanics.containers.visibility-port)

(defprotocol ContainerVisibilityPort
  (update-container-visibility! [this pos owner]
    "Apply visibility updates for container-triggered movement at pos for owner."))

(defrecord NoopContainerVisibilityPort []
  ContainerVisibilityPort
  (update-container-visibility! [_ _ _] nil))

(defonce ^:private active-port (atom (->NoopContainerVisibilityPort)))

(defn set-container-visibility-port!
  [port]
  (reset! active-port port))

(defn container-visibility-port
  []
  @active-port)

(defn apply-container-visibility!
  [port pos owner]
  (update-container-visibility! port pos owner))

(ns empire.game-mechanics.services.combat-visibility-port)

(defprotocol CombatVisibilityPort
  (update-visibility! [this pos owner]
    "Apply visibility updates for a combat side-effect at pos for owner."))

(defrecord NoopCombatVisibilityPort []
  CombatVisibilityPort
  (update-visibility! [_ _ _] nil))

(defonce ^:private active-port (atom (->NoopCombatVisibilityPort)))

(defn set-combat-visibility-port!
  [port]
  (reset! active-port port))

(defn combat-visibility-port
  []
  @active-port)

(defn apply-visibility-effects!
  [port effects]
  (doseq [{:keys [pos owner]} effects]
    (update-visibility! port pos owner)))

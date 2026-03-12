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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T12:02:10.893642-05:00", :module-hash "-1365284871", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "-1521309743"} {:id "form/1/defprotocol", :kind "defprotocol", :line 3, :end-line 5, :hash "-168533004"} {:id "form/2/defrecord", :kind "defrecord", :line 7, :end-line 9, :hash "-816682433"} {:id "form/3/defonce", :kind "defonce", :line 11, :end-line 11, :hash "-882008535"} {:id "defn/set-combat-visibility-port!", :kind "defn", :line 13, :end-line 15, :hash "36135198"} {:id "defn/combat-visibility-port", :kind "defn", :line 17, :end-line 19, :hash "133341876"} {:id "defn/apply-visibility-effects!", :kind "defn", :line 21, :end-line 24, :hash "-1549427287"}]}
;; clj-mutate-manifest-end

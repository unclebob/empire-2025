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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T12:00:39.509864-05:00", :module-hash "-351044551", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "-303073505"} {:id "form/1/defprotocol", :kind "defprotocol", :line 3, :end-line 5, :hash "1443378145"} {:id "form/2/defrecord", :kind "defrecord", :line 7, :end-line 9, :hash "-2007250438"} {:id "form/3/defonce", :kind "defonce", :line 11, :end-line 11, :hash "-603186866"} {:id "defn/set-container-visibility-port!", :kind "defn", :line 13, :end-line 15, :hash "1229703387"} {:id "defn/container-visibility-port", :kind "defn", :line 17, :end-line 19, :hash "-1609888028"} {:id "defn/apply-container-visibility!", :kind "defn", :line 21, :end-line 23, :hash "-917545615"}]}
;; clj-mutate-manifest-end

(ns empire.game-mechanics.containers-visibility-port-spec
  (:require [empire.game-mechanics.containers.visibility-port :as visibility-port]
            [speclj.core :refer :all]))

(defrecord RecordingPort [calls]
  visibility-port/ContainerVisibilityPort
  (update-container-visibility! [_ pos owner]
    (swap! calls conj [pos owner])
    :updated))

(describe "container visibility port"
  (it "defaults to a noop port"
    (should-be-nil
     (visibility-port/apply-container-visibility!
      (visibility-port/container-visibility-port)
      [1 2]
      :computer)))

  (it "stores and returns the active container visibility port"
    (let [calls (atom [])
          port (->RecordingPort calls)]
      (visibility-port/set-container-visibility-port! port)
      (should= port (visibility-port/container-visibility-port))
      (should= :updated
               (visibility-port/apply-container-visibility! port [3 4] :player))
      (should= [[[3 4] :player]] @calls))))

(ns empire.sound-spec
  (:require [empire.notifications :as notifications]
            [empire.sound :as sound]
            [speclj.core :refer :all]))

(describe "sound module"
  (after (notifications/reset-alert-port!))

  (it "init-sound! does not throw when resource is missing"
    (with-redefs [clojure.java.io/resource (fn [_] nil)]
      (should-not-throw (sound/init-sound!))))

  (it "init-sound! registers the device alert port"
    (with-redefs [clojure.java.io/resource (fn [_] nil)]
      (sound/init-sound!)
      (should (instance? empire.sound.DeviceAlertPort (notifications/alert-port)))))

  (it "play-bonk! does not throw when clip is nil"
    (should-not-throw (sound/play-bonk!))))

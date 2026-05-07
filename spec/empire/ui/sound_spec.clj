(ns empire.sound-spec
  (:require [empire.sound :as sound]
            [speclj.core :refer :all]))

(describe "sound module"
  (it "init-sound! does not throw when resource is missing"
    (with-redefs [clojure.java.io/resource (fn [_] nil)]
      (should-not-throw (sound/init-sound!))))

  (it "play-bonk! does not throw when clip is nil"
    (should-not-throw (sound/play-bonk!))))

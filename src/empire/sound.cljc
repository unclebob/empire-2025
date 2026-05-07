(ns empire.sound
  (:import [javax.sound.sampled AudioSystem Clip]))

(def ^:private bonk-clip (atom nil))

(defn init-sound!
  "Loads the bonk sound clip. Call once at startup."
  []
  (try
    (let [url (clojure.java.io/resource "bonk.wav")
          clip (AudioSystem/getClip)
          stream (AudioSystem/getAudioInputStream url)]
      (.open clip stream)
      (reset! bonk-clip clip))
    (catch Exception _ nil)))

(defn play-bonk!
  "Plays the bonk warning sound."
  []
  (when-let [clip @bonk-clip]
    (.setFramePosition clip 0)
    (.start clip)))

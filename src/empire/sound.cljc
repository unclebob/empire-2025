(ns empire.sound
  (:require [empire.notifications :as notifications])
  (:import [javax.sound.sampled AudioSystem Clip]))

(def ^:private bonk-clip (atom nil))

(defn play-bonk!
  "Plays the bonk warning sound."
  []
  (when-let [clip @bonk-clip]
    (.setFramePosition clip 0)
    (.start clip)))

(defrecord DeviceAlertPort []
  notifications/AlertPort
  (play-alert! [_] (play-bonk!)))

(defn init-sound!
  "Loads the bonk sound clip and registers it as the alert port. Call once at startup."
  []
  (try
    (let [url (clojure.java.io/resource "bonk.wav")
          clip (AudioSystem/getClip)
          stream (AudioSystem/getAudioInputStream url)]
      (.open clip stream)
      (reset! bonk-clip clip))
    (catch Exception _ nil))
  (notifications/set-alert-port! (->DeviceAlertPort)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T15:03:19.703012-05:00", :module-hash "1446465842", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "1711425783"} {:id "def/bonk-clip", :kind "def", :line 5, :end-line nil, :hash "-209480403"} {:id "defn/play-bonk!", :kind "defn", :line 7, :end-line nil, :hash "-1995940467"} {:id "form/3/defrecord", :kind "defrecord", :line 14, :end-line nil, :hash "784916365"} {:id "defn/init-sound!", :kind "defn", :line 18, :end-line nil, :hash "1309606949"}]}
;; clj-mutate-manifest-end

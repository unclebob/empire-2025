(ns empire.player.command-decisions
  (:require [empire.config.core :as config]))

(defn movement-decision
  [k]
  (when-let [direction (or (config/key->direction k)
                           (config/key->extended-direction k))]
    {:scope :unit
     :action :move
     :direction direction
     :extended? (boolean (config/key->extended-direction k))}))

(defn unit-key-action
  [k active-unit]
  (when (= :player (:owner active-unit))
    (or ({:space {:scope :unit :action :skip}
          :u {:scope :unit :action :unload}
          :s {:scope :unit :action :sentry}
          :l {:scope :unit :action :look-around}}
         k)
        (movement-decision k))))

(defn city-key-action
  [k cell]
  (when (and (= (:type cell) :city)
             (= (:city-status cell) :player))
    (or ({:space {:scope :city :action :skip}
          :x {:scope :city :action :clear-production}}
         k)
        (when-let [item (config/key->production-item k)]
          {:scope :city :action :set-production :item item}))))

(defn attention-key-action
  [k cell active-unit]
  (if active-unit
    (unit-key-action k active-unit)
    (city-key-action k cell)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T02:19:32.696526-05:00", :module-hash "15407043", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 2, :hash "-1264958867"} {:id "defn/movement-decision", :kind "defn", :line 4, :end-line 11, :hash "1862646296"} {:id "defn/unit-key-action", :kind "defn", :line 13, :end-line 21, :hash "-983902472"} {:id "defn/city-key-action", :kind "defn", :line 23, :end-line 31, :hash "1104560693"} {:id "defn/attention-key-action", :kind "defn", :line 33, :end-line 37, :hash "1593375486"}]}
;; clj-mutate-manifest-end

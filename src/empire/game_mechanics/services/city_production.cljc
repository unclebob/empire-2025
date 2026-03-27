;; mutation-tested: no
(ns empire.game-mechanics.services.city-production
  (:require [empire.state.api :as sa]
            [empire.config.core :as config]))

(defn set-city-production
  "Sets production and rounds remaining for a city in runtime state."
  [coords item]
  (let [current (sa/read-state :production)]
    (sa/write-state! :production
                          (assoc current
                                 coords
                                 {:item item
                                  :remaining-rounds (config/item-cost item)}))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T01:39:47.906191-05:00", :module-hash "1378850599", :forms [{:id "form/0/ns", :kind "ns", :line 2, :end-line 4, :hash "2051790061"} {:id "defn/set-city-production", :kind "defn", :line 6, :end-line 14, :hash "569870887"}]}
;; clj-mutate-manifest-end

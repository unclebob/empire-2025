(ns empire.game-mechanics.movement.wake-conditions.transport
  (:require [empire.game-mechanics.movement.map-utils :as map-utils]))

(defn- found-land? [was-in-open-sea? at-beach?]
  (and was-in-open-sea? at-beach?))

(defn- should-wake-at-beach? [has-armies? at-beach? been-to-sea?]
  (and has-armies? at-beach? been-to-sea?))

(defn wake-check [unit from-pos final-pos current-map]
  (let [has-armies? (pos? (:army-count unit 0))
        at-beach? (map-utils/adjacent-to-land? final-pos current-map)
        was-in-open-sea? (map-utils/completely-surrounded-by-sea? from-pos current-map)
        now-in-open-sea? (map-utils/completely-surrounded-by-sea? final-pos current-map)
        been-to-sea? (:been-to-sea unit true)]
    (cond
      (found-land? was-in-open-sea? at-beach?) {:wake? true :reason :transport-found-land :been-to-sea false}
      (should-wake-at-beach? has-armies? at-beach? been-to-sea?) {:wake? true :reason :transport-at-beach :been-to-sea false}
      now-in-open-sea? {:been-to-sea true}
      :else nil)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T01:37:11.30526-05:00", :module-hash "-1899438896", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 2, :hash "-309459085"} {:id "defn-/found-land?", :kind "defn-", :line 4, :end-line 5, :hash "-1455074046"} {:id "defn-/should-wake-at-beach?", :kind "defn-", :line 7, :end-line 8, :hash "-566558367"} {:id "defn/wake-check", :kind "defn", :line 10, :end-line 20, :hash "1838725030"}]}
;; clj-mutate-manifest-end

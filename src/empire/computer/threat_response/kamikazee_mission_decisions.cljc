(ns empire.computer.threat-response.kamikazee-mission-decisions)

(defn dec-count
  [n]
  (max 0 (dec (or n 0))))

(defn close-enough-to-hunt?
  [distance]
  (and distance (<= distance 1)))

(defn- staged-transition-action
  [{:keys [stage has-wait-site? has-resume-pos? fuel refuel-threshold has-adjacent-player-army? has-reachable-refuel-site?]}]
  (cond
    (and (= :refuel stage) has-wait-site?) :refuel
    (and (= :return stage) has-resume-pos?) :return
    :else nil))

(defn- active-hunt-action
  [{:keys [fuel refuel-threshold has-adjacent-player-army? has-reachable-refuel-site?]}]
  (cond
    has-adjacent-player-army? :attack
    (and (<= fuel refuel-threshold) has-reachable-refuel-site?) :start-refuel
    :else :walk))

(defn hunt-stage-action
  [state]
  (or (staged-transition-action state)
      (active-hunt-action state)))

(defn route-stage-action
  [{:keys [adjacent-route-city? at-route-site? has-next-site? close-enough-to-goal? has-goal?]}]
  (cond
    adjacent-route-city? :land-at-city
    at-route-site? :finish-route-node
    has-next-site? :move-to-next-site
    close-enough-to-goal? :enter-hunt
    has-goal? :move-to-goal
    :else :walk))

(defn hunt-step-result
  [moved-unit moved-pos pos hunt-trail-length default-fuel]
  (let [next-fuel (dec (:fuel moved-unit default-fuel))]
    (if (<= next-fuel 0)
      {:action :destroy
       :pos moved-pos}
      {:action :update-unit
       :pos moved-pos
       :unit-updates {:fuel next-fuel
                      :kamikazee-trail
                      (vec (take-last hunt-trail-length
                                      (conj (:kamikazee-trail moved-unit []) pos)))}})))

(defn finish-route-node-update
  [route next-site terminal-site]
  {:kamikazee-terminal-site (or terminal-site next-site)
   :kamikazee-stage (if (= 1 (count route)) :hunt :route)
   :kamikazee-route (vec (rest route))
   :clear-keys [:kamikazee-wait-site :kamikazee-hunt-resume-pos]})

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-16T08:14:13.586184-05:00", :module-hash "-1533290206", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "1815674099"} {:id "defn/dec-count", :kind "defn", :line 3, :end-line 5, :hash "685841636"} {:id "defn/close-enough-to-hunt?", :kind "defn", :line 7, :end-line 9, :hash "-924260457"} {:id "defn-/staged-transition-action", :kind "defn-", :line 11, :end-line 16, :hash "1033102202"} {:id "defn-/active-hunt-action", :kind "defn-", :line 18, :end-line 23, :hash "998061886"} {:id "defn/hunt-stage-action", :kind "defn", :line 25, :end-line 28, :hash "-693065041"} {:id "defn/route-stage-action", :kind "defn", :line 30, :end-line 38, :hash "1325226521"} {:id "defn/hunt-step-result", :kind "defn", :line 40, :end-line 51, :hash "1569329205"} {:id "defn/finish-route-node-update", :kind "defn", :line 53, :end-line 58, :hash "-1265533165"}]}
;; clj-mutate-manifest-end

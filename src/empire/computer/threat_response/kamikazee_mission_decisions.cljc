(ns empire.computer.threat-response.kamikazee-mission-decisions)

(defn dec-count
  [n]
  (max 0 (dec (or n 0))))

(defn kamikazee-stage-action
  [{:keys [stage]}]
  (if (#{:hunt :refuel :return} stage)
    :hunt-stage
    :route-stage))

(defn close-enough-to-hunt?
  [distance]
  (and distance (<= distance 1)))

(defn- staged-transition-action
  [{:keys [stage has-wait-site? has-resume-pos?]}]
  (cond
    (and (= :refuel stage) has-wait-site?) :refuel
    (and (= :return stage) has-resume-pos?) :return))

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

(defn- hunt-route-action
  [close-enough-to-goal? has-goal?]
  (cond
    close-enough-to-goal? :enter-hunt
    has-goal? :move-to-goal
    :else :walk))

(defn route-stage-action
  [{:keys [adjacent-route-city? at-route-site? has-next-site? close-enough-to-goal? has-goal?]}]
  (cond
    adjacent-route-city? :land-at-city
    at-route-site? :finish-route-node
    has-next-site? :move-to-next-site
    :else (hunt-route-action close-enough-to-goal? has-goal?)))

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

(defn airport-launch-state
  [{:keys [city-has-capacity? next-route-city-capacity? next-route-city launch-pos major-target targets plan fighter-fuel]}]
  {:current-city-capacity? city-has-capacity?
   :next-route-city-capacity? (or (nil? next-route-city) next-route-city-capacity?)
   :next-route-city next-route-city
   :launch-pos launch-pos
   :major-target major-target
   :targets targets
   :plan plan
   :fighter-fuel fighter-fuel})

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T16:10:42.871885-05:00", :module-hash "336177963", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "1815674099"} {:id "defn/dec-count", :kind "defn", :line 3, :end-line nil, :hash "685841636"} {:id "defn/kamikazee-stage-action", :kind "defn", :line 7, :end-line nil, :hash "238914229"} {:id "defn/close-enough-to-hunt?", :kind "defn", :line 13, :end-line nil, :hash "-924260457"} {:id "defn-/staged-transition-action", :kind "defn-", :line 17, :end-line nil, :hash "-2122322714"} {:id "defn-/active-hunt-action", :kind "defn-", :line 23, :end-line nil, :hash "998061886"} {:id "defn/hunt-stage-action", :kind "defn", :line 30, :end-line nil, :hash "-693065041"} {:id "defn-/hunt-route-action", :kind "defn-", :line 35, :end-line nil, :hash "298107878"} {:id "defn/route-stage-action", :kind "defn", :line 42, :end-line nil, :hash "-1211244141"} {:id "defn/hunt-step-result", :kind "defn", :line 50, :end-line nil, :hash "1569329205"} {:id "defn/finish-route-node-update", :kind "defn", :line 63, :end-line nil, :hash "-1265533165"} {:id "defn/airport-launch-state", :kind "defn", :line 70, :end-line nil, :hash "-2119739542"}]}
;; clj-mutate-manifest-end

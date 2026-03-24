(ns empire.computer.early-game.strategy
  (:require [empire.config.ai :as ai]
            [empire.computer.early-game.roles :as roles]
            [empire.computer.early-game.theater :as theater]
            [empire.game.loop.profiling :as profiling]
            [empire.state.api :as sa]))

(defn- opening-phase
  [suffix]
  (keyword "process-computer"
           (str "army-opening-" suffix)))

(def city-usable-coastal? theater/city-usable-coastal?)
(def invasion-started? theater/invasion-started?)
(def opening-active? theater/opening-active?)
(def theater-summary theater/theater-summary)
(def desired-role-counts roles/desired-role-counts)

(defn assigned-role
  [city-pos]
  (let [summary (theater-summary city-pos)]
    (get (:role-plan summary) city-pos :CA)))

(defn- original-continent-city?
  [city-pos]
  (= 1 (:country-id (get-in (sa/read-state :computer-map) city-pos))))

(defn- opening-satellite-ready?
  [city-pos assigned-role]
  (and (> (or (sa/read-state :round-number) 0) 50)
       (= :CA assigned-role)
       (not (original-continent-city? city-pos))
       (not (sa/read-state :opening-satellite-produced?))
       (not-any? (fn [[_ prod]] (= :satellite (:item prod)))
                 (sa/read-state :production))
       (opening-active?)
       (contains? (:positions (theater-summary city-pos)) city-pos)))

(defn opening-production
  [city-pos]
  (when (opening-active?)
    (let [role (assigned-role city-pos)]
      (if (opening-satellite-ready? city-pos role)
        :satellite
        (roles/role->item role)))))

(defn should-reset-lake-production?
  [city-pos]
  (let [cell (get-in (sa/read-state :computer-map) city-pos)
        production (get (sa/read-state :production) city-pos)
        role (:opening-role cell)]
    (and (opening-active?)
         (map? production)
         (#{:CT :CP} role)
         (not (city-usable-coastal? city-pos)))))

(defn opening-exploration-profile
  [city-pos]
  (let [{:keys [coastal-count]} (theater-summary city-pos)]
    (ai/opening-exploration-profile coastal-count)))

(defn theater-loading-transports
  [start-pos]
  (:loading-transport-positions (theater-summary start-pos)))

(defn allow-coastal-staging?
  [pos]
  (if-not (profiling/time-phase
           (opening-phase "active")
           opening-active?)
    true
    (let [{:keys [phase loading-transport-positions staging-allowed-positions]}
          (profiling/time-phase
           (opening-phase "theater-summary")
           #(theater-summary pos))]
      (cond
      (= phase :phase-1) false
      (profiling/time-phase
       (opening-phase "loading-transports")
       #(seq loading-transport-positions)) true
      :else
      (profiling/time-phase
       (opening-phase "staging-membership")
       #(contains? staging-allowed-positions pos))))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-13T21:05:59.722934-05:00", :module-hash "-925446871", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 5, :hash "-1659688238"} {:id "def/city-usable-coastal?", :kind "def", :line 7, :end-line 7, :hash "-347196069"} {:id "def/invasion-started?", :kind "def", :line 8, :end-line 8, :hash "607622252"} {:id "def/opening-active?", :kind "def", :line 9, :end-line 9, :hash "-581060577"} {:id "def/theater-summary", :kind "def", :line 10, :end-line 10, :hash "380656184"} {:id "def/desired-role-counts", :kind "def", :line 11, :end-line 11, :hash "1148564132"} {:id "defn/assigned-role", :kind "defn", :line 13, :end-line 16, :hash "-1590439152"} {:id "defn-/original-continent-city?", :kind "defn-", :line 18, :end-line 20, :hash "1203555759"} {:id "defn-/opening-satellite-ready?", :kind "defn-", :line 22, :end-line 31, :hash "-1700019421"} {:id "defn/opening-production", :kind "defn", :line 33, :end-line 39, :hash "-374953766"} {:id "defn/should-reset-lake-production?", :kind "defn", :line 41, :end-line 49, :hash "1416073797"} {:id "defn/opening-exploration-profile", :kind "defn", :line 51, :end-line 58, :hash "-385741527"} {:id "defn/theater-loading-transports", :kind "defn", :line 60, :end-line 69, :hash "-889714929"} {:id "defn-/theater-transport-producers", :kind "defn-", :line 71, :end-line 78, :hash "903840240"} {:id "defn/allow-coastal-staging?", :kind "defn", :line 80, :end-line 91, :hash "-252872511"}]}
;; clj-mutate-manifest-end

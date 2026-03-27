(ns empire.computer.early-game.strategy
  (:require [empire.config.ai :as ai]
            [empire.computer.early-game.roles :as roles]
            [empire.computer.early-game.theater :as theater]
            [empire.state.api :as sa]))

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
  (if-not (opening-active?)
    true
    (let [{:keys [phase loading-transport-positions staging-allowed-positions]}
          (theater-summary pos)]
      (cond
      (= phase :phase-1) false
      (seq loading-transport-positions) true
      :else
      (contains? staging-allowed-positions pos)))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T22:48:10.992617-05:00", :module-hash "1242597937", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 5, :hash "-554712774"} {:id "def/city-usable-coastal?", :kind "def", :line 7, :end-line 7, :hash "-347196069"} {:id "def/invasion-started?", :kind "def", :line 8, :end-line 8, :hash "607622252"} {:id "def/opening-active?", :kind "def", :line 9, :end-line 9, :hash "-581060577"} {:id "def/theater-summary", :kind "def", :line 10, :end-line 10, :hash "380656184"} {:id "def/desired-role-counts", :kind "def", :line 11, :end-line 11, :hash "1148564132"} {:id "defn/assigned-role", :kind "defn", :line 13, :end-line 16, :hash "1695583153"} {:id "defn-/original-continent-city?", :kind "defn-", :line 18, :end-line 20, :hash "1079065226"} {:id "defn-/opening-satellite-ready?", :kind "defn-", :line 22, :end-line 31, :hash "-1700019421"} {:id "defn/opening-production", :kind "defn", :line 33, :end-line 39, :hash "-374953766"} {:id "defn/should-reset-lake-production?", :kind "defn", :line 41, :end-line 49, :hash "1436634441"} {:id "defn/opening-exploration-profile", :kind "defn", :line 51, :end-line 54, :hash "-1106320558"} {:id "defn/theater-loading-transports", :kind "defn", :line 56, :end-line 58, :hash "894778124"} {:id "defn/allow-coastal-staging?", :kind "defn", :line 60, :end-line 70, :hash "1249048549"}]}
;; clj-mutate-manifest-end

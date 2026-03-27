(ns empire.computer.early-game.role-policy
  (:require [empire.computer.early-game.role-policy-large :as large]
            [empire.computer.early-game.role-policy-minimal :as minimal]
            [empire.computer.early-game.role-policy-one-coast :as one-coast]
            [empire.computer.early-game.role-policy-two-coast :as two-coast]))

(def ^:private role->item-map
  {:CA :army
   :CF :fighter
   :CT :transport
   :CP :patrol-boat})

(defn role->item
  [role]
  (get role->item-map role))

(defn- strong-army-backlog?
  [{:keys [army-count]}]
  (>= army-count 6))

(defn desired-role-counts
  [{:keys [coastal-count landlocked-count phase] :as summary}]
  (let [total (+ coastal-count landlocked-count)
        strong? (strong-army-backlog? summary)]
    (cond
      (zero? coastal-count)
      (minimal/no-coast-role-counts total landlocked-count strong?)

      (= phase :phase-1)
      (minimal/all-army-roles total)

      (= coastal-count 1)
      (one-coast/one-coast-role-counts landlocked-count strong?)

      (= coastal-count 2)
      (two-coast/two-coast-role-counts landlocked-count strong?)

      :else
      (large/many-coast-role-counts coastal-count landlocked-count strong?))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T22:45:25.70846-05:00", :module-hash "1281412380", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 5, :hash "-159998796"} {:id "def/role->item-map", :kind "def", :line 7, :end-line 11, :hash "-1533521591"} {:id "defn/role->item", :kind "defn", :line 13, :end-line 15, :hash "-2085333229"} {:id "defn-/strong-army-backlog?", :kind "defn-", :line 17, :end-line 19, :hash "1046624873"} {:id "defn/desired-role-counts", :kind "defn", :line 21, :end-line 39, :hash "23081746"}]}
;; clj-mutate-manifest-end

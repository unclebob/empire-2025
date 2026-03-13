(ns empire.computer.early-game.role-assignment
  (:require [empire.computer.early-game.role-policy :as policy]))

(defn- pinned-role
  [{:keys [role production coastal?]}]
  (when (and role (map? production))
    (when-not (and (#{:CT :CP} role) (not coastal?))
      role)))

(defn- assign-random-role
  [cities assignments remaining role eligible?]
  (loop [assignments assignments
         remaining remaining
         need (get remaining role 0)]
    (if (pos? need)
      (let [eligible (->> cities
                          (remove #(contains? assignments (:pos %)))
                          (filter eligible?)
                          vec)]
        (if (seq eligible)
          (let [chosen (rand-nth eligible)]
            (recur (assoc assignments (:pos chosen) role)
                   (update remaining role dec)
                   (dec need)))
          [assignments remaining]))
      [assignments remaining])))

(defn theater-role-plan
  [{:keys [cities] :as summary}]
  (let [desired (policy/desired-role-counts summary)
        pinned (reduce (fn [m city]
                         (if-let [role (pinned-role city)]
                           (assoc m (:pos city) role)
                           m))
                       {}
                       cities)
        remaining (reduce-kv (fn [m _pos role] (update m role (fnil dec 0))) desired pinned)
        [assignments remaining] (assign-random-role cities pinned remaining :CF #(not (:coastal? %)))
        [assignments remaining] (assign-random-role cities assignments remaining :CP :coastal?)
        [assignments remaining] (assign-random-role cities assignments remaining :CT :coastal?)
        [assignments _remaining] (assign-random-role cities assignments remaining :CA (constantly true))]
    (reduce (fn [m city]
              (update m (:pos city) #(or % :CA)))
            assignments
            cities)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-13T16:02:57.128702-05:00", :module-hash "343065411", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 2, :hash "-1554357976"} {:id "defn-/pinned-role", :kind "defn-", :line 4, :end-line 8, :hash "536844627"} {:id "defn-/assign-random-role", :kind "defn-", :line 10, :end-line 26, :hash "319169559"} {:id "defn/theater-role-plan", :kind "defn", :line 28, :end-line 45, :hash "458882448"}]}
;; clj-mutate-manifest-end

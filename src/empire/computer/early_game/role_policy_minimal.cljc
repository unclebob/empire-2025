(ns empire.computer.early-game.role-policy-minimal)

(defn all-army-roles
  [total]
  {:CA total :CF 0 :CT 0 :CP 0})

(defn no-coast-role-counts
  [total landlocked-count strong?]
  (if (and (pos? landlocked-count) strong?)
    {:CA (dec total) :CF 1 :CT 0 :CP 0}
    (all-army-roles total)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-13T16:07:07.379555-05:00", :module-hash "1331548408", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "-1487147503"} {:id "defn/all-army-roles", :kind "defn", :line 3, :end-line 5, :hash "1003957431"} {:id "defn/no-coast-role-counts", :kind "defn", :line 7, :end-line 11, :hash "-645730070"}]}
;; clj-mutate-manifest-end

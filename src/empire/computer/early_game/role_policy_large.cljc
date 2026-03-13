(ns empire.computer.early-game.role-policy-large)

(defn many-coast-role-counts
  [coastal-count landlocked-count strong?]
  (get {0 {:CA 1
           :CF 0
           :CT (max 1 (- coastal-count (if strong? 2 1)))
           :CP (if strong? 1 0)}
        1 {:CA 1 :CF 1 :CT (max 1 (- coastal-count 2)) :CP 1}
        2 {:CA 1 :CF 1 :CT (dec coastal-count) :CP 1}}
       landlocked-count
       {:CA (dec landlocked-count) :CF 1 :CT (dec coastal-count) :CP 1}))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-13T16:17:05.23236-05:00", :module-hash "76161457", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "-566336849"} {:id "defn/many-coast-role-counts", :kind "defn", :line 3, :end-line 12, :hash "49050565"}]}
;; clj-mutate-manifest-end

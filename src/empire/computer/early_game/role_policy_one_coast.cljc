(ns empire.computer.early-game.role-policy-one-coast)

(defn one-coast-role-counts
  [landlocked-count strong?]
  (get {[0 false] {:CA 1 :CF 0 :CT 0 :CP 0}
        [0 true] {:CA 0 :CF 0 :CT 1 :CP 0}
        [1 false] {:CA 1 :CF 1 :CT 0 :CP 0}
        [1 true] {:CA 0 :CF 1 :CT 1 :CP 0}}
       [landlocked-count strong?]
       {:CA (dec landlocked-count) :CF 1 :CT 1 :CP 0}))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T22:42:36.490616-05:00", :module-hash "-1292558443", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "-339645299"} {:id "defn/one-coast-role-counts", :kind "defn", :line 3, :end-line 10, :hash "-1209047307"}]}
;; clj-mutate-manifest-end

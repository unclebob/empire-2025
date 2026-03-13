(ns empire.computer.early-game.role-policy-two-coast)

(defn two-coast-role-counts
  [landlocked-count strong?]
  (get {[0 false] {:CA 1 :CF 0 :CT 1 :CP 0}
        [0 true] {:CA 1 :CF 0 :CT 1 :CP 0}
        [1 false] {:CA 1 :CF 1 :CT 1 :CP 0}
        [1 true] {:CA 1 :CF 1 :CT 1 :CP 0}}
       [landlocked-count strong?]
       (if strong?
         {:CA (dec landlocked-count) :CF 1 :CT 1 :CP 1}
         {:CA landlocked-count :CF 1 :CT 1 :CP 0})))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-13T16:14:15.198807-05:00", :module-hash "-1833373316", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "-799273982"} {:id "defn/two-coast-role-counts", :kind "defn", :line 3, :end-line 12, :hash "-1967803075"}]}
;; clj-mutate-manifest-end

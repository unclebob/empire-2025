(ns empire.computer.transport-targeting-decisions)

(defn claimed-target-choice
  [priority-targets claimed score-fn]
  (when (seq priority-targets)
    (let [unclaimed (remove claimed priority-targets)
          candidates (if (seq unclaimed) unclaimed priority-targets)
          best (apply min-key score-fn candidates)]
      {:best best
       :claimed (conj claimed best)})))

(defn pickup-continent-choice
  [transport-pos continents min-armies distance-fn]
  (let [qualifying (filter #(> (count (:armies %)) min-armies) continents)]
    (when (seq qualifying)
      (let [best-continent (apply min-key
                                  (fn [{:keys [armies]}]
                                    (apply min (map #(distance-fn transport-pos %) armies)))
                                  qualifying)]
        (apply min-key #(distance-fn transport-pos %) (:armies best-continent))))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-16T14:19:44.119659-05:00", :module-hash "953545863", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "575159759"} {:id "defn/claimed-target-choice", :kind "defn", :line 3, :end-line 10, :hash "-832415697"} {:id "defn/pickup-continent-choice", :kind "defn", :line 12, :end-line 20, :hash "-602823111"}]}
;; clj-mutate-manifest-end

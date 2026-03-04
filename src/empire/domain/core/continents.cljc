;; mutation-tested: no
(ns empire.domain.core.continents)

(defmulti on-same-continent?
  "Returns true if two country-ids are on the same landmass."
  (fn [& _] :default))

(defmulti merge-continents
  "Returns updated union-find groups after linking two country-ids."
  (fn [& _] :default))

(defmethod on-same-continent? :default
  [groups cid1 cid2]
  (or (= cid1 cid2)
      (and cid1 cid2
           (= (get groups cid1 cid1)
              (get groups cid2 cid2)))))

(defmethod merge-continents :default
  [groups cid1 cid2]
  (if (or (nil? cid1) (nil? cid2) (= cid1 cid2))
    groups
    (let [g1 (get groups cid1 cid1)
          g2 (get groups cid2 cid2)]
      (if (= g1 g2)
        groups
        (reduce-kv (fn [m k v]
                     (if (= v g2) (assoc m k g1) m))
                   (assoc groups cid1 g1 cid2 g1)
                   groups)))))

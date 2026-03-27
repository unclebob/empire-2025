;; mutation-tested: no
(ns empire.config.domain.core.continents)

(defn on-same-continent?
  "Returns true if two country-ids are on the same landmass."
  [groups cid1 cid2]
  (or (= cid1 cid2)
      (and cid1 cid2
           (= (get groups cid1 cid1)
              (get groups cid2 cid2)))))

(defn merge-continents
  "Returns updated union-find groups after linking two country-ids."
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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T00:26:41.168964-05:00", :module-hash "-1169377561", :forms [{:id "form/0/ns", :kind "ns", :line 2, :end-line 2, :hash "-952559796"} {:id "defn/on-same-continent?", :kind "defn", :line 4, :end-line 10, :hash "-2043680342"} {:id "defn/merge-continents", :kind "defn", :line 12, :end-line 24, :hash "1559328188"}]}
;; clj-mutate-manifest-end

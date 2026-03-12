(ns empire.config.keys)

(def key->direction
  {:q [-1 -1]
   :w [0 -1]
   :e [1 -1]
   :a [-1 0]
   :d [1 0]
   :z [-1 1]
   :x [0 1]
   :c [1 1]})

(def key->extended-direction
  {:Q [-1 -1]
   :W [0 -1]
   :E [1 -1]
   :A [-1 0]
   :D [1 0]
   :Z [-1 1]
   :X [0 1]
   :C [1 1]})

(def key->production-item
  {:a :army
   :f :fighter
   :z :satellite
   :t :transport
   :p :patrol-boat
   :d :destroyer
   :s :submarine
   :c :carrier
   :b :battleship})

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T11:59:36.061416-05:00", :module-hash "167431805", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "-1747540107"} {:id "def/key->direction", :kind "def", :line 3, :end-line 11, :hash "-140696758"} {:id "def/key->extended-direction", :kind "def", :line 13, :end-line 21, :hash "1035256048"} {:id "def/key->production-item", :kind "def", :line 23, :end-line 32, :hash "1250156818"}]}
;; clj-mutate-manifest-end

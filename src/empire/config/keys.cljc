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

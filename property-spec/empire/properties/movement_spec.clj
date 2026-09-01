(ns empire.properties.movement-spec
  (:require [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [empire.config.units.dispatcher :as dispatcher]
            [empire.properties.check :as p]
            [speclj.core :refer :all]))

(def terrain-gen
  (gen/elements [:land :sea :city :unexplored]))

(def city-status-gen
  (gen/elements [:player :computer :free]))

(def cell-gen
  (gen/one-of
   [(gen/return nil)
    (gen/hash-map :type terrain-gen
                  :city-status city-status-gen)]))

(def sea-unit-gen
  (gen/elements [:transport :carrier :patrol-boat :destroyer :submarine :battleship]))

(describe "movement legality properties"
  (it "fighters can enter any cell"
    (p/check 40
             (prop/for-all [cell cell-gen]
               (true? (dispatcher/can-move-to? :fighter cell)))))

  (it "armies never enter sea"
    (p/check 40
             (prop/for-all [cell cell-gen]
               (if (= :sea (:type cell))
                 (not (dispatcher/can-move-to? :army cell))
                 true))))

  (it "ships only enter sea"
    (p/check 50
             (prop/for-all [unit-type sea-unit-gen
                            cell cell-gen]
               (= (boolean (and cell (= :sea (:type cell))))
                  (boolean (dispatcher/can-move-to? unit-type cell)))))))

(ns empire.computer.army-movement-spec
  (:require [speclj.core :refer :all]
            [empire.state.api :as sa]
            [empire.computer.army.movement :as movement]
            [empire.computer.shared.world-query :as world-query]))

(describe "register-coastal-cells"
  (it "does nothing when country-id is nil"
    (let [writes (atom 0)]
      (with-redefs [sa/write-state! (fn [& _] (swap! writes inc))]
        (movement/register-coastal-cells [0 0] nil)
        (should= 0 @writes))))

  (it "merges adjacent foreign country ids"
    (let [merged (atom [])]
      (with-redefs [sa/read-state (fn [k]
                                    (case k
                                      :computer-map [[{:type :land :country-id 1}]
                                                     [{:type :land :country-id 2}]]
                                      {}))
                    sa/write-state! (fn [& _])
                    movement/adjacent-to-sea? (fn [_] false)
                    world-query/get-neighbors (fn [_] [[1 0]])
                    movement/merge-continents! (fn [a b] (swap! merged conj [a b]))]
        (movement/register-coastal-cells [0 0] 1)
        (should-contain [1 2] @merged))))

  (it "registers local coastal cells into country registry"
    (let [written (atom nil)]
      (with-redefs [sa/read-state (fn [k]
                                    (case k
                                      :computer-map [[{:type :land :country-id 1}]
                                                     [{:type :land :country-id 1}]
                                                     [{:type :sea}]]
                                      :coastal-cells-by-country {1 #{[9 9]}}
                                      nil))
                    sa/write-state! (fn [k v] (reset! written [k v]))
                    movement/adjacent-to-sea? (fn [p] (contains? #{[0 0] [1 0]} p))
                    movement/merge-continents! (fn [& _])
                    movement/on-same-continent? (fn [a b] (= a b))]
        (movement/register-coastal-cells [0 0] 1)
        (should= :coastal-cells-by-country (first @written))
        (should (contains? (get-in (second @written) [1]) [9 9]))
        (should (contains? (get-in (second @written) [1]) [0 0])))))

  (it "does not write registry when no coastal land cells are found"
    (let [writes (atom 0)]
      (with-redefs [sa/read-state (fn [k]
                                    (case k
                                      :computer-map [[{:type :land :country-id 1}]]
                                      {}))
                    sa/write-state! (fn [& _] (swap! writes inc))
                    movement/adjacent-to-sea? (fn [_] false)
                    movement/merge-continents! (fn [& _])
                    movement/on-same-continent? (fn [& _] true)]
        (movement/register-coastal-cells [0 0] 1)
        (should= 0 @writes)))))

(describe "ensure-coastal-registry"
  (it "seeds coastal registry from indexed land cells for the country"
    (let [written (atom nil)]
      (with-redefs [sa/read-state (fn [k]
                                    (case k
                                      :computer-map [[{:type :land}
                                                      {:type :land :country-id 1}
                                                      {:type :sea}
                                                      {:type :land :country-id 2}]]
                                      :coastal-index {:coastal-land-cells [[0 0] [0 1] [0 2] [0 3]]}
                                      :coastal-cells-by-country {}
                                      nil))
                    sa/write-state! (fn [k v] (reset! written [k v]))]
        (movement/ensure-coastal-registry 1)
        (should= :coastal-cells-by-country (first @written))
        (should= #{[0 0] [0 1]} (get-in (second @written) [1]))))))

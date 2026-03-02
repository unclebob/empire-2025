(ns empire.computer.army-movement-spec
  (:require [speclj.core :refer :all]
            [empire.computer.army.movement :as movement]
            [empire.computer.core :as core]
            [empire.adapters.state.runtime :as runtime-state]))

(describe "register-coastal-cells"
  (it "does nothing when country-id is nil"
    (let [writes (atom 0)]
      (with-redefs [movement/write-runtime-state! (fn [& _] (swap! writes inc))]
        (movement/register-coastal-cells [0 0] nil)
        (should= 0 @writes))))

  (it "merges adjacent foreign country ids"
    (let [merged (atom [])]
      (with-redefs [movement/current-world (fn [] [[{:type :land :country-id 1}]
                                                   [{:type :land :country-id 2}]])
                    movement/read-runtime-state (fn [_] {})
                    movement/write-runtime-state! (fn [& _])
                    movement/adjacent-to-sea? (fn [_] false)
                    core/get-neighbors (fn [_] [[1 0]])
                    runtime-state/merge-continents! (fn [a b] (swap! merged conj [a b]))]
        (movement/register-coastal-cells [0 0] 1)
        (should-contain [1 2] @merged))))

  (it "registers local coastal cells into country registry"
    (let [written (atom nil)]
      (with-redefs [movement/current-world (fn [] [[{:type :land :country-id 1}]
                                                   [{:type :land :country-id 1}]
                                                   [{:type :sea}]])
                    movement/read-runtime-state (fn [k]
                                                  (case k
                                                    :coastal-cells-by-country {1 #{[9 9]}}
                                                    nil))
                    movement/write-runtime-state! (fn [k v] (reset! written [k v]))
                    movement/adjacent-to-sea? (fn [p] (contains? #{[0 0] [1 0]} p))
                    runtime-state/merge-continents! (fn [& _])
                    runtime-state/on-same-continent? (fn [a b] (= a b))]
        (movement/register-coastal-cells [0 0] 1)
        (should= :coastal-cells-by-country (first @written))
        (should (contains? (get-in (second @written) [1]) [9 9]))
        (should (contains? (get-in (second @written) [1]) [0 0])))))

  (it "does not write registry when no coastal land cells are found"
    (let [writes (atom 0)]
      (with-redefs [movement/current-world (fn [] [[{:type :land :country-id 1}]])
                    movement/read-runtime-state (fn [_] {})
                    movement/write-runtime-state! (fn [& _] (swap! writes inc))
                    movement/adjacent-to-sea? (fn [_] false)
                    runtime-state/merge-continents! (fn [& _])
                    runtime-state/on-same-continent? (fn [& _] true)]
        (movement/register-coastal-cells [0 0] 1)
        (should= 0 @writes)))))

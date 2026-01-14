(ns empire.core-spec
  (:require [empire.atoms :as atoms]
            [empire.rendering :as rendering]
            [speclj.core :refer :all]))

(describe "format-unit-status"
  (it "formats army status"
    (let [unit {:type :army :hits 1 :mode :awake}]
      (should= "army [1/1] awake" (#'rendering/format-unit-status unit))))

  (it "formats fighter with fuel"
    (let [unit {:type :fighter :hits 1 :mode :sentry :fuel 20}]
      (should= "fighter [1/1] fuel:20 sentry" (#'rendering/format-unit-status unit))))

  (it "formats transport with cargo"
    (let [unit {:type :transport :hits 1 :mode :awake :army-count 4}]
      (should= "transport [1/1] cargo:4 awake" (#'rendering/format-unit-status unit))))

  (it "formats carrier with cargo"
    (let [unit {:type :carrier :hits 8 :mode :moving :fighter-count 2}]
      (should= "carrier [8/8] cargo:2 moving" (#'rendering/format-unit-status unit))))

  (it "formats unit with marching orders"
    (let [unit {:type :army :hits 1 :mode :moving :marching-orders [5 5]}]
      (should= "army [1/1] march moving" (#'rendering/format-unit-status unit))))

  (it "formats unit with flight path"
    (let [unit {:type :fighter :hits 1 :mode :moving :fuel 30 :flight-path [10 10]}]
      (should= "fighter [1/1] fuel:30 flight moving" (#'rendering/format-unit-status unit)))))

(describe "format-city-status"
  (before
    (reset! atoms/production {}))

  (it "formats player city"
    (let [cell {:type :city :city-status :player}]
      (should= "city:player" (#'rendering/format-city-status cell [0 0]))))

  (it "formats computer city"
    (let [cell {:type :city :city-status :computer}]
      (should= "city:computer" (#'rendering/format-city-status cell [0 0]))))

  (it "formats player city with production"
    (reset! atoms/production {[1 2] {:item :army :remaining-rounds 3}})
    (let [cell {:type :city :city-status :player}]
      (should= "city:player producing:army" (#'rendering/format-city-status cell [1 2]))))

  (it "formats player city with :none production"
    (reset! atoms/production {[1 2] :none})
    (let [cell {:type :city :city-status :player}]
      (should= "city:player producing:none" (#'rendering/format-city-status cell [1 2]))))

  (it "formats city with fighters"
    (let [cell {:type :city :city-status :player :fighter-count 3}]
      (should= "city:player fighters:3" (#'rendering/format-city-status cell [0 0]))))

  (it "formats city with sleeping fighters"
    (let [cell {:type :city :city-status :player :fighter-count 2 :sleeping-fighters 1}]
      (should= "city:player fighters:2 sleeping:1" (#'rendering/format-city-status cell [0 0]))))

  (it "formats city with marching orders"
    (let [cell {:type :city :city-status :player :marching-orders [5 5]}]
      (should= "city:player march" (#'rendering/format-city-status cell [0 0]))))

  (it "formats city with flight path"
    (let [cell {:type :city :city-status :player :flight-path [10 10]}]
      (should= "city:player flight" (#'rendering/format-city-status cell [0 0])))))

(describe "format-hover-status"
  (before
    (reset! atoms/production {}))

  (it "returns nil for empty land"
    (let [cell {:type :land}]
      (should-be-nil (rendering/format-hover-status cell [0 0]))))

  (it "formats unit when present"
    (let [cell {:type :land :contents {:type :army :hits 1 :mode :awake}}]
      (should= "army [1/1] awake" (rendering/format-hover-status cell [0 0]))))

  (it "formats city when no unit"
    (let [cell {:type :city :city-status :player}]
      (should= "city:player" (rendering/format-hover-status cell [0 0]))))

  (it "prefers unit over city"
    (let [cell {:type :city :city-status :player :contents {:type :army :hits 1 :mode :awake}}]
      (should= "army [1/1] awake" (rendering/format-hover-status cell [0 0])))))

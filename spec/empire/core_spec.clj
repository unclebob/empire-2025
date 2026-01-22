(ns empire.core-spec
  (:require [empire.atoms :as atoms]
            [empire.core :as core]
            [empire.rendering-util :as ru]
            [empire.test-utils :refer [reset-all-atoms!]]
            [speclj.core :refer :all]))

(describe "format-unit-status"
  (before (reset-all-atoms!))
  (it "formats army status"
    (let [unit {:type :army :hits 1 :mode :awake :owner :player}]
      (should= "player army [1/1] awake" (ru/format-unit-status unit))))

  (it "formats fighter with fuel"
    (let [unit {:type :fighter :hits 1 :mode :sentry :fuel 20 :owner :player}]
      (should= "player fighter [1/1] fuel:20 sentry" (ru/format-unit-status unit))))

  (it "formats transport with cargo"
    (let [unit {:type :transport :hits 1 :mode :awake :army-count 4 :owner :player}]
      (should= "player transport [1/1] cargo:4 awake" (ru/format-unit-status unit))))

  (it "formats carrier with cargo"
    (let [unit {:type :carrier :hits 8 :mode :moving :fighter-count 2 :owner :player}]
      (should= "player carrier [8/8] cargo:2 moving" (ru/format-unit-status unit))))

  (it "formats unit with marching orders"
    (let [unit {:type :army :hits 1 :mode :moving :marching-orders [5 5] :owner :player}]
      (should= "player army [1/1] march moving" (ru/format-unit-status unit))))

  (it "formats unit with flight path"
    (let [unit {:type :fighter :hits 1 :mode :moving :fuel 30 :flight-path [10 10] :owner :player}]
      (should= "player fighter [1/1] fuel:30 flight moving" (ru/format-unit-status unit)))))

(describe "format-city-status"
  (before (reset-all-atoms!))
  (it "formats player city"
    (let [cell {:type :city :city-status :player}]
      (should= "city:player" (ru/format-city-status cell nil))))

  (it "formats computer city"
    (let [cell {:type :city :city-status :computer}]
      (should= "city:computer" (ru/format-city-status cell nil))))

  (it "formats player city with production"
    (let [cell {:type :city :city-status :player}
          production {:item :army :remaining-rounds 3}]
      (should= "city:player producing:army" (ru/format-city-status cell production))))

  (it "formats player city with :none production"
    (let [cell {:type :city :city-status :player}]
      (should= "city:player producing:none" (ru/format-city-status cell :none))))

  (it "formats city with fighters"
    (let [cell {:type :city :city-status :player :fighter-count 3}]
      (should= "city:player fighters:3" (ru/format-city-status cell nil))))

  (it "formats city with marching orders"
    (let [cell {:type :city :city-status :player :marching-orders [5 5]}]
      (should= "city:player march" (ru/format-city-status cell nil))))

  (it "formats city with flight path"
    (let [cell {:type :city :city-status :player :flight-path [10 10]}]
      (should= "city:player flight" (ru/format-city-status cell nil))))

  (it "formats city with lookaround marching orders"
    (let [cell {:type :city :city-status :player :marching-orders :lookaround}]
      (should= "city:player lookaround" (ru/format-city-status cell nil)))))

(describe "format-hover-status"
  (before (reset-all-atoms!))
  (it "returns nil for empty land"
    (let [cell {:type :land}]
      (should-be-nil (ru/format-hover-status [0 0] cell nil))))

  (it "formats unit when present"
    (let [cell {:type :land :contents {:type :army :hits 1 :mode :awake :owner :player}}]
      (should= "[5,10] player army [1/1] awake" (ru/format-hover-status [5 10] cell nil))))

  (it "formats city when no unit"
    (let [cell {:type :city :city-status :player}]
      (should= "[3,7] city:player" (ru/format-hover-status [3 7] cell nil))))

  (it "prefers unit over city"
    (let [cell {:type :city :city-status :player :contents {:type :army :hits 1 :mode :awake :owner :computer}}]
      (should= "[1,2] computer army [1/1] awake" (ru/format-hover-status [1 2] cell nil)))))

(describe "key-released"
  (before (reset-all-atoms!))
  (it "resets last-key atom to nil"
    (reset! atoms/last-key :a)
    (core/key-released nil nil)
    (should-be-nil @atoms/last-key))

  (it "returns nil when last-key was already nil"
    (reset! atoms/last-key nil)
    (core/key-released nil nil)
    (should-be-nil @atoms/last-key)))

(describe "compute-screen-dimensions"
  (before (reset-all-atoms!))
  (it "calculates dimensions for standard screen"
    (let [char-width 10
          char-height 20
          screen-w 1000
          screen-h 800
          result (core/compute-screen-dimensions char-width char-height screen-w screen-h)]
      ;; cols = 1000 / 10 = 100
      (should= 100 (first (:map-size result)))
      ;; text-h = 4 * 20 = 80
      ;; rows = (800 - 80 + 7) / 20 = 727 / 20 = 36
      (should= 36 (second (:map-size result)))
      ;; map-display-w = 100 * 10 = 1000
      (should= 1000 (first (:map-screen-dimensions result)))
      ;; map-display-h = 36 * 20 = 720
      (should= 720 (second (:map-screen-dimensions result)))
      ;; text-x = 0
      (should= 0 (first (:text-area-dimensions result)))
      ;; text-y = 720 + 7 = 727
      (should= 727 (second (:text-area-dimensions result)))
      ;; text-w = 1000
      (should= 1000 (nth (:text-area-dimensions result) 2))
      ;; text-h = 80
      (should= 80 (nth (:text-area-dimensions result) 3))))

  (it "calculates dimensions for small screen"
    (let [result (core/compute-screen-dimensions 8 16 640 480)]
      ;; cols = 640 / 8 = 80
      (should= 80 (first (:map-size result)))
      ;; text-h = 4 * 16 = 64
      ;; rows = (480 - 64 + 7) / 16 = 423 / 16 = 26
      (should= 26 (second (:map-size result)))
      ;; map-display-w = 80 * 8 = 640
      (should= 640 (first (:map-screen-dimensions result)))
      ;; map-display-h = 26 * 16 = 416
      (should= 416 (second (:map-screen-dimensions result)))))

  (it "calculates dimensions for wide screen"
    (let [result (core/compute-screen-dimensions 12 24 1920 1080)]
      ;; cols = 1920 / 12 = 160
      (should= 160 (first (:map-size result)))
      ;; text-h = 4 * 24 = 96
      ;; rows = (1080 - 96 + 7) / 24 = 991 / 24 = 41
      (should= 41 (second (:map-size result))))))

(ns empire.config-spec
  (:require [speclj.core :refer :all]
            [empire.config.core :as config]))

(describe "city-color-key"
  (it "returns :player-city for :player"
    (should= :player-city (config/city-color-key :player)))
  (it "returns :computer-city for :computer"
    (should= :computer-city (config/city-color-key :computer)))
  (it "returns :free-city for :free"
    (should= :free-city (config/city-color-key :free))))

(describe "country-land-color"
  (it "returns first color for country-id 0"
    (should= [139 69 19] (config/country-land-color 0)))
  (it "wraps around for large country-ids"
    (should= (config/country-land-color 0) (config/country-land-color 8)))
  (it "returns different colors for different ids"
    (should-not= (config/country-land-color 0) (config/country-land-color 1))))

(describe "color-of"
  (it "returns green for player city"
    (should= [0 255 0] (config/color-of {:type :city :city-status :player})))

  (it "returns red for computer city"
    (should= [255 0 0] (config/color-of {:type :city :city-status :computer})))

  (it "returns white for free city"
    (should= [255 255 255] (config/color-of {:type :city :city-status :free})))

  (it "returns brown for land"
    (should= [139 69 19] (config/color-of {:type :land})))

  (it "returns blue for sea"
    (should= [0 191 255] (config/color-of {:type :sea})))

  (it "returns different browns for different country-ids on land"
    (let [color1 (config/color-of {:type :land :country-id 1})
          color2 (config/color-of {:type :land :country-id 2})]
      (should-not= color1 color2)))

  (it "returns default brown for land without country-id"
    (should= [139 69 19] (config/color-of {:type :land})))

  (it "returns standard city colors regardless of country-id"
    (should= [0 255 0] (config/color-of {:type :city :city-status :player :country-id 3}))
    (should= [255 0 0] (config/color-of {:type :city :city-status :computer :country-id 3}))
    (should= [255 255 255] (config/color-of {:type :city :city-status :free :country-id 3})))

  (it "returns sea color for sea cells"
    (should= [0 191 255] (config/color-of {:type :sea :country-id 3}))))

(describe "mode->color"
  (it "returns white for awake"
    (should= [255 255 255] (config/mode->color :awake)))

  (it "returns pink for sentry"
    (should= [255 128 128] (config/mode->color :sentry)))

  (it "returns light green for explore"
    (should= [144 238 144] (config/mode->color :explore)))

  (it "returns black for other modes"
    (should= [0 0 0] (config/mode->color :moving))
    (should= [0 0 0] (config/mode->color :unknown))))

(describe "unit->color"
  (it "returns black for units with mission :loading"
    (should= [0 0 0] (config/unit->color {:mode :awake :mission :loading}))
    (should= [0 0 0] (config/unit->color {:mode :sentry :mission :loading})))

  (it "returns mode-based color when no loading mission"
    (should= [255 255 255] (config/unit->color {:mode :awake}))
    (should= [255 128 128] (config/unit->color {:mode :sentry}))
    (should= [144 238 144] (config/unit->color {:mode :explore}))
    (should= [0 0 0] (config/unit->color {:mode :moving})))

  (it "returns white for computer armies regardless of mode"
    (should= [255 255 255] (config/unit->color {:type :army :owner :computer :mode :awake}))
    (should= [255 255 255] (config/unit->color {:type :army :owner :computer :mode :sentry}))
    (should= [255 255 255] (config/unit->color {:type :army :owner :computer :mode :explore}))
    (should= [255 255 255] (config/unit->color {:type :army :owner :computer :mode :coastline-follow})))

  (it "returns mode-based color for player armies"
    (should= [255 255 255] (config/unit->color {:type :army :owner :player :mode :awake}))
    (should= [255 128 128] (config/unit->color {:type :army :owner :player :mode :sentry}))
    (should= [144 238 144] (config/unit->color {:type :army :owner :player :mode :explore})))

  (it "returns mode-based color for computer ships"
    (should= [255 128 128] (config/unit->color {:type :transport :owner :computer :mode :sentry}))
    (should= [144 238 144] (config/unit->color {:type :destroyer :owner :computer :mode :explore}))))

(describe "compute-size-constants"
  (it "returns correct values for default 100x60 map"
    (let [result (config/compute-size-constants 100 60)]
      (should= 100 (:cols result))
      (should= 60 (:rows result))
      (should= 70 (:number-of-cities result))))

  (it "scales number-of-cities with map area"
    (let [result (config/compute-size-constants 120 200)]
      ;; area = 24000, ref = 6000, ratio = 4.0
      ;; number-of-cities = 70 * 4 = 280
      (should= 280 (:number-of-cities result))))

  (it "enforces minimum of 10 cities for tiny maps"
    (let [result (config/compute-size-constants 10 10)]
      ;; area = 100, ref = 6000, ratio = 0.0167
      ;; 70 * 0.0167 = 1.17 -> 1, but min is 10
      (should= 10 (:number-of-cities result))))

  (it "computes correct values for medium map"
    (let [result (config/compute-size-constants 80 120)]
      ;; area = 9600, ref = 6000, ratio = 1.6
      ;; number-of-cities = 70 * 1.6 = 112
      (should= 80 (:cols result))
      (should= 120 (:rows result))
      (should= 112 (:number-of-cities result)))))


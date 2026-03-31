(ns empire.player.orders-decisions-spec
  (:require [empire.player.orders-decisions :as sut]
            [speclj.core :refer :all]))

(describe "orders decisions"
  (it "returns a city marching-order action"
    (should= {:action :set-marching-orders
              :path [:marching-orders]
              :dest [4 5]}
             (sut/marching-orders-action {:type :city :city-status :player} [4 5])))

  (it "returns a carrier flight-path action with clamped destination"
    (should= {:action :set-flight-path
              :path [:contents :flight-path]
              :dest [1 1]}
             (sut/flight-path-action [[{} {}] [{} {}]]
                                     {:contents {:type :carrier :owner :player}}
                                     [9 9])))

  (it "returns a waypoint direction action for a waypoint cell"
    (should= {:action :set-waypoint-orders-by-direction
              :direction [1 0]}
             (sut/marching-orders-by-direction-action [[{}]]
                                                     {:waypoint true}
                                                     [0 0]
                                                     [1 0])))

  (it "builds marching-order state"
    (should= {:path [:marching-orders]
              :dest [7 8]
              :clear-destination? true
              :message "Marching orders set to 7,8"}
             (sut/marching-orders-state [:marching-orders] [7 8]))))

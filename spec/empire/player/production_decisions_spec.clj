(ns empire.player.production-decisions-spec
  (:require [empire.player.production-decisions :as decisions]
            [speclj.core :refer :all]))

(describe "build-produced-unit"
  (it "builds fighter with fuel"
    (should= {:type :fighter :hits 1 :mode :awake :owner :player :fuel 32}
             (decisions/build-produced-unit :fighter :player nil nil)))

  (it "applies lookaround army orders"
    (should= {:type :army :hits 1 :mode :explore :owner :player :explore-steps 50}
             (decisions/build-produced-unit :army :player :lookaround nil)))

  (it "applies fighter flight path"
    (should= [5 5]
             (:target (decisions/build-produced-unit :fighter :player nil [5 5])))))

(describe "city-production-step"
  (it "blocks when city already has contents"
    (should= {:action :blocked}
             (decisions/city-production-step {:contents {:type :army}} {:item :army :remaining-rounds 2})))

  (it "decrements unfinished production"
    (should= {:action :decrement :production {:item :army :remaining-rounds 1}}
             (decisions/city-production-step {} {:item :army :remaining-rounds 2})))

  (it "marks complete when remaining reaches zero"
    (should= {:action :complete :item :army}
             (decisions/city-production-step {} {:item :army :remaining-rounds 1}))))

(describe "production-complete-action"
  (it "clears computer production after spawning"
    (should= {:action :clear-production}
             (decisions/production-complete-action :computer [0 0] {:item :army :remaining-rounds 1})))

  (it "resets player production to full item cost after spawning"
    (should= {:action :reset-production
              :coords [0 0]
              :production {:item :army :remaining-rounds 5}}
             (decisions/production-complete-action :player [0 0] {:item :army :remaining-rounds 1}))))

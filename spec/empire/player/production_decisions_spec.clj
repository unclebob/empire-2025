(ns empire.player.production-decisions-spec
  (:require [empire.player.production-decisions :as decisions]
            [speclj.core :refer :all]))

(describe "build-produced-unit"
  (it "returns nil for fighter"
    (should-be-nil (decisions/build-produced-unit :fighter :player nil nil)))

  (it "returns nil for fighter even with flight path"
    (should-be-nil (decisions/build-produced-unit :fighter :player nil [5 5])))

  (it "applies lookaround army orders"
    (should= {:type :army :hits 1 :mode :explore :owner :player :explore-steps 50}
             (decisions/build-produced-unit :army :player :lookaround nil))))

(describe "city-production-step"
  (it "blocks when city already has contents"
    (should= {:action :blocked}
             (decisions/city-production-step {:contents {:type :army}} {:item :army :remaining-rounds 2})))

  (it "does not block fighter production when city has contents"
    (should= {:action :complete :item :fighter}
             (decisions/city-production-step {:contents {:type :army}} {:item :fighter :remaining-rounds 1})))

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

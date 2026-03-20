(ns empire.computer.transport.mission-handler-decisions-spec
  (:require [empire.computer.transport.mission-handler-decisions :as decisions]
            [speclj.core :refer :all]))

(describe "transport mission handler decisions"
  (it "classifies find-armies-for-invasion outcomes"
    (should= :start-load-for-invasion
             (decisions/find-armies-for-invasion-action {:army-count 1 :loadable-neighbor? false :reachable-path? false}))
    (should= :start-load-for-invasion
             (decisions/find-armies-for-invasion-action {:army-count 0 :loadable-neighbor? true :reachable-path? false}))
    (should= :follow-path
             (decisions/find-armies-for-invasion-action {:army-count 0 :loadable-neighbor? false :reachable-path? true}))
    (should= :revert-loading
             (decisions/find-armies-for-invasion-action {:army-count 0 :loadable-neighbor? false :reachable-path? false})))

  (it "builds load-for-invasion state and classifies the action"
    (should= {:has-armies? true :in-unload-zone? false :timed-out? true :nearby-unloadable-land? false}
             (decisions/load-for-invasion-state {:army-count 2 :in-unload-zone? false :timed-out? true :nearby-unloadable-land? false}))
    (should= :sail
             (decisions/load-for-invasion-with-armies-action {:in-unload-zone? false :timed-out? false :nearby-unloadable-land? true}))
    (should= :hold
             (decisions/load-for-invasion-without-armies-action {:timed-out? false}))
    (should= :unload
             (decisions/load-for-invasion-action {:has-armies? true :in-unload-zone? true :timed-out? false :nearby-unloadable-land? false}))
    (should= :sail
             (decisions/load-for-invasion-action {:has-armies? true :in-unload-zone? false :timed-out? true :nearby-unloadable-land? false}))
    (should= :revert-loading
             (decisions/load-for-invasion-action {:has-armies? false :in-unload-zone? false :timed-out? true :nearby-unloadable-land? false}))
    (should= :hold
             (decisions/load-for-invasion-action {:has-armies? false :in-unload-zone? false :timed-out? false :nearby-unloadable-land? false})))

  (it "classifies unloading and lake transport flows"
    (should= :crawl-and-unload (decisions/unloading-with-armies-action {:nearby-unloadable-land? true}))
    (should= :start-sailing (decisions/unloading-with-armies-action {:nearby-unloadable-land? false}))
    (should= :already-handled (decisions/lake-transport-action {:sentry? true}))
    (should= :land-locked-unload (decisions/lake-transport-action {:sentry? false :lake-locked? true :has-armies? true}))
    (should= :park-empty (decisions/lake-transport-action {:sentry? false :lake-locked? true :has-armies? false}))
    (should-be-nil (decisions/lake-transport-action {:sentry? false :lake-locked? false :has-armies? false}))))

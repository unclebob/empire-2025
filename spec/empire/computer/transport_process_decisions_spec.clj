(ns empire.computer.transport-process-decisions-spec
  (:require [empire.computer.transport-process-decisions :as decisions]
            [speclj.core :refer :all]))

(describe "transport process decisions"
  (it "normalizes mission dispatch state"
    (should= {:fix-idle? true :force-sailing? true :mission :loading}
             (decisions/transport-mission-action {:mission :loading :army-count 0 :never-reload? true}))
    (should= {:fix-idle? true :force-sailing? false :mission :sail-to-unload}
             (decisions/transport-mission-action {:mission :sailing :army-count 2 :never-reload? false}))
    (should= {:fix-idle? true :force-sailing? false :mission :sail-to-load}
             (decisions/transport-mission-action {:mission :sailing :army-count 0 :never-reload? false})))

  (it "chooses active transport action"
    (should= :skip (decisions/active-transport-action {:sentry? true :lake-handled? false}))
    (should= :skip (decisions/active-transport-action {:sentry? false :lake-handled? true}))
    (should= :dispatch (decisions/active-transport-action {:sentry? false :lake-handled? false})))

  (it "chooses overall transport process action"
    (should= :random-walk (decisions/transport-process-action {:transport? true :computer-owned? true :random-walk? true}))
    (should= :active (decisions/transport-process-action {:transport? true :computer-owned? true :random-walk? false}))
    (should-be-nil (decisions/transport-process-action {:transport? false :computer-owned? true :random-walk? false})))

  (it "maps transport missions to concrete handlers"
    (should= :loading (decisions/transport-mission-handler :loading))
    (should= :compat-sailing (decisions/transport-mission-handler :sailing))
    (should= :sail-to-unload (decisions/transport-mission-handler :sail-to-unload))
    (should= :sail-to-load (decisions/transport-mission-handler :sail-to-load))
    (should-be-nil (decisions/transport-mission-handler :bogus))))

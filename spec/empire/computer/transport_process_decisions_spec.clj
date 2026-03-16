(ns empire.computer.transport-process-decisions-spec
  (:require [empire.computer.transport-process-decisions :as decisions]
            [speclj.core :refer :all]))

(describe "transport process decisions"
  (it "normalizes mission dispatch state"
    (should= {:fix-idle? true :force-sailing? true :mission :loading}
             (decisions/transport-mission-action {:mission :loading :never-reload? true}))
    (should= {:fix-idle? true :force-sailing? false :mission :sailing}
             (decisions/transport-mission-action {:mission :sailing :never-reload? false})))

  (it "chooses active transport action"
    (should= :skip (decisions/active-transport-action {:sentry? true :lake-handled? false}))
    (should= :skip (decisions/active-transport-action {:sentry? false :lake-handled? true}))
    (should= :dispatch (decisions/active-transport-action {:sentry? false :lake-handled? false})))

  (it "chooses overall transport process action"
    (should= :random-walk (decisions/transport-process-action {:transport? true :computer-owned? true :random-walk? true}))
    (should= :active (decisions/transport-process-action {:transport? true :computer-owned? true :random-walk? false}))
    (should-be-nil (decisions/transport-process-action {:transport? false :computer-owned? true :random-walk? false}))))

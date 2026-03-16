(ns empire.computer.transport-sailing-decisions-spec
  (:require [empire.computer.transport-sailing-decisions :as decisions]
            [speclj.core :refer :all]))

(describe "transport sailing decisions"
  (it "derives sailing state"
    (should= :empty-reload (decisions/sailing-state [] 0 false))
    (should= :empty-never-reload (decisions/sailing-state [] 0 true))
    (should= :loaded-no-path (decisions/sailing-state [] 2 false))
    (should= :follow-path (decisions/sailing-state [[1 0]] 2 false)))

  (it "wraps sailing state as an action map"
    (should= {:action :follow-path}
             (decisions/sailing-action [[1 0]] 2 false)))

  (it "derives loaded-no-path state"
    (should= :launch-or-sail (decisions/loaded-no-path-state {:city-cell? true}))
    (should= :unload-or-sail (decisions/loaded-no-path-state {:adjacent-land? true}))
    (should= :unload (decisions/loaded-no-path-state {})))

  (it "wraps loaded-no-path state as an action map"
    (should= {:action :launch-or-sail}
             (decisions/loaded-no-path-action {:city-cell? true})))

  (it "derives invading state"
    (should= :threat (decisions/invading-state {:threat-near-target? true}))
    (should= :crawl (decisions/invading-state {:empty-path? true}))
    (should= :crawl (decisions/invading-state {:direct-shortcut? true}))
    (should= :random-walk (decisions/invading-state {:blocked? true :sidestep-succeeded? false}))
    (should= :sidestep (decisions/invading-state {:blocked? true :sidestep-succeeded? true}))
    (should= :path (decisions/invading-state {})))

  (it "derives crawl follow-up actions"
    (should= {:set-mission :unloading}
             (decisions/crawl-follow-up {:target? false}))
    (should= {:start-random-walk? true}
             (decisions/crawl-follow-up {:target? true :moved1? false :moved2? false :unload-zone? false}))
    (should= {:set-mission :unloading}
             (decisions/crawl-follow-up {:target? true :moved1? true :moved2? false :unload-zone? true}))
    (should-be-nil
      (decisions/crawl-follow-up {:target? true :moved1? true :moved2? false :unload-zone? false})))

  (it "derives blocked path follow-up actions"
    (should= {:start-random-walk? true}
             (decisions/blocked-path-follow-up false))
    (should-be-nil
      (decisions/blocked-path-follow-up true))))

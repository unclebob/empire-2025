(ns empire.computer.transport-sailing-decisions-spec
  (:require [empire.computer.transport-sailing-decisions :as decisions]
            [speclj.core :refer :all]))

(describe "transport sailing decisions"
  (it "derives sailing state"
    (should= :empty-reload (decisions/sailing-state [] 0 false))
    (should= :empty-never-reload (decisions/sailing-state [] 0 true))
    (should= :loaded-no-path (decisions/sailing-state [] 2 false))
    (should= :follow-path (decisions/sailing-state [[1 0]] 2 false)))

  (it "derives loaded-no-path state"
    (should= :launch-or-sail (decisions/loaded-no-path-state {:city-cell? true}))
    (should= :unload-or-sail (decisions/loaded-no-path-state {:adjacent-land? true}))
    (should= :unload (decisions/loaded-no-path-state {})))

  (it "derives invading state"
    (should= :threat (decisions/invading-state {:threat-near-target? true}))
    (should= :crawl (decisions/invading-state {:empty-path? true}))
    (should= :crawl (decisions/invading-state {:direct-shortcut? true}))
    (should= :random-walk (decisions/invading-state {:blocked? true :sidestep-succeeded? false}))
    (should= :sidestep (decisions/invading-state {:blocked? true :sidestep-succeeded? true}))
    (should= :path (decisions/invading-state {}))))

(ns empire.application.state-spec
  (:require [speclj.core :refer :all]
            [empire.application.state :as state]))

(describe "application state boundary"
  (it "apply-command! loads, executes, checks, and saves world"
    (let [store (atom {:hp 10})
          checked (atom nil)
          ctx {:load-world (fn [] @store)
               :save-world! (fn [w] (reset! store w))
               :execute-command (fn [world command]
                                  {:world (update world :hp - (:damage command))
                                   :events [{:event :damaged}]})
               :check-invariants (fn [world] (reset! checked world))}
          result (state/apply-command! ctx {:damage 3})]
      (should= {:hp 7} @store)
      (should= {:hp 7} @checked)
      (should= {:hp 7} (:world result))
      (should= [{:event :damaged}] (:events result))))

  (it "apply-events! executes each event through :execute-event"
    (let [store (atom {:count 0})
          ctx {:load-world (fn [] @store)
               :save-world! (fn [w] (reset! store w))
               :execute-event (fn [world event]
                                (update world :count + (:delta event)))}]
      (state/apply-events! ctx [{:delta 2} {:delta 3}])
      (should= {:count 5} @store)))

  (it "set-world! writes world through boundary"
    (let [store (atom {:old true})
          ctx {:load-world (fn [] @store)
               :save-world! (fn [w] (reset! store w))
               :execute-command (fn [world _] world)}]
      (state/set-world! ctx {:new true})
      (should= {:new true} @store)))

  (it "update-world! transforms world through boundary"
    (let [store (atom {:n 1})
          ctx {:load-world (fn [] @store)
               :save-world! (fn [w] (reset! store w))
               :execute-command (fn [world _] world)}]
      (state/update-world! ctx update :n inc)
      (should= {:n 2} @store)))

  (it "throws if required context functions are missing"
    (should-throw clojure.lang.ExceptionInfo
                  (state/apply-command! {} {:cmd :noop}))))

(run-specs)

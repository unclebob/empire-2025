;; mutation-tested: no
(ns empire.application.impl.state
  (:require [clojure.string :as str]
            [empire.application.state :as app-state]))

(defn- require-fn
  [ctx k]
  (let [f (get ctx k)]
    (when-not (fn? f)
      (throw (ex-info (str "Missing function in state context: " (name k))
                      {:missing k
                       :provided (->> (keys ctx) (map name) sort vec)})))
    f))

(defmethod app-state/with-invariants! :default
  [ctx world]
  (if-let [check-fn (:check-invariants ctx)]
    (do
      (check-fn world)
      world)
    world))

(defmethod app-state/apply-command! :default
  [ctx command]
  (let [load-world-fn (require-fn ctx :load-world)
        save-world-fn (require-fn ctx :save-world!)
        execute-command-fn (require-fn ctx :execute-command)
        world (load-world-fn)
        result (execute-command-fn world command)
        next-world (if (and (map? result) (contains? result :world))
                     (:world result)
                     result)
        events (if (and (map? result) (contains? result :events))
                 (:events result)
                 [])
        checked-world (app-state/with-invariants! ctx next-world)]
    (save-world-fn checked-world)
    {:world checked-world
     :events events}))

(defmethod app-state/apply-events! :default
  [ctx events]
  (let [execute-event-fn (require-fn ctx :execute-event)]
    (reduce (fn [_ event]
              (app-state/apply-command! (assoc ctx :execute-command execute-event-fn) event))
            nil
            events)))

(defmethod app-state/set-world! :default
  [ctx world]
  (:world (app-state/apply-command! (assoc ctx :execute-command (fn [_ _] world))
                                    {:op :set-world})))

(defmethod app-state/update-world! :default
  [ctx f & args]
  (:world (app-state/apply-command! (assoc ctx :execute-command
                                           (fn [world _]
                                             (apply f world args)))
                                    {:op :update-world})))

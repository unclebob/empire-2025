;; mutation-tested: no
(ns empire.application.state
  "Application-level state mutation boundary."
  (:require [clojure.string :as str]))

(defn- require-fn
  [ctx k]
  (let [f (get ctx k)]
    (when-not (fn? f)
      (throw (ex-info (str "Missing function in state context: " (name k))
                      {:missing k
                       :provided (->> (keys ctx) (map name) sort vec)})))
    f))

(defn with-invariants!
  "Runs invariant check on world if :check-invariants is provided.
   Returns world."
  [ctx world]
  (if-let [check-fn (:check-invariants ctx)]
    (do
      (check-fn world)
      world)
    world))

(defn apply-command!
  "Loads world, applies one command through :execute-command, checks invariants,
   saves world, and returns {:world ... :events [...]}. execute-command may return:
   - next-world map
   - {:world next-world :events [...]}"
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
        checked-world (with-invariants! ctx next-world)]
    (save-world-fn checked-world)
    {:world checked-world
     :events events}))

(defn apply-events!
  "Applies events sequentially using :execute-event through the same state boundary."
  [ctx events]
  (let [execute-event-fn (require-fn ctx :execute-event)]
    (reduce (fn [_ event]
              (apply-command! (assoc ctx :execute-command execute-event-fn) event))
            nil
            events)))

(defn set-world!
  "Boundary helper to replace world state without exposing direct store mutation."
  [ctx world]
  (:world (apply-command! (assoc ctx :execute-command (fn [_ _] world))
                          {:op :set-world})))

(defn update-world!
  "Boundary helper to transform world state with function f."
  [ctx f & args]
  (:world (apply-command! (assoc ctx :execute-command
                                 (fn [world _]
                                   (apply f world args)))
                          {:op :update-world})))

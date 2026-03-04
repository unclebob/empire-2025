;; mutation-tested: no
(ns empire.application.state
  "Application-level state mutation boundary.")

(def ^:private impl-loaded?
  (delay
    (try
      (require 'empire.application.impl.state)
      true
      (catch #?(:clj Throwable :cljs :default) _
        false))))

(defn- ensure-impl-loaded!
  []
  (force impl-loaded?)
  nil)

(defmulti with-invariants!
  "Runs invariant check on world if :check-invariants is provided.
   Returns world."
  (fn [& _]
    (ensure-impl-loaded!)
    :default))

(defmulti apply-command!
  "Loads world, applies one command through :execute-command, checks invariants,
   saves world, and returns {:world ... :events [...]}. execute-command may return:
   - next-world map
   - {:world next-world :events [...]}"
  (fn [& _]
    (ensure-impl-loaded!)
    :default))

(defmulti apply-events!
  "Applies events sequentially using :execute-event through the same state boundary."
  (fn [& _]
    (ensure-impl-loaded!)
    :default))

(defmulti set-world!
  "Boundary helper to replace world state without exposing direct store mutation."
  (fn [& _]
    (ensure-impl-loaded!)
    :default))

(defmulti update-world!
  "Boundary helper to transform world state with function f."
  (fn [& _]
    (ensure-impl-loaded!)
    :default))

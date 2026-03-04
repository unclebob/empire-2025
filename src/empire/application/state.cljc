;; mutation-tested: no
(ns empire.application.state
  "Application-level state mutation boundary.")

(defmulti with-invariants!
  "Runs invariant check on world if :check-invariants is provided.
   Returns world."
  (fn [& _] :default))

(defmulti apply-command!
  "Loads world, applies one command through :execute-command, checks invariants,
   saves world, and returns {:world ... :events [...]}. execute-command may return:
   - next-world map
   - {:world next-world :events [...]}"
  (fn [& _] :default))

(defmulti apply-events!
  "Applies events sequentially using :execute-event through the same state boundary."
  (fn [& _] :default))

(defmulti set-world!
  "Boundary helper to replace world state without exposing direct store mutation."
  (fn [& _] :default))

(defmulti update-world!
  "Boundary helper to transform world state with function f."
  (fn [& _] :default))

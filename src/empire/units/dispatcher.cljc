(ns empire.units.dispatcher
  "Dispatches to the appropriate unit module based on unit type.
   Provides a unified interface for accessing unit configuration and behavior.

   Uses multimethods for extensibility - each unit module registers its own
   implementations via defmethod.")

;; Configuration multimethods - dispatch on unit-type keyword
(defmulti speed
  "Returns movement speed for the unit type."
  identity)

(defmulti cost
  "Returns production cost for the unit type."
  identity)

(defmulti hits
  "Returns max hit points for the unit type."
  identity)

(defmulti strength
  "Returns combat strength for the unit type."
  identity)

(defmulti display-char
  "Returns display character for the unit type."
  identity)

(defmulti visibility-radius
  "Returns visibility radius for the unit type."
  identity)

;; Behavior multimethods
(defmulti initial-state
  "Returns initial state fields for a new unit of this type."
  identity)

(defmulti can-move-to?
  "Returns true if unit type can move to the given cell."
  (fn [unit-type _cell] unit-type))

(defmulti needs-attention?
  "Returns true if the unit needs player attention."
  (fn [unit] (:type unit)))

;; Default implementations for missing types
(defmethod speed :default [_] nil)
(defmethod cost :default [_] nil)
(defmethod hits :default [_] nil)
(defmethod strength :default [_] nil)
(defmethod display-char :default [_] nil)
(defmethod visibility-radius :default [_] nil)
(defmethod initial-state :default [_] {})
(defmethod can-move-to? :default [_ _] false)
(defmethod needs-attention? :default [_] false)

;; Naval unit check
(def naval-units #{:transport :patrol-boat :destroyer :submarine :carrier :battleship})

(defn naval-unit? [unit-type]
  (contains? naval-units unit-type))

;; Registration macro
(defmacro defunit
  "Register a unit type with the dispatcher.
   config: map with :speed :cost :hits :strength :display-char :visibility-radius
   initial-state-fn: 0-arity fn returning initial state map
   can-move-to-fn: 1-arity fn [cell] -> boolean
   needs-attention-fn: 1-arity fn [unit] -> boolean"
  [unit-type config initial-state-fn can-move-to-fn needs-attention-fn]
  `(do
     (defmethod speed ~unit-type [~'_] ~(:speed config))
     (defmethod cost ~unit-type [~'_] ~(:cost config))
     (defmethod hits ~unit-type [~'_] ~(:hits config))
     (defmethod strength ~unit-type [~'_] ~(:strength config))
     (defmethod display-char ~unit-type [~'_] ~(:display-char config))
     (defmethod visibility-radius ~unit-type [~'_] ~(:visibility-radius config))
     (defmethod initial-state ~unit-type [~'_] (~initial-state-fn))
     (defmethod can-move-to? ~unit-type [~'_ cell#] (~can-move-to-fn cell#))
     (defmethod needs-attention? ~unit-type [unit#] (~needs-attention-fn unit#))))

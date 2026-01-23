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

;; mutation-tested: no
(ns empire.application.coords)

(def ^:private impl-loaded?
  (delay
    (try
      (require 'empire.application.impl.coords)
      true
      (catch #?(:clj Throwable :cljs :default) _
        false))))

(defn- ensure-impl-loaded!
  []
  (force impl-loaded?)
  nil)

(defmulti screen->cell
  "Converts screen pixel coordinates to map cell coordinates [row col].
   Note: Uses legacy formula where width is divided by rows and height by cols."
  (fn [& _]
    (ensure-impl-loaded!)
    :default))

;; mutation-tested: no
(ns empire.domain.core.messages)

(defmulti expires-at
  "Returns an absolute expiration timestamp in milliseconds."
  (fn [& _] :default))

(defmethod expires-at :default
  [now-ms duration-ms]
  (+ now-ms duration-ms))

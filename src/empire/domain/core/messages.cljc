;; mutation-tested: no
(ns empire.domain.core.messages)

(defn expires-at
  "Returns an absolute expiration timestamp in milliseconds."
  [now-ms duration-ms]
  (+ now-ms duration-ms))

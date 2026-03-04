;; mutation-tested: no
(ns empire.domain.core.impl.messages
  (:require [empire.domain.core.messages :as messages]))

(defmethod messages/expires-at :default
  [now-ms duration-ms]
  (+ now-ms duration-ms))

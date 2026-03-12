;; mutation-tested: no
(ns empire.config.domain.core.messages)

(defn expires-at
  "Returns an absolute expiration timestamp in milliseconds."
  [now-ms duration-ms]
  (+ now-ms duration-ms))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T11:59:22.390375-05:00", :module-hash "-1714267686", :forms [{:id "form/0/ns", :kind "ns", :line 2, :end-line 2, :hash "-1631013525"} {:id "defn/expires-at", :kind "defn", :line 4, :end-line 7, :hash "-2104806780"}]}
;; clj-mutate-manifest-end

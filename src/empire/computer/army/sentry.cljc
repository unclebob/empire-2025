(ns empire.computer.army.sentry
  (:require [empire.state.api :as sa]
            [empire.game-mechanics.visibility :as visibility]
            [empire.game-mechanics.debug.integrity :as integrity]))

(defn- log-missing-army-contents!
  [reason context]
  (integrity/write-stacktrace-error-log!
   "army-error"
   (merge {:reason reason} context)
   (ex-info "Army sentry update attempted without unit contents"
            (merge {:reason reason} context))))

(defn set-sentry-mode-if-unit!
  [pos context]
  (if (get-in (sa/read-state :computer-map) (conj pos :contents))
    (do
      (sa/update-world! update-in (conj pos :contents) assoc :mode :sentry)
      (visibility/sync-ai-unit-to-computer-map! pos))
    (log-missing-army-contents! :missing-contents-for-sentry
                                (assoc context
                                       :pos pos
                                       :cell (get-in (sa/read-state :computer-map) pos)))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-05-07T14:03:37.485259-05:00", :module-hash "-1845172154", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 4, :hash "1651526937"} {:id "defn-/log-missing-army-contents!", :kind "defn-", :line 6, :end-line 12, :hash "-385626896"} {:id "defn/set-sentry-mode-if-unit!", :kind "defn", :line 14, :end-line 23, :hash "-173234480"}]}
;; clj-mutate-manifest-end

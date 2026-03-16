(ns empire.game.loop.round-setup.satellite-decisions)

(defn satellite-step-action
  [satellite steps-left]
  (cond
    (not satellite) {:action :missing}
    (<= (:turns-remaining satellite 0) 0) {:action :expire}
    (zero? steps-left) {:action :finish-round}
    :else {:action :move}))

(defn finish-round-action
  [satellite]
  (let [new-turns (dec (:turns-remaining satellite 1))]
    (if (<= new-turns 0)
      {:action :expire}
      {:action :decrement-turns
       :turns-remaining new-turns})))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-15T16:51:57.488572-05:00", :module-hash "1020877673", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "705496148"} {:id "defn/satellite-step-action", :kind "defn", :line 3, :end-line 9, :hash "-501197628"} {:id "defn/finish-round-action", :kind "defn", :line 11, :end-line 17, :hash "-1103773186"}]}
;; clj-mutate-manifest-end

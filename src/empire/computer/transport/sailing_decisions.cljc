(ns empire.computer.transport.sailing-decisions)

(defn sailing-state
  [sail-path army-count never-reload?]
  (or ({[true true false] :empty-reload
        [true true true] :empty-never-reload
      [true false false] :loaded-no-path
      [true false true] :loaded-no-path}
       [(empty? sail-path) (zero? army-count) (boolean never-reload?)])
      (when (seq sail-path) :follow-path)))

(defn sailing-action
  [sail-path army-count never-reload?]
  {:action (sailing-state sail-path army-count never-reload?)})

(defn loaded-no-path-state
  [{:keys [city-cell? adjacent-land?]}]
  (cond
    city-cell? :launch-or-sail
    adjacent-land? :unload-or-sail
    :else :unload))

(defn loaded-no-path-action
  [context]
  {:action (loaded-no-path-state context)})

(defn invading-state
  [{:keys [threat-near-target? empty-path? direct-shortcut? blocked? sidestep-succeeded?]}]
  (cond
    threat-near-target? :threat
    (or empty-path? direct-shortcut?) :crawl
    blocked? (if sidestep-succeeded? :sidestep :random-walk)
    :else :path))

(defn invading-action
  [context]
  {:action (invading-state context)})

(defn crawl-follow-up
  [{:keys [target? moved1? moved2? unload-zone?]}]
  (cond
    (not target?) {:set-mission :unloading}
    (and (not moved1?) (not moved2?)) {:start-random-walk? true}
    unload-zone? {:set-mission :unloading}
    :else nil))

(defn blocked-path-follow-up
  [sidestep-succeeded?]
  (case (invading-state {:blocked? true
                         :sidestep-succeeded? sidestep-succeeded?})
    :random-walk {:start-random-walk? true}
    nil))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-16T14:42:01.277147-05:00", :module-hash "-914068400", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "776078683"} {:id "defn/sailing-state", :kind "defn", :line 3, :end-line 10, :hash "-1805949570"} {:id "defn/sailing-action", :kind "defn", :line 12, :end-line 14, :hash "-766036911"} {:id "defn/loaded-no-path-state", :kind "defn", :line 16, :end-line 21, :hash "409978249"} {:id "defn/loaded-no-path-action", :kind "defn", :line 23, :end-line 25, :hash "-537020520"} {:id "defn/invading-state", :kind "defn", :line 27, :end-line 33, :hash "-249598690"} {:id "defn/invading-action", :kind "defn", :line 35, :end-line 37, :hash "808668000"} {:id "defn/crawl-follow-up", :kind "defn", :line 39, :end-line 45, :hash "1917366854"} {:id "defn/blocked-path-follow-up", :kind "defn", :line 47, :end-line 52, :hash "1608636573"}]}
;; clj-mutate-manifest-end

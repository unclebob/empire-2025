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

(defn- blocked-invading-state
  [sidestep-succeeded?]
  (if sidestep-succeeded? :sidestep :random-walk))

(defn invading-state
  [{:keys [threat-near-target? empty-path? direct-shortcut? blocked? sidestep-succeeded?]}]
  (cond
    threat-near-target? :threat
    (or empty-path? direct-shortcut?) :crawl
    blocked? (blocked-invading-state sidestep-succeeded?)
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
;; {:version 1, :tested-at "2026-09-01T16:12:13.611489-05:00", :module-hash "912895785", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "-547962594"} {:id "defn/sailing-state", :kind "defn", :line 3, :end-line nil, :hash "-1805949570"} {:id "defn/sailing-action", :kind "defn", :line 12, :end-line nil, :hash "-766036911"} {:id "defn/loaded-no-path-state", :kind "defn", :line 16, :end-line nil, :hash "409978249"} {:id "defn/loaded-no-path-action", :kind "defn", :line 23, :end-line nil, :hash "-537020520"} {:id "defn-/blocked-invading-state", :kind "defn-", :line 27, :end-line nil, :hash "1184724444"} {:id "defn/invading-state", :kind "defn", :line 31, :end-line nil, :hash "1102464769"} {:id "defn/invading-action", :kind "defn", :line 39, :end-line nil, :hash "808668000"} {:id "defn/crawl-follow-up", :kind "defn", :line 43, :end-line nil, :hash "1917366854"} {:id "defn/blocked-path-follow-up", :kind "defn", :line 51, :end-line nil, :hash "1608636573"}]}
;; clj-mutate-manifest-end

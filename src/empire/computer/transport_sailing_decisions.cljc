(ns empire.computer.transport-sailing-decisions)

(defn sailing-state
  [sail-path army-count never-reload?]
  (or ({[true true false] :empty-reload
        [true true true] :empty-never-reload
        [true false false] :loaded-no-path
        [true false true] :loaded-no-path}
       [(empty? sail-path) (zero? army-count) (boolean never-reload?)])
      (when (seq sail-path) :follow-path)))

(defn loaded-no-path-state
  [{:keys [city-cell? adjacent-land?]}]
  (cond
    city-cell? :launch-or-sail
    adjacent-land? :unload-or-sail
    :else :unload))

(defn invading-state
  [{:keys [threat-near-target? empty-path? direct-shortcut? blocked? sidestep-succeeded?]}]
  (cond
    threat-near-target? :threat
    (or empty-path? direct-shortcut?) :crawl
    blocked? (if sidestep-succeeded? :sidestep :random-walk)
    :else :path))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-16T09:35:02.19357-05:00", :module-hash "-642228106", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "776078683"} {:id "defn/sailing-state", :kind "defn", :line 3, :end-line 10, :hash "-1805949570"} {:id "defn/loaded-no-path-state", :kind "defn", :line 12, :end-line 17, :hash "409978249"} {:id "defn/invading-state", :kind "defn", :line 19, :end-line 25, :hash "-249598690"}]}
;; clj-mutate-manifest-end

(ns empire.ui.util.rendering.format.production
  (:require [empire.game.production-status :as production-status]))

(defn format-production-status
  "Formats production status string: unit counts and exploration %.
   Format: A:n F:n T:n D:n S:n P:n C:n B:n Z:n | nn%"
  [game-map player-map]
  (production-status/format-production-status game-map player-map))

(defn- parse-count-entry
  [entry]
  (let [[label count-str] (clojure.string/split entry #":" 2)
        count (when (seq count-str)
                (parse-long count-str))]
    {:label label
     :count count}))

(defn- compact-nonzero-units
  [counts-part]
  (->> (clojure.string/split (or counts-part "") #" ")
       (map parse-count-entry)
       (keep (fn [{:keys [label count]}]
               (when (and label count (pos? count))
                 (str label count))))
       vec))

(defn- summarize-nonzero-units
  [nonzero-units]
  (let [visible-units (take 3 nonzero-units)
        hidden-count (- (count nonzero-units) (count visible-units))]
    (str (clojure.string/join " " visible-units)
         (when (pos? hidden-count)
           (str " +" hidden-count)))))

(defn- parseable-production-status?
  [counts-part nonzero-units]
      (or (seq nonzero-units)
      (clojure.string/includes? (or counts-part "") "A:")))

(defn- join-production-summary
  [units-summary pct-part]
  (str units-summary
       (when (seq pct-part)
         (str " | " pct-part))))

(defn compact-production-status
  "Compacts a production status string for the HUD status line."
  [production-status]
  (when (seq production-status)
    (let [[counts-part pct-part] (clojure.string/split production-status #" \| " 2)
          nonzero-units (compact-nonzero-units counts-part)
          parseable? (parseable-production-status? counts-part nonzero-units)
          units-summary (if (seq nonzero-units)
                          (summarize-nonzero-units nonzero-units)
                          "0 units")]
      (if-not parseable?
        production-status
        (join-production-summary units-summary pct-part)))))

(defn hidden-production-status
  "Returns the hidden non-zero unit counts omitted from the compact HUD summary."
  [production-status]
  (when (seq production-status)
    (let [[counts-part] (clojure.string/split production-status #" \| " 2)
          nonzero-units (compact-nonzero-units counts-part)
          hidden-units (drop 3 nonzero-units)]
      (when (seq hidden-units)
        (clojure.string/join " " hidden-units)))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T12:08:22.029415-05:00", :module-hash "1512738124", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 2, :hash "-829571969"} {:id "defn/format-production-status", :kind "defn", :line 4, :end-line 8, :hash "1957771995"} {:id "defn-/parse-count-entry", :kind "defn-", :line 10, :end-line 16, :hash "-1237319404"} {:id "defn-/compact-nonzero-units", :kind "defn-", :line 18, :end-line 25, :hash "131416162"} {:id "defn-/summarize-nonzero-units", :kind "defn-", :line 27, :end-line 33, :hash "-489438097"} {:id "defn-/parseable-production-status?", :kind "defn-", :line 35, :end-line 38, :hash "1843558349"} {:id "defn-/join-production-summary", :kind "defn-", :line 40, :end-line 44, :hash "-2105547342"} {:id "defn/compact-production-status", :kind "defn", :line 46, :end-line 58, :hash "644266267"} {:id "defn/hidden-production-status", :kind "defn", :line 60, :end-line 68, :hash "-1276929923"}]}
;; clj-mutate-manifest-end

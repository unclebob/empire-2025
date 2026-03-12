(ns empire.acceptance.generator.utils)

(def city-chars #{"O" "X" "+"})
(def cell-label-chars #{"=" "%"})

(defn city-spec?
  "Returns true if spec refers to a city (starts with O, X, or +)."
  [spec]
  (contains? city-chars (str (first spec))))

(defn target-pos-expr [target]
  (cond
    (city-spec? target)
    (str "(:pos (h/get-city \"" target "\"))")

    (contains? cell-label-chars target)
    (str "(:pos (h/get-cell \"" target "\"))")

    :else
    (str "(:pos (h/get-unit \"" target "\"))")))

(defn area->state-key [area]
  (case area
    :attention :attention-message
    :turn :turn-message
    :error :error-message))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T11:56:29.586083-05:00", :module-hash "1658949067", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "-1522984869"} {:id "def/city-chars", :kind "def", :line 3, :end-line 3, :hash "-928646798"} {:id "def/cell-label-chars", :kind "def", :line 4, :end-line 4, :hash "-82215533"} {:id "defn/city-spec?", :kind "defn", :line 6, :end-line 9, :hash "-1520742944"} {:id "defn/target-pos-expr", :kind "defn", :line 11, :end-line 20, :hash "1110509128"} {:id "defn/area->state-key", :kind "defn", :line 22, :end-line 26, :hash "-397603595"}]}
;; clj-mutate-manifest-end

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

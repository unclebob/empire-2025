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
    (str "(:pos (get-test-city atoms/game-map \"" target "\"))")

    (contains? cell-label-chars target)
    (str "(:pos (get-test-cell atoms/game-map \"" target "\"))")

    :else
    (str "(:pos (get-test-unit atoms/game-map \"" target "\"))")))

(defn area->atom [area]
  (case area
    :attention "atoms/attention-message"
    :turn "atoms/turn-message"
    :error "atoms/error-message"))

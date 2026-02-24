(ns empire.paren-check.core)

(defn- process-char [state c next-c]
  (let [{:keys [mode depth line escape skip]} state]
    (cond
      skip
      (assoc state :skip false)

      escape
      (assoc state :escape false)

      (= mode :comment)
      (if (= c \newline)
        (assoc state :mode :normal :line (inc line))
        state)

      (= mode :string)
      (cond
        (= c \\) (assoc state :escape true)
        (= c \") (assoc state :mode :normal)
        (= c \newline) (update state :line inc)
        :else state)

      (= mode :regex)
      (cond
        (= c \\) (assoc state :escape true)
        (= c \") (assoc state :mode :normal)
        (= c \newline) (update state :line inc)
        :else state)

      ;; :normal mode
      (= c \;) (assoc state :mode :comment)
      (= c \\) (assoc state :escape true)
      (= c \") (assoc state :mode :string)
      (and (= c \#) (= next-c \")) (assoc state :mode :regex :skip true)
      (= c \newline) (update state :line inc)
      (= c \() (update state :depth inc)
      (= c \)) (update state :depth dec)
      :else state)))

(defn scan [text]
  (let [chars (vec text)
        n (count chars)
        init {:mode :normal :depth 0 :line 1 :escape false
              :skip false :errors [] :stack []}
        result (reduce
                 (fn [state i]
                   (let [c (nth chars i)
                         next-c (when (< (inc i) n)
                                  (nth chars (inc i)))]
                     (process-char state c next-c)))
                 init
                 (range n))]
    (select-keys result [:errors :depth])))

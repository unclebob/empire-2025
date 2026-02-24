(ns empire.paren-check.core)

(def ^:private speclj-keywords
  #{"describe" "context" "it" "before" "before-all"
    "after" "with-stubs" "with" "around"})

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

(defn- extract-token [chars i]
  (let [n (count chars)
        start (inc i)]
    (when (< start n)
      (let [end (loop [j start]
                  (if (or (>= j n)
                          (let [ch (nth chars j)]
                            (or (= ch \space) (= ch \newline)
                                (= ch \tab) (= ch \()
                                (= ch \)) (= ch \"))))
                    j
                    (recur (inc j))))]
        (when (> end start)
          (apply str (subvec chars start end)))))))

(defn- pop-form [form-stack]
  (let [completed (peek form-stack)
        stack (pop form-stack)
        entry (dissoc completed :depth)]
    (if (empty? stack)
      {:stack [] :form entry}
      (let [parent (peek stack)
            children (conj (or (:children parent) []) entry)
            parent (assoc parent :children children)]
        {:stack (conj (pop stack) parent) :form nil}))))

(defn scan [text]
  (let [chars (vec text)
        n (count chars)
        init {:mode :normal :depth 0 :line 1 :escape false
              :skip false :errors [] :form-stack [] :forms []}
        result (reduce
                 (fn [state i]
                   (let [c (nth chars i)
                         next-c (when (< (inc i) n)
                                  (nth chars (inc i)))
                         old-depth (:depth state)
                         old-mode (:mode state)
                         state (process-char state c next-c)
                         new-depth (:depth state)]
                     (cond
                       ;; open paren in normal mode: check for speclj keyword
                       (and (= old-mode :normal) (= c \()
                            (> new-depth old-depth))
                       (let [token (extract-token chars i)]
                         (if (and token (speclj-keywords token))
                           (update state :form-stack conj
                                   {:form token :line (:line state)
                                    :depth old-depth})
                           state))

                       ;; close paren: check if form completed
                       (and (= old-mode :normal) (= c \))
                            (< new-depth old-depth)
                            (seq (:form-stack state))
                            (= new-depth (:depth (peek (:form-stack state)))))
                       (let [{:keys [stack form]} (pop-form (:form-stack state))]
                         (if form
                           (-> state
                               (assoc :form-stack stack)
                               (update :forms conj form))
                           (assoc state :form-stack stack)))

                       :else state)))
                 init
                 (range n))]
    (select-keys result [:errors :depth :forms])))

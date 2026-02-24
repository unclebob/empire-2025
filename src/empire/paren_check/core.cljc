(ns empire.paren-check.core
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

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

(defn- validate-nesting [form line form-stack]
  (when-let [parent (peek form-stack)]
    (let [parent-form (:form parent)]
      (cond
        (= parent-form "it")
        (str "ERROR line " line ": (" form ") inside (it) at line " (:line parent))

        (and (= parent-form "describe") (= form "describe"))
        (str "ERROR line " line ": (describe) inside (describe) at line " (:line parent))

        (and (= parent-form "context") (= form "context"))
        (str "ERROR line " line ": (context) inside (context) at line " (:line parent))

        (and (= parent-form "context") (= form "describe"))
        (str "ERROR line " line ": (describe) inside (context) at line " (:line parent))))))

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
                           (let [error (validate-nesting token (:line state) (:form-stack state))]
                             (cond-> state
                               error (update :errors conj error)
                               true (update :form-stack conj
                                            {:form token :line (:line state)
                                             :depth old-depth})))
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
                 (range n))
        eof-line (:line result)
        unclosed-errors (mapv (fn [entry]
                                (str "ERROR line " eof-line
                                     ": unclosed (" (:form entry)
                                     ") from line " (:line entry)))
                              (:form-stack result))
        result (update result :errors into unclosed-errors)]
    (select-keys result [:errors :depth :forms])))

(defn check-file [path]
  (let [text (slurp path)
        result (scan text)
        errors (:errors result)]
    (if (empty? errors)
      "OK"
      (str/join "\n" errors))))

(defn- find-clj-files [dir]
  (let [f (io/file dir)]
    (when (.isDirectory f)
      (->> (file-seq f)
           (filter #(and (.isFile %) (str/ends-with? (.getName %) ".clj")))
           (sort-by #(.getAbsolutePath %))))))

(defn check-directory [path]
  (mapv (fn [f]
          {:file (.getAbsolutePath f)
           :result (check-file (.getAbsolutePath f))})
        (find-clj-files path)))

(defn -main [& args]
  (let [paths (remove #(str/starts-with? % "--") args)
        has-errors? (atom false)]
    (doseq [path paths]
      (let [f (io/file path)]
        (cond
          (.isFile f)
          (let [result (check-file path)]
            (println (str path ": " result))
            (when (not= "OK" result)
              (reset! has-errors? true)))

          (.isDirectory f)
          (doseq [{:keys [file result]} (check-directory path)]
            (println (str file ": " result))
            (when (not= "OK" result)
              (reset! has-errors? true)))

          :else
          (println (str path ": not found")))))
    (System/exit (if @has-errors? 1 0))))

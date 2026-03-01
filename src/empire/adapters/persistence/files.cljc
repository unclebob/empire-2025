;; mutation-tested: no
(ns empire.adapters.persistence.files
  (:require [clojure.edn :as edn]
            [empire.application.ports :as ports]))

(defn- timestamp []
  (let [now (java.time.LocalDateTime/now)
        fmt (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd-HHmmss")]
    (.format now fmt)))

(defrecord FilesPersistenceAdapter []
  ports/PersistencePort
  (list-saves [_ dir-path]
    (let [dir (java.io.File. dir-path)]
      (if (.exists dir)
        (->> (.listFiles dir)
             (filter #(.endsWith (.getName %) ".edn"))
             (sort-by #(- (.lastModified %)))
             (mapv #(.getName %)))
        [])))
  (save-state! [_ dir-path data]
    (let [dir (java.io.File. dir-path)]
      (when-not (.exists dir)
        (.mkdirs dir))
      (let [filename (str "save-" (timestamp) ".edn")
            filepath (str dir-path "/" filename)]
        (spit filepath (pr-str data))
        filename)))
  (load-state [_ dir-path filename]
    (let [filepath (str dir-path "/" filename)]
      (edn/read-string (slurp filepath)))))

(defn persistence-adapter
  []
  (->FilesPersistenceAdapter))

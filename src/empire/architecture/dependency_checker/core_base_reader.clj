(ns empire.architecture.dependency-checker.core-base-reader
  (:require [clojure.java.io :as io]))

(defn read-forms
  [file]
  (with-open [r (java.io.PushbackReader. (io/reader file))]
    (binding [*read-eval* false]
      (loop [acc []]
        (let [form (try
                     (read {:eof ::eof :read-cond :allow :features #{:clj}} r)
                     (catch Exception _
                       ::read-error))]
          (cond
            (= ::eof form) acc
            (= ::read-error form) acc
            :else (recur (conj acc form))))))))

(defn ns-form?
  [form]
  (and (seq? form) (= 'ns (first form))))

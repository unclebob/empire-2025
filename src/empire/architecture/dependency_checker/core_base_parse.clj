(ns empire.architecture.dependency-checker.core-base-parse
  (:require [empire.architecture.dependency-checker.core-base-config :as cfg]
            [empire.architecture.dependency-checker.core-base-dependencies :as deps]
            [empire.architecture.dependency-checker.core-base-reader :as reader]
            [empire.architecture.dependency-checker.core-base-stats :as stats]))

(defn parse-source-entry
  [file component-rules]
  (let [forms (reader/read-forms file)
        ns-decl (first (filter reader/ns-form? forms))]
    (when ns-decl
      (let [ns-name (second ns-decl)
            var-stats (stats/var-stats forms)]
        {:file (.getPath file)
         :namespace ns-name
         :component (cfg/component-for-ns component-rules ns-name)
         :requires (deps/extract-dependencies forms ns-decl)
         :warnings (deps/extract-dynamic-lookup-warnings forms)
         :public-count (:public-count var-stats)
         :abstract-count (:abstract-count var-stats)}))))

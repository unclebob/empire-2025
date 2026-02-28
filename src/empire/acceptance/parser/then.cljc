;; mutation-tested: 2026-02-28
(ns empire.acceptance.parser.then
  (:require [empire.acceptance.parser.then.parse :as parse]))

(defn parse-then
  "Parse THEN lines into IR. Returns {:thens [...]}"
  [lines context]
  (parse/parse-then lines context))

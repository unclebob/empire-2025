(ns empire.acceptance.parser.then
  (:require [empire.acceptance.parser.then.parse :as parse]))

(defn parse-then
  "Parse THEN lines into IR. Returns {:thens [...]}"
  [lines context]
  (parse/parse-then lines context))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T22:07:03.241221-05:00", :module-hash "923217774", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 2, :hash "-1129166046"} {:id "defn/parse-then", :kind "defn", :line 4, :end-line 7, :hash "-103158944"}]}
;; clj-mutate-manifest-end

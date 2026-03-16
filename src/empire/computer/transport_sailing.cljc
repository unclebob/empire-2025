(ns empire.computer.transport-sailing
  "Facade namespace for transport sailing and invading mission handlers."
  (:require [empire.computer.transport-sailing-invasion :as invasion]
            [empire.computer.transport-sailing-regular :as regular]
            [empire.computer.transport-sailing-support :as support]))

(def compute-sail-path support/compute-sail-path)
(def process-sailing-mission regular/process-sailing-mission)
(def process-invading-mission invasion/process-invading-mission)

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-16T15:05:42.233237-05:00", :module-hash "630999352", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 5, :hash "-636977502"} {:id "def/compute-sail-path", :kind "def", :line 7, :end-line 7, :hash "-138756772"} {:id "def/process-sailing-mission", :kind "def", :line 8, :end-line 8, :hash "-1782111387"} {:id "def/process-invading-mission", :kind "def", :line 9, :end-line 9, :hash "197588737"}]}
;; clj-mutate-manifest-end

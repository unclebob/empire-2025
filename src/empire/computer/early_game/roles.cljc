(ns empire.computer.early-game.roles
  (:require [empire.computer.early-game.role-assignment :as assignment]
            [empire.computer.early-game.role-policy :as policy]))

(def role->item policy/role->item)
(def desired-role-counts policy/desired-role-counts)
(def theater-role-plan assignment/theater-role-plan)

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-13T16:01:56.139652-05:00", :module-hash "-945837121", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 3, :hash "-404096451"} {:id "def/role->item", :kind "def", :line 5, :end-line 5, :hash "-641933247"} {:id "def/desired-role-counts", :kind "def", :line 6, :end-line 6, :hash "209191827"} {:id "def/theater-role-plan", :kind "def", :line 7, :end-line 7, :hash "-2051064072"}]}
;; clj-mutate-manifest-end

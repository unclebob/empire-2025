(ns empire.computer.fighter-process-decisions)

(defn step-result
  [result burned-pos]
  (cond
    (= result :landed) nil
    (map? result) {:pos (:pos result) :steps-used (:hops result)}
    burned-pos {:pos burned-pos :steps-used 1}
    :else nil))

(defn continue-steps?
  [steps-remaining step]
  (and (pos? steps-remaining) step))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-16T09:08:26.159786-05:00", :module-hash "-1074600217", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "-2007009387"} {:id "defn/step-result", :kind "defn", :line 3, :end-line 9, :hash "-526129637"} {:id "defn/continue-steps?", :kind "defn", :line 11, :end-line 13, :hash "1104591639"}]}
;; clj-mutate-manifest-end

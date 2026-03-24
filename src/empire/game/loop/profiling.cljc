(ns empire.game.loop.profiling)

(def ^:dynamic *round-phase-recorder* nil)

(defn now-ns []
  (System/nanoTime))

(defn with-round-phase-recorder
  [recorder f]
  (binding [*round-phase-recorder* recorder]
    (f)))

(defn record-phase!
  [phase elapsed-ns]
  (when *round-phase-recorder*
    (*round-phase-recorder* phase elapsed-ns)))

(defn time-phase
  [phase f]
  (if *round-phase-recorder*
    (let [started-at (now-ns)
          result (f)
          elapsed-ns (- (now-ns) started-at)]
      (record-phase! phase elapsed-ns)
      result)
    (f)))

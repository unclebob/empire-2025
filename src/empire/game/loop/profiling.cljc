(ns empire.game.loop.profiling)

(defn now-ns []
  (System/nanoTime))

(defn with-round-phase-recorder
  [_recorder f]
  (f))

(defn time-phase
  [_phase f]
  (f))

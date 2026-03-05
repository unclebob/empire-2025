(ns empire.movement.pathfinding-bfs.context)

(defonce ^:private world-loader* (atom nil))
(defonce ^:private runtime-reader* (atom nil))

(defn set-world-loader!
  [f]
  (reset! world-loader* f))

(defn set-runtime-reader!
  [f]
  (reset! runtime-reader* f))

(defn current-world
  []
  (if-let [f @world-loader*]
    (f)
    (throw (ex-info "BFS world loader not initialized"
                    {:hint "Initialize app bootstrap before calling BFS pathfinding functions."}))))

(defn read-runtime-state
  [k]
  (if-let [f @runtime-reader*]
    (f k)
    (throw (ex-info "BFS runtime reader not initialized"
                    {:hint "Initialize app bootstrap before calling BFS pathfinding functions."}))))

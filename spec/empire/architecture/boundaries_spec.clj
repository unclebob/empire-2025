(ns empire.architecture.boundaries-spec
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [speclj.core :refer :all]))

(defn- allowed-graph
  []
  (:allowed-dependencies (edn/read-string (slurp "dependency-checker.edn"))))

(defn- cyclic-from?
  [graph node path]
  (cond
    (contains? path node) true
    :else (boolean
           (some #(cyclic-from? graph % (conj path node))
                 (get graph node)))))

(defn- cyclic?
  [graph]
  (boolean (some #(cyclic-from? graph % #{}) (keys graph))))

(defn- dependents-of
  [graph component]
  (set (for [[from tos] graph
             :when (some #{component} tos)]
         from)))

(defn- source-files
  []
  (->> (file-seq (io/file "src"))
       (filter #(.isFile %))
       (filter #(re-find #"\.clj[cs]?$" (.getName %)))))

(describe "allowed dependency graph"
  (it "is acyclic"
    (should-not (cyclic? (allowed-graph))))

  (it "does not let policy components depend on sound"
    (should= #{:ui}
             (dependents-of (allowed-graph) :sound)))

  (it "does not let game-mechanics depend on player or computer"
    (let [mechanics-deps (set (:game-mechanics (allowed-graph)))]
      (should-not (contains? mechanics-deps :player))
      (should-not (contains? mechanics-deps :computer))))

  (it "points sound inward toward notifications"
    (should= [:notifications] (:sound (allowed-graph)))))

(describe "quil adapter boundary"
  (it "does not require quil outside src/empire/ui/quil"
    (let [violations
          (->> (source-files)
               (remove #(str/includes? (.getPath %) "src/empire/ui/quil/"))
               (filter #(re-find #"\[quil\." (slurp %)))
               (map #(.getPath %))
               vec)]
      (should= [] violations))))

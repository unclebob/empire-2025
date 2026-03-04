(ns empire.architecture.dependency-checker.core-report
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn fmt-double [d]
  (format "%.3f" (double d)))

(defn print-labeled-section
  [title underline lines]
  (when (seq lines)
    (println)
    (println title)
    (println underline)
    (doseq [line lines]
      (println line))))

(defn metric-lines
  [component-stats]
  (cons
   (format "%-18s %6s %7s %11s %11s %9s" "Component" "FanIn" "FanOut" "Instability" "Abstract" "Distance")
   (for [[component {:keys [fan-in fan-out instability abstractness distance]}] component-stats]
     (format "%-18s %6d %7d %11s %11s %9s"
             (str component)
             fan-in
             fan-out
             (fmt-double instability)
             (fmt-double abstractness)
             (fmt-double distance)))))

(defn edge-lines
  [component-edges]
  (map (fn [[from to]] (format "%s -> %s" from to)) component-edges))

(defn warning-lines
  [warnings]
  (map (fn [{:keys [namespace callee targets]}]
         (format "%s uses %s%s"
                 namespace
                 callee
                 (if (seq targets)
                   (str " -> " (str/join ", " targets))
                   "")))
       warnings))

(defn violation-lines
  [violations]
  (map (fn [{:keys [from-component to-component from-ns to-ns]}]
         (format "%s -> %s  (%s -> %s)"
                 from-component to-component from-ns to-ns))
       violations))

(defn cycle-lines
  [cycles]
  (map (fn [cycle] (str/join " -> " (map str cycle))) cycles))

(defn distance-violation-lines
  [distance-violations max-distance]
  (map (fn [[component distance]]
         (format "%s distance=%s exceeds limit=%s"
                 component
                 (fmt-double distance)
                 (fmt-double max-distance)))
       distance-violations))

(defn report-text
  [{:keys [component-stats component-edges warnings violations cycles]}
   {:keys [max-distance distance-violations]}]
  (let [components (keys component-stats)]
    (println "Dependency Analysis")
    (println "===================")
    (println)
    (println (format "Components: %d" (count components)))
    (println (format "Component edges: %d" (count component-edges)))
    (println (format "Warnings: %d" (count warnings)))
    (println (format "Violations: %d" (count violations)))
    (println (format "Cycles: %d" (count cycles)))
    (println (format "Distance limit: %.3f" (double max-distance)))
    (println (format "Distance violations: %d" (count distance-violations)))
    (print-labeled-section "Component Metrics" "-----------------" (metric-lines component-stats))
    (print-labeled-section "Component Dependencies" "----------------------" (edge-lines component-edges))
    (print-labeled-section "Warnings" "--------" (warning-lines warnings))
    (print-labeled-section "Boundary Violations" "-------------------" (violation-lines violations))
    (print-labeled-section "Cycles" "------" (cycle-lines cycles))
    (print-labeled-section "Distance Violations" "-------------------" (distance-violation-lines distance-violations max-distance))))

(defn load-config
  [path]
  (if (and path (.exists (io/file path)))
    (edn/read-string (slurp path))
    {}))

(defn usage!
  []
  (binding [*out* *err*]
    (println "Usage: clj -M:check-dependencies [config.edn] [--format text|edn] [--max-distance N] [--init|--force-init]"))
  2)

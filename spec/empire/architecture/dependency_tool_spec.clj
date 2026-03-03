(ns empire.architecture.dependency-tool-spec
  (:require [speclj.core :refer :all]
            [clojure.java.io :as io]
            [empire.architecture.dependency-tool :as tool]))

(defn- temp-dir []
  (.toFile (java.nio.file.Files/createTempDirectory "dependency-tool-spec" (make-array java.nio.file.attribute.FileAttribute 0))))

(defn- write-file!
  [root rel-path content]
  (let [f (io/file root rel-path)]
    (io/make-parents f)
    (spit f content)))

(describe "dependency-tool/analyze-project"
  (it "computes dependencies, metrics, cycles, and boundary violations"
    (let [root (temp-dir)]
      (write-file! root "demo/a.clj"
                   "(ns demo.a (:require [demo.b :as b] [demo.c :as c]))\n(defprotocol APort (x [this]))\n")
      (write-file! root "demo/b.clj"
                   "(ns demo.b (:require [demo.c :as c]))\n(defn b-fn [] :ok)\n")
      (write-file! root "demo/c.clj"
                   "(ns demo.c (:require [demo.a :as a]))\n(def ^:private hidden 1)\n(defn c-fn [] :ok)\n")
      (let [result (tool/analyze-project
                    {:source-paths [(.getPath root)]
                     :component-rules [{:component :alpha :match "demo.a"}
                                       {:component :beta :match "demo.b"}
                                       {:component :gamma :match "demo.c"}]
                     :forbidden-dependencies [[:alpha :beta]]})
            stats (:component-stats result)]
        (should= #{[:alpha :beta] [:alpha :gamma] [:beta :gamma] [:gamma :alpha]}
                 (set (:component-edges result)))
        (should= 1 (count (:violations result)))
        (should= :alpha (:from-component (first (:violations result))))
        (should= :beta (:to-component (first (:violations result))))
        (should (some #(= #{:alpha :beta :gamma} (set %)) (:cycles result)))
        (should= 1 (get-in stats [:alpha :fan-in]))
        (should= 2 (get-in stats [:alpha :fan-out]))
        (should= 1 (get-in stats [:alpha :public-vars]))
        (should= 1 (get-in stats [:alpha :abstract-vars])))))

  (it "supports allowed exceptions for forbidden component dependencies"
    (let [root (temp-dir)]
      (write-file! root "demo/a.clj" "(ns demo.a (:require [demo.b :as b]))\n(defn call [] (b/id))\n")
      (write-file! root "demo/b.clj" "(ns demo.b)\n(defn id [] :ok)\n")
      (let [result (tool/analyze-project
                    {:source-paths [(.getPath root)]
                     :component-rules [{:component :left :match "demo.a"}
                                       {:component :right :match "demo.b"}]
                     :forbidden-dependencies [[:left :right]]
                     :allowed-exceptions [{:from-ns "demo.a" :to-ns "demo.b"}]})]
        (should= [] (:violations result))))))

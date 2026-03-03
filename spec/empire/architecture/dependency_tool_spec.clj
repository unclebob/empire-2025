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
        (should= [] (:violations result)))))

  (it "generates a starter config with inferred component rules"
    (let [root (temp-dir)]
      (write-file! root "empire/application/runtime.cljc" "(ns empire.application.runtime)\n")
      (write-file! root "empire/adapters/state.cljc" "(ns empire.adapters.state)\n")
      (write-file! root "empire/acceptance/parser.cljc" "(ns empire.acceptance.parser)\n")
      (write-file! root "empire/acceptance/generator.cljc" "(ns empire.acceptance.generator)\n")
      (let [cfg (#'tool/generate-starter-config [(.getPath root)])
            by-component (into {} (map (juxt :component identity) (:component-rules cfg)))]
        (should= "empire.application*" (:match (get by-component :application)))
        (should= "empire.adapters*" (:match (get by-component :adapters)))
        (should= "empire.acceptance*" (:match (get by-component :acceptance)))))

  (it "infers abstract and concrete component roots from module abstractness"
    (let [root (temp-dir)]
      (write-file! root "empire/api/protocols.cljc"
                   "(ns empire.api.protocols)\n(defprotocol Port (go [this]))\n")
      (write-file! root "empire/api/events.cljc"
                   "(ns empire.api.events)\n(defmulti handle-event :type)\n")
      (write-file! root "empire/impl/service_a.cljc"
                   "(ns empire.impl.service-a (:require [empire.api.protocols :as p]))\n(defn run [] :ok)\n")
      (write-file! root "empire/impl/service_b.cljc"
                   "(ns empire.impl.service-b (:require [empire.api.events :as e]))\n(defn execute [] :ok)\n")
      (let [cfg (#'tool/generate-starter-config [(.getPath root)])
            by-component (into {} (map (juxt :component identity) (:component-rules cfg)))]
        (should= "empire.api*" (:match (get by-component :api)))
        (should= "empire.impl*" (:match (get by-component :impl))))))))

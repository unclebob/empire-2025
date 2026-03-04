(ns empire.architecture.dependency-checker-spec
  (:require [speclj.core :refer :all]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [empire.architecture.dependency-checker :as tool]))

(defn- temp-dir []
  (.toFile (java.nio.file.Files/createTempDirectory "dependency-tool-spec" (make-array java.nio.file.attribute.FileAttribute 0))))

(defn- write-file!
  [root rel-path content]
  (let [f (io/file root rel-path)]
    (io/make-parents f)
    (spit f content)))

(describe "dependency-tool/analyze-project"
  (it "computes component dependencies, cycles, and boundary violations"
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
                     :forbidden-dependencies [[:alpha :beta]]})]
        (should= #{[:alpha :beta] [:alpha :gamma] [:beta :gamma] [:gamma :alpha]}
                 (set (:component-edges result)))
        (should= 1 (count (:violations result)))
        (should= :alpha (:from-component (first (:violations result))))
        (should= :beta (:to-component (first (:violations result))))
        (should (some #(= #{:alpha :beta :gamma} (set %)) (:cycles result)))))

  (it "calculates fan-in, fan-out, instability, abstractness, and distance metrics"
    (let [root (temp-dir)]
      (write-file! root "demo/a.clj"
                   "(ns demo.a (:require [demo.b :as b] [demo.c :as c]))\n(defprotocol APort (x [this]))\n")
      (write-file! root "demo/b.clj"
                   "(ns demo.b (:require [demo.c :as c]))\n(defn b-fn [] :ok)\n")
      (write-file! root "demo/c.clj"
                   "(ns demo.c (:require [demo.a :as a]))\n(defn c-fn [] :ok)\n")
      (let [result (tool/analyze-project
                    {:source-paths [(.getPath root)]
                     :component-rules [{:component :alpha :match "demo.a"}
                                       {:component :beta :match "demo.b"}
                                       {:component :gamma :match "demo.c"}]})
            stats (:component-stats result)]
        (should= 1 (get-in stats [:alpha :fan-in]))
        (should= 2 (get-in stats [:alpha :fan-out]))
        (should= 1 (get-in stats [:alpha :public-vars]))
        (should= 1 (get-in stats [:alpha :abstract-vars]))
        (should (< (Math/abs (- 0.6666666666666666 (get-in stats [:alpha :instability]))) 1.0e-9))
        (should (< (Math/abs (- 1.0 (get-in stats [:alpha :abstractness]))) 1.0e-9))
        (should (< (Math/abs (- 0.6666666666666666 (get-in stats [:alpha :distance]))) 1.0e-9))
        (should (< (Math/abs (- 0.5 (get-in stats [:beta :instability]))) 1.0e-9))
        (should (< (Math/abs (- 0.0 (get-in stats [:beta :abstractness]))) 1.0e-9))
        (should (< (Math/abs (- 0.5 (get-in stats [:beta :distance]))) 1.0e-9)))))

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

  (it "includes dynamic namespace lookups in dependency metrics and emits warnings"
    (let [root (temp-dir)]
      (write-file! root "demo/a.clj"
                   "(ns demo.a (:import [demo.imported Thing]))\n(require '[demo.b :as b])\n(defn call []\n  (requiring-resolve 'demo.c/run)\n  (resolve 'demo.d/id)\n  (ns-resolve 'demo.e 'id)\n  (find-ns 'demo.f)\n  (the-ns 'demo.g)\n  (b/id))\n")
      (write-file! root "demo/b.clj" "(ns demo.b)\n(defn id [] :ok)\n")
      (write-file! root "demo/c.clj" "(ns demo.c)\n(defn run [] :ok)\n")
      (write-file! root "demo/d.clj" "(ns demo.d)\n(defn id [] :ok)\n")
      (write-file! root "demo/e.clj" "(ns demo.e)\n(defn id [] :ok)\n")
      (write-file! root "demo/f.clj" "(ns demo.f)\n(defn id [] :ok)\n")
      (write-file! root "demo/g.clj" "(ns demo.g)\n(defn id [] :ok)\n")
      (let [result (tool/analyze-project
                    {:source-paths [(.getPath root)]
                     :component-rules [{:component :alpha :match "demo.a"}
                                       {:component :beta :match "demo.b"}
                                       {:component :gamma :match "demo.c"}
                                       {:component :delta :match "demo.d"}
                                       {:component :epsilon :match "demo.e"}
                                       {:component :zeta :match "demo.f"}
                                       {:component :eta :match "demo.g"}
                                       {:component :imports :match "demo.imported"}]})
            stats (:component-stats result)]
        (should= #{[:alpha :beta] [:alpha :gamma] [:alpha :delta] [:alpha :epsilon] [:alpha :zeta] [:alpha :eta] [:alpha :imports]}
                 (set (:component-edges result)))
        (should= 7 (get-in stats [:alpha :fan-out]))
        (should= 5 (count (:warnings result)))
        (should (every? #(= "demo.a" (:namespace %)) (:warnings result))))))

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

(describe "dependency-tool helper behavior"
  (it "matches patterns for keyword, exact string, glob, and regex"
    (let [kw-m (#'tool/pattern->matcher :demo.ns)
          exact-m (#'tool/pattern->matcher "demo.ns")
          glob-m (#'tool/pattern->matcher "demo.*")
          rx-m (#'tool/pattern->matcher "^demo\\..*")
          invalid-m (#'tool/pattern->matcher 42)]
      (should (kw-m "demo.ns"))
      (should-not (kw-m "demo.other"))
      (should (exact-m "demo.ns"))
      (should-not (exact-m "demo.ns.x"))
      (should (glob-m "demo.alpha"))
      (should (rx-m "demo.beta"))
      (should-not (invalid-m "anything"))))

  (it "normalizes component rules in map and vector forms"
    (let [map-rule (#'tool/compile-normalized-rule (#'tool/normalize-component-rule {:component :c1 :match "demo.*"}))
          vec-rule (#'tool/compile-normalized-rule (#'tool/normalize-component-rule [:c2 "demo.x"]))
          invalid-rule (fn [] (#'tool/normalize-component-rule :bad))]
      (should= :c1 (:component map-rule))
      (should ((:matches? map-rule) "demo.a"))
      (should= :c2 (:component vec-rule))
      (should ((:matches? vec-rule) "demo.x"))
      (should-not ((:matches? vec-rule) "demo.y"))
      (should-throw clojure.lang.ExceptionInfo (invalid-rule))))

  (it "normalizes match-patterns for nil, scalar, and sequential definitions"
    (should= [] (#'tool/match-patterns {:component :a}))
    (should= ["demo.*"] (#'tool/match-patterns {:component :a :match "demo.*"}))
    (should= ["demo.a" "demo.b"] (#'tool/match-patterns {:component :a :matches ["demo.a" "demo.b"]})))

  (it "reads and writes config and reports usage text"
    (let [root (temp-dir)
          cfg-file (io/file root "dep.edn")
          cfg {:source-paths ["src"] :component-rules [{:component :a :match "a.*"}]}
          usage-output (binding [*err* (java.io.StringWriter.)]
                         (let [code (#'tool/usage!)]
                           {:code code :text (str *err*)}))]
      (#'tool/write-config! (.getPath cfg-file) cfg)
      (should= cfg (edn/read-string (slurp cfg-file)))
      (should= cfg (#'tool/load-config (.getPath cfg-file)))
      (should= {} (#'tool/load-config (.getPath (io/file root "missing.edn"))))
      (should= 2 (:code usage-output))
      (should (str/includes? (:text usage-output) "Usage: clj -M:check-dependencies"))))

  (it "formats text report with warnings, violations, cycles, and distance violations"
    (let [report (with-out-str
                   (#'tool/report-text
                    {:component-stats {:alpha {:fan-in 1
                                               :fan-out 2
                                               :instability 0.66
                                               :abstractness 0.10
                                               :distance 0.24}}
                     :component-edges [[:alpha :beta]]
                     :warnings [{:namespace "demo.a" :callee "requiring-resolve" :targets ["demo.b"]}]
                     :violations [{:from-component :alpha
                                   :to-component :beta
                                   :from-ns "demo.a"
                                   :to-ns "demo.b"}]
                     :cycles [[:alpha :beta]]
                     :config {}}
                    {:max-distance 0.20
                     :distance-violations [[:alpha 0.24]]}))]
      (should (str/includes? report "Warnings: 1"))
      (should (str/includes? report "demo.a uses requiring-resolve -> demo.b"))
      (should (str/includes? report "Boundary Violations"))
      (should (str/includes? report "Cycles"))
      (should (str/includes? report "Distance Violations")))))

(describe "dependency-tool CLI flow"
  (it "parses args with defaults"
    (let [defaults (#'tool/parse-args [])]
      (should= "dependency-checker.edn" (:config-path defaults))
      (should= :text (:fmt defaults))
      (should= false (:init? defaults))
      (should= false (:force-init? defaults))
      (should= 0 (:max-distance defaults))))

  (it "parses explicit config path and options"
    (let [parsed (#'tool/parse-args ["cfg.edn" "--format" "edn" "--max-distance" "0.25" "--init"])]
      (should= "cfg.edn" (:config-path parsed))
      (should= :edn (:fmt parsed))
      (should= true (:init? parsed))
      (should= false (:force-init? parsed))
      (should= 0.25 (:max-distance parsed))))

  (it "returns usage error for unknown option"
    (should= :usage (:error (#'tool/parse-args ["--unknown"])))
    (should= :usage (:error (#'tool/parse-args ["--format"])))
    (should= :usage (:error (#'tool/parse-args ["--max-distance"]))))

  (it "returns invalid-max-distance parsing error for bad numeric value"
    (let [err (#'tool/parse-args ["--max-distance" "not-a-number"])]
      (should= :invalid-max-distance (:error err))
      (should= "not-a-number" (:value err))))

  (it "run-cli creates config for --init"
    (let [root (temp-dir)
          cfg-path (.getPath (io/file root "dep.edn"))]
      (with-redefs [tool/generate-starter-config (fn [] {:source-paths ["src"] :component-rules []})]
        (should= 0 (#'tool/run-cli [cfg-path "--init"])))
      (should (.exists (io/file cfg-path)))))

  (it "run-cli --init does not overwrite existing config"
    (let [root (temp-dir)
          cfg-path (.getPath (io/file root "dep.edn"))]
      (with-redefs [tool/generate-starter-config (fn [] {:source-paths ["src"] :component-rules []})]
        (should= 0 (#'tool/run-cli [cfg-path "--init"])))
      (let [before (slurp cfg-path)]
        (should= 0 (#'tool/run-cli [cfg-path "--init"]))
        (should= before (slurp cfg-path)))))

  (it "run-cli overwrites existing config for --force-init"
    (let [root (temp-dir)
          cfg-path (.getPath (io/file root "dep.edn"))]
      (with-redefs [tool/generate-starter-config (fn [] {:source-paths ["src"] :component-rules []})]
        (should= 0 (#'tool/run-cli [cfg-path "--init"])))
      (with-redefs [tool/generate-starter-config (fn [] {:source-paths ["other"] :component-rules []})]
        (should= 0 (#'tool/run-cli [cfg-path "--force-init"])))
      (should (str/includes? (slurp cfg-path) "other"))))

  (it "run-cli returns usage error when --init and --force-init are both set"
    (let [root (temp-dir)
          cfg-path (.getPath (io/file root "dep.edn"))]
      (should= 2 (#'tool/run-cli [cfg-path "--init" "--force-init"]))))

  (it "run-cli returns 2 for unsupported format"
    (let [root (temp-dir)
          cfg-path (.getPath (io/file root "dep.edn"))]
      (spit cfg-path "{}")
      (with-redefs [tool/analyze-project (fn [_]
                                           {:config {:fail-on-violations true :fail-on-cycles true}
                                            :component-stats {:alpha {:distance 0.0}}
                                            :violations []
                                            :cycles []
                                            :component-edges []
                                            :warnings []})
                    tool/report-text (fn [& _] nil)]
        (should= 2 (#'tool/run-cli [cfg-path "--format" "xml"])))))

  (it "run-cli returns 1 when analysis fails"
    (let [root (temp-dir)
          cfg-path (.getPath (io/file root "dep.edn"))]
      (spit cfg-path "{}")
      (with-redefs [tool/analyze-project (fn [_]
                                           {:config {:fail-on-violations true :fail-on-cycles true}
                                            :component-stats {:alpha {:distance 1.2}}
                                            :violations [{:from-component :a}]
                                            :cycles []
                                            :component-edges []
                                            :warnings []})
                    tool/report-text (fn [& _] nil)]
        (should= 1 (#'tool/run-cli [cfg-path "--max-distance" "1.0"])))))

  (it "run-cli returns 0 when analysis passes"
    (let [root (temp-dir)
          cfg-path (.getPath (io/file root "dep.edn"))]
      (spit cfg-path "{}")
      (with-redefs [tool/analyze-project (fn [_]
                                           {:config {:fail-on-violations true :fail-on-cycles true}
                                            :component-stats {:alpha {:distance 0.0}}
                                            :violations []
                                            :cycles []
                                            :component-edges []
                                            :warnings []})
                    tool/report-text (fn [& _] nil)]
        (should= 0 (#'tool/run-cli [cfg-path "--max-distance" "1.0"])))))

  (it "run-cli supports edn output format"
    (let [root (temp-dir)
          cfg-path (.getPath (io/file root "dep.edn"))
          result {:config {:fail-on-violations true :fail-on-cycles true}
                  :component-stats {:alpha {:distance 0.0}}
                  :violations []
                  :cycles []
                  :component-edges []
                  :warnings []}
          out (java.io.StringWriter.)]
      (spit cfg-path "{}")
      (with-redefs [tool/analyze-project (fn [_] result)]
        (binding [*out* out]
          (should= 0 (#'tool/run-cli [cfg-path "--format" "edn" "--max-distance" "1.0"]))))
      (should (str/includes? (str out) ":component-stats"))))

  (it "run-cli returns 2 for invalid max-distance value"
    (let [root (temp-dir)
          cfg-path (.getPath (io/file root "dep.edn"))]
      (with-redefs [tool/generate-starter-config (fn [] {:source-paths ["src"]})]
        (should= 0 (#'tool/run-cli [cfg-path "--force-init"])))
      (should= 2 (#'tool/run-cli [cfg-path "--max-distance" "bad"]))))

  (it "run-cli returns 0 for --force-init when config does not yet exist"
    (let [root (temp-dir)
          cfg-path (.getPath (io/file root "dep.edn"))]
      (with-redefs [tool/generate-starter-config (fn [] {:source-paths ["src"]})]
        (should= 0 (#'tool/run-cli [cfg-path "--force-init"])))))

  (it "run-cli returns usage error for unknown top-level argument"
    (should= 2 (#'tool/run-cli ["--bogus"])))

  (it "run-cli returns usage error for missing --format value"
    (should= :usage (:error (#'tool/parse-args ["--format"])))
    (should= 2 (#'tool/run-cli ["--format"]))))

(describe "dependency-tool inference helpers"
  (it "infers concrete prefixes and prefix helpers"
    (let [records [{:namespace "empire.api.port" :requires #{} :public-count 1 :abstract-count 1}
                   {:namespace "empire.impl.a" :requires #{"empire.api.port"} :public-count 1 :abstract-count 0}
                   {:namespace "empire.impl.b" :requires #{"empire.api.port"} :public-count 1 :abstract-count 0}]
          abstract-prefixes #{"empire.api"}
          concrete (#'tool/infer-concrete-prefixes records abstract-prefixes)]
      (should (contains? concrete "empire.impl"))
      (should= "empire" (#'tool/parent-prefix "empire.api"))
      (should= nil (#'tool/parent-prefix "empire"))
      (should= "empire.api" (#'tool/best-abstract-prefix abstract-prefixes "empire.api.port")))))

(describe "dependency-tool decrap targets"
  (it "handles ns-target for symbol, string, quoted symbol, and unsupported types"
    (should= 'demo.ns (#'tool/ns-target 'demo.ns))
    (should= 'demo.ns (#'tool/ns-target "demo.ns"))
    (should= 'demo.ns (#'tool/ns-target '(quote demo.ns)))
    (should= 'demo.ns (#'tool/ns-target '(quote "demo.ns")))
    (should= nil (#'tool/ns-target 42)))

  (it "extracts dynamic-lookup targets for all supported lookup forms"
    (should= ['demo.alpha] (#'tool/dynamic-lookup-targets '(requiring-resolve 'demo.alpha/run)))
    (should= ['demo.beta] (#'tool/dynamic-lookup-targets '(resolve 'demo.beta/run)))
    (should= ['demo.gamma 'demo.delta] (#'tool/dynamic-lookup-targets '(ns-resolve 'demo.gamma 'demo.delta/run)))
    (should= ['demo.epsilon] (#'tool/dynamic-lookup-targets '(find-ns 'demo.epsilon)))
    (should= ['demo.zeta] (#'tool/dynamic-lookup-targets '(the-ns 'demo.zeta)))
    (should= [] (#'tool/dynamic-lookup-targets '(println "x"))))

  (it "matches dependency exceptions by component and namespace rules"
    (let [ex (#'tool/compile-exception {:from-component :a
                                        :to-component :b
                                        :from-ns "demo.from"
                                        :to-ns "demo.to"})
          edge {:from-component :a :to-component :b :from-ns "demo.from" :to-ns "demo.to"}
          wrong-comp (assoc edge :to-component :c)
          wrong-ns (assoc edge :to-ns "demo.other")]
      (should (#'tool/exception-matches? ex edge))
      (should-not (#'tool/exception-matches? ex wrong-comp))
      (should-not (#'tool/exception-matches? ex wrong-ns))
      (should (#'tool/exception-matches? (#'tool/compile-exception {}) edge))))

  (it "infers abstract prefixes from fully abstract namespace trees"
    (let [records [{:namespace "demo.api.port" :public-count 1 :abstract-count 1}
                   {:namespace "demo.api.events" :public-count 1 :abstract-count 1}
                   {:namespace "demo.impl.core" :public-count 1 :abstract-count 0}]
          inferred (#'tool/infer-abstract-prefixes records)]
      (should (contains? inferred "demo.api"))
      (should-not (contains? inferred "demo.impl"))))

  (it "generates a starter config for empty source roots"
    (let [root (temp-dir)
          cfg (#'tool/generate-starter-config [(.getPath root)])]
      (should= [(.getPath root)] (:source-paths cfg))
      (should (vector? (:component-rules cfg)))
      (should (vector? (:forbidden-dependencies cfg)))
      (should= false (:fail-on-cycles cfg))
      (should= true (:fail-on-violations cfg))))

  (it "generates starter config with fallback namespace partitioning"
    (let [root (temp-dir)]
      (write-file! root "demo/a/core.clj" "(ns demo.a.core)\n")
      (write-file! root "demo/b/core.clj" "(ns demo.b.core)\n")
      (let [cfg (#'tool/generate-starter-config [(.getPath root)])
            components (set (map :component (:component-rules cfg)))]
        (should (contains? components :a))
        (should (contains? components :b)))))

  (it "computes strongly connected components for cyclic and acyclic graphs"
    (let [nodes #{:a :b :c :d}
          edges [[:a :b] [:b :a] [:b :c]]
          sccs (#'tool/strongly-connected-components nodes edges)
          sets (set (map set sccs))]
      (should (contains? sets #{:a :b}))
      (should (contains? sets #{:c}))
      (should (contains? sets #{:d}))))

  (it "keeps self-loop nodes as single-node components in SCC output"
    (let [nodes #{:x :y}
          edges [[:x :x]]
          sccs (#'tool/strongly-connected-components nodes edges)
          sets (set (map set sccs))]
      (should (contains? sets #{:x}))
      (should (contains? sets #{:y}))))

  (it "handles edges to already-visited nodes that are no longer on stack"
    (let [nodes #{:a :b :c}
          edges [[:a :b] [:b :c] [:c :b] [:a :c]]
          sccs (#'tool/strongly-connected-components nodes edges)
          sets (set (map set sccs))]
      (should (contains? sets #{:a}))
      (should (contains? sets #{:b :c})))))
)

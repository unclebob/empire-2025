(ns empire.mutation.runner-spec
  (:require [speclj.core :refer :all]
            [clojure.java.shell :as shell]
            [empire.mutation.runner :as runner]))

(describe "source->spec-path"
  (it "maps root source to spec"
    (should= "spec/empire/combat_spec.clj"
             (runner/source->spec-path "src/empire/combat.cljc")))

  (it "maps subdirectory source to spec"
    (should= "spec/empire/computer/ship_spec.clj"
             (runner/source->spec-path "src/empire/computer/ship.cljc")))

  (it "handles deeply nested paths"
    (should= "spec/empire/movement/map_utils_spec.clj"
             (runner/source->spec-path "src/empire/movement/map_utils.cljc"))))

(describe "spec-exists?"
  (it "returns true for existing spec"
    (should (runner/spec-exists? "spec/empire/combat_spec.clj")))

  (it "returns false for nonexistent spec"
    (should-not (runner/spec-exists? "spec/empire/nonexistent_spec.clj"))))

(describe "run-spec"
  (it "returns :killed when spec fails (exit non-zero)"
    (with-redefs [clojure.java.shell/sh (fn [& _] {:exit 1 :out "" :err ""})]
      (should= :killed (runner/run-spec "spec/empire/combat_spec.clj"))))

  (it "returns :survived when spec passes (exit 0)"
    (with-redefs [clojure.java.shell/sh (fn [& _] {:exit 0 :out "" :err ""})]
      (should= :survived (runner/run-spec "spec/empire/combat_spec.clj")))))

(run-specs)

(ns empire.acceptance.generator-spec
  (:require [speclj.core :refer :all]
            [empire.acceptance.generator :as gen]
            [clojure.string :as str]
            [clojure.java.io :as io]))

;; --- determine-needs tests ---

(describe "determine-needs"

  (it "detects :config when thens have :config-key"
    (let [tests [{:givens [] :whens [] :thens [{:type :message-contains :area :attention :config-key :foo}]}]]
      (should-contain :config (gen/determine-needs tests))))

  (it "detects :game-loop when whens have :start-new-round"
    (let [tests [{:givens [] :whens [{:type :start-new-round}] :thens []}]]
      (should-contain :game-loop (gen/determine-needs tests))))

  (it "does not require :quil when whens have :key-down input-fn"
    (let [tests [{:givens [] :whens [{:type :key-press :key :s :input-fn :key-down}] :thens []}]]
      (should-not-contain :quil (gen/determine-needs tests))))

  (it "detects :advance-helper when thens have :unit-at-next-round"
    (let [tests [{:givens [] :whens [] :thens [{:type :unit-at-next-round :unit "D" :target "="}]}]]
      (should-contain :advance-helper (gen/determine-needs tests))))

  (it "detects :game-loop but not :advance-helper when thens have :at-next-step"
    (let [tests [{:givens [] :whens [] :thens [{:type :message-contains :area :attention :config-key :foo :at-next-step true}]}]]
      (should-contain :game-loop (gen/determine-needs tests))
      (should-not-contain :advance-helper (gen/determine-needs tests))))

  (it "detects :make-initial-test-map when whens have :waiting-for-input"
    (let [tests [{:givens [] :whens [{:type :waiting-for-input :unit "F" :set-mode true}] :thens []}]]
      (should-contain :make-initial-test-map (gen/determine-needs tests))))

  (it "detects :advance-until-waiting-helper when whens have :advance-until-waiting"
    (let [tests [{:givens [] :whens [{:type :advance-until-waiting :unit "F"}] :thens []}]
          needs (gen/determine-needs tests)]
      (should-contain :advance-until-waiting-helper needs)
      (should-not-contain :quil needs)
      (should-contain :game-loop needs)))

  (it "detects :advance-until-waiting-helper when thens have :unit-waiting-for-input"
    (let [tests [{:givens [] :whens [] :thens [{:type :unit-waiting-for-input :unit "C"}]}]
          needs (gen/determine-needs tests)]
      (should-contain :advance-until-waiting-helper needs)
      (should-not-contain :quil needs)
      (should-contain :game-loop needs)))

  (it "detects :game-loop when whens have :visibility-update"
    (let [tests [{:givens [] :whens [{:type :visibility-update}] :thens []}]]
      (should-contain :game-loop (gen/determine-needs tests))))

  (it "does not require :quil when whens have :mouse-at-key"
    (let [tests [{:givens [] :whens [{:type :mouse-at-key :coords [0 0] :key :period}] :thens []}]]
      (should-not-contain :quil (gen/determine-needs tests))))

  (it "detects :computer-production when whens have :evaluate-production"
    (let [tests [{:givens [] :whens [{:type :evaluate-production :city "X"}] :thens []}]]
      (should-contain :computer-production (gen/determine-needs tests))))

  (it "detects :visibility-mask when thens have :player-map-visibility"
    (let [tests [{:givens [] :whens [] :thens [{:type :player-map-visibility :rows [".#." ".#."]}]}]]
      (should-contain :visibility-mask (gen/determine-needs tests)))))

;; --- generate-given tests ---

(describe "generate-given"

  (it "generates map given"
    (let [result (gen/generate-given {:type :map :target :game-map :rows ["A#"]})]
      (should-contain "build-test-map" result)
      (should-contain "\"A#\"" result)))

  (it "generates player-map given targeting h/set-state! :player-map"
    (let [result (gen/generate-given {:type :map :target :player-map :rows ["..." ".."]})]
      (should-contain "h/set-state! :player-map" result)
      (should-contain "build-test-map" result)
      (should-not-contain "(test-utils/game-map-atom)" result)))

  (it "generates unit-props given"
    (let [result (gen/generate-given {:type :unit-props :unit "F" :props {:fuel 32}})]
      (should-contain "h/set-unit!" result)
      (should-contain ":fuel 32" result)))

  (it "generates waiting-for-input given with set-mode true"
    (let [result (gen/generate-given {:type :waiting-for-input :unit "A" :set-mode true})]
      (should-contain "h/set-unit!" result)
      (should-contain ":mode :awake" result)
      (should-contain "make-initial-test-map" result)
      (should-contain "process-player-items-batch" result)))

  (it "generates waiting-for-input given with set-mode false"
    (let [result (gen/generate-given {:type :waiting-for-input :unit "A" :set-mode false})]
      (should-not-contain ":mode :awake" result)
      (should-contain "make-initial-test-map" result)))

  (it "generates waiting-for-input given for airport fighter not on map"
    (let [givens [{:type :map :target :game-map :rows ["O%"]}
                  {:type :container-state :target "O" :props {:fighter-count 1 :awake-fighters 1}}
                  {:type :waiting-for-input :unit "F" :set-mode true}]
          result (gen/generate-given (nth givens 2) givens)]
      (should-not-contain "h/set-unit!" result)
      (should-contain "h/get-city" result)
      (should-contain "\"O\"" result)
      (should-contain "process-player-items-batch" result)))

  (it "generates container-state given for city"
    (let [result (gen/generate-given {:type :container-state :target "O" :props {:fighter-count 1 :awake-fighters 1}})]
      (should-contain "h/get-city" result)
      (should-contain ":fighter-count 1" result)))

  (it "generates container-state given for unit"
    (let [result (gen/generate-given {:type :container-state :target "C" :props {:fighter-count 0}})]
      (should-contain "h/set-unit!" result)
      (should-contain ":fighter-count 0" result)))

  (it "generates container-state given for unit with awake-fighters but no fighter-count"
    (let [result (gen/generate-given {:type :container-state :target "C" :props {:awake-fighters 1}})]
      (should-contain "h/set-unit!" result)
      (should-contain ":awake-fighters 1" result)
      (should-contain ":fighter-count 1" result)))

  (it "generates container-state given for unit with awake-armies but no army-count"
    (let [result (gen/generate-given {:type :container-state :target "T" :props {:awake-armies 2}})]
      (should-contain "h/set-unit!" result)
      (should-contain ":awake-armies 2" result)
      (should-contain ":army-count 2" result)))

  (it "generates city-prop given"
    (let [result (gen/generate-given {:type :city-prop :city "X" :prop :country-id :value 1})]
      (should-contain "h/get-city" result)
      (should-contain "\"X\"" result)
      (should-contain ":country-id" result)
      (should-contain "1" result)))

  (it "generates production given"
    (let [result (gen/generate-given {:type :production :city "O" :item :army :remaining-rounds 10})]
      (should-contain "h/update-state! :production" result)
      (should-contain ":army" result)
      (should-contain ":remaining-rounds 10" result)))

  (it "generates round given"
    (let [result (gen/generate-given {:type :round :value 5})]
      (should= "    (h/set-state! :round-number 5)" result)))

  (it "generates destination given"
    (let [result (gen/generate-given {:type :destination :coords [3 4]})]
      (should= "    (h/set-state! :destination [3 4])" result)))

  (it "generates unrecognized given"
    (let [result (gen/generate-given {:type :unrecognized :text "something weird"})]
      (should-contain "pending" result)
      (should-contain "something weird" result))))

;; --- generate-when tests ---

(describe "generate-when"

  (it "generates key-down when"
    (let [result (gen/generate-when {:type :key-press :key :s :input-fn :key-down})]
      (should-contain "h/key-down! :s" result)))

  (it "generates handle-key when"
    (let [result (gen/generate-when {:type :key-press :key :d :input-fn :handle-key})]
      (should-contain "h/handle-key! :d" result)))

  (it "generates army battle win when"
    (let [result (gen/generate-when {:type :battle :key :d :outcome :win :combat-type :army})]
      (should-contain "rand (constantly 0.0)" result)
      (should-contain "h/handle-key! :d" result)
      (should-not-contain "advance-game" result)))

  (it "generates ship battle win when"
    (let [result (gen/generate-when {:type :battle :key :d :outcome :win :combat-type :ship})]
      (should-contain "rand (constantly 0.0)" result)
      (should-contain "advance-game" result)))

  (it "generates ship battle lose when"
    (let [result (gen/generate-when {:type :battle :key :d :outcome :lose :combat-type :ship})]
      (should-contain "rand (constantly 1.0)" result)
      (should-contain "advance-game" result)))

  (it "generates backtick when"
    (let [result (gen/generate-when {:type :backtick :key :A :mouse-cell [0 0]})]
      (should-contain "map-screen-dimensions" result)
      (should-contain "keyword \"`\"" result)
      (should-contain "h/key-down-at! :A" result)))

  (it "generates start-new-round when"
    (let [result (gen/generate-when {:type :start-new-round})]
      (should-contain "start-new-round" result)
      (should-contain "advance-game" result)))

  (it "generates rounds-complete when"
    (let [result (gen/generate-when {:type :rounds-complete :count 6})]
      (should-contain "dotimes [_ 6]" result)
      (should-contain "start-new-round" result)
      (should-contain "advance-game" result)))

  (it "generates advance-game when"
    (let [result (gen/generate-when {:type :advance-game})]
      (should-contain "advance-game" result)))

  (it "generates advance-until-waiting when"
    (let [result (gen/generate-when {:type :advance-until-waiting :unit "F"})]
      (should-contain "advance-until-unit-waiting" result)
      (should-contain "\"F\"" result)))

  (it "generates waiting-for-input when"
    (let [result (gen/generate-when {:type :waiting-for-input :unit "F" :set-mode true})]
      (should-contain "h/set-unit!" result)
      (should-contain ":mode :awake" result)
      (should-contain "make-initial-test-map" result)
      (should-contain "process-player-items-batch" result)))

  (it "generates mouse-at-key when with period key"
    (let [result (gen/generate-when {:type :mouse-at-key :coords [0 1] :key :period})]
      (should-contain "h/key-down-at!" result)
      (should-contain (str "(keyword \".\")") result)))

  (it "generates mouse-at-key when with u key"
    (let [result (gen/generate-when {:type :mouse-at-key :coords [0 0] :key :u})]
      (should-contain "h/key-down-at! :u" result)))

  (it "generates mouse-at-key when with l key"
    (let [result (gen/generate-when {:type :mouse-at-key :coords [0 0] :key :l})]
      (should-contain "h/key-down-at! :l" result)))

  (it "generates visibility-update when"
    (let [result (gen/generate-when {:type :visibility-update})]
      (should-contain "update-player-map" result)))

  (it "generates evaluate-production when"
    (let [result (gen/generate-when {:type :evaluate-production :city "X"})]
      (should-contain "h/evaluate-computer-production!" result)
      (should-contain "h/get-city" result)
      (should-contain "\"X\"" result))))

;; --- generate-ns-form tests ---

(describe "generate-ns-form"

  (it "generates correct ns name from source filename"
    (let [result (gen/generate-ns-form "army.txt" #{})]
      (should-contain "(ns acceptance.army-spec" result)))

  (it "always includes speclj require"
    (let [result (gen/generate-ns-form "foo.txt" #{})]
      (should-contain "[speclj.core :refer :all]" result)))

  (it "always includes base harness refers"
    (let [result (gen/generate-ns-form "foo.txt" #{})]
      (should-not-contain "empire.test.utils" result)
      (should-contain "build-test-map" result)
      (should-not-contain "set-test-unit" result)
      (should-not-contain "get-test-unit" result)
      (should-contain "reset-all-atoms!" result)))

  (it "always includes harness require with refers and omits atoms require"
    (let [result (gen/generate-ns-form "foo.txt" #{})]
      (should-contain "[empire.acceptance.harness :as h :refer [" result)
      (should-not-contain "[empire.atoms :as atoms]" result)))

  (it "includes config require and message-matches? refer when :config needed"
    (let [result (gen/generate-ns-form "foo.txt" #{:config})]
      (should-contain "[empire.config.core :as config]" result)
      (should-contain "message-matches?" result)))

  (it "does not include game-loop require when :game-loop needed"
    (let [result (gen/generate-ns-form "foo.txt" #{:game-loop})]
      (should-not-contain "[empire.game-loop :as game-loop]" result)))

  (it "includes visibility-mask refer when :visibility-mask needed"
    (let [result (gen/generate-ns-form "foo.txt" #{:visibility-mask})]
      (should-contain "visibility-mask" result)))

  (it "includes territory refs when :territory-mask needed"
    (let [result (gen/generate-ns-form "foo.txt" #{:territory-mask})]
      (should-contain "territory-mask" result)
      (should-contain "build-territory-expected" result)))

  (it "includes make-initial-test-map refer when needed"
    (let [result (gen/generate-ns-form "foo.txt" #{:make-initial-test-map})]
      (should-contain "make-initial-test-map" result)))

  (it "omits optional requires when not needed"
    (let [result (gen/generate-ns-form "foo.txt" #{})]
      (should-not-contain "config" result)
      (should-not-contain "game-loop" result)
      (should-not-contain "visibility" result)))

  (it "combines multiple needs correctly"
    (let [result (gen/generate-ns-form "foo.txt" #{:config :game-loop})]
      (should-contain "[empire.config.core :as config]" result)
      (should-not-contain "[empire.game-loop :as game-loop]" result)
      (should-contain "message-matches?" result)
      (should-not-contain "get-test-cell" result))))

;; --- IR contract validation in generator ---

(describe "generate-spec IR contract validation"

  (it "accepts valid IR"
    (let [edn-data {:source "foo.txt"
                    :tests [{:line 1
                             :description "valid test."
                             :givens [{:type :map :target :game-map :rows ["X"]}]
                             :whens [{:type :start-new-round}]
                             :thens [{:type :waiting-for-input :expected true}]}]}
          out (gen/generate-spec edn-data)]
      (should-contain "(describe \"foo.txt\"" out)))

  (it "throws on invalid IR shape"
    (let [edn-data {:source "foo.txt"
                    :tests [{:line 2
                             :description "invalid test."
                             :givens [{:type :map :target :game-map}] ; missing :rows
                             :whens []
                             :thens []}]}]
      (should-throw clojure.lang.ExceptionInfo
                    (gen/generate-spec edn-data)))))

(describe "generator -main"
  (it "writes generated spec files for each input edn"
    (let [base (str (java.nio.file.Files/createTempDirectory "gen-main-spec" (make-array java.nio.file.attribute.FileAttribute 0)))
          in-dir (str base "/in")
          out-dir (str base "/out")
          _ (.mkdirs (io/file in-dir))
          _ (spit (str in-dir "/alpha.edn") "{:source \"alpha.txt\" :tests []}")
          _ (spit (str in-dir "/beta-case.edn") "{:source \"beta-case.txt\" :tests []}")]
      (with-redefs [gen/generate-spec (fn [_] "(ns acceptance.stub-spec)\n(describe \"stub\")\n")]
        (gen/-main in-dir out-dir))
      (should (.exists (io/file (str out-dir "/alpha_spec.clj"))))
      (should (.exists (io/file (str out-dir "/beta_case_spec.clj")))))))

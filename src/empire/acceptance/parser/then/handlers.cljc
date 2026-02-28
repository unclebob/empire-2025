;; mutation-tested: 2026-02-28
(ns empire.acceptance.parser.then.handlers
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [empire.acceptance.parser.helpers :as h]))

(defn then-handle-after-moves [[_ n unit target]]
  {:type :unit-after-moves :unit unit :moves (h/parse-count n) :target target})

(defn then-handle-after-steps-coords [[_ n unit x y]]
  {:type :unit-after-steps :unit unit :steps (h/parse-count n)
   :coords [(Integer/parseInt x) (Integer/parseInt y)]})

(defn then-handle-after-steps-target [[_ n unit target]]
  {:type :unit-after-steps :unit unit :steps (h/parse-count n) :target target})

(defn then-handle-unit-waiting-for-input [[_ unit]]
  {:type :unit-waiting-for-input :unit unit})

(defn then-handle-unit-at-position-with-mode [[_ unit x y mode]]
  [{:type :unit-at :unit unit :coords [(Integer/parseInt x) (Integer/parseInt y)]}
   {:type :unit-prop :unit unit :property :mode :expected (keyword mode)}])

(defn then-handle-unit-at-coords [[_ unit x y]]
  {:type :unit-at :unit unit :coords [(Integer/parseInt x) (Integer/parseInt y)]})

(defn then-handle-unit-at-target [[_ unit target]]
  {:type :unit-at :unit unit :target target})

(defn then-handle-eventually-at [[_ unit target]]
  {:type :unit-eventually-at :unit unit :target target})

(defn then-handle-unit-absent-on-map [[_ unit]]
  {:type :unit-absent :unit (h/normalize-unit-ref unit)})

(defn then-handle-no-message [[_ area]]
  {:type :no-message :area (keyword area)})

(defn then-handle-message-for-unit [[_ area unit key-str]]
  {:type :message-for-unit :area (keyword area) :unit unit
   :config-key (keyword (h/strip-trailing-period key-str))})

(defn then-handle-message-contains-literal [[_ area text]]
  {:type :message-contains :area (keyword area) :text text})

(defn then-handle-message-contains-key [[_ area key-str]]
  {:type :message-contains :area (keyword area)
   :config-key (keyword (h/strip-trailing-period key-str))})

(defn then-handle-message-is-key [[_ area key-str]]
  {:type :message-is :area (keyword area)
   :config-key (keyword (h/strip-trailing-period key-str))})

(defn then-handle-message-is-format [[_ area key-str args-str]]
  (let [args (mapv #(let [s (str/trim %)]
                      (or (h/parse-number s) s))
                   (str/split args-str #"\\s+"))]
    {:type :message-is :area (keyword area) :format {:key (keyword key-str) :args args}}))

(defn then-handle-bare-message-literal [[_ text]]
  {:type :message-contains :area :attention :text text})

(defn then-handle-bare-message-key [[_ key-str]]
  {:type :message-contains :area :attention
   :config-key (keyword (h/strip-trailing-period key-str))})

(defn then-handle-out-of-fuel [_]
  {:type :message-contains :area :attention :config-key :fighter-out-of-fuel})

(defn then-handle-player-map-not-nil [[_ x y]]
  {:type :player-map-cell-not-nil :coords [(Integer/parseInt x) (Integer/parseInt y)]})

(defn then-handle-computer-map-not-nil [[_ x y]]
  {:type :computer-map-cell-not-nil :coords [(Integer/parseInt x) (Integer/parseInt y)]})

(defn then-handle-player-map-nil [[_ x y]]
  {:type :player-map-cell-nil :coords [(Integer/parseInt x) (Integer/parseInt y)]})

(defn then-handle-cell-prop [[_ x y prop val]]
  {:type :cell-prop :coords [(Integer/parseInt x) (Integer/parseInt y)]
   :property (h/resolve-cell-prop prop)
   :expected (or (h/parse-number val) (h/parse-coords val) (keyword val))})

(defn then-handle-cell-type [[_ x y t]]
  {:type :cell-type :coords [(Integer/parseInt x) (Integer/parseInt y)]
   :expected (keyword t)})

(defn then-handle-waiting-for-input [_]
  {:type :waiting-for-input :expected true})

(defn then-handle-not-waiting-for-input [_]
  {:type :waiting-for-input :expected false})

(defn then-handle-game-paused [_]
  {:type :game-paused :expected true})

(defn then-handle-round [[_ n]]
  {:type :round :expected (Integer/parseInt n)})

(defn then-handle-destination [[_ x y]]
  {:type :destination :expected [(Integer/parseInt x) (Integer/parseInt y)]})

(defn then-handle-production-with-rounds [[_ city item n]]
  {:type :production-with-rounds :city city :expected (keyword item)
   :remaining-rounds (Integer/parseInt n)})

(defn then-handle-production [[_ city item]]
  {:type :production :city city :expected (keyword item)})

(defn then-handle-no-production [[_ city]]
  {:type :no-production :city city})

(defn then-handle-production-not [[_ city item]]
  {:type :production-not :city city :excluded (keyword item)})

(defn then-handle-no-unit-at [[_ x y]]
  {:type :no-unit-at :coords [(Integer/parseInt x) (Integer/parseInt y)]})

(defn then-handle-unit-has-mission [[_ unit val]]
  (when (h/city-or-unit-char? unit)
    {:type :unit-prop :unit unit :property :transport-mission :expected (keyword val)}))

(defn parse-edn-value [s]
  (when (str/starts-with? s "[")
    (try (edn/read-string s) (catch Exception _ nil))))

(defn parse-bool [s]
  (case (str/lower-case (str/trim s))
    "true" true
    "false" false
    nil))

(defn then-handle-unit-has-prop [[_ unit prop val]]
  (let [val (str/trim val)]
    (when (h/city-or-unit-char? unit)
      {:type :unit-prop :unit unit
       :property (keyword prop)
       :expected (or (h/parse-number val) (parse-edn-value val) (parse-bool val)
                     (h/parse-coords val) (keyword val))})))

(defn then-handle-unit-is-mode [[_ unit val]]
  (when (h/city-or-unit-char? unit)
    {:type :unit-prop :unit unit :property :mode :expected (keyword val)}))

(defn then-handle-unit-prop-absent [[_ unit prop]]
  {:type :unit-prop-absent :unit unit :property (keyword prop)})

(defn then-handle-will-be-at [[_ unit target]]
  (if-let [coords (h/parse-coords (str "[" (str/replace target #"[\[\]]" "") "]"))]
    {:type :unit-at-next-round :unit unit :coords coords}
    {:type :unit-at-next-round :unit unit :target target}))

(defn then-handle-occupies-cell [[_ unit target]]
  {:type :unit-occupies-cell :unit unit :target-unit target})

(defn then-handle-remains-unmoved [[_ unit]]
  {:type :unit-unmoved :unit unit})

(defn then-handle-airport-fighter [[_ target]]
  {:type :container-prop :target target :property :fighter-count :expected 1 :lookup :city})

(defn then-handle-fighter-aboard [[_ target]]
  {:type :container-prop :target target :property :fighter-count :expected 1 :lookup :unit})

(defn then-handle-no-fighters [[_ target]]
  {:type :container-prop :target target :property :fighter-count :expected 0
   :lookup (if (contains? h/city-chars target) :city :unit)})

(defn then-handle-awake-fighters [[_ target n]]
  {:type :container-prop :target target :property :awake-fighters
   :expected (h/parse-count n)
   :lookup (if (contains? h/city-chars target) :city :unit)})

(defn then-handle-unit-absent-short [[_ unit]]
  {:type :unit-absent :unit (h/normalize-unit-ref unit)})

(defn then-handle-refueling-position-near [[_ unit target]]
  {:type :refueling-position-near :unit unit :target target})

(defn then-handle-unit-present-coords [[_ unit x y]]
  {:type :unit-present :unit unit :coords [(Integer/parseInt x) (Integer/parseInt y)]})

(defn then-handle-unit-present-target [[_ unit target]]
  {:type :unit-present :unit unit :target target})

(defn then-handle-shipyard-has-ship [[_ city ship-type hits]]
  {:type :shipyard-has-ship :city city :ship-type (keyword ship-type) :hits (Integer/parseInt hits)})

(defn then-handle-shipyard-empty [[_ city]]
  {:type :shipyard-empty :city city})

(defn then-handle-map-is [[_ map-str]]
  {:type :map-is :expected (h/strip-trailing-period map-str)})

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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T11:56:53.277476-05:00", :module-hash "-1945433381", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 4, :hash "1759721047"} {:id "defn/then-handle-after-moves", :kind "defn", :line 6, :end-line 7, :hash "-1542203090"} {:id "defn/then-handle-after-steps-coords", :kind "defn", :line 9, :end-line 11, :hash "-1946568659"} {:id "defn/then-handle-after-steps-target", :kind "defn", :line 13, :end-line 14, :hash "1407524663"} {:id "defn/then-handle-unit-waiting-for-input", :kind "defn", :line 16, :end-line 17, :hash "689085784"} {:id "defn/then-handle-unit-at-position-with-mode", :kind "defn", :line 19, :end-line 21, :hash "-348548138"} {:id "defn/then-handle-unit-at-coords", :kind "defn", :line 23, :end-line 24, :hash "1344289338"} {:id "defn/then-handle-unit-at-target", :kind "defn", :line 26, :end-line 27, :hash "-238342850"} {:id "defn/then-handle-eventually-at", :kind "defn", :line 29, :end-line 30, :hash "680244178"} {:id "defn/then-handle-unit-absent-on-map", :kind "defn", :line 32, :end-line 33, :hash "-56034517"} {:id "defn/then-handle-no-message", :kind "defn", :line 35, :end-line 36, :hash "2137981582"} {:id "defn/then-handle-message-for-unit", :kind "defn", :line 38, :end-line 40, :hash "941714887"} {:id "defn/then-handle-message-contains-literal", :kind "defn", :line 42, :end-line 43, :hash "1509504000"} {:id "defn/then-handle-message-contains-key", :kind "defn", :line 45, :end-line 47, :hash "-297038170"} {:id "defn/then-handle-message-is-key", :kind "defn", :line 49, :end-line 51, :hash "1160554347"} {:id "defn/then-handle-message-is-format", :kind "defn", :line 53, :end-line 57, :hash "1504770142"} {:id "defn/then-handle-bare-message-literal", :kind "defn", :line 59, :end-line 60, :hash "-2026730327"} {:id "defn/then-handle-bare-message-key", :kind "defn", :line 62, :end-line 64, :hash "805433974"} {:id "defn/then-handle-out-of-fuel", :kind "defn", :line 66, :end-line 67, :hash "-748449982"} {:id "defn/then-handle-player-map-not-nil", :kind "defn", :line 69, :end-line 70, :hash "1421353744"} {:id "defn/then-handle-computer-map-not-nil", :kind "defn", :line 72, :end-line 73, :hash "-475734592"} {:id "defn/then-handle-player-map-nil", :kind "defn", :line 75, :end-line 76, :hash "798499759"} {:id "defn/then-handle-cell-prop", :kind "defn", :line 78, :end-line 81, :hash "-42006807"} {:id "defn/then-handle-cell-type", :kind "defn", :line 83, :end-line 85, :hash "-826953596"} {:id "defn/then-handle-waiting-for-input", :kind "defn", :line 87, :end-line 88, :hash "-415955929"} {:id "defn/then-handle-not-waiting-for-input", :kind "defn", :line 90, :end-line 91, :hash "1177317507"} {:id "defn/then-handle-game-paused", :kind "defn", :line 93, :end-line 94, :hash "-375072425"} {:id "defn/then-handle-round", :kind "defn", :line 96, :end-line 97, :hash "402378373"} {:id "defn/then-handle-destination", :kind "defn", :line 99, :end-line 100, :hash "1333566120"} {:id "defn/then-handle-production-with-rounds", :kind "defn", :line 102, :end-line 104, :hash "-1406499179"} {:id "defn/then-handle-production", :kind "defn", :line 106, :end-line 107, :hash "597171010"} {:id "defn/then-handle-no-production", :kind "defn", :line 109, :end-line 110, :hash "655457101"} {:id "defn/then-handle-production-not", :kind "defn", :line 112, :end-line 113, :hash "-1443227192"} {:id "defn/then-handle-no-unit-at", :kind "defn", :line 115, :end-line 116, :hash "1675476889"} {:id "defn/then-handle-unit-has-mission", :kind "defn", :line 118, :end-line 120, :hash "1498172041"} {:id "defn/parse-edn-value", :kind "defn", :line 122, :end-line 124, :hash "-339210524"} {:id "defn/parse-bool", :kind "defn", :line 126, :end-line 130, :hash "1869938307"} {:id "defn/then-handle-unit-has-prop", :kind "defn", :line 132, :end-line 138, :hash "-1755307168"} {:id "defn/then-handle-unit-is-mode", :kind "defn", :line 140, :end-line 142, :hash "-1699952534"} {:id "defn/then-handle-unit-prop-absent", :kind "defn", :line 144, :end-line 145, :hash "-1690745460"} {:id "defn/then-handle-will-be-at", :kind "defn", :line 147, :end-line 150, :hash "468394377"} {:id "defn/then-handle-occupies-cell", :kind "defn", :line 152, :end-line 153, :hash "23469825"} {:id "defn/then-handle-remains-unmoved", :kind "defn", :line 155, :end-line 156, :hash "180518299"} {:id "defn/then-handle-airport-fighter", :kind "defn", :line 158, :end-line 159, :hash "-826646667"} {:id "defn/then-handle-fighter-aboard", :kind "defn", :line 161, :end-line 162, :hash "797913632"} {:id "defn/then-handle-no-fighters", :kind "defn", :line 164, :end-line 166, :hash "1577514876"} {:id "defn/then-handle-awake-fighters", :kind "defn", :line 168, :end-line 171, :hash "-2038580625"} {:id "defn/then-handle-unit-absent-short", :kind "defn", :line 173, :end-line 174, :hash "-2027201371"} {:id "defn/then-handle-refueling-position-near", :kind "defn", :line 176, :end-line 177, :hash "1214598645"} {:id "defn/then-handle-unit-present-coords", :kind "defn", :line 179, :end-line 180, :hash "-1895031117"} {:id "defn/then-handle-unit-present-target", :kind "defn", :line 182, :end-line 183, :hash "1941658296"} {:id "defn/then-handle-shipyard-has-ship", :kind "defn", :line 185, :end-line 186, :hash "-2104877890"} {:id "defn/then-handle-shipyard-empty", :kind "defn", :line 188, :end-line 189, :hash "742368612"} {:id "defn/then-handle-map-is", :kind "defn", :line 191, :end-line 192, :hash "-421459909"}]}
;; clj-mutate-manifest-end

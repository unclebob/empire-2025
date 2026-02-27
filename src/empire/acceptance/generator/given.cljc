(ns empire.acceptance.generator.given
  (:require [clojure.string :as str]
            [empire.acceptance.generator.utils :as utils]))

(defn- generate-map-given [{:keys [rows target]}]
  (let [row-strs (str/join " " (map #(str "\"" % "\"") rows))
        atom-str (case target
                   :player-map "atoms/player-map"
                   :computer-map "atoms/computer-map"
                   "atoms/game-map")]
    (str "    (reset! " atom-str " (build-test-map [" row-strs "]))")))

(defn- generate-unit-props-given [{:keys [unit props]}]
  (if (utils/city-spec? unit)
    (let [kvs (mapv (fn [[k v]]
                      (str ":" (name k) " " (pr-str v)))
                    props)]
      (str "    (let [city-pos (:pos (get-test-city atoms/game-map \"" unit "\"))]\n"
           "      (swap! atoms/game-map update-in city-pos assoc " (str/join " " kvs) "))"))
    (let [kvs (mapv (fn [[k v]]
                      (str ":" (name k) " " (pr-str v)))
                    props)]
      (str "    (set-test-unit atoms/game-map \"" unit "\" " (str/join " " kvs) ")"))))

(defn- find-container-city
  "When a unit label doesn't appear in the map rows, find the city
   that contains it by looking at container-state givens with awake-fighters."
  [givens]
  (let [map-rows (:rows (first (filter #(= :map (:type %)) givens)))]
    (when map-rows
      (->> givens
           (filter #(and (= :container-state (:type %))
                         (utils/city-spec? (:target %))
                         (pos? (get-in % [:props :awake-fighters] 0))))
           first
           :target))))

(defn- generate-waiting-for-input-given
  ([given] (generate-waiting-for-input-given given []))
  ([{:keys [unit set-mode]} givens]
   (let [lines (atom [])
         is-city (utils/city-spec? unit)
         map-given (first (filter #(= :map (:type %)) givens))
         unit-on-map? (or is-city
                          (nil? map-given)
                          (some #(str/includes? % unit) (:rows map-given)))
         container-city (when (not unit-on-map?) (find-container-city givens))
         effective-unit (or container-city unit)
         effective-is-city (or (some? container-city) is-city)
         pos-lookup (if effective-is-city
                      (str "(:pos (get-test-city atoms/game-map \"" effective-unit "\"))")
                      (str "(:pos (get-test-unit atoms/game-map \"" effective-unit "\"))"))]
     (when (and set-mode unit-on-map? (not is-city))
       (swap! lines conj (str "    (set-test-unit atoms/game-map \"" unit "\" :mode :awake)")))
     (when container-city
       (swap! lines conj (str "    (swap! atoms/production assoc " pos-lookup " :none)")))
     (swap! lines conj
            (str "    (let [cols (count @atoms/game-map)\n"
                 "          rows (count (first @atoms/game-map))\n"
                 "          pos " pos-lookup "]\n"
                 "      (reset! atoms/player-map (make-initial-test-map rows cols nil))\n"
                 "      (reset! atoms/player-items [pos])\n"
                 "      (item-processing/process-player-items-batch))"))
     (str/join "\n" @lines))))

(defn- infer-container-counts
  "When awake-fighters or awake-armies are set but the corresponding
   count key is missing, infer the count from the awake value."
  [props]
  (cond-> props
    (and (contains? props :awake-fighters) (not (contains? props :fighter-count)))
    (assoc :fighter-count (:awake-fighters props))
    (and (contains? props :awake-armies) (not (contains? props :army-count)))
    (assoc :army-count (:awake-armies props))))

(defn- generate-container-state-given [{:keys [target props]}]
  (let [props (infer-container-counts props)]
    (if (utils/city-spec? target)
      (let [prop-map (str/join " " (mapcat (fn [[k v]] [(str ":" (name k)) (pr-str v)]) props))]
        (str "    (let [" (str/lower-case target) "-pos (:pos (get-test-city atoms/game-map \"" target "\"))]\n"
             "      (swap! atoms/game-map update-in " (str/lower-case target) "-pos merge {" prop-map "}))"))
      (let [kvs (mapv (fn [[k v]] (str ":" (name k) " " (pr-str v))) props)]
        (str "    (set-test-unit atoms/game-map \"" target "\" " (str/join " " kvs) ")")))))

(defn- generate-production-given [{:keys [city item remaining-rounds]}]
  (let [pos-expr (utils/target-pos-expr city)]
    (if remaining-rounds
      (str "    (let [" (str/lower-case city) "-pos " pos-expr "]\n"
           "      (swap! atoms/production assoc " (str/lower-case city) "-pos {:item :" (name item) " :remaining-rounds " remaining-rounds "}))")
      (str "    (let [" (str/lower-case city) "-pos " pos-expr "]\n"
           "      (swap! atoms/production assoc " (str/lower-case city) "-pos {:item :" (name item) "}))"))))

(defn- generate-city-prop-given [{:keys [city prop value]}]
  (let [pos-expr (utils/target-pos-expr city)]
    (str "    (let [" (str/lower-case city) "-pos " pos-expr "]\n"
         "      (swap! atoms/game-map update-in " (str/lower-case city) "-pos assoc :" (name prop) " " value "))")))

(defn- generate-territory-around-given [{:keys [city country-id]}]
  (let [pos-expr (utils/target-pos-expr city)]
    (str "    (let [center " pos-expr "]\n"
         "      (doseq [[di dj] [[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]]]\n"
         "        (let [npos [(+ (first center) di) (+ (second center) dj)]\n"
         "              cell (get-in @atoms/game-map npos)]\n"
         "          (when (and cell (#{:land :city} (:type cell)))\n"
         "            (swap! atoms/game-map assoc-in (conj npos :country-id) " country-id ")))))")))

(defn- generate-unit-target-given [{:keys [unit target]}]
  (let [target-expr (utils/target-pos-expr target)]
    (str "    (set-test-unit atoms/game-map \"" unit "\" :mode :moving :target " target-expr ")")))

(defn- generate-round-given [{:keys [value]}]
  (str "    (reset! atoms/round-number " value ")"))

(defn- generate-destination-given [{:keys [coords]}]
  (str "    (reset! atoms/destination " (pr-str coords) ")"))

(defn- generate-cell-props-given [{:keys [coords props]}]
  (let [prop-map (str/join " " (mapcat (fn [[k v]] [(str ":" (name k)) (pr-str v)]) props))]
    (str "    (swap! atoms/game-map update-in " (pr-str coords) " merge {" prop-map "})")))

(defn- generate-player-items-given [{:keys [items]}]
  (let [exprs (mapv utils/target-pos-expr items)]
    (str "    (reset! atoms/player-items [" (str/join " " exprs) "])")))

(defn- generate-waiting-for-input-state-given [_]
  "    (reset! atoms/waiting-for-input true)")

(defn- generate-no-production-given [_]
  "    (reset! atoms/production {})")

(defn- generate-city-unit-given [{:keys [city unit-type owner]}]
  (let [pos-expr (utils/target-pos-expr city)]
    (str "    (let [" (str/lower-case city) "-pos " pos-expr "]\n"
         "      (swap! atoms/game-map assoc-in (conj " (str/lower-case city) "-pos :contents)\n"
         "        {:type :" (name unit-type) " :owner :" (name owner) " :mode :sentry :hits 1}))")))

(defn- generate-shipyard-state-given [{:keys [city ship-type hits]}]
  (let [pos-expr (utils/target-pos-expr city)]
    (str "    (let [" (str/lower-case city) "-pos " pos-expr "]\n"
         "      (swap! atoms/game-map update-in " (str/lower-case city) "-pos\n"
         "        update :shipyard (fnil conj []) {:type :" (name ship-type) " :hits " hits "}))")))

(defn- generate-visible-to-computer-given [{:keys [ref]}]
  (let [pos-expr (utils/target-pos-expr ref)]
    (str "    (let [pos " pos-expr "\n"
         "          gm @atoms/game-map]\n"
         "      (when (not (vector? @atoms/computer-map))\n"
         "        (reset! atoms/computer-map (make-initial-test-map (count (first gm)) (count gm) nil)))\n"
         "      (swap! atoms/computer-map assoc-in pos (get-in gm pos)))")))

(def ^:private given-generators
  {:map                     generate-map-given
   :unit-props              generate-unit-props-given
   :container-state         generate-container-state-given
   :production              generate-production-given
   :unit-target             generate-unit-target-given
   :round                   generate-round-given
   :destination             generate-destination-given
   :cell-props              generate-cell-props-given
   :city-prop               generate-city-prop-given
   :player-items            generate-player-items-given
   :waiting-for-input-state generate-waiting-for-input-state-given
   :no-production           generate-no-production-given
   :shipyard-state          generate-shipyard-state-given
   :city-unit               generate-city-unit-given
   :territory-around        generate-territory-around-given
   :visible-to-computer     generate-visible-to-computer-given
   :game-over-check-enabled (fn [_] "    (reset! atoms/game-over-check-enabled true)")
   :game-paused             (fn [_] "    (reset! atoms/paused true)")
   :pause-requested         (fn [_] "    (reset! atoms/pause-requested true)")
   :load-menu-open          (fn [_] "    (reset! atoms/load-menu-open true)")})

(defn generate-given
  "Generate code string for a single GIVEN IR node."
  ([given] (generate-given given []))
  ([given givens]
   (let [type (:type given)
         gen (get given-generators type)]
     (cond
       gen (gen given)
       (= type :waiting-for-input)
       (generate-waiting-for-input-given given givens)
       (= type :stub) ""
       (= type :unrecognized)
       (str "    (pending \"Unrecognized: " (:text given) "\")")
       :else
       (str "    ;; Unknown given type: " type)))))

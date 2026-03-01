(ns empire.acceptance.generator.given
  (:require [clojure.string :as str]
            [empire.acceptance.generator.utils :as utils]))

(defn- generate-map-given [{:keys [rows target]}]
  (let [row-strs (str/join " " (map #(str "\"" % "\"") rows))]
    (case target
      :player-map (str "    (h/set-state! :player-map (build-test-map [" row-strs "]))")
      :computer-map (str "    (h/set-state! :computer-map (build-test-map [" row-strs "]))")
      (str "    (set-test-world! (build-test-map [" row-strs "]))"))))

(defn- generate-unit-props-given [{:keys [unit props]}]
  (if (utils/city-spec? unit)
    (let [kvs (mapv (fn [[k v]]
                      (str ":" (name k) " " (pr-str v)))
                    props)]
      (str "    (let [city-pos (:pos (h/get-city \"" unit "\"))]\n"
           "      (update-test-world! update-in city-pos assoc " (str/join " " kvs) "))"))
    (let [kvs (mapv (fn [[k v]]
                      (str ":" (name k) " " (pr-str v)))
                    props)]
      (str "    (h/set-unit! \"" unit "\" " (str/join " " kvs) ")"))))

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
                      (str "(:pos (h/get-city \"" effective-unit "\"))")
                      (str "(:pos (h/get-unit \"" effective-unit "\"))"))]
     (when (and set-mode unit-on-map? (not is-city))
       (swap! lines conj (str "    (h/set-unit! \"" unit "\" :mode :awake)")))
     (when container-city
       (swap! lines conj (str "    (h/update-state! :production assoc " pos-lookup " :none)")))
     (swap! lines conj
            (str "    (let [gm (h/read-state :game-map)\n"
                 "          cols (count gm)\n"
                 "          rows (count (first gm))\n"
                 "          pos " pos-lookup "]\n"
                 "      (h/set-state! :player-map (make-initial-test-map rows cols nil))\n"
                 "      (h/set-state! :player-items [pos])\n"
                 "      (h/process-player-items-batch!))"))
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
        (str "    (let [" (str/lower-case target) "-pos (:pos (h/get-city \"" target "\"))]\n"
             "      (update-test-world! update-in " (str/lower-case target) "-pos merge {" prop-map "}))"))
      (let [kvs (mapv (fn [[k v]] (str ":" (name k) " " (pr-str v))) props)]
        (str "    (h/set-unit! \"" target "\" " (str/join " " kvs) ")")))))

(defn- generate-production-given [{:keys [city item remaining-rounds]}]
  (let [pos-expr (utils/target-pos-expr city)]
    (if remaining-rounds
      (str "    (let [" (str/lower-case city) "-pos " pos-expr "]\n"
           "      (h/update-state! :production assoc " (str/lower-case city) "-pos {:item :" (name item) " :remaining-rounds " remaining-rounds "}))")
      (str "    (let [" (str/lower-case city) "-pos " pos-expr "]\n"
           "      (h/update-state! :production assoc " (str/lower-case city) "-pos {:item :" (name item) "}))"))))

(defn- generate-city-prop-given [{:keys [city prop value]}]
  (let [pos-expr (utils/target-pos-expr city)]
    (str "    (let [" (str/lower-case city) "-pos " pos-expr "]\n"
         "      (update-test-world! update-in " (str/lower-case city) "-pos assoc :" (name prop) " " value "))")))

(defn- generate-territory-around-given [{:keys [city country-id]}]
  (let [pos-expr (utils/target-pos-expr city)]
    (str "    (let [center " pos-expr "]\n"
         "      (doseq [[di dj] [[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]]]\n"
         "        (let [npos [(+ (first center) di) (+ (second center) dj)]\n"
         "              cell (get-in (h/read-state :game-map) npos)]\n"
         "          (when (and cell (#{:land :city} (:type cell)))\n"
         "            (update-test-world! assoc-in (conj npos :country-id) " country-id ")))))")))

(defn- generate-unit-target-given [{:keys [unit target]}]
  (let [target-expr (utils/target-pos-expr target)]
    (str "    (h/set-unit! \"" unit "\" :mode :moving :target " target-expr ")")))

(defn- generate-round-given [{:keys [value]}]
  (str "    (h/set-state! :round-number " value ")"))

(defn- generate-destination-given [{:keys [coords]}]
  (str "    (h/set-state! :destination " (pr-str coords) ")"))

(defn- generate-cell-props-given [{:keys [coords props]}]
  (let [prop-map (str/join " " (mapcat (fn [[k v]] [(str ":" (name k)) (pr-str v)]) props))]
    (str "    (update-test-world! update-in " (pr-str coords) " merge {" prop-map "})")))

(defn- generate-player-items-given [{:keys [items]}]
  (let [exprs (mapv utils/target-pos-expr items)]
    (str "    (h/set-state! :player-items [" (str/join " " exprs) "])")))

(defn- generate-waiting-for-input-state-given [_]
  "    (h/set-state! :waiting-for-input true)")

(defn- generate-no-production-given [_]
  "    (h/set-state! :production {})")

(defn- generate-city-unit-given [{:keys [city unit-type owner]}]
  (let [pos-expr (utils/target-pos-expr city)]
    (str "    (let [" (str/lower-case city) "-pos " pos-expr "]\n"
         "      (update-test-world! assoc-in (conj " (str/lower-case city) "-pos :contents)\n"
         "        {:type :" (name unit-type) " :owner :" (name owner) " :mode :sentry :hits 1}))")))

(defn- generate-shipyard-state-given [{:keys [city ship-type hits]}]
  (let [pos-expr (utils/target-pos-expr city)]
    (str "    (let [" (str/lower-case city) "-pos " pos-expr "]\n"
         "      (update-test-world! update-in " (str/lower-case city) "-pos\n"
         "        update :shipyard (fnil conj []) {:type :" (name ship-type) " :hits " hits "}))")))

(defn- generate-visible-to-computer-given [{:keys [ref]}]
  (let [pos-expr (utils/target-pos-expr ref)]
    (str "    (let [pos " pos-expr "\n"
         "          gm (h/read-state :game-map)]\n"
         "      (when (not (vector? (h/read-state :computer-map)))\n"
         "        (h/set-state! :computer-map (make-initial-test-map (count (first gm)) (count gm) nil)))\n"
         "      (h/update-state! :computer-map assoc-in pos (get-in gm pos)))")))

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
   :game-over-check-enabled (fn [_] "    (h/set-state! :game-over-check-enabled true)")
   :game-paused             (fn [_] "    (h/set-state! :paused true)")
   :pause-requested         (fn [_] "    (h/set-state! :pause-requested true)")
   :load-menu-open          (fn [_] "    (h/set-state! :load-menu-open true)")
   :map-display-setup       (fn [{:keys [value]}] (str "    (h/set-state! :map-to-display :" (name value) ")"))})

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

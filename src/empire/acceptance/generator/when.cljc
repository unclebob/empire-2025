(ns empire.acceptance.generator.when
  (:require [empire.acceptance.generator.utils :as utils]
            [empire.acceptance.generator.given :as given]))

(defn- generate-key-press-when [{:keys [key input-fn]}]
  (if (= input-fn :key-down)
    (str "    (with-redefs [q/mouse-x (constantly 0)\n"
         "                  q/mouse-y (constantly 0)]\n"
         "      (reset! atoms/last-key nil)\n"
         "      (quil-input/key-down :" (name key) "))")
    (str "    (input/handle-key :" (name key) ")")))

(defn- generate-battle-when [{:keys [key outcome combat-type]}]
  (let [rand-val (if (= outcome :win) "0.0" "1.0")]
    (if (= combat-type :ship)
      (str "    (with-redefs [rand (constantly " rand-val ")]\n"
           "      (input/handle-key :" (name key) ")\n"
           "      (game-loop/advance-game))")
      (str "    (with-redefs [rand (constantly " rand-val ")]\n"
           "      (input/handle-key :" (name key) "))"))))

(defn- generate-backtick-when [{:keys [key mouse-cell]}]
  (let [[x y] mouse-cell]
    (str "    (reset! atoms/map-screen-dimensions [22 16])\n"
         "    (with-redefs [q/mouse-x (constantly " x ")\n"
         "                  q/mouse-y (constantly " y ")]\n"
         "      (quil-input/key-down (keyword \"`\"))\n"
         "      (quil-input/key-down :" (name key) "))")))

(defn- mouse-at-key-expr [key]
  (case key
    :period "(keyword \".\")"
    (str ":" (name key))))

(defn- generate-mouse-at-key-when [{:keys [coords key]}]
  (let [[cx cy] coords
        key-expr (mouse-at-key-expr key)]
    (str "    (let [map-w 22 map-h 16\n"
         "          cols (count @atoms/game-map)\n"
         "          rows (count (first @atoms/game-map))\n"
         "          cell-w (/ map-w cols) cell-h (/ map-h rows)\n"
         "          px (int (+ (* " cx " cell-w) (/ cell-w 2)))\n"
         "          py (int (+ (* " cy " cell-h) (/ cell-h 2)))]\n"
         "      (reset! atoms/map-screen-dimensions [map-w map-h])\n"
         "      (with-redefs [q/mouse-x (constantly px)\n"
         "                    q/mouse-y (constantly py)]\n"
         "        (quil-input/key-down " key-expr ")))")))

(defn- generate-visibility-update-when [_]
  "    (game-loop/update-player-map)")

(defn- generate-cell-visibility-update-when [{:keys [unit]}]
  (let [pos-expr (utils/target-pos-expr unit)]
    (str "    (let [pos " pos-expr "\n"
         "          cell (get-in @atoms/game-map pos)]\n"
         "      (visibility/update-cell-visibility pos (:owner (:contents cell)) (:contents cell)))")))

(defn- generate-evaluate-production-when [{:keys [city]}]
  (let [pos-expr (utils/target-pos-expr city)]
    (str "    (computer-production/process-computer-city " pos-expr ")")))

(defn- generate-process-computer-transport-when [{:keys [unit]}]
  (let [pos-expr (utils/target-pos-expr unit)]
    (str "    (computer-transport/process-transport " pos-expr ")")))

(defn- generate-process-computer-fighter-when [{:keys [unit]}]
  (let [pos-expr (utils/target-pos-expr unit)]
    (str "    (let [pos " pos-expr "\n"
         "          unit (get-in @atoms/game-map (conj pos :contents))]\n"
         "      (computer-fighter/process-fighter pos unit))")))

(defn- generate-computer-rounds-when [{:keys [count]}]
  (str "    (dotimes [_ " count "]\n"
       "      ;; Process all computer transports\n"
       "      (doseq [i (range (count @atoms/game-map))\n"
       "              j (range (count (first @atoms/game-map)))\n"
       "              :let [cell (get-in @atoms/game-map [i j])\n"
       "                    unit (:contents cell)]\n"
       "              :when (and unit\n"
       "                         (= :transport (:type unit))\n"
       "                         (= :computer (:owner unit)))]\n"
       "        (computer-transport/process-transport [i j])))"))

(defn- generate-start-new-round-when [_]
  (str "    (game-loop/start-new-round)\n"
       "    (loop [n 200]\n"
       "      (when (and (pos? n)\n"
       "                 (not @atoms/waiting-for-input)\n"
       "                 (not @atoms/paused)\n"
       "                 (or (seq @atoms/player-items) (seq @atoms/computer-items)))\n"
       "        (game-loop/advance-game)\n"
       "        (recur (dec n))))"))

(defn- generate-advance-game-when [_]
  "    (game-loop/advance-game)")

(defn- generate-process-player-items-when [_]
  "    (item-processing/process-player-items-batch)")

(defn- generate-advance-until-waiting-when [{:keys [unit]}]
  (str "    (should= :ok (advance-until-unit-waiting \"" unit "\"))"))

(def ^:private when-generators
  {:key-press                  generate-key-press-when
   :battle                     generate-battle-when
   :backtick                   generate-backtick-when
   :mouse-at-key               generate-mouse-at-key-when
   :visibility-update          generate-visibility-update-when
   :cell-visibility-update     generate-cell-visibility-update-when
   :start-new-round            generate-start-new-round-when
   :advance-game               generate-advance-game-when
   :advance-game-batch         generate-advance-game-when
   :process-player-items       generate-process-player-items-when
   :advance-until-waiting      generate-advance-until-waiting-when
   :evaluate-production        generate-evaluate-production-when
   :process-computer-transport generate-process-computer-transport-when
   :process-computer-fighter   generate-process-computer-fighter-when
   :computer-rounds            generate-computer-rounds-when})

(defn generate-when
  "Generate code string for a single WHEN IR node."
  ([when-ir] (generate-when when-ir []))
  ([when-ir givens]
   (let [type (:type when-ir)
         gen (get when-generators type)]
     (cond
       gen (gen when-ir)
       (= type :waiting-for-input)
       (given/generate-given when-ir givens)
       (= type :unrecognized)
       (str "    (pending \"Unrecognized: " (:text when-ir) "\")")
       :else
       (str "    ;; Unknown when type: " type)))))

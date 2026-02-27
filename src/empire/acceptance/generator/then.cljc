(ns empire.acceptance.generator.then
  (:require [clojure.string :as str]
            [empire.acceptance.generator.utils :as utils]))

(defn- generate-unit-prop-then [{:keys [unit property expected]}]
  (if (= :nil expected)
    (str "    (should-be-nil (:" (name property) " (:unit (get-test-unit atoms/game-map \"" unit "\"))))")
    (str "    (should= " (pr-str expected) " (:" (name property) " (:unit (get-test-unit atoms/game-map \"" unit "\"))))")))

(defn- generate-unit-absent-then [{:keys [unit]}]
  (str "    (should-be-nil (get-test-unit atoms/game-map \"" unit "\"))"))

(defn- generate-unit-present-then [{:keys [unit coords target]}]
  (if target
    (let [target-expr (utils/target-pos-expr target)]
      (str "    (let [{:keys [pos]} (get-test-unit atoms/game-map \"" unit "\")]\n"
           "      (should-not-be-nil pos)\n"
           "      (should= " target-expr " pos))"))
    (str "    (let [{:keys [pos]} (get-test-unit atoms/game-map \"" unit "\")]\n"
         "      (should= " (pr-str coords) " pos))")))

(defn- generate-unit-at-then [{:keys [unit coords target]}]
  (if target
    (let [target-expr (utils/target-pos-expr target)]
      (str "    (let [{:keys [pos]} (get-test-unit atoms/game-map \"" unit "\")]\n"
           "      (should= " target-expr " pos))"))
    (str "    (let [{:keys [pos]} (get-test-unit atoms/game-map \"" unit "\")]\n"
         "      (should= " (pr-str coords) " pos))")))

(defn- generate-unit-at-next-round-then [{:keys [unit target at-next-step]}]
  (let [target-expr (utils/target-pos-expr target)
        advance (if at-next-step
                  "    (game-loop/advance-game)\n"
                  "    (should= :ok (advance-until-next-round))\n")]
    (str advance
         "    (let [{:keys [pos]} (get-test-unit atoms/game-map \"" unit "\")\n"
         "          target-pos " target-expr "]\n"
         "      (should= target-pos pos))")))

(defn- generate-unit-after-moves-then [{:keys [unit moves target]}]
  (let [target-expr (utils/target-pos-expr target)]
    (str "    (dotimes [_ " moves "] (game-loop/advance-game))\n"
         "    (let [{:keys [pos]} (get-test-unit atoms/game-map \"" unit "\")\n"
         "          target-pos " target-expr "]\n"
         "      (should= target-pos pos))")))

(defn- generate-unit-after-steps-then [{:keys [unit steps target coords]}]
  (let [pos-expr (if target
                   (utils/target-pos-expr target)
                   (pr-str coords))]
    (str "    (dotimes [_ " steps "] (game-loop/advance-game))\n"
         "    (let [target-pos " pos-expr "\n"
         "          f-result (get-test-unit atoms/game-map \"" unit "\")]\n"
         "      (should-not-be-nil f-result)\n"
         "      (should= target-pos (:pos f-result)))")))

(defn- generate-unit-eventually-at-then [{:keys [unit target]}]
  (let [target-expr (utils/target-pos-expr target)]
    (str "    (let [target-pos " target-expr "]\n"
         "      (loop [n 0]\n"
         "        (when (< n 20)\n"
         "          (let [{:keys [pos]} (get-test-unit atoms/game-map \"" unit "\")]\n"
         "            (when (not= target-pos pos)\n"
         "              (game-loop/advance-game)\n"
         "              (recur (inc n))))))\n"
         "      (should= target-pos (:pos (get-test-unit atoms/game-map \"" unit "\"))))")))

(defn- find-unit-initial-pos
  "Given a map rows and a unit spec, find the [col row] position."
  [rows unit-spec]
  (let [ch (first unit-spec)]
    (first (for [r (range (count rows))
                 c (range (count (nth rows r)))
                 :when (= ch (nth (nth rows r) c))]
             [c r]))))

(defn- generate-unit-occupies-cell-then
  "Unit occupies the cell where target-unit was originally placed."
  [{:keys [unit target-unit]} givens]
  (let [map-given (first (filter #(= :map (:type %)) givens))
        rows (:rows map-given)
        target-pos (find-unit-initial-pos rows target-unit)]
    (str "    (let [{:keys [pos]} (get-test-unit atoms/game-map \"" unit "\")]\n"
         "      (should= " (pr-str target-pos) " pos))")))

(defn- generate-unit-unmoved-then
  "Assert unit hasn't moved from its original position."
  [{:keys [unit]} givens]
  (let [map-given (first (filter #(= :map (:type %)) givens))
        rows (:rows map-given)
        orig-pos (find-unit-initial-pos rows unit)]
    (str "    (let [{:keys [pos]} (get-test-unit atoms/game-map \"" unit "\")]\n"
         "      (should= " (pr-str orig-pos) " pos))")))

(defn- generate-unit-waiting-for-input-then [{:keys [unit]}]
  (str "    (should= :ok (advance-until-unit-waiting \"" unit "\"))"))

(defn- generate-message-assertion [{:keys [area config-key text at-next-round at-next-step unit] :as ir}]
  (let [atom-str (utils/area->atom area)]
    (case (:type ir)
      :message-contains
      (let [advance (cond
                      at-next-round "    (should= :ok (advance-until-next-round))\n"
                      at-next-step  "    (game-loop/advance-game)\n"
                      :else         "")]
        (if config-key
          (str advance
               "    (should-not-be-nil (:" (name config-key) " config/messages))\n"
               "    (should (message-matches? (:" (name config-key) " config/messages) @" atom-str "))")
          (str advance "    (should-contain \"" text "\" @" atom-str ")")))
      :message-for-unit
      (str "    (should= :ok\n"
           "      (loop [n 100]\n"
           "        (let [u (get-test-unit atoms/game-map \"" unit "\")]\n"
           "          (cond\n"
           "            (and u (= :awake (:mode (:unit u))) @atoms/waiting-for-input) :ok\n"
           "            (zero? n) :timeout\n"
           "            :else (do (game-loop/advance-game) (recur (dec n)))))))\n"
           "    (should-not-be-nil (:" (name config-key) " config/messages))\n"
           "    (should (message-matches? (:" (name config-key) " config/messages) @" atom-str "))")
      :message-is
      (if config-key
        (str "    (should= (:" (name config-key) " config/messages) @" atom-str ")")
        (let [{:keys [key args]} (:format ir)
              args-str (str/join " " (map pr-str args))]
          (str "    (should= (format (:" (name key) " config/messages) " args-str ") @" atom-str ")")))
      :no-message
      (str "    (should= \"\" @" atom-str ")"))))

(defn- generate-cell-prop-then [{:keys [coords property expected target]}]
  (let [map-atom (if (= target :computer-map) "atoms/computer-map" "atoms/game-map")]
    (str "    (should= " (pr-str expected) " (:" (name property) " (get-in @" map-atom " " (pr-str coords) ")))")))

(defn- generate-cell-type-then [{:keys [coords expected]}]
  (str "    (should= " (pr-str expected) " (:type (get-in @atoms/game-map " (pr-str coords) ")))"))

(defn- generate-waiting-for-input-then [{:keys [expected]}]
  (if expected
    "    (should @atoms/waiting-for-input)"
    "    (should-not @atoms/waiting-for-input)"))

(defn- generate-container-prop-then [{:keys [target property expected lookup at-next-round at-next-step]}]
  (let [advance (cond
                  at-next-round "    (should= :ok (advance-until-next-round))\n"
                  at-next-step  "    (game-loop/advance-game)\n"
                  :else         "")]
    (if (= lookup :city)
      (str advance
           "    (let [" (str/lower-case target) "-pos (:pos (get-test-city atoms/game-map \"" target "\"))\n"
           "          cell (get-in @atoms/game-map " (str/lower-case target) "-pos)]\n"
           "      (should= " (pr-str expected) " (:" (name property) " cell)))")
      (str advance
           "    (should= " (pr-str expected) " (:" (name property) " (:unit (get-test-unit atoms/game-map \"" target "\"))))"))))


(defn- generate-round-then [{:keys [expected]}]
  (str "    (should= " expected " @atoms/round-number)"))

(defn- generate-destination-then [{:keys [expected]}]
  (str "    (should= " (pr-str expected) " @atoms/destination)"))

(defn- generate-production-assertion [{:keys [city expected excluded remaining-rounds] :as ir}]
  (let [pos-expr (utils/target-pos-expr city)]
    (case (:type ir)
      :production
      (str "    (should= " (pr-str expected) " (:item (get @atoms/production " pos-expr ")))")
      :no-production
      (str "    (let [prod (get @atoms/production " pos-expr ")]\n"
           "      (should (or (nil? prod) (= :none prod))))")
      :production-with-rounds
      (str "    (let [prod (get @atoms/production " pos-expr ")]\n"
           "      (should= " (pr-str expected) " (:item prod))\n"
           "      (should= " remaining-rounds " (:remaining-rounds prod)))")
      :production-not
      (str "    (should-not= " (pr-str excluded) " (:item (get @atoms/production " pos-expr ")))"))))

(defn- generate-game-paused-then [_]
  "    (should @atoms/paused)")

(defn- generate-map-cell-assertion [{:keys [coords]} map-atom assertion]
  (let [[x y] coords]
    (str "    (" assertion " (get-in @" map-atom " [" x " " y "]))")))

(defn- generate-territory-map-then [{:keys [rows]}]
  (let [row-strs (str/join " " (map #(str "\"" % "\"") rows))]
    (str "    (should= (build-territory-expected [" row-strs "])"
         "\n             (territory-mask @atoms/game-map))")))

(defn- generate-player-map-visibility-then [{:keys [rows]}]
  (let [row-strs (str/join " " (map #(str "\"" % "\"") rows))]
    (str "    (should= (visibility-mask (build-test-map [" row-strs "]))"
         "\n             (visibility-mask @atoms/player-map))")))

(defn- generate-no-unit-at-then [{:keys [coords]}]
  (str "    (should-be-nil (:contents (get-in @atoms/game-map " (pr-str coords) ")))"))

(defn- generate-unit-prop-absent-then [{:keys [unit property]}]
  (str "    (should-be-nil (:" (name property) " (:unit (get-test-unit atoms/game-map \"" unit "\"))))"))

(defn- generate-computer-army-count-then [{:keys [expected]}]
  (str "    (let [count (count (for [i (range (count @atoms/game-map))\n"
       "                             j (range (count (first @atoms/game-map)))\n"
       "                             :let [cell (get-in @atoms/game-map [i j])]\n"
       "                             :when (and (:contents cell)\n"
       "                                        (= :army (:type (:contents cell)))\n"
       "                                        (= :computer (:owner (:contents cell))))]\n"
       "                         true))]\n"
       "      (should= " expected " count))"))

(defn- generate-shipyard-has-ship-then [{:keys [city ship-type hits]}]
  (let [pos-expr (utils/target-pos-expr city)]
    (str "    (let [cell (get-in @atoms/game-map " pos-expr ")\n"
         "          shipyard (:shipyard cell [])]\n"
         "      (should (some #(and (= :" (name ship-type) " (:type %)) (= " hits " (:hits %))) shipyard)))")))

(defn- generate-shipyard-empty-then [{:keys [city]}]
  (let [pos-expr (utils/target-pos-expr city)]
    (str "    (let [cell (get-in @atoms/game-map " pos-expr ")\n"
         "          shipyard (:shipyard cell [])]\n"
         "      (should= [] shipyard))")))

(defn- generate-map-is-then [{:keys [expected]}]
  (str "    (let [expected (build-test-map [\"" expected "\"])\n"
       "          actual @atoms/game-map]\n"
       "      (doseq [col (range (count expected))\n"
       "              row (range (count (first expected)))\n"
       "              :let [exp-cell (get-in expected [col row])\n"
       "                    act-cell (get-in actual [col row])]\n"
       "              :when exp-cell]\n"
       "        (should= (:type exp-cell) (:type act-cell))\n"
       "        (when (:city-status exp-cell)\n"
       "          (should= (:city-status exp-cell) (:city-status act-cell)))\n"
       "        (when (:contents exp-cell)\n"
       "          (should-not-be-nil (:contents act-cell))\n"
       "          (should= (:type (:contents exp-cell)) (:type (:contents act-cell)))\n"
       "          (should= (:owner (:contents exp-cell)) (:owner (:contents act-cell))))))"))

(defn- generate-refueling-position-near-then [{:keys [unit target]}]
  (let [target-expr (utils/target-pos-expr target)]
    (str "    (let [unit-data (:unit (get-test-unit atoms/game-map \"" unit "\"))\n"
         "          carrier-target (:carrier-target unit-data)\n"
         "          expected-pos " target-expr "\n"
         "          distance (+ (Math/abs (- (first carrier-target) (first expected-pos)))\n"
         "                      (Math/abs (- (second carrier-target) (second expected-pos))))]\n"
         "      (should= :position (:refueling unit-data))\n"
         "      (should (<= distance 1)))")))

(def ^:private then-dispatch
  {:unit-prop (fn [ir _] (generate-unit-prop-then ir))
   :unit-absent (fn [ir _] (generate-unit-absent-then ir))
   :unit-present (fn [ir _] (generate-unit-present-then ir))
   :unit-at (fn [ir _] (generate-unit-at-then ir))
   :unit-at-next-round (fn [ir _] (generate-unit-at-next-round-then ir))
   :unit-after-moves (fn [ir _] (generate-unit-after-moves-then ir))
   :unit-after-steps (fn [ir _] (generate-unit-after-steps-then ir))
   :unit-eventually-at (fn [ir _] (generate-unit-eventually-at-then ir))
   :unit-waiting-for-input (fn [ir _] (generate-unit-waiting-for-input-then ir))
   :unit-occupies-cell generate-unit-occupies-cell-then
   :unit-unmoved generate-unit-unmoved-then
   :message-contains (fn [ir _] (generate-message-assertion ir))
   :message-for-unit (fn [ir _] (generate-message-assertion ir))
   :message-is (fn [ir _] (generate-message-assertion ir))
   :no-message (fn [ir _] (generate-message-assertion ir))
   :cell-prop (fn [ir _] (generate-cell-prop-then ir))
   :cell-type (fn [ir _] (generate-cell-type-then ir))
   :waiting-for-input (fn [ir _] (generate-waiting-for-input-then ir))
   :container-prop (fn [ir _] (generate-container-prop-then ir))
   :round (fn [ir _] (generate-round-then ir))
   :destination (fn [ir _] (generate-destination-then ir))
   :production (fn [ir _] (generate-production-assertion ir))
   :production-with-rounds (fn [ir _] (generate-production-assertion ir))
   :no-production (fn [ir _] (generate-production-assertion ir))
   :production-not (fn [ir _] (generate-production-assertion ir))
   :game-paused (fn [ir _] (generate-game-paused-then ir))
   :player-map-cell-not-nil (fn [ir _] (generate-map-cell-assertion ir "atoms/player-map" "should-not-be-nil"))
   :player-map-cell-nil (fn [ir _] (generate-map-cell-assertion ir "atoms/player-map" "should-be-nil"))
   :computer-map-cell-not-nil (fn [ir _] (generate-map-cell-assertion ir "atoms/computer-map" "should-not-be-nil"))
   :player-map-visibility (fn [ir _] (generate-player-map-visibility-then ir))
   :territory-map (fn [ir _] (generate-territory-map-then ir))
   :no-unit-at (fn [ir _] (generate-no-unit-at-then ir))
   :unit-prop-absent (fn [ir _] (generate-unit-prop-absent-then ir))
   :computer-army-count (fn [ir _] (generate-computer-army-count-then ir))
   :refueling-position-near (fn [ir _] (generate-refueling-position-near-then ir))
   :shipyard-has-ship (fn [ir _] (generate-shipyard-has-ship-then ir))
   :shipyard-empty (fn [ir _] (generate-shipyard-empty-then ir))
   :map-is (fn [ir _] (generate-map-is-then ir))
   :game-not-paused (fn [_ _] "    (should-not @atoms/paused)")
   :map-display (fn [ir _] (str "    (should= " (pr-str (:expected ir)) " @atoms/map-to-display)"))
   :load-menu-state (fn [ir _] (if (:expected ir)
                                  "    (should @atoms/load-menu-open)"
                                  "    (should-not @atoms/load-menu-open)"))})

(defn generate-then
  "Generate code string for a single THEN IR node."
  [then-ir givens]
  (if-let [gen (get then-dispatch (:type then-ir))]
    (gen then-ir givens)
    (if (= :unrecognized (:type then-ir))
      (str "    (pending \"Unrecognized: " (:text then-ir) "\")")
      (str "    ;; Unknown then type: " (:type then-ir)))))

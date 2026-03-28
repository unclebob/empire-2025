(ns empire.acceptance.generator.then
  (:require [clojure.string :as str]
            [empire.acceptance.generator.utils :as utils]))

(defn- generate-unit-prop-then [{:keys [unit property expected]}]
  (if (= :nil expected)
    (str "    (should-be-nil (:" (name property) " (:unit (h/get-unit \"" unit "\"))))")
    (str "    (should= " (pr-str expected) " (:" (name property) " (:unit (h/get-unit \"" unit "\"))))")))

(defn- generate-unit-absent-then [{:keys [unit]}]
  (str "    (should-be-nil (h/get-map-unit \"" unit "\"))"))

(defn- generate-unit-present-then [{:keys [unit coords target]}]
  (if target
    (let [target-expr (utils/target-pos-expr target)]
      (str "    (let [{:keys [pos]} (h/get-unit \"" unit "\")]\n"
           "      (should-not-be-nil pos)\n"
           "      (should= " target-expr " pos))"))
    (str "    (let [{:keys [pos]} (h/get-unit \"" unit "\")]\n"
         "      (should= " (pr-str coords) " pos))")))

(defn- generate-unit-at-then [{:keys [unit coords target]}]
  (if target
    (let [target-expr (utils/target-pos-expr target)]
      (str "    (let [{:keys [pos]} (h/get-unit \"" unit "\")]\n"
           "      (should= " target-expr " pos))"))
    (str "    (let [{:keys [pos]} (h/get-unit \"" unit "\")]\n"
         "      (should= " (pr-str coords) " pos))")))

(defn- generate-unit-at-next-round-then [{:keys [unit target at-next-step]}]
  (let [target-expr (utils/target-pos-expr target)
        advance (if at-next-step
                  "    (h/advance-game!)\n"
                  "    (should= :ok (advance-until-next-round))\n")]
    (str advance
         "    (let [{:keys [pos]} (h/get-unit \"" unit "\")\n"
         "          target-pos " target-expr "]\n"
         "      (should= target-pos pos))")))

(defn- generate-unit-after-moves-then [{:keys [unit moves target]}]
  (let [target-expr (utils/target-pos-expr target)]
    (str "    (dotimes [_ " moves "] (h/advance-game!))\n"
         "    (let [{:keys [pos]} (h/get-unit \"" unit "\")\n"
         "          target-pos " target-expr "]\n"
         "      (should= target-pos pos))")))

(defn- generate-unit-after-steps-then [{:keys [unit steps target coords]}]
  (let [pos-expr (if target
                   (utils/target-pos-expr target)
                   (pr-str coords))]
    (str "    (dotimes [_ " steps "] (h/advance-game!))\n"
         "    (let [target-pos " pos-expr "\n"
         "          f-result (h/get-unit \"" unit "\")]\n"
         "      (should-not-be-nil f-result)\n"
         "      (should= target-pos (:pos f-result)))")))

(defn- generate-unit-eventually-at-then [{:keys [unit target]}]
  (let [target-expr (utils/target-pos-expr target)]
    (str "    (let [target-pos " target-expr "]\n"
         "      (loop [n 0]\n"
         "        (when (< n 200)\n"
         "          (let [{:keys [pos]} (h/get-unit \"" unit "\")]\n"
         "            (when (not= target-pos pos)\n"
         "              (h/advance-game!)\n"
         "              (recur (inc n))))))\n"
         "      (should= target-pos (:pos (h/get-unit \"" unit "\"))))")))

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
    (str "    (let [{:keys [pos]} (h/get-unit \"" unit "\")]\n"
         "      (should= " (pr-str target-pos) " pos))")))

(defn- generate-unit-unmoved-then
  "Assert unit hasn't moved from its original position."
  [{:keys [unit]} givens]
  (let [map-given (first (filter #(= :map (:type %)) givens))
        rows (:rows map-given)
        orig-pos (find-unit-initial-pos rows unit)]
    (str "    (let [{:keys [pos]} (h/get-unit \"" unit "\")]\n"
         "      (should= " (pr-str orig-pos) " pos))")))

(defn- generate-unit-waiting-for-input-then [{:keys [unit]}]
  (str "    (should= :ok (advance-until-unit-waiting \"" unit "\"))"))

(defn- message-advance-prefix [at-next-round at-next-step]
  (cond
    at-next-round "    (should= :ok (advance-until-next-round))\n"
    at-next-step  "    (h/advance-game!)\n"
    :else         ""))

(defn- generate-message-contains-assertion [{:keys [config-key text at-next-round at-next-step]} atom-str]
  (let [advance (message-advance-prefix at-next-round at-next-step)]
    (if config-key
      (str advance
           "    (should-not-be-nil (:" (name config-key) " config/messages))\n"
           "    (should (message-matches? (:" (name config-key) " config/messages) " atom-str "))")
      (str advance "    (should-contain \"" text "\" " atom-str ")"))))

(defn- generate-message-for-unit-assertion [{:keys [unit config-key]} atom-str]
  (str "    (should= :ok\n"
       "      (loop [n 100]\n"
        "        (let [u (h/get-unit \"" unit "\")]\n"
       "          (cond\n"
       "            (and u (= :awake (:mode (:unit u))) (h/read-state :waiting-for-input)) :ok\n"
       "            (zero? n) :timeout\n"
       "            :else (do (h/advance-game!) (recur (dec n)))))))\n"
       "    (should-not-be-nil (:" (name config-key) " config/messages))\n"
       "    (should (message-matches? (:" (name config-key) " config/messages) " atom-str "))"))

(defn- generate-message-is-assertion [{:keys [config-key format]} atom-str]
  (if config-key
    (str "    (should= (:" (name config-key) " config/messages) " atom-str ")")
    (let [{:keys [key args]} format
          args-str (str/join " " (map pr-str args))]
      (str "    (should= (format (:" (name key) " config/messages) " args-str ") " atom-str ")"))))

(defn- generate-no-message-assertion [atom-str]
  (str "    (should= \"\" " atom-str ")"))

(defn- generate-message-assertion [{:keys [area] :as ir}]
  (let [state-key (utils/area->state-key area)
        value-expr (str "(h/read-state :" (name state-key) ")")]
    (case (:type ir)
      :message-contains (generate-message-contains-assertion ir value-expr)
      :message-for-unit (generate-message-for-unit-assertion ir value-expr)
      :message-is (generate-message-is-assertion ir value-expr)
      :no-message (generate-no-message-assertion value-expr))))

(defn- generate-cell-prop-then [{:keys [coords property expected target]}]
  (let [map-key (if (= target :computer-map) ":computer-map" ":game-map")]
    (str "    (should= " (pr-str expected) " (:" (name property) " (h/cell-at " map-key " " (pr-str coords) ")))")))

(defn- generate-cell-type-then [{:keys [coords expected]}]
  (str "    (should= " (pr-str expected) " (:type (h/cell-at " (pr-str coords) ")))"))

(defn- generate-waiting-for-input-then [{:keys [expected]}]
  (if expected
    "    (should (h/read-state :waiting-for-input))"
    "    (should-not (h/read-state :waiting-for-input))"))

(defn- generate-container-prop-then [{:keys [target property expected lookup at-next-round at-next-step]}]
  (let [advance (cond
                  at-next-round "    (should= :ok (advance-until-next-round))\n"
                  at-next-step  "    (h/advance-game!)\n"
                  :else         "")]
    (if (= lookup :city)
      (str advance
           "    (let [" (str/lower-case target) "-pos (:pos (h/get-city \"" target "\"))\n"
           "          cell (h/cell-at " (str/lower-case target) "-pos)]\n"
           "      (should= " (pr-str expected) " (:" (name property) " cell)))")
      (str advance
           "    (should= " (pr-str expected) " (:" (name property) " (:unit (h/get-unit \"" target "\"))))"))))


(defn- generate-round-then [{:keys [expected]}]
  (str "    (should= " expected " (h/read-state :round-number))"))

(defn- generate-destination-then [{:keys [expected]}]
  (str "    (should= " (pr-str expected) " (h/read-state :destination))"))

(defn- generate-production-assertion [{:keys [city expected excluded remaining-rounds] :as ir}]
  (let [pos-expr (utils/target-pos-expr city)]
    (case (:type ir)
      :production
      (str "    (should= " (pr-str expected) " (:item (get (h/read-state :production) " pos-expr ")))")
      :no-production
      (str "    (let [prod (get (h/read-state :production) " pos-expr ")]\n"
           "      (should (or (nil? prod) (= :none prod))))")
      :production-with-rounds
      (str "    (let [prod (get (h/read-state :production) " pos-expr ")]\n"
           "      (should= " (pr-str expected) " (:item prod))\n"
           "      (should= " remaining-rounds " (:remaining-rounds prod)))")
      :production-not
      (str "    (should-not= " (pr-str excluded) " (:item (get (h/read-state :production) " pos-expr ")))"))))

(defn- generate-game-paused-then [_]
  "    (should (h/read-state :paused))")

(defn- generate-map-cell-assertion [{:keys [coords]} state-key assertion]
  (let [[x y] coords]
    (str "    (" assertion " (h/cell-at :" (name state-key) " [" x " " y "]))")))

(defn- generate-territory-map-then [{:keys [rows]}]
  (let [row-strs (str/join " " (map #(str "\"" % "\"") rows))]
    (str "    (should= (build-territory-expected [" row-strs "])"
         "\n             (territory-mask (h/read-state :game-map)))")))

(defn- generate-player-map-visibility-then [{:keys [rows]}]
  (let [row-strs (str/join " " (map #(str "\"" % "\"") rows))]
    (str "    (should= (visibility-mask (build-test-map [" row-strs "]))"
         "\n             (visibility-mask (h/read-state :player-map)))")))

(defn- generate-no-unit-at-then [{:keys [coords]}]
  (str "    (should-be-nil (:contents (h/cell-at " (pr-str coords) ")))"))

(defn- generate-unit-prop-absent-then [{:keys [unit property]}]
  (str "    (should-be-nil (:" (name property) " (:unit (h/get-unit \"" unit "\"))))"))

(defn- generate-computer-army-count-then [{:keys [expected]}]
  (str "    (should= " expected " (h/count-computer-armies))"))

(defn- generate-shipyard-has-ship-then [{:keys [city ship-type hits]}]
  (let [pos-expr (utils/target-pos-expr city)]
    (str "    (let [shipyard (h/shipyard-at " pos-expr ")]\n"
         "      (should (some #(and (= :" (name ship-type) " (:type %)) (= " hits " (:hits %))) shipyard)))")))

(defn- generate-shipyard-empty-then [{:keys [city]}]
  (let [pos-expr (utils/target-pos-expr city)]
    (str "    (let [shipyard (h/shipyard-at " pos-expr ")]\n"
         "      (should= [] shipyard))")))

(defn- generate-map-is-then [{:keys [expected]}]
  (str "    (let [expected (build-test-map [\"" expected "\"])\n"
       "          actual (h/read-state :game-map)]\n"
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
    (str "    (let [unit-data (:unit (h/get-unit \"" unit "\"))\n"
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
   :player-map-cell-not-nil (fn [ir _] (generate-map-cell-assertion ir :player-map "should-not-be-nil"))
   :player-map-cell-nil (fn [ir _] (generate-map-cell-assertion ir :player-map "should-be-nil"))
   :computer-map-cell-not-nil (fn [ir _] (generate-map-cell-assertion ir :computer-map "should-not-be-nil"))
   :player-map-visibility (fn [ir _] (generate-player-map-visibility-then ir))
   :territory-map (fn [ir _] (generate-territory-map-then ir))
   :no-unit-at (fn [ir _] (generate-no-unit-at-then ir))
   :unit-prop-absent (fn [ir _] (generate-unit-prop-absent-then ir))
   :computer-army-count (fn [ir _] (generate-computer-army-count-then ir))
   :refueling-position-near (fn [ir _] (generate-refueling-position-near-then ir))
   :shipyard-has-ship (fn [ir _] (generate-shipyard-has-ship-then ir))
   :shipyard-empty (fn [ir _] (generate-shipyard-empty-then ir))
   :map-is (fn [ir _] (generate-map-is-then ir))
   :game-not-paused (fn [_ _] "    (should-not (h/read-state :paused))")
   :map-display (fn [ir _] (str "    (should= " (pr-str (:expected ir)) " (h/read-state :map-to-display))"))
   :load-menu-state (fn [ir _] (if (:expected ir)
                                  "    (should (h/read-state :load-menu-open))"
                                  "    (should-not (h/read-state :load-menu-open))"))})

(defn generate-then
  "Generate code string for a single THEN IR node."
  [then-ir givens]
  (if-let [gen (get then-dispatch (:type then-ir))]
    (gen then-ir givens)
    (if (= :unrecognized (:type then-ir))
      (str "    (pending \"Unrecognized: " (:text then-ir) "\")")
      (str "    ;; Unknown then type: " (:type then-ir)))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T11:56:27.611592-05:00", :module-hash "-178648792", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 3, :hash "1613807724"} {:id "defn-/generate-unit-prop-then", :kind "defn-", :line 5, :end-line 8, :hash "2141285562"} {:id "defn-/generate-unit-absent-then", :kind "defn-", :line 10, :end-line 11, :hash "961123256"} {:id "defn-/generate-unit-present-then", :kind "defn-", :line 13, :end-line 20, :hash "880543474"} {:id "defn-/generate-unit-at-then", :kind "defn-", :line 22, :end-line 28, :hash "353903710"} {:id "defn-/generate-unit-at-next-round-then", :kind "defn-", :line 30, :end-line 38, :hash "131389821"} {:id "defn-/generate-unit-after-moves-then", :kind "defn-", :line 40, :end-line 45, :hash "2108294579"} {:id "defn-/generate-unit-after-steps-then", :kind "defn-", :line 47, :end-line 55, :hash "645409693"} {:id "defn-/generate-unit-eventually-at-then", :kind "defn-", :line 57, :end-line 66, :hash "-2049372339"} {:id "defn-/find-unit-initial-pos", :kind "defn-", :line 68, :end-line 75, :hash "-1079606236"} {:id "defn-/generate-unit-occupies-cell-then", :kind "defn-", :line 77, :end-line 84, :hash "882987924"} {:id "defn-/generate-unit-unmoved-then", :kind "defn-", :line 86, :end-line 93, :hash "23146003"} {:id "defn-/generate-unit-waiting-for-input-then", :kind "defn-", :line 95, :end-line 96, :hash "1951816330"} {:id "defn-/message-advance-prefix", :kind "defn-", :line 98, :end-line 102, :hash "-1650536763"} {:id "defn-/generate-message-contains-assertion", :kind "defn-", :line 104, :end-line 110, :hash "52264763"} {:id "defn-/generate-message-for-unit-assertion", :kind "defn-", :line 112, :end-line 121, :hash "625496121"} {:id "defn-/generate-message-is-assertion", :kind "defn-", :line 123, :end-line 128, :hash "-127021755"} {:id "defn-/generate-no-message-assertion", :kind "defn-", :line 130, :end-line 131, :hash "-59951715"} {:id "defn-/generate-message-assertion", :kind "defn-", :line 133, :end-line 140, :hash "1302527750"} {:id "defn-/generate-cell-prop-then", :kind "defn-", :line 142, :end-line 144, :hash "257259077"} {:id "defn-/generate-cell-type-then", :kind "defn-", :line 146, :end-line 147, :hash "-1133238022"} {:id "defn-/generate-waiting-for-input-then", :kind "defn-", :line 149, :end-line 152, :hash "-599265345"} {:id "defn-/generate-container-prop-then", :kind "defn-", :line 154, :end-line 165, :hash "587492740"} {:id "defn-/generate-round-then", :kind "defn-", :line 168, :end-line 169, :hash "-222907855"} {:id "defn-/generate-destination-then", :kind "defn-", :line 171, :end-line 172, :hash "-671736091"} {:id "defn-/generate-production-assertion", :kind "defn-", :line 174, :end-line 187, :hash "999637961"} {:id "defn-/generate-game-paused-then", :kind "defn-", :line 189, :end-line 190, :hash "2143143742"} {:id "defn-/generate-map-cell-assertion", :kind "defn-", :line 192, :end-line 194, :hash "2000852318"} {:id "defn-/generate-territory-map-then", :kind "defn-", :line 196, :end-line 199, :hash "1203838298"} {:id "defn-/generate-player-map-visibility-then", :kind "defn-", :line 201, :end-line 204, :hash "-1607920814"} {:id "defn-/generate-no-unit-at-then", :kind "defn-", :line 206, :end-line 207, :hash "-1325553730"} {:id "defn-/generate-unit-prop-absent-then", :kind "defn-", :line 209, :end-line 210, :hash "-1740714149"} {:id "defn-/generate-computer-army-count-then", :kind "defn-", :line 212, :end-line 213, :hash "1299666343"} {:id "defn-/generate-shipyard-has-ship-then", :kind "defn-", :line 215, :end-line 218, :hash "1601210877"} {:id "defn-/generate-shipyard-empty-then", :kind "defn-", :line 220, :end-line 223, :hash "-1595537796"} {:id "defn-/generate-map-is-then", :kind "defn-", :line 225, :end-line 239, :hash "1516052135"} {:id "defn-/generate-refueling-position-near-then", :kind "defn-", :line 241, :end-line 249, :hash "1776091763"} {:id "def/then-dispatch", :kind "def", :line 251, :end-line 294, :hash "1571550272"} {:id "defn/generate-then", :kind "defn", :line 296, :end-line 303, :hash "7097794"}]}
;; clj-mutate-manifest-end

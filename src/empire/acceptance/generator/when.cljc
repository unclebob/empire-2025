(ns empire.acceptance.generator.when
  (:require [empire.acceptance.generator.utils :as utils]
            [empire.acceptance.generator.given :as given]))

(defn- generate-key-press-when [{:keys [key input-fn]}]
  (if (= input-fn :key-down)
    (str "    (h/key-down! :" (name key) ")")
    (str "    (h/handle-key! :" (name key) ")")))

(defn- generate-battle-when [{:keys [key outcome combat-type]}]
  (let [rand-val (if (= outcome :win) "0.0" "1.0")]
    (if (= combat-type :ship)
      (str "    (with-redefs [rand (constantly " rand-val ")]\n"
           "      (h/handle-key! :" (name key) ")\n"
           "      (h/advance-game!))")
      (str "    (with-redefs [rand (constantly " rand-val ")]\n"
           "      (h/handle-key! :" (name key) ")\n"
           "      (h/advance-game!))"))))

(defn- generate-backtick-when [{:keys [key mouse-cell]}]
  (let [[x y] mouse-cell]
    (str "    (h/set-state! :map-screen-dimensions [22 16])\n"
         "    (h/key-down-at! (keyword \"`\") " x " " y ")\n"
         "    (h/key-down-at! :" (name key) " " x " " y ")")))

(defn- mouse-at-key-expr [key]
  (case key
    :period "(keyword \".\")"
    :star "(keyword \"*\")"
    (str ":" (name key))))

(defn- generate-mouse-at-key-when [{:keys [coords key]}]
  (let [[cx cy] coords
        key-expr (mouse-at-key-expr key)]
    (str "    (let [map-w 22 map-h 16\n"
         "          gm (h/read-state :game-map)\n"
         "          cols (count gm)\n"
         "          rows (count (first gm))\n"
         "          cell-w (/ map-w cols) cell-h (/ map-h rows)\n"
         "          px (int (+ (* " cx " cell-w) (/ cell-w 2)))\n"
         "          py (int (+ (* " cy " cell-h) (/ cell-h 2)))]\n"
         "      (h/set-state! :map-screen-dimensions [map-w map-h])\n"
         "      (h/key-down-at! " key-expr " px py))")))

(defn- generate-visibility-update-when [_]
  "    (h/update-player-map!)")

(defn- generate-cell-visibility-update-when [{:keys [unit]}]
  (let [pos-expr (utils/target-pos-expr unit)]
    (str "    (let [pos " pos-expr "\n"
          "          cell (get-in (h/read-state :game-map) pos)]\n"
         "      (h/update-cell-visibility! pos (:owner (:contents cell)) (:contents cell)))")))

(defn- generate-evaluate-production-when [{:keys [city]}]
  (let [pos-expr (utils/target-pos-expr city)]
    (str "    (h/evaluate-computer-production! " pos-expr ")")))

(defn- generate-process-computer-transport-when [{:keys [unit]}]
  (let [pos-expr (utils/target-pos-expr unit)]
    (str "    (h/process-computer-transport! " pos-expr ")")))

(defn- generate-process-computer-fighter-when [{:keys [unit]}]
  (let [pos-expr (utils/target-pos-expr unit)]
    (str "    (let [pos " pos-expr "\n"
          "          unit (get-in (h/read-state :game-map) (conj pos :contents))]\n"
         "      (h/process-computer-fighter! pos unit))")))

(defn- generate-process-computer-ship-when [{:keys [unit ship-type]}]
  (let [pos-expr (utils/target-pos-expr unit)]
    (str "    (h/process-computer-ship! " pos-expr " :" (name ship-type) ")")))

(defn- generate-computer-rounds-when [{:keys [count]}]
  (str "    (dotimes [_ " count "]\n"
       "      ;; Process all computer transports\n"
       "      (doseq [i (range (count (h/read-state :game-map)))\n"
       "              j (range (count (first (h/read-state :game-map))))\n"
       "              :let [cell (get-in (h/read-state :game-map) [i j])\n"
       "                    unit (:contents cell)]\n"
       "              :when (and unit\n"
       "                         (= :transport (:type unit))\n"
       "                         (= :computer (:owner unit)))]\n"
       "        (h/process-computer-transport! [i j])))"))

(def ^:private start-new-round-step
  (str "      (h/start-new-round!)\n"
       "      (loop [n 200]\n"
       "        (when (and (pos? n)\n"
       "                   (not (h/read-state :waiting-for-input))\n"
       "                   (not (h/read-state :paused))\n"
       "                   (or (seq (h/read-state :player-items)) (seq (h/read-state :computer-items))))\n"
       "          (h/advance-game!)\n"
       "          (recur (dec n))))"))

(defn- generate-start-new-round-when [_]
  (str "    (do\n" start-new-round-step "\n    )"))

(defn- generate-rounds-complete-when [{:keys [count]}]
  (str "    (dotimes [_ " count "]\n"
       start-new-round-step
       "\n    )"))

(defn- generate-advance-game-when [_]
  "    (h/advance-game!)")

(defn- generate-process-player-items-when [_]
  "    (h/process-player-items-batch!)")

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
   :computer-rounds            generate-computer-rounds-when
   :rounds-complete            generate-rounds-complete-when
   :process-computer-ship      generate-process-computer-ship-when
   :save-game                  (fn [_]
                                  (str "    (with-redefs [spit (constantly nil)]\n"
                                       "      (h/key-down! :!))"))
   :open-load-menu             (fn [_]
                                  (str "    (h/key-down! (keyword \"^\"))"))})

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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T21:57:04.505098-05:00", :module-hash "-1155497975", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 3, :hash "401875766"} {:id "defn-/generate-key-press-when", :kind "defn-", :line 5, :end-line 8, :hash "653351182"} {:id "defn-/generate-battle-when", :kind "defn-", :line 10, :end-line 18, :hash "636356161"} {:id "defn-/generate-backtick-when", :kind "defn-", :line 20, :end-line 24, :hash "298774770"} {:id "defn-/mouse-at-key-expr", :kind "defn-", :line 26, :end-line 30, :hash "715537869"} {:id "defn-/generate-mouse-at-key-when", :kind "defn-", :line 32, :end-line 43, :hash "-1246038032"} {:id "defn-/generate-visibility-update-when", :kind "defn-", :line 45, :end-line 46, :hash "1584876063"} {:id "defn-/generate-cell-visibility-update-when", :kind "defn-", :line 48, :end-line 52, :hash "267489652"} {:id "defn-/generate-evaluate-production-when", :kind "defn-", :line 54, :end-line 56, :hash "666763105"} {:id "defn-/generate-process-computer-transport-when", :kind "defn-", :line 58, :end-line 60, :hash "-546766140"} {:id "defn-/generate-process-computer-fighter-when", :kind "defn-", :line 62, :end-line 66, :hash "-797050054"} {:id "defn-/generate-process-computer-ship-when", :kind "defn-", :line 68, :end-line 70, :hash "1090142395"} {:id "defn-/generate-computer-rounds-when", :kind "defn-", :line 72, :end-line 82, :hash "2116163251"} {:id "def/start-new-round-step", :kind "def", :line 84, :end-line 92, :hash "-546269705"} {:id "defn-/generate-start-new-round-when", :kind "defn-", :line 94, :end-line 95, :hash "-809901383"} {:id "defn-/generate-rounds-complete-when", :kind "defn-", :line 97, :end-line 100, :hash "1507409683"} {:id "defn-/generate-advance-game-when", :kind "defn-", :line 102, :end-line 103, :hash "272973498"} {:id "defn-/generate-process-player-items-when", :kind "defn-", :line 105, :end-line 106, :hash "31827892"} {:id "defn-/generate-advance-until-waiting-when", :kind "defn-", :line 108, :end-line 109, :hash "-620043933"} {:id "def/when-generators", :kind "def", :line 111, :end-line 133, :hash "390219139"} {:id "defn/generate-when", :kind "defn", :line 135, :end-line 148, :hash "-32536941"}]}
;; clj-mutate-manifest-end

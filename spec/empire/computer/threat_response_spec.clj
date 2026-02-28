(ns empire.computer.threat-response-spec
  (:require [speclj.core :refer :all]
            [empire.atoms :as atoms]
            [empire.computer.threat-response :as threat-response]
            [empire.test-utils :refer [build-test-map reset-all-atoms!]]))

(defn computer-units
  [pred]
  (for [i (range (count @atoms/game-map))
        j (range (count (first @atoms/game-map)))
        :let [u (get-in @atoms/game-map [i j :contents])]
        :when (and u (= :computer (:owner u)) (pred u))]
    [[i j] u]))

(describe "threat-response"
  (before (reset-all-atoms!))

  (it "assigns up to 4 closest fighters when enemy fighter detected"
    (let [gm (build-test-map ["f~~"
                              "~~~"
                              "f~~"
                              "~~~"
                              "f~~"
                              "~~~"
                              "f~~"
                              "~~F"
                              "f~~"])]
      (reset! atoms/game-map gm)
      (reset! atoms/computer-map gm)
      (let [enemy-cell (get-in @atoms/game-map [2 7])]
        (threat-response/handle-detection! [2 7] enemy-cell))
      (let [assigned (computer-units #(= :fighter-sweep (:threat-mission %)))
            assigned-positions (set (map first assigned))]
        (should= 4 (count assigned))
        (should (not (contains? assigned-positions [0 0])))
        (doseq [[_ unit] assigned]
          (should= [2 7] (:threat-center unit))))))

  (it "assigns 2 patrol boats and 2 battleships when enemy ship detected"
    (let [gm (build-test-map ["p~~~"
                              "~b~~"
                              "p~~~"
                              "~b~~"
                              "p~~~"
                              "~b~~"
                              "~~~~"
                              "~~~~"
                              "~~~D"])]
      (reset! atoms/game-map gm)
      (reset! atoms/computer-map gm)
      (threat-response/handle-detection! [3 8] (get-in @atoms/game-map [3 8]))
      (let [assigned (computer-units #(= :sea-scout (:threat-mission %)))
            patrols (count (filter #(= :patrol-boat (:type (second %))) assigned))
            battleships (count (filter #(= :battleship (:type (second %))) assigned))]
        (should= 4 (count assigned))
        (should= 2 patrols)
        (should= 2 battleships))))

  (it "activates major invasion and assigns loaded transport to invading mission"
    (let [gm (build-test-map ["t~~~"
                              "~~~~"
                              "##O#"
                              "~~~~"])]
      (reset! atoms/game-map gm)
      (reset! atoms/computer-map gm)
      (swap! atoms/game-map assoc-in [0 0 :contents :army-count] 4)
      (swap! atoms/game-map assoc-in [0 0 :contents :transport-mission] :sailing)
      (threat-response/handle-detection! [2 2] (get-in @atoms/game-map [2 2]))
      (threat-response/refresh-major-invasion-assignments!)
      (let [transport (get-in @atoms/game-map [0 0 :contents])]
        (should (:active? @atoms/major-invasion-state))
        (should-contain [2 2] (:detection-points @atoms/major-invasion-state))
        (should= :invading (:transport-mission transport))
        (should= [2 2] (:invasion-target transport))
        (should (seq (:invasion-path transport))))
      (should (threat-response/major-invasion-target-land? [1 2])))))

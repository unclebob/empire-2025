(ns empire.computer.land-objectives-spec
  "Tests for land objective detection using fog-of-war flood-fill."
  (:require [speclj.core :refer :all]
            [empire.computer.land-objectives :as land-objectives]
            [empire.test-utils :refer [build-test-map reset-all-atoms! set-test-player-map! set-test-computer-map! set-test-world!]]))

(describe "flood-fill-continent"
  (before (reset-all-atoms!))

  (it "finds all connected land cells"
    (set-test-computer-map! (build-test-map ["###"
                                                 "###"
                                                 "###"]))
    (let [cont (land-objectives/flood-fill-continent [1 1])]
      (should= 9 (count cont))))

  (it "stops at sea boundaries"
    (set-test-computer-map! (build-test-map ["###~##"
                                                 "###~##"]))
    (let [cont (land-objectives/flood-fill-continent [0 0])]
      ;; Should only find left 3x2 = 6 cells
      (should= 6 (count cont))
      (should-contain [0 0] cont)
      (should-contain [2 1] cont)
      (should-not-contain [4 0] cont)))

  (it "marks but does not expand through unexplored territory"
    ;; Map where middle column is unexplored (nil)
    (set-test-computer-map! [[{:type :land} nil {:type :land}]
                                 [{:type :land} nil {:type :land}]])
    (let [cont (land-objectives/flood-fill-continent [0 0])]
      ;; Should find left column (2) + adjacent unexplored (2) = 4
      ;; Should NOT find right column
      (should= 4 (count cont))
      (should-contain [0 0] cont)
      (should-contain [1 0] cont)
      (should-contain [0 1] cont)  ; unexplored but adjacent
      (should-contain [1 1] cont)  ; unexplored but adjacent
      (should-not-contain [0 2] cont)
      (should-not-contain [1 2] cont)))

  (it "treats cities as land for connectivity"
    (set-test-computer-map! (build-test-map ["#X#"]))
    (let [cont (land-objectives/flood-fill-continent [0 0])]
      (should= 3 (count cont))))

  (it "finds isolated landmass when separated by unexplored"
    ;; Two explored regions separated by unexplored
    (set-test-computer-map! [[{:type :land} nil nil {:type :land}]])
    (let [cont-left (land-objectives/flood-fill-continent [0 0])
          cont-right (land-objectives/flood-fill-continent [0 3])]
      ;; Left region sees 1 land + 1 adjacent unexplored
      (should= 2 (count cont-left))
      ;; Right region sees 1 land + 1 adjacent unexplored
      (should= 2 (count cont-right))
      ;; They are disjoint (different continents from fog-of-war perspective)
      (should-not-contain [0 3] cont-left)
      (should-not-contain [0 0] cont-right)))

  (it "returns nil for empty map"
    (set-test-computer-map! [])
    (should-be-nil (land-objectives/flood-fill-continent [0 0]))))

(describe "scan-continent"
  (before (reset-all-atoms!))

  (it "counts unexplored cells"
    (set-test-computer-map! [[{:type :land} nil]
                                 [{:type :land} nil]])
    (set-test-world! [[{:type :land} {:type :land}]
                             [{:type :land} {:type :land}]])
    (let [cont (land-objectives/flood-fill-continent [0 0])
          counts (land-objectives/scan-continent cont)]
      (should= 2 (:unexplored counts))))

  (it "counts cities by owner"
    (set-test-computer-map! (build-test-map ["X+O#"]))
    (set-test-world! (build-test-map ["X+O#"]))
    (let [cont (land-objectives/flood-fill-continent [0 0])
          counts (land-objectives/scan-continent cont)]
      (should= 1 (:computer-cities counts))
      (should= 1 (:free-cities counts))
      (should= 1 (:player-cities counts))))

  (it "counts units by owner"
    (set-test-computer-map! (build-test-map ["aA#"]))
    (set-test-world! (build-test-map ["aA#"]))
    (let [cont (land-objectives/flood-fill-continent [0 0])
          counts (land-objectives/scan-continent cont)]
      (should= 1 (:computer-units counts))
      (should= 1 (:player-units counts))))

  (it "counts land size separately from unexplored"
    ;; Asymmetric: 6 land, 2 unexplored — kills L97 and L101
    (set-test-computer-map! [[{:type :land} {:type :land} {:type :land} nil]
                                [{:type :land} {:type :land} {:type :land} nil]])
    (set-test-world! [[{:type :land} {:type :land} {:type :land} {:type :land}]
                            [{:type :land} {:type :land} {:type :land} {:type :land}]])
    (let [cont (land-objectives/flood-fill-continent [0 0])
          counts (land-objectives/scan-continent cont)]
      (should= 6 (:size counts))
      (should= 2 (:unexplored counts))))

  (it "distinguishes computer from player units with unequal counts"
    ;; 2 player armies, 1 computer army — kills L117 and L120
    (set-test-computer-map! (build-test-map ["AAa#"]))
    (set-test-world! (build-test-map ["AAa#"]))
    (let [cont (land-objectives/flood-fill-continent [0 0])
          counts (land-objectives/scan-continent cont)]
      (should= 1 (:computer-units counts))
      (should= 2 (:player-units counts)))))

(describe "city-status-key"
  (it "returns :computer-cities for computer city"
    (should= :computer-cities
             (land-objectives/city-status-key {:type :city :city-status :computer})))

  (it "returns :player-cities for player city"
    (should= :player-cities
             (land-objectives/city-status-key {:type :city :city-status :player})))

  (it "returns :free-cities for free city"
    (should= :free-cities
             (land-objectives/city-status-key {:type :city :city-status :free})))

  (it "returns nil for non-city"
    (should-be-nil (land-objectives/city-status-key {:type :land})))

  (it "returns nil for nil cell"
    (should-be-nil (land-objectives/city-status-key nil))))

(describe "unit-owner-key"
  (it "returns :computer-units for computer unit"
    (should= :computer-units
             (land-objectives/unit-owner-key {:contents {:owner :computer}})))

  (it "returns :player-units for player unit"
    (should= :player-units
             (land-objectives/unit-owner-key {:contents {:owner :player}})))

  (it "returns nil when no contents"
    (should-be-nil (land-objectives/unit-owner-key {:type :land})))

  (it "returns nil for nil cell"
    (should-be-nil (land-objectives/unit-owner-key nil))))

(describe "has-land-objective?"
  (it "returns true when unexplored territory exists"
    (should (land-objectives/has-land-objective? {:unexplored 5 :free-cities 0 :player-cities 0})))

  (it "returns true when free cities exist"
    (should (land-objectives/has-land-objective? {:unexplored 0 :free-cities 1 :player-cities 0})))

  (it "returns true when player cities exist"
    (should (land-objectives/has-land-objective? {:unexplored 0 :free-cities 0 :player-cities 2})))

  (it "returns false when nothing to explore or attack"
    (should-not (land-objectives/has-land-objective? {:unexplored 0 :free-cities 0 :player-cities 0}))))

(describe "find-all-objectives-on-continent"
  (before (reset-all-atoms!))

  (it "includes free and player cities as objectives"
    ;; Kills L141
    (set-test-computer-map! (build-test-map ["#+O"]))
    (let [cont (land-objectives/flood-fill-continent [0 0])
          objectives (set (land-objectives/find-all-objectives-on-continent cont))]
      (should= 2 (count objectives))
      (should-contain [1 0] objectives)
      (should-contain [2 0] objectives))))

(describe "find-unexplored-on-continent"
  (before (reset-all-atoms!))

  (it "finds nearest unexplored cell"
    (set-test-computer-map! [[{:type :land} {:type :land} nil]
                                 [{:type :land} {:type :land} {:type :land}]])
    (let [cont (land-objectives/flood-fill-continent [0 0])
          nearest (land-objectives/find-unexplored-on-continent [0 0] cont)]
      (should= [0 2] nearest)))

  (it "returns nil when no unexplored on continent"
    (set-test-computer-map! (build-test-map ["###"]))
    (let [cont (land-objectives/flood-fill-continent [0 0])
          nearest (land-objectives/find-unexplored-on-continent [0 0] cont)]
      (should-be-nil nearest))))

(describe "find-free-city-on-continent"
  (before (reset-all-atoms!))

  (it "finds nearest free city"
    (set-test-computer-map! (build-test-map ["##+"]))
    (let [cont (land-objectives/flood-fill-continent [0 0])
          nearest (land-objectives/find-free-city-on-continent [0 0] cont)]
      (should= [2 0] nearest)))

  (it "returns nil when no free city on continent"
    (set-test-computer-map! (build-test-map ["##X"]))
    (let [cont (land-objectives/flood-fill-continent [0 0])
          nearest (land-objectives/find-free-city-on-continent [0 0] cont)]
      (should-be-nil nearest))))

(describe "find-player-city-on-continent"
  (before (reset-all-atoms!))

  (it "finds nearest player city"
    (set-test-computer-map! (build-test-map ["##O"]))
    (let [cont (land-objectives/flood-fill-continent [0 0])
          nearest (land-objectives/find-player-city-on-continent [0 0] cont)]
      (should= [2 0] nearest)))

  (it "returns nil when no player city on continent"
    (set-test-computer-map! (build-test-map ["##X"]))
    (let [cont (land-objectives/flood-fill-continent [0 0])
          nearest (land-objectives/find-player-city-on-continent [0 0] cont)]
      (should-be-nil nearest))))

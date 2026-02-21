(ns empire.computer.navigation-spec
  "Tests for degree-based navigation system."
  (:require [speclj.core :refer :all]
            [empire.computer.navigation :as nav]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [build-test-map reset-all-atoms!]]))

(describe "heading-to-delta"
  (it "converts cardinal headings to deltas"
    (should= [-1 0] (nav/heading-to-delta 0))     ; N
    (should= [0 1] (nav/heading-to-delta 90))      ; E
    (should= [1 0] (nav/heading-to-delta 180))     ; S
    (should= [0 -1] (nav/heading-to-delta 270)))   ; W

  (it "converts diagonal headings to deltas"
    (should= [-1 1] (nav/heading-to-delta 45))     ; NE
    (should= [1 1] (nav/heading-to-delta 135))     ; SE
    (should= [1 -1] (nav/heading-to-delta 225))    ; SW
    (should= [-1 -1] (nav/heading-to-delta 315)))  ; NW

  (it "converts intermediate headings to nearest sector"
    (should= [-1 0] (nav/heading-to-delta 10))     ; close to N
    (should= [-1 1] (nav/heading-to-delta 50))     ; close to NE
    (should= [0 1] (nav/heading-to-delta 80))))    ; close to E

(defn- angular-distance
  "Minimum angular distance between two headings on a 360 circle."
  [a b]
  (let [d (Math/abs (- (double a) (double b)))]
    (min d (- 360.0 d))))

(describe "reflect-heading"
  (it "reflects heading off a horizontal surface"
    ;; heading 180 (S) → 0 (N)
    (with-redefs [rand-int (constantly 10)]
      (let [reflected (nav/reflect-heading 180 :horizontal)]
        (should (<= (angular-distance 0 reflected) 15)))
      ;; heading 45 (NE) → 135 (SE)
      (let [reflected (nav/reflect-heading 45 :horizontal)]
        (should (<= (angular-distance 135 reflected) 15)))))

  (it "reflects heading off a vertical surface"
    (with-redefs [rand-int (constantly 10)]
      ;; heading 90 (E) → 270 (W)
      (let [reflected (nav/reflect-heading 90 :vertical)]
        (should (<= (angular-distance 270 reflected) 15)))
      ;; heading 45 (NE) → 315 (NW)
      (let [reflected (nav/reflect-heading 45 :vertical)]
        (should (<= (angular-distance 315 reflected) 15)))))

  (it "result is always in 0-359 range"
    (dotimes [_ 100]
      (let [heading (rand-int 360)
            surface (rand-nth [:horizontal :vertical])
            reflected (nav/reflect-heading heading surface)]
        (should (>= reflected 0))
        (should (< reflected 360))))))

(describe "is-explored-coast?"
  (before (reset-all-atoms!))

  (it "returns true for sea cell adjacent to visible land"
    (reset! atoms/game-map (build-test-map ["~#"]))
    (reset! atoms/computer-map (build-test-map ["~#"]))
    (should (nav/is-explored-coast? [0 0])))

  (it "returns false for sea cell with no adjacent land"
    (reset! atoms/game-map (build-test-map ["~~"]))
    (reset! atoms/computer-map (build-test-map ["~~"]))
    (should-not (nav/is-explored-coast? [0 0])))

  (it "returns false for sea cell adjacent to unexplored land"
    (reset! atoms/game-map (build-test-map ["~#"]))
    (reset! atoms/computer-map (vec (repeat 2 (vec (repeat 1 nil)))))
    (should-not (nav/is-explored-coast? [0 0])))

  (it "returns false for land cells"
    (reset! atoms/game-map (build-test-map ["#~"]))
    (reset! atoms/computer-map (build-test-map ["#~"]))
    (should-not (nav/is-explored-coast? [0 0]))))

(describe "apply-heading"
  ;; Positions are [col row]. North=row-1, East=col+1, South=row+1, West=col-1.
  (it "computes next position from heading"
    (should= [5 5] (nav/apply-heading [5 6] 0))    ; north: row-1
    (should= [5 7] (nav/apply-heading [5 6] 180))  ; south: row+1
    (should= [6 6] (nav/apply-heading [5 6] 90))   ; east: col+1
    (should= [4 6] (nav/apply-heading [5 6] 270))) ; west: col-1

  (it "computes diagonal next position"
    (should= [6 5] (nav/apply-heading [5 6] 45))   ; NE: col+1, row-1
    (should= [6 7] (nav/apply-heading [5 6] 135))  ; SE: col+1, row+1
    (should= [4 7] (nav/apply-heading [5 6] 225))  ; SW: col-1, row+1
    (should= [4 5] (nav/apply-heading [5 6] 315)))) ; NW: col-1, row-1

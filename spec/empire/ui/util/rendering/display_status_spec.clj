(ns empire.ui.util.rendering.display-status-spec
  (:require [empire.ui.util.rendering.display :as display]
            [speclj.core :refer :all]))

(describe "resolve-turn-text"
  (it "returns turn-message when present"
    (should= "Hello" (display/resolve-turn-text "Hello" nil)))

  (it "returns formatted destination when no turn-message"
    (should= "Dest: 5,10" (display/resolve-turn-text nil [5 10])))

  (it "prefers turn-message over destination"
    (should= "Msg" (display/resolve-turn-text "Msg" [5 10])))

  (it "returns nil when neither turn-message nor destination"
    (should-be-nil (display/resolve-turn-text nil nil)))

  (it "returns nil for empty string turn-message and no destination"
    (should-be-nil (display/resolve-turn-text "" nil)))

  (it "returns formatted destination for empty string turn-message"
    (should= "Dest: 3,7" (display/resolve-turn-text "" [3 7]))))

(describe "resolve-round-status-text"
  (it "returns round string when not paused"
    (let [result (display/resolve-round-status-text 5 false false)]
      (should= "Round: 5" (:text result))
      (should-not (:paused? result))))

  (it "returns paused prefix when paused"
    (let [result (display/resolve-round-status-text 3 true false)]
      (should= "PAUSED  Round: 3" (:text result))
      (should (:paused? result))
      (should= "Round: 3" (:round-str result))))

  (it "returns paused prefix when pause-requested"
    (let [result (display/resolve-round-status-text 1 false true)]
      (should= "PAUSED  Round: 1" (:text result))
      (should (:paused? result))))

  (it "returns paused prefix when both paused and requested"
    (let [result (display/resolve-round-status-text 10 true true)]
      (should= "PAUSED  Round: 10" (:text result))
      (should (:paused? result))))

  (it "returns exact non-paused shape when both paused flags are false"
    (should= {:text "Round: 9" :paused? false}
             (display/resolve-round-status-text 9 false false))))

(describe "should-show-error?"
  (it "returns true when error-until is in the future"
    (should (display/should-show-error? (+ (System/currentTimeMillis) 10000))))

  (it "returns false when error-until is in the past"
    (should-not (display/should-show-error? (- (System/currentTimeMillis) 10000))))

  (it "returns false when error-until equals current time"
    (let [t (System/currentTimeMillis)]
      (should-not (display/should-show-error? t)))))

(describe "resolve-banner"
  (it "prefers a live error over attention and turn messages"
    (should= {:kind :error :text "Bad news"}
             (display/resolve-banner "Bad news"
                                     (+ (System/currentTimeMillis) 1000)
                                     "Need orders"
                                     "Moved")))

  (it "uses attention when there is no live error"
    (should= {:kind :attention :text "Need orders"}
             (display/resolve-banner "Old error"
                                     (- (System/currentTimeMillis) 1000)
                                     "Need orders"
                                     "Moved")))

  (it "uses turn message when error and attention are absent"
    (should= {:kind :result :text "Moved"}
             (display/resolve-banner "" 0 "" "Moved")))

  (it "returns empty banner when no messages are available"
    (should= {:kind :empty :text nil}
             (display/resolve-banner "" 0 "" ""))))

(describe "resolve-banner-pair"
  (it "returns the highest-priority active banner as primary and the next as secondary"
    (should= {:primary {:kind :attention :text "City needs attention"}
              :secondary {:kind :result :text "City conquered."}}
             (display/resolve-banner-pair "" 0 "City needs attention" "City conquered.")))

  (it "prefers error over attention and turn message"
    (should= {:primary {:kind :error :text "Bad news"}
              :secondary {:kind :attention :text "Need orders"}}
             (display/resolve-banner-pair "Bad news"
                                          (+ (System/currentTimeMillis) 1000)
                                          "Need orders"
                                          "Moved")))

  (it "returns only a primary banner when there is no secondary candidate"
    (should= {:primary {:kind :result :text "Moved"}
              :secondary nil}
             (display/resolve-banner-pair "" 0 "" "Moved"))))

(describe "resolve-banner-list"
  (it "returns active banner messages in priority order"
    (should= [{:kind :error :text "Bad news"}
              {:kind :attention :text "Need orders"}
              {:kind :result :text "Moved"}]
             (display/resolve-banner-list "Bad news"
                                          (+ (System/currentTimeMillis) 1000)
                                          "Need orders"
                                          "Moved")))

  (it "omits expired errors"
    (should= [{:kind :attention :text "Need orders"}
              {:kind :result :text "Moved"}]
             (display/resolve-banner-list "Old error"
                                          (- (System/currentTimeMillis) 1000)
                                          "Need orders"
                                          "Moved")))

  (it "limits the list to three messages"
    (should= 3
             (count (display/resolve-banner-list "Bad news"
                                                 (+ (System/currentTimeMillis) 1000)
                                                 "Need orders"
                                                 "Moved")))))

(describe "resolve-status-line"
  (it "formats paused round and map label on the left"
    (should= {:left "PAUSED  R17  Comp"
              :center "Dest 12,7"
              :right "A1 F2 | 75%"}
             (display/resolve-status-line 17 nil true false :computer-map [12 7]
                                          "A:1 F:2 T:0 D:0 S:0 P:0 C:0 B:0 Z:0 | 75%"
                                          [] [])))

  (it "omits player-map label and empty optional fields"
    (should= {:left "R3"
              :center nil
              :right nil}
             (display/resolve-status-line 3 nil false false :player-map nil "" [] [])))

  (it "shows actual-map label when appropriate"
    (should= {:left "R9  Actual"
              :center nil
              :right "0 units | 4%"}
             (display/resolve-status-line 9 nil false false :actual-map nil
                                          "A:0 F:0 T:0 D:0 S:0 P:0 C:0 B:0 Z:0 | 4%"
                                          [] [])))

  (it "truncates long center and right fields"
    (should= {:left "R7"
              :center "Dest 1234567890..."
              :right "A12 F9 D3 | 100%"}
             (display/resolve-status-line 7 nil false false :player-map [123456789012 999]
                                          "A:12 F:9 T:0 D:3 S:0 P:0 C:0 B:0 Z:0 | 100%"
                                          [] [])))

  (it "shows city lookaround in the center when there is no pending destination"
    (should= {:left "R11"
              :center "Lookaround"
              :right nil}
             (display/resolve-status-line 11 nil false false :player-map nil ""
                                          [[{:type :city :city-status :player
                                              :marching-orders :lookaround}]]
                                          [[0 0]])))

  (it "shows transport marching orders from the current attention cell"
    (should= {:left "R5"
              :center "March 8,3"
              :right nil}
             (display/resolve-status-line 5 nil false false :player-map nil ""
                                          [[{:type :sea
                                             :contents {:type :transport
                                                        :owner :player
                                                        :marching-orders [8 3]}}]]
                                          [[0 0]])))

  (it "shows carrier flight orders from the current attention cell"
    (should= {:left "R6"
              :center "Flight 9,4"
              :right nil}
             (display/resolve-status-line 6 nil false false :player-map nil ""
                                          [[{:type :sea
                                             :contents {:type :carrier
                                                        :owner :player
                                                        :flight-path [9 4]}}]]
                                          [[0 0]])))

  (it "shows handicap countdown on the left"
    (should= {:left "R4  HC12"
              :center nil
              :right nil}
             (display/resolve-status-line 4 12 false false :player-map nil "" [] []))))

(describe "resolve-display-map"
  (it "returns player-map for :player-map"
    (should= :p (display/resolve-display-map :player-map :p :c :g)))

  (it "returns computer-map for :computer-map"
    (should= :c (display/resolve-display-map :computer-map :p :c :g)))

  (it "returns game-map for :actual-map"
    (should= :g (display/resolve-display-map :actual-map :p :c :g))))

(describe "resolve-center-lines"
  (it "shows debug lines on computer-map"
    (let [lines (display/resolve-center-lines :computer-map
                                              {}
                                              10
                                              "line1\nline2\nline3\nline4")]
      (should= ["line1" "line2" "line3"]
               lines)))

  (it "returns empty vector for nil debug message on computer-map"
    (let [lines (display/resolve-center-lines :computer-map
                                              {}
                                              10
                                              nil)]
      (should= [] lines)))

  (it "falls back to debug lines when not on computer-map"
    (should= ["line1" "line2" "line3"]
             (display/resolve-center-lines :player-map {} 10 "line1\nline2\nline3\nline4")))

  (it "returns empty vector for blank debug message outside computer-map"
    (should= [] (display/resolve-center-lines :actual-map {} 10 ""))))

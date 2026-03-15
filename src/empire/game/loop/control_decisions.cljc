(ns empire.game.loop.control-decisions)

(defn handicap-active?
  [remaining]
  (pos? (or remaining 0)))

(defn current-player-items
  [handicap-rounds-remaining player-items]
  (if (handicap-active? handicap-rounds-remaining)
    []
    (vec player-items)))

(defn handicap-update
  [remaining display]
  (let [remaining* (or remaining 0)]
    (cond
      (pos? remaining*)
      {:action :count-down
       :remaining (dec remaining*)
       :display (dec remaining*)}

      (and (zero? remaining*) (zero? (or display 0)) (some? display))
      {:action :clear-display}

      :else nil)))

(defn- round-end-action
  [both-lists-empty? pause-requested]
  (when both-lists-empty?
    (if pause-requested :pause :new-round)))

(defn game-over-action
  [game-over-check-enabled player-items computer-items handicap-rounds-remaining]
  (when game-over-check-enabled
    (cond
      (and (empty? player-items) (not (handicap-active? handicap-rounds-remaining)))
      {:action :lose
       :message "****GAME OVER*****  You Lose"}

      (empty? computer-items)
      {:action :win
       :message "****GAME OVER*****  I Resign  YOU WIN!"}

      :else nil)))

(defn advance-game-action
  [{:keys [load-menu-open save-menu-open paused both-lists-empty? pause-requested waiting-for-input
           player-items]}]
  (cond
    load-menu-open nil
    save-menu-open nil
    paused nil
    (round-end-action both-lists-empty? pause-requested) (round-end-action both-lists-empty? pause-requested)
    waiting-for-input nil
    (seq player-items) :process-player
    :else :process-computer))

(defn continue-batch?
  [remaining paused waiting-for-input player-items computer-items]
  (and (> remaining 1)
       (not paused)
       (not waiting-for-input)
       (or (seq player-items)
           (seq computer-items))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-15T16:25:40.152481-05:00", :module-hash "-1628015192", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "2005738187"} {:id "defn/handicap-active?", :kind "defn", :line 3, :end-line 5, :hash "372465724"} {:id "defn/current-player-items", :kind "defn", :line 7, :end-line 11, :hash "2008481851"} {:id "defn/handicap-update", :kind "defn", :line 13, :end-line 25, :hash "380936411"} {:id "defn-/round-end-action", :kind "defn-", :line 27, :end-line 30, :hash "1785046917"} {:id "defn/game-over-action", :kind "defn", :line 32, :end-line 44, :hash "705515003"} {:id "defn/advance-game-action", :kind "defn", :line 46, :end-line 56, :hash "1898346841"} {:id "defn/continue-batch?", :kind "defn", :line 58, :end-line 64, :hash "1811766690"}]}
;; clj-mutate-manifest-end

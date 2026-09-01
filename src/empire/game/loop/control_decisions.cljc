(ns empire.game.loop.control-decisions)

(defn handicap-active?
  [remaining]
  (pos? (or remaining 0)))

(defn current-player-items
  [handicap-rounds-remaining player-items]
  (if (handicap-active? handicap-rounds-remaining)
    []
    (vec player-items)))

(defn- handicap-display-clear?
  [remaining* display]
  (and (zero? remaining*) (zero? (or display 0)) (some? display)))

(defn handicap-update
  [remaining display]
  (let [remaining* (or remaining 0)]
    (cond
      (pos? remaining*)
      {:action :count-down
       :remaining (dec remaining*)
       :display (dec remaining*)}

      (handicap-display-clear? remaining* display)
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

(defn- blocked-by-ui?
  [{:keys [load-menu-open save-menu-open paused]}]
  (or load-menu-open save-menu-open paused))

(defn advance-game-action
  [{:keys [both-lists-empty? pause-requested waiting-for-input player-items] :as state}]
  (cond
    (blocked-by-ui? state) nil
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

(defn toggle-pause-action
  [paused?]
  (if paused?
    :resume
    :request-pause))

(defn step-one-round-action
  [{:keys [paused? player-items computer-items]}]
  (cond
    (not paused?) nil
    (and (empty? player-items) (empty? computer-items)) :start-round
    :else :resume-one-round))

(defn round-start-state
  [{:keys [handicap-rounds-remaining player-items computer-items game-over-check-enabled]}]
  {:player-items player-items
   :computer-items computer-items
   :game-over (game-over-action game-over-check-enabled
                                player-items
                                computer-items
                                handicap-rounds-remaining)
   :waiting-for-input false
   :attention-message ""
   :cells-needing-attention []})

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T01:50:27.760138-05:00", :module-hash "1435071677", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "2005738187"} {:id "defn/handicap-active?", :kind "defn", :line 3, :end-line 5, :hash "372465724"} {:id "defn/current-player-items", :kind "defn", :line 7, :end-line 11, :hash "2008481851"} {:id "defn/handicap-update", :kind "defn", :line 13, :end-line 25, :hash "380936411"} {:id "defn-/round-end-action", :kind "defn-", :line 27, :end-line 30, :hash "1785046917"} {:id "defn/game-over-action", :kind "defn", :line 32, :end-line 44, :hash "705515003"} {:id "defn/advance-game-action", :kind "defn", :line 46, :end-line 56, :hash "1898346841"} {:id "defn/continue-batch?", :kind "defn", :line 58, :end-line 64, :hash "1811766690"} {:id "defn/toggle-pause-action", :kind "defn", :line 66, :end-line 70, :hash "-205862481"} {:id "defn/step-one-round-action", :kind "defn", :line 72, :end-line 77, :hash "-881642960"} {:id "defn/round-start-state", :kind "defn", :line 79, :end-line 89, :hash "-1580191887"}]}
;; clj-mutate-manifest-end

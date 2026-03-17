(ns empire.ui.quil.core-spec
  (:require [empire.ui.quil.core :as quil-core]
            [empire.ui.quil.input :as quil-input]
            [empire.state.api :as sa]
            [empire.test.utils :refer [reset-all-atoms!]]
            [empire.ui.util.core :as util-core]
            [speclj.core :refer :all]))

(describe "-main"
  (before (reset-all-atoms!))

  (it "prints usage and returns before startup when help is requested"
    (with-redefs [util-core/help-requested? (constantly true)
                  util-core/usage-text (constantly "Usage text")
                  empire.ui.quil.core/screen-dimensions (fn [] (throw (ex-info "should not run" {})))]
      (should= "Usage text\n"
               (with-out-str
                 (quil-core/-main "--help")))))

  (it "initializes startup state and launches the sketch on the happy path"
    (let [initialized (atom nil)
          started (atom nil)]
      (with-redefs [util-core/help-requested? (constantly false)
                    empire.ui.quil.core/screen-dimensions (constantly [1920 1080])
                    empire.ui.quil.core/parse-startup-config (fn [args screen-w screen-h]
                                                               (should= ["--seed" "7"] args)
                                                               (should= 1920 screen-w)
                                                               (should= 1080 screen-h)
                                                               {:cols 80 :rows 40 :seed 7 :window-w 640 :window-h 480 :handicap 0})
                    empire.ui.quil.core/initialize-startup-state! (fn [startup effective-seed]
                                                                     (reset! initialized [startup effective-seed]))
                    empire.ui.quil.core/start-sketch! (fn [startup]
                                                        (reset! started startup))]
        (should= "empire has begun. Map size: [80 40], seed: 7\n"
                 (with-out-str
                   (quil-core/-main "--seed" "7")))
        (should= [{:cols 80 :rows 40 :seed 7 :window-w 640 :window-h 480 :handicap 0} 7]
                 @initialized)
        (should= {:cols 80 :rows 40 :seed 7 :window-w 640 :window-h 480 :handicap 0}
                 @started)))))

(describe "initialize-startup-state!"
  (before (reset-all-atoms!))

  (it "stores handicap startup flags"
    (#'quil-core/initialize-startup-state!
     {:cols 80 :rows 40 :handicap 12}
     12345)
    (should= [80 40] (sa/read-state :map-size))
    (should= 12345 (sa/read-state :random-seed))
    (should= 12 (sa/read-state :handicap-rounds-remaining))))

(describe "create-fonts"
  (before (reset-all-atoms!))

  (it "creates and stores both cached fonts"
    (let [calls (atom [])]
      (with-redefs [quil.core/create-font (fn [name size]
                                            (swap! calls conj [name size])
                                            [name size])]
        (quil-core/create-fonts)
        (should= [[empire.config.core/text-font-name empire.config.core/text-font-size]
                  [empire.config.core/cell-char-font-name empire.config.core/cell-char-font-size]]
                 @calls)
        (should= [empire.config.core/text-font-name empire.config.core/text-font-size]
                 (sa/read-state :text-font))
        (should= [empire.config.core/cell-char-font-name empire.config.core/cell-char-font-size]
                 (sa/read-state :production-char-font))))))

(describe "setup"
  (before (reset-all-atoms!))

  (it "initializes fonts, dimensions, map, and frame rate without a seed"
    (let [calls (atom [])]
      (sa/write-state! :map-size [80 40])
      (sa/write-state! :map-size-constants {:number-of-cities 12})
      (with-redefs [empire.ui.quil.core/create-fonts (fn [] (swap! calls conj :fonts))
                    empire.ui.util.core/calculate-screen-dimensions (fn [] (swap! calls conj :screen))
                    empire.game.initialization/make-initial-map (fn [map-size smooth-count land-fraction num-cities min-city-distance]
                                                                  (swap! calls conj [:map map-size smooth-count land-fraction num-cities min-city-distance]))
                    quil.core/frame-rate (fn [fps] (swap! calls conj [:fps fps]))]
        (should= {} (quil-core/setup))
        (should= [:fonts
                  :screen
                  [:map [80 40]
                   empire.config.core/smooth-count
                   empire.config.core/land-fraction
                   12
                   empire.config.core/min-city-distance]
                  [:fps 30]]
                 @calls))))

  (it "installs seeded random functions when random-seed is present"
    (let [calls (atom [])
          seeded-rand (atom nil)
          seeded-rand-int (atom nil)]
      (sa/write-state! :random-seed 123)
      (sa/write-state! :map-size [60 30])
      (sa/write-state! :map-size-constants {})
      (with-redefs [empire.ui.quil.core/create-fonts (fn [] nil)
                    empire.ui.util.core/calculate-screen-dimensions (fn [] nil)
                    empire.game.initialization/make-initial-map (fn [& _] nil)
                    quil.core/frame-rate (fn [_] nil)
                    alter-var-root (fn [v f]
                                     (swap! calls conj v)
                                     (let [replacement (f ::ignored)]
                                       (cond
                                         (= v #'clojure.core/rand) (reset! seeded-rand replacement)
                                         (= v #'clojure.core/rand-int) (reset! seeded-rand-int replacement)))
                                     nil)]
        (quil-core/setup)
        (should= [#'clojure.core/rand #'clojure.core/rand-int] @calls)
        (should (fn? @seeded-rand))
        (should (fn? @seeded-rand-int))
        (should (number? (@seeded-rand)))
        (should (number? (@seeded-rand 5)))
        (should (integer? (@seeded-rand-int 7)))))))

(describe "parse-startup-config"
  (it "returns parsed startup options on success"
    (with-redefs [empire.ui.util.core/parse-args (fn [args screen-w screen-h]
                                                   (should= ["80" "40"] args)
                                                   (should= 1000 screen-w)
                                                   (should= 800 screen-h)
                                                   {:cols 80 :rows 40})]
      (should= {:cols 80 :rows 40}
               (#'quil-core/parse-startup-config ["80" "40"] 1000 800))))

  (it "prints map size errors through the exit helper"
    (let [error (ex-info "too big" {:cols 120 :rows 80})
          captured (atom nil)]
      (with-redefs [empire.ui.util.core/parse-args (fn [& _] (throw error))
                    empire.ui.quil.core/print-map-size-error-and-exit! (fn [e]
                                                                          (reset! captured e)
                                                                          :exit)]
        (should= :exit
                 (#'quil-core/parse-startup-config ["120" "80"] 1000 800))
        (should= error @captured)))))

(describe "update-state"
  (it "runs the game-loop and hover update before returning state"
    (let [calls (atom [])]
      (with-redefs [empire.game.loop.core/update-player-map (fn [] (swap! calls conj :player))
                    empire.game.loop.core/update-computer-map (fn [] (swap! calls conj :computer))
                    empire.game.loop.core/advance-game-batch (fn [] (swap! calls conj :advance))
                    empire.ui.quil.rendering.overlay/update-hover-status (fn [] (swap! calls conj :hover))]
        (should= :state (quil-core/update-state :state))
        (should= [:player :computer :advance :hover] @calls)))))

(describe "draw-state"
  (before (reset-all-atoms!))

  (it "resolves the current map and renders all draw phases"
    (let [calls (atom [])]
      (sa/write-state! :map-to-display :player-map)
      (sa/write-state! :player-map :player)
      (sa/write-state! :computer-map :computer)
      (sa/write-state! :game-map :world)
      (with-redefs [quil.core/background (fn [n] (swap! calls conj [:background n]))
                    empire.ui.util.rendering.display/resolve-display-map (fn [display-key player-map computer-map game-map]
                                                                           (swap! calls conj [:resolve display-key player-map computer-map game-map])
                                                                           :resolved-map)
                    empire.ui.quil.rendering.map/draw-map (fn [the-map] (swap! calls conj [:draw-map the-map]))
                    empire.ui.quil.rendering.map/draw-debug-selection-rectangle (fn [] (swap! calls conj :debug-rect))
                    empire.ui.quil.rendering.messages/draw-message-area (fn [] (swap! calls conj :messages))
                    empire.ui.quil.rendering.overlay/draw-load-menu (fn [] (swap! calls conj :load-menu))
                    empire.ui.quil.rendering.overlay/draw-save-menu (fn [] (swap! calls conj :save-menu))]
        (quil-core/draw-state nil)
        (should= [[:background 0]
                  [:resolve :player-map :player :computer :world]
                  [:draw-map :resolved-map]
                  :debug-rect
                  :messages
                  :load-menu
                  :save-menu]
                 @calls)))))

(describe "key-pressed"
  (before (reset-all-atoms!))

  (it "normalizes special keys and remembers them"
    (let [downs (atom [])]
      (with-redefs [quil.core/key-as-keyword (constantly :ignored)
                    quil.core/key-code (constantly java.awt.event.KeyEvent/VK_DELETE)
                    quil-input/key-down (fn [k] (swap! downs conj k))]
        (should= :state (quil-core/key-pressed :state nil))
        (should= [:delete] @downs)
        (should= :delete (sa/read-state :last-key)))))

  (it "ignores shift presses"
    (let [downs (atom [])]
      (with-redefs [quil.core/key-as-keyword (constantly :shift)
                    quil.core/key-code (constantly 0)
                    quil-input/key-down (fn [k] (swap! downs conj k))]
        (should= :state (quil-core/key-pressed :state nil))
        (should= [] @downs)
        (should-be-nil (sa/read-state :last-key)))))

  (it "does not repeat key-down while a key is already held"
    (let [downs (atom [])]
      (sa/write-state! :last-key :existing)
      (with-redefs [quil.core/key-as-keyword (constantly :a)
                    quil.core/key-code (constantly 0)
                    quil-input/key-down (fn [k] (swap! downs conj k))]
        (should= :state (quil-core/key-pressed :state nil))
        (should= [] @downs)
        (should= :a (sa/read-state :last-key))))))

(describe "mouse handlers"
  (it "starts debug dragging when a modifier is held"
    (let [calls (atom [])]
      (with-redefs [quil.core/mouse-x (constantly 12)
                    quil.core/mouse-y (constantly 34)
                    quil.core/mouse-button (constantly :left)
                    quil.core/key-modifiers (constantly {:control true :meta false :alt false})
                    empire.ui.util.input.dispatch/modifier-held? (fn [mods]
                                                                    (swap! calls conj [:modifier mods])
                                                                    true)
                    empire.ui.util.input.dispatch/debug-drag-start! (fn [x y] (swap! calls conj [:drag-start x y]))
                    empire.ui.util.input.dispatch/mouse-down (fn [& args] (swap! calls conj [:mouse-down args]))]
        (should= :state (quil-core/mouse-pressed :state nil))
        (should= [[:modifier {:ctrl true :meta false :alt false}]
                  [:drag-start 12 34]]
                 @calls))))

  (it "dispatches normal mouse down when no modifier is held"
    (let [calls (atom [])]
      (with-redefs [quil.core/mouse-x (constantly 12)
                    quil.core/mouse-y (constantly 34)
                    quil.core/mouse-button (constantly :right)
                    quil.core/key-modifiers (constantly {:control false :meta false :alt false})
                    empire.ui.util.input.dispatch/modifier-held? (fn [mods]
                                                                    (swap! calls conj [:modifier mods])
                                                                    false)
                    empire.ui.util.input.dispatch/debug-drag-start! (fn [& args] (swap! calls conj [:drag-start args]))
                    empire.ui.util.input.dispatch/mouse-down (fn [x y button] (swap! calls conj [:mouse-down x y button]))]
        (should= :state (quil-core/mouse-pressed :state nil))
        (should= [[:modifier {:ctrl false :meta false :alt false}]
                  [:mouse-down 12 34 :right]]
                 @calls))))

  (it "forwards drag and release coordinates"
    (let [calls (atom [])]
      (with-redefs [quil.core/mouse-x (constantly 22)
                    quil.core/mouse-y (constantly 44)
                    quil.core/key-modifiers (constantly {:control false :meta true :alt true})
                    empire.ui.util.input.dispatch/debug-drag-update! (fn [x y] (swap! calls conj [:drag-update x y]))
                    empire.ui.util.input.dispatch/debug-drag-end! (fn [x y mods] (swap! calls conj [:drag-end x y mods]))]
        (should= :state (quil-core/mouse-dragged :state nil))
        (should= :state (quil-core/mouse-released :state nil))
        (should= [[:drag-update 22 44]
                  [:drag-end 22 44 {:ctrl false :meta true :alt true}]]
                 @calls)))))

(ns empire.ui.util.help-spec
  (:require [clojure.string :as string]
            [empire.config.keys :as keys-config]
            [empire.test.utils :as test-utils]
            [empire.ui.util.help :as help]
            [empire.ui.util.input.dispatch-keys :as dispatch-keys]
            [speclj.core :refer :all]))

(defn- all-entries []
  (mapcat :entries help/keystroke-sections))

(defn- keys-text []
  (string/join " " (map :keys (all-entries))))

(defn- explanations-text []
  (string/join " " (map :explanation (all-entries))))

(describe "keystroke-sections"
  (it "has titled sections with keystroke entries"
    (should (seq help/keystroke-sections))
    (doseq [section help/keystroke-sections]
      (should (string? (:title section)))
      (should (seq (:title section)))
      (should (seq (:entries section)))))

  (it "gives every entry a keystroke and a non-empty explanation"
    (doseq [entry (all-entries)]
      (should (seq (:keys entry)))
      (should (>= (count (:explanation entry)) 10))))

  (it "lists the help key"
    (should (string/includes? (keys-text) "?")))

  (it "lists movement and extended movement keys"
    (let [keys (keys-text)]
      (should (string/includes? keys "q"))
      (should (string/includes? keys "w"))
      (should (string/includes? keys "Q"))
      (should (string/includes? keys "W"))))

  (it "lists unit, city, and standing-order keys"
    (let [keys (keys-text)]
      (doseq [k ["space" "s" "u" "l" "." "*" "m" "f" "p" "x"
                 "a" "t" "d" "b"]]
        (should (string/includes? keys k)))))

  (it "lists game-control keys"
    (let [keys (keys-text)]
      (doseq [k ["P" "+" "!" "^"]]
        (should (string/includes? keys k)))))

  (it "lists backtick commands including unit placement and claiming a city"
    (let [keys (keys-text)
          explanations (explanations-text)]
      (should (string/includes? keys "`"))
      (should (string/includes? keys "A"))
      (should (string/includes? keys "a"))
      (should (string/includes? keys "o"))
      (should (string/includes? explanations "player"))
      (should (string/includes? explanations "computer"))
      (should (re-find #"(?i)claim|own" explanations))))

  (it "lists every configured movement, production, standing-order, and backtick key"
    (let [blob (keys-text)
          configured (concat (keys keys-config/key->direction)
                             (keys keys-config/key->extended-direction)
                             (keys keys-config/key->production-item)
                             (keys dispatch-keys/standing-order-handlers)
                             (keys dispatch-keys/backtick-unit-map)
                             [:o])]
      (doseq [k configured]
        (should (string/includes? blob (name k))))))

  (it "wraps explanations so they fit a help column"
    (doseq [entry (all-entries)
            line (help/wrap-words (:explanation entry) help/explanation-wrap)]
      (should (<= (count line) help/explanation-wrap)))))

(describe "open-help! and close-help!"
  (before (test-utils/reset-all-atoms!))

  (it "opens the help window"
    (help/open-help!)
    (should= true (test-utils/read-test-state :help-open)))

  (it "closes the help window"
    (help/open-help!)
    (help/close-help!)
    (should= false (test-utils/read-test-state :help-open))))

(describe "wrap-words"
  (it "keeps a short line intact"
    (should= ["hello world"] (help/wrap-words "hello world" 20)))

  (it "wraps at word boundaries"
    (should= ["hello world" "friends"] (help/wrap-words "hello world friends" 12))))

(describe "help-geometry"
  (it "centers a window that contains the dismiss button"
    (let [geom (help/help-geometry 800 600)
          button (:dismiss-button geom)]
      (should= (/ (- 800 (:width geom)) 2) (:left geom))
      (should= (/ (- 600 (:height geom)) 2) (:top geom))
      (should (<= (:left geom) (:x button)))
      (should (>= (:right geom) (+ (:x button) (:w button))))
      (should (<= (:top geom) (:y button)))
      (should (>= (:bottom geom) (+ (:y button) (:h button))))))

  (it "labels the dismiss button"
    (should= "Dismiss" help/dismiss-label))

  (it "fits inside the default game window"
    (let [geom (help/help-geometry 1100 1047)
          button (:dismiss-button geom)]
      (should (<= (:width geom) 1100))
      (should (<= (:height geom) 1047))
      (should (>= (:x button) 0))
      (should (>= (:y button) 0))
      (should (<= (+ (:x button) (:w button)) 1100))
      (should (<= (+ (:y button) (:h button)) 1047)))))

(describe "current-geometry"
  (before (test-utils/reset-all-atoms!))

  (it "returns a stored help geometry"
    (let [geom (help/help-geometry 1100 1047)]
      (test-utils/set-test-state! :help-geometry geom)
      (should= geom (help/current-geometry))))

  (it "builds geometry from the map window when none is stored"
    (test-utils/set-test-state! :map-screen-dimensions [1100 1047])
    (test-utils/set-test-state! :text-area-dimensions [0 900 1100 147])
    (let [geom (help/current-geometry)]
      (should= (help/help-geometry 1100 1047) geom)))

  (it "uses the text-area bottom when it is taller than the map"
    (test-utils/set-test-state! :map-screen-dimensions [1100 400])
    (test-utils/set-test-state! :text-area-dimensions [0 900 1100 200])
    (should= (help/help-geometry 1100 1100) (help/current-geometry)))

  (it "treats missing screen dimensions as zeros"
    (test-utils/set-test-state! :map-screen-dimensions nil)
    (test-utils/set-test-state! :text-area-dimensions nil)
    (should= (help/help-geometry 0 0) (help/current-geometry)))

  (it "treats missing text-area pieces as zero"
    (test-utils/set-test-state! :map-screen-dimensions [800 100])
    (test-utils/set-test-state! :text-area-dimensions [0 nil 0 nil])
    (should= (help/help-geometry 800 100) (help/current-geometry))))

(describe "dismiss-button-hit?"
  (it "is true inside the dismiss button and false outside"
    (let [geom (help/help-geometry 800 600)
          button (:dismiss-button geom)
          inside-x (+ (:x button) (/ (:w button) 2))
          inside-y (+ (:y button) (/ (:h button) 2))]
      (should (help/dismiss-button-hit? inside-x inside-y geom))
      (should-not (help/dismiss-button-hit? 0 0 geom))
      (should-not (help/dismiss-button-hit? (:left geom) (:top geom) geom)))))

(describe "handle-help-click"
  (before (test-utils/reset-all-atoms!))

  (it "closes help when the dismiss button is clicked"
    (help/open-help!)
    (let [geom (help/help-geometry 800 600)
          button (:dismiss-button geom)]
      (test-utils/set-test-state! :help-geometry geom)
      (help/handle-help-click (+ (:x button) 1) (+ (:y button) 1))
      (should= false (test-utils/read-test-state :help-open))))

  (it "leaves help open when the click misses the dismiss button"
    (help/open-help!)
    (test-utils/set-test-state! :help-geometry (help/help-geometry 800 600))
    (help/handle-help-click 0 0)
    (should= true (test-utils/read-test-state :help-open))))

(defn- short-screen-geom []
  (help/help-geometry 400 280))

(describe "help-geometry when content does not fit"
  (it "caps the window to the screen and reports leftover content as scroll"
    (let [geom (short-screen-geom)]
      (should (<= (:height geom) 280))
      (should (> (:content-height geom) (:viewport-height geom)))
      (should (pos? (:max-scroll geom)))
      (should (:scrollable? geom))))

  (it "does not need scrolling on a tall screen"
    (let [geom (help/help-geometry 1100 2000)]
      (should= 0 (:max-scroll geom))
      (should-not (:scrollable? geom))
      (should= (:content-height geom) (:viewport-height geom)))))

(describe "help scrolling"
  (before
    (test-utils/reset-all-atoms!)
    (test-utils/set-test-state! :help-geometry (short-screen-geom)))

  (it "resets scroll when help opens"
    (test-utils/set-test-state! :help-scroll 40)
    (help/open-help!)
    (should= 0 (test-utils/read-test-state :help-scroll)))

  (it "resets scroll when help closes"
    (help/open-help!)
    (test-utils/set-test-state! :help-scroll 40)
    (help/close-help!)
    (should= 0 (test-utils/read-test-state :help-scroll)))

  (it "clamps scroll to the hidden content"
    (let [max-scroll (:max-scroll (short-screen-geom))]
      (help/scroll-help! 10000)
      (should= max-scroll (test-utils/read-test-state :help-scroll))
      (help/scroll-help! -10000)
      (should= 0 (test-utils/read-test-state :help-scroll))))

  (it "scrolls one line down and up with the arrow keys"
    (help/handle-help-key :down)
    (should= help/line-height (test-utils/read-test-state :help-scroll))
    (help/handle-help-key :up)
    (should= 0 (test-utils/read-test-state :help-scroll)))

  (it "jumps to the end and back to the top"
    (help/handle-help-key :end)
    (should= (:max-scroll (short-screen-geom))
             (test-utils/read-test-state :help-scroll))
    (help/handle-help-key :home)
    (should= 0 (test-utils/read-test-state :help-scroll)))

  (it "scrolls by a page with page-down and page-up"
    (let [geom (short-screen-geom)
          view (max help/line-height (:viewport-height geom))
          expected (help/clamp-scroll view (:max-scroll geom))]
      (help/handle-help-key :page-down)
      (should= expected (test-utils/read-test-state :help-scroll))
      (help/handle-help-key :page-up)
      (should= 0 (test-utils/read-test-state :help-scroll))))

  (it "accepts pgdn and pgup aliases"
    (let [geom (short-screen-geom)
          view (max help/line-height (:viewport-height geom))
          expected (help/clamp-scroll view (:max-scroll geom))]
      (help/handle-help-key :pgdn)
      (should= expected (test-utils/read-test-state :help-scroll))
      (help/handle-help-key :pgup)
      (should= 0 (test-utils/read-test-state :help-scroll))))

  (it "ignores keys that are not scroll commands"
    (should (help/handle-help-key :P))
    (should= 0 (test-utils/read-test-state :help-scroll)))

  (it "scrolls by the mouse wheel"
    (help/handle-help-wheel 2)
    (should= (* 2 help/line-height) (test-utils/read-test-state :help-scroll))
    (help/handle-help-wheel {:count -1})
    (should= help/line-height (test-utils/read-test-state :help-scroll)))

  (it "reads wheel-rotation when count is absent"
    (help/handle-help-wheel {:wheel-rotation 1})
    (should= help/line-height (test-utils/read-test-state :help-scroll)))

  (it "treats an unknown wheel event as no movement"
    (help/handle-help-wheel "noop")
    (should= 0 (test-utils/read-test-state :help-scroll)))

  (it "treats a wheel map without counts as no movement"
    (help/handle-help-wheel {})
    (should= 0 (test-utils/read-test-state :help-scroll))))

(ns empire.game.save-load-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.game.save-load :as save-load]
            [empire.test.utils :refer [reset-all-atoms! set-test-world!]]))

(describe "load menu atoms"
  (before (reset-all-atoms!))

  (it "load-menu-open defaults to false"
    (should= false (test-utils/read-test-state :load-menu-open)))

  (it "load-menu-files defaults to empty vector"
    (should= [] (test-utils/read-test-state :load-menu-files)))

  (it "load-menu-hovered defaults to nil"
    (should-be-nil (test-utils/read-test-state :load-menu-hovered))))

(describe "list-save-files"
  (it "returns empty vector when saves directory doesn't exist"
    (should= [] (save-load/list-save-files "nonexistent-dir")))

  (it "returns empty vector when saves directory is empty"
    (let [dir (java.io.File/createTempFile "saves" "")]
      (.delete dir)
      (.mkdir dir)
      (try
        (should= [] (save-load/list-save-files (.getPath dir)))
        (finally
          (.delete dir)))))

  (it "returns edn files sorted newest first"
    (let [dir (java.io.File/createTempFile "saves" "")]
      (.delete dir)
      (.mkdir dir)
      (try
        (let [f1 (java.io.File. dir "save-2026-01-01-120000.edn")
              f2 (java.io.File. dir "save-2026-01-02-120000.edn")]
          (spit f1 "{}")
          (Thread/sleep 10)
          (spit f2 "{}")
          (let [files (save-load/list-save-files (.getPath dir))]
            (should= 2 (count files))
            (should= "save-2026-01-02-120000.edn" (first files))
            (should= "save-2026-01-01-120000.edn" (second files))))
        (finally
          (doseq [f (.listFiles dir)] (.delete f))
          (.delete dir))))))

(describe "save-game!"
  (before (reset-all-atoms!))

  (it "creates saves directory if it doesn't exist"
    (let [dir (str (java.io.File/createTempFile "saves" "") "-dir")]
      (try
        (save-load/save-game! dir)
        (should (.exists (java.io.File. dir)))
        (finally
          (doseq [f (.listFiles (java.io.File. dir))] (.delete f))
          (.delete (java.io.File. dir))))))

  (it "creates a timestamped edn file"
    (let [dir (str (java.io.File/createTempFile "saves" "") "-dir")]
      (try
        (let [filename (save-load/save-game! dir)
              files (.listFiles (java.io.File. dir))]
          (should= 1 (count files))
          (should (.startsWith filename "save-"))
          (should (.endsWith filename ".edn")))
        (finally
          (doseq [f (.listFiles (java.io.File. dir))] (.delete f))
          (.delete (java.io.File. dir))))))

  (it "saves game-map atom value"
    (let [dir (str (java.io.File/createTempFile "saves" "") "-dir")
          test-map [[{:type :land}]]]
      (set-test-world! test-map)
      (try
        (let [filename (save-load/save-game! dir)
              saved (clojure.edn/read-string (slurp (str dir "/" filename)))]
          (should= test-map (:game-map saved)))
        (finally
          (doseq [f (.listFiles (java.io.File. dir))] (.delete f))
          (.delete (java.io.File. dir)))))))

(describe "saveable-atoms"
  (it "contains game-map"
    (should-contain :game-map save-load/saveable-atoms))

  (it "contains production"
    (should-contain :production save-load/saveable-atoms))

  (it "contains round-number"
    (should-contain :round-number save-load/saveable-atoms))

  (it "does not contain transient atoms like last-key"
    (should-not-contain :last-key save-load/saveable-atoms))

  (it "does not contain load-menu-open"
    (should-not-contain :load-menu-open save-load/saveable-atoms)))

(describe "load-game!"
  (before (reset-all-atoms!))

  (it "restores game-map from saved file"
    (let [dir (str (java.io.File/createTempFile "saves" "") "-dir")
          test-map [[{:type :land} {:type :sea}]]]
      (set-test-world! test-map)
      (try
        (let [filename (save-load/save-game! dir)]
          (set-test-world! nil)
          (save-load/load-game! dir filename)
          (should= test-map (test-utils/read-test-state :game-map)))
        (finally
          (doseq [f (.listFiles (java.io.File. dir))] (.delete f))
          (.delete (java.io.File. dir))))))

  (it "restores round-number from saved file"
    (let [dir (str (java.io.File/createTempFile "saves" "") "-dir")]
      (test-utils/set-test-state! :round-number 42)
      (try
        (let [filename (save-load/save-game! dir)]
          (test-utils/set-test-state! :round-number 0)
          (save-load/load-game! dir filename)
          (should= 42 (test-utils/read-test-state :round-number)))
        (finally
          (doseq [f (.listFiles (java.io.File. dir))] (.delete f))
          (.delete (java.io.File. dir))))))

  (it "closes the load menu after loading"
    (let [dir (str (java.io.File/createTempFile "saves" "") "-dir")]
      (test-utils/set-test-state! :load-menu-open true)
      (try
        (let [filename (save-load/save-game! dir)]
          (save-load/load-game! dir filename)
          (should= false (test-utils/read-test-state :load-menu-open)))
        (finally
          (doseq [f (.listFiles (java.io.File. dir))] (.delete f))
          (.delete (java.io.File. dir)))))))

(describe "open-load-menu!"
  (before (reset-all-atoms!))

  (it "sets load-menu-open to true"
    (save-load/open-load-menu!)
    (should= true (test-utils/read-test-state :load-menu-open)))

  (it "populates load-menu-files from saves directory"
    (let [dir (str (java.io.File/createTempFile "saves" "") "-dir")]
      (try
        (.mkdirs (java.io.File. dir))
        (spit (str dir "/save-test.edn") "{}")
        (save-load/open-load-menu! dir)
        (should= ["save-test.edn"] (test-utils/read-test-state :load-menu-files))
        (finally
          (doseq [f (.listFiles (java.io.File. dir))] (.delete f))
          (.delete (java.io.File. dir)))))))

(describe "close-load-menu!"
  (before (reset-all-atoms!))

  (it "sets load-menu-open to false"
    (test-utils/set-test-state! :load-menu-open true)
    (save-load/close-load-menu!)
    (should= false (test-utils/read-test-state :load-menu-open)))

  (it "clears load-menu-files"
    (test-utils/set-test-state! :load-menu-files ["file.edn"])
    (save-load/close-load-menu!)
    (should= [] (test-utils/read-test-state :load-menu-files)))

  (it "clears load-menu-hovered"
    (test-utils/set-test-state! :load-menu-hovered 2)
    (save-load/close-load-menu!)
    (should-be-nil (test-utils/read-test-state :load-menu-hovered))))

(describe "menu-geometry"
  (it "calculates exact values for 3 files"
    (let [geom (save-load/menu-geometry 800 601 3)]
      (should= 225 (:left geom))
      (should= 233 (:top geom))
      (should= 575 (:right geom))
      (should= 368 (:bottom geom))
      (should= 350 (:width geom))
      (should= 135 (:height geom))
      (should= 278 (:content-top geom))
      (should= 25 (:item-height geom))))

  (it "uses minimum height of 1 item for 0 files"
    (let [geom (save-load/menu-geometry 800 601 0)]
      (should= 85 (:height geom))
      (should= 258 (:top geom)))))

(describe "hovered-file-index"
  (it "returns nil when mouse is outside menu"
    (let [geom (save-load/menu-geometry 800 601 3)]
      (should-be-nil (save-load/hovered-file-index 0 0 geom 3))))

  (it "returns 0 for first item"
    (let [geom (save-load/menu-geometry 800 601 3)]
      (should= 0 (save-load/hovered-file-index 400 280 geom 3))))

  (it "returns nil when no files"
    (let [geom (save-load/menu-geometry 800 601 0)]
      (should-be-nil (save-load/hovered-file-index 400 300 geom 0))))

  (it "returns 0 at exact left boundary"
    (let [geom (save-load/menu-geometry 800 601 3)]
      (should= 0 (save-load/hovered-file-index 225 280 geom 3))))

  (it "returns 0 at exact right boundary"
    (let [geom (save-load/menu-geometry 800 601 3)]
      (should= 0 (save-load/hovered-file-index 575 280 geom 3))))

  (it "returns 0 at exact content-top boundary"
    (let [geom (save-load/menu-geometry 800 601 3)]
      (should= 0 (save-load/hovered-file-index 400 278 geom 3))))

  (it "returns nil at exact bottom boundary"
    (let [geom (save-load/menu-geometry 800 601 3)]
      (should-be-nil (save-load/hovered-file-index 400 353 geom 3))))

  (it "returns last item just above bottom boundary"
    (let [geom (save-load/menu-geometry 800 601 3)]
      (should= 2 (save-load/hovered-file-index 400 352 geom 3))))

  (it "returns 0 for single file"
    (let [geom (save-load/menu-geometry 800 601 1)]
      (should= 0 (save-load/hovered-file-index 400 305 geom 1)))))

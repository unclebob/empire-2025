(ns empire.state.computer)

(def defaults
  {:computer-items []
   :computer-turn false
   :claimed-objectives #{}
   :claimed-transport-targets #{}
   :claimed-patrol-targets #{}
   :last-transport-city {}
   :fighter-leg-records {}
   :computer-city-positions #{}
   :computer-carrier-positions #{}
   :country-stats {}
   :coastal-cells-by-country {}
   :coast-walkers-produced {}
   :opening-satellite-produced? false
   :patrol-boats-produced {}
   :seen-coast #{}
   :land-ho-targets []
   :major-invasion-state {:active? false
                          :decision nil
                          :failure-reason nil
                          :next-review-round nil
                          :detection-points #{}
                          :target-land-set #{}
                          :started-round nil
                          :first-landing-round nil}
   :transport-fully-loaded? false
   :early-patrol-boat-produced? false
   :early-satellite-produced? false
   :computer-event-log []
   :distant-city-pairs nil
   :lake-max-cells 0
   :known-lake-cells #{}
   :next-transport-id 1
   :next-unload-event-id 1
   :next-destroyer-id 1
   :next-carrier-id 1
   :next-escort-id 1})

(def state (atom defaults))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-13T15:28:04.640574-05:00", :module-hash "-1517718144", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "-654248230"} {:id "def/defaults", :kind "def", :line 3, :end-line 39, :hash "-1215465483"} {:id "def/state", :kind "def", :line 41, :end-line 41, :hash "-1274501582"}]}
;; clj-mutate-manifest-end

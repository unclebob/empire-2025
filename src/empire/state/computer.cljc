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
   :major-invasion-state {:active? false
                          :decision nil
                          :failure-reason nil
                          :next-review-round nil
                          :detection-points #{}
                          :kamikazee-army-targets []
                          :kamikazee-root-city nil
                          :kamikazee-city-next-hops {}
                          :kamikazee-carrier-next-hops {}
                          :kamikazee-bridge-carriers #{}
                          :kamikazee-forward-carrier nil
                          :kamikazee-terminal-sites #{}
                          :target-land-set #{}
                          :started-round nil
                          :first-landing-round nil}
   :transport-fully-loaded? false
   :transport-load-reservations {}
   :early-patrol-boat-produced? false
   :early-satellite-produced? false
   :computer-event-log []
   :computer-unit-round-discoveries {}
   :computer-unit-round-conquests {}
   :distant-city-pairs nil
   :lake-max-cells 0
   :known-lake-cells #{}
   :next-computer-unit-id 1
   :next-transport-id 1
   :next-unload-event-id 1
   :next-destroyer-id 1
   :next-carrier-id 1
   :next-escort-id 1})

(def state (atom defaults))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T02:32:07.068269-05:00", :module-hash "-1275854544", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "-654248230"} {:id "def/defaults", :kind "def", :line 3, :end-line 49, :hash "1402202788"} {:id "def/state", :kind "def", :line 51, :end-line 51, :hash "-1274501582"}]}
;; clj-mutate-manifest-end

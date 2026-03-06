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
   :patrol-boats-produced {}
   :seen-coast #{}
   :land-ho-targets []
   :major-invasion-state {:active? false
                          :detection-points #{}
                          :target-land-set #{}
                          :started-round nil}
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

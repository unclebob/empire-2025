(ns empire.config.messages)

(def messages
  {:army-found-city "Army found a city!"
   :fighter-bingo "Bingo! Refuel?"
   :fighter-out-of-fuel "Fighter out of fuel."
   :fighter-landed-and-refueled "Landed and refueled."
   :fighter-over-defended-city "Fighter about to fly over defended city."
   :fighter-shot-down "Incoming anti-aircraft fire!"
   :fighter-destroyed-by-city "Fighter destroyed by city defenses."
   :fighter-crashed "Fighter crashed."
   :army-drowned "Army drowned."
   :failed-to-conquer "Failed to conquer city."
   :conquest-failed "Conquest Failed"
   :cant-move-into-water "Can't move into water."
   :cant-move-into-city "Can't move into city."
   :ships-cant-drive-on-land "Ships don't drive on land."
   :ships-cant-enter-city "Ships can't enter city."
   :transport-at-beach "At beach."
   :transport-found-land "Found land!"
   :found-a-bay "Found a bay!"
   :somethings-in-the-way "Something's in the way."
   :enemy-spotted "Enemy spotted."
   :not-on-map "That's not on the map!"
   :returned-to-start "Done looking around."
   :hit-edge "Hit map edge."
   :blocked "Blocked."
   :steps-exhausted "Done looking around."
   :not-near-coast "Not near coast."
   :skipping-this-round "Skipping this round."
   :marching-orders-set "Marching orders set to %d,%d"
   :marching-orders-lookaround "Marching orders set to lookaround"
   :flight-path-set "Flight path set to %d,%d"
   :waypoint-placed "Waypoint placed at %d,%d"
   :waypoint-removed "Waypoint removed from %d,%d"
   :waypoint-orders-set "Waypoint orders set to %d,%d"
   :docked-for-repair "%s docked for repair. %d/%d hits remain."
   :combat-result "%s. %s destroyed."
   :destination "Dest: %d,%d"
   :coastal-city-required "Must be coastal city to produce %s."})

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T00:37:22.795158-05:00", :module-hash "-955726927", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "917790933"} {:id "def/error-message-duration", :kind "def", :line 3, :end-line 3, :hash "-2136737491"} {:id "def/messages", :kind "def", :line 5, :end-line 48, :hash "1834249931"}]}
;; clj-mutate-manifest-end

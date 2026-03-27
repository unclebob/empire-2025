(ns empire.state.api
  "Direct atom-backed state access. Public boundary for all game state."
  (:require [empire.state.world :as world]
            [empire.state.computer :as computer]
            [empire.state.player :as player]
            [empire.state.ui :as ui]
            [empire.config.domain.core.continents :as continents]
            [empire.config.domain.core.refueling :as refueling]))

(def ^:private key->group
  (merge
    (zipmap (keys world/defaults) (repeat ::world))
    (zipmap (keys computer/defaults) (repeat ::computer))
    (zipmap (keys player/defaults) (repeat ::player))
    (zipmap (keys ui/defaults) (repeat ::ui))))

(def ^:private group->atom
  {::world world/state
   ::computer computer/state
   ::player player/state
   ::ui ui/state})

(defn- group-atom [k]
  (or (some-> k key->group group->atom)
      (throw (ex-info (str "Unknown state key: " k) {:key k}))))

(defn current-world []
  (:game-map @world/state))

(defn update-world! [f & args]
  (apply swap! world/state update :game-map f args))

(defn read-state [k]
  (get @(group-atom k) k))

(defn write-state! [k v] (swap! (group-atom k) assoc k v))

(defn update-state! [k f & args]
  (apply swap! (group-atom k) update k f args))

(defn merge-continents! [stamp-id existing-cid]
  (swap! world/state update :continent-groups
         continents/merge-continents stamp-id existing-cid))

(defn on-same-continent? [cid1 cid2]
  (continents/on-same-continent? (:continent-groups @world/state) cid1 cid2))

(defn rebuild-refueling-caches! []
  (let [{:keys [cities carriers]}
        (refueling/scan-refueling-positions (read-state :computer-map))]
    (swap! computer/state assoc
           :computer-city-positions cities
           :computer-carrier-positions carriers)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T02:30:55.939235-05:00", :module-hash "-933618131", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 8, :hash "-241155621"} {:id "def/key->group", :kind "def", :line 10, :end-line 15, :hash "1907128933"} {:id "def/group->atom", :kind "def", :line 17, :end-line 21, :hash "1419998616"} {:id "defn-/group-atom", :kind "defn-", :line 23, :end-line 25, :hash "-1913066708"} {:id "defn/current-world", :kind "defn", :line 27, :end-line 28, :hash "-1919766652"} {:id "defn/update-world!", :kind "defn", :line 30, :end-line 31, :hash "1709587760"} {:id "defn/read-state", :kind "defn", :line 33, :end-line 34, :hash "1744086287"} {:id "defn/write-state!", :kind "defn", :line 36, :end-line 36, :hash "1575745319"} {:id "defn/update-state!", :kind "defn", :line 38, :end-line 39, :hash "1107140020"} {:id "defn/merge-continents!", :kind "defn", :line 41, :end-line 43, :hash "1590992933"} {:id "defn/on-same-continent?", :kind "defn", :line 45, :end-line 46, :hash "1119672438"} {:id "defn/rebuild-refueling-caches!", :kind "defn", :line 48, :end-line 53, :hash "168932745"}]}
;; clj-mutate-manifest-end

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

(defn- group-atom [k]
  (case (or (get key->group k)
            (throw (ex-info (str "Unknown state key: " k) {:key k})))
    ::world world/state
    ::computer computer/state
    ::player player/state
    ::ui ui/state))

(defn current-world [] (:game-map @world/state))

(defn update-world! [f & args]
  (apply swap! world/state update :game-map f args))

(defn read-state [k] (get @(group-atom k) k))

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
        (refueling/scan-refueling-positions (:game-map @world/state))]
    (swap! computer/state assoc
           :computer-city-positions cities
           :computer-carrier-positions carriers)))

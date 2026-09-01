(ns empire.game-mechanics.services.combat.resolution
  (:require [empire.state.api :as sa]
            [empire.config.domain.model.combat :as domain-combat]
            [empire.game-mechanics.combat-visibility-port :as visibility-port]
            [empire.config.units.dispatcher :as dispatcher]
            [empire.notifications :as notifications]))

(defn warning-message-map
  [msg]
  {:warning-message msg})

(defn command-message-map
  [msg]
  {:command-message msg})

(defn apply-combat-result!
  "Applies a combat result map's side effects: world update, messages, state changes, visibility."
  [{:keys [world messages state-updates visibility]}]
  (when world
    (sa/update-world! (constantly world)))
  (doseq [[k v] messages]
    (if (and (= k :warning-message) (seq v))
      (notifications/warn! v)
      (sa/write-state! k v)))
  (doseq [[k v] state-updates]
    (if (fn? v)
      (sa/update-state! k v)
      (sa/write-state! k v)))
  (visibility-port/apply-visibility-effects!
    (visibility-port/combat-visibility-port)
    visibility))

(defn drown-excess-cargo-world
  [world coords survivor]
  (if-not (#{:transport :carrier} (:type survivor))
    world
    (let [cap (dispatcher/effective-capacity (:type survivor) (:hits survivor))
          [count-key awake-key] (if (= :transport (:type survivor))
                                  [:army-count :awake-armies]
                                  [:fighter-count :awake-fighters])
          current-count (get survivor count-key 0)
          excess (- current-count cap)]
      (if (pos? excess)
        (let [current-awake (get survivor awake-key 0)
              new-awake (min current-awake cap)]
          (update-in world (conj coords :contents)
                     assoc count-key cap awake-key new-awake))
        world))))

(defn format-combat-log
  [log attacker-type defender-type winner]
  (domain-combat/format-combat-log log attacker-type defender-type winner))

(defn format-combat-status
  [log attacker-type defender-type winner]
  (domain-combat/format-combat-status log attacker-type defender-type winner))

(defn format-combat-outcome
  [attacker-type defender-type winner]
  (domain-combat/format-combat-outcome attacker-type defender-type winner))

(defn fight-round
  [attacker defender]
  (domain-combat/fight-round attacker defender))

(defn resolve-combat
  [attacker defender]
  (domain-combat/resolve-combat attacker defender))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T15:06:35.706014-05:00", :module-hash "-789877701", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "1973767976"} {:id "defn/warning-message-map", :kind "defn", :line 8, :end-line nil, :hash "1125172603"} {:id "defn/command-message-map", :kind "defn", :line 12, :end-line nil, :hash "2125787001"} {:id "defn/apply-combat-result!", :kind "defn", :line 16, :end-line nil, :hash "-1681125125"} {:id "defn/drown-excess-cargo-world", :kind "defn", :line 33, :end-line nil, :hash "-394118541"} {:id "defn/format-combat-log", :kind "defn", :line 50, :end-line nil, :hash "-298786363"} {:id "defn/format-combat-status", :kind "defn", :line 54, :end-line nil, :hash "552980926"} {:id "defn/format-combat-outcome", :kind "defn", :line 58, :end-line nil, :hash "-377935311"} {:id "defn/fight-round", :kind "defn", :line 62, :end-line nil, :hash "-400173913"} {:id "defn/resolve-combat", :kind "defn", :line 66, :end-line nil, :hash "-1610119610"}]}
;; clj-mutate-manifest-end

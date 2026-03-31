(ns empire.game-mechanics.services.combat.resolution
  (:require [empire.state.api :as sa]
            [empire.config.domain.model.combat :as domain-combat]
            [empire.game-mechanics.combat-visibility-port :as visibility-port]
            [empire.config.units.dispatcher :as dispatcher]))

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
    (sa/write-state! k v))
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
;; {:version 1, :tested-at "2026-03-27T11:49:09.360457-05:00", :module-hash "-1531340368", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 5, :hash "-627268661"} {:id "defn/error-message-map", :kind "defn", :line 7, :end-line 10, :hash "1443867130"} {:id "defn/turn-message-map", :kind "defn", :line 12, :end-line 17, :hash "-2014708459"} {:id "defn/apply-combat-result!", :kind "defn", :line 19, :end-line 32, :hash "1493705292"} {:id "defn/drown-excess-cargo-world", :kind "defn", :line 34, :end-line 49, :hash "-394118541"} {:id "defn/format-combat-log", :kind "defn", :line 51, :end-line 53, :hash "-298786363"} {:id "defn/format-combat-status", :kind "defn", :line 55, :end-line 57, :hash "552980926"} {:id "defn/fight-round", :kind "defn", :line 59, :end-line 61, :hash "-400173913"} {:id "defn/resolve-combat", :kind "defn", :line 63, :end-line 65, :hash "-1610119610"}]}
;; clj-mutate-manifest-end

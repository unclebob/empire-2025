;; mutation-tested: 2026-02-28
(ns empire.acceptance.parser.given
  (:require [clojure.string :as str]
            [empire.acceptance.parser.helpers :as h]
            [empire.acceptance.parser.given.directives :as directives]))

(def ^:private append-ir-directives
  #{:waiting-for-input :container-state :production :no-production :round :destination
    :cell-props :player-items :waiting-for-input-bare :unit-target :city-prop :stub
    :shipyard-state :city-unit :territory-around :visible-to-computer
    :game-over-check-enabled :game-paused :pause-requested :load-menu-open
    :map-display-setup :unrecognized})

(defn- consume-map-rows
  [lines i]
  (let [rows (atom [])]
    (while (and (< @i (count lines))
                (h/map-row? (nth lines @i)))
      (swap! rows conj (str/trim (nth lines @i)))
      (swap! i inc))
    @rows))

(defn- handle-map-start!
  [lines i givens parsed]
  (let [target (:target parsed)]
    (swap! i inc)
    (swap! givens conj {:type :map :target target :rows (consume-map-rows lines i)})))

(defn- handle-unit-props!
  [i givens context parsed]
  (let [ir (:ir parsed)]
    (when (:mode (:props ir))
      (swap! context update :units-with-mode conj (:unit ir)))
    (when (seq (:props ir))
      (swap! givens conj (dissoc ir :container-props)))
    (when-let [cp (:container-props ir)]
      (swap! givens conj {:type :container-state :target (:unit ir) :props cp}))
    (swap! i inc)))

(defn- handle-parsed-directive!
  [lines i givens context parsed]
  (let [directive (:directive parsed)]
    (cond
      (= :map-start directive)
      (handle-map-start! lines i givens parsed)

      (= :unit-props directive)
      (handle-unit-props! i givens context parsed)

      (contains? append-ir-directives directive)
      (do (swap! givens conj (:ir parsed))
          (swap! i inc))

      :else
      (swap! i inc))))

(defn parse-given
  "Parse GIVEN lines into IR. Returns {:givens [...] :context updated-context}"
  [lines context]
  (let [context (atom (merge {:units-with-mode #{}} context))
        givens (atom [])
        i (atom 0)]
    (while (< @i (count lines))
      (let [line (nth lines @i)
            trimmed (str/trim line)]
        (if (h/blank-or-comment? line)
          (swap! i inc)
          (let [parsed (directives/parse-given-line trimmed @context)]
            (handle-parsed-directive! lines i givens context parsed)))))
    {:givens @givens :context @context}))

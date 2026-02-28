;; mutation-tested: 2026-02-28
(ns empire.acceptance.parser.given
  (:require [clojure.string :as str]
            [empire.acceptance.parser.helpers :as h]
            [empire.acceptance.parser.given.directives :as directives]))

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
            (case (:directive parsed)
              :map-start
              (let [target (:target parsed)
                    _ (swap! i inc)
                    rows (atom [])]
                (while (and (< @i (count lines))
                            (h/map-row? (nth lines @i)))
                  (swap! rows conj (str/trim (nth lines @i)))
                  (swap! i inc))
                (swap! givens conj {:type :map :target target :rows @rows}))

              :waiting-for-input
              (do
                (swap! givens conj (:ir parsed))
                (swap! i inc))

              :unit-props
              (let [ir (:ir parsed)]
                (when (:mode (:props ir))
                  (swap! context update :units-with-mode conj (:unit ir)))
                (when (seq (:props ir))
                  (swap! givens conj (dissoc ir :container-props)))
                (when-let [cp (:container-props ir)]
                  (swap! givens conj {:type :container-state :target (:unit ir) :props cp}))
                (swap! i inc))

              :container-state
              (do (swap! givens conj (:ir parsed))
                  (swap! i inc))

              (:production :no-production :round :destination :cell-props
               :player-items :waiting-for-input-bare :unit-target :city-prop :stub
               :shipyard-state :city-unit :territory-around :visible-to-computer
               :game-over-check-enabled :game-paused :pause-requested :load-menu-open
               :map-display-setup)
              (do (swap! givens conj (:ir parsed))
                  (swap! i inc))

              :unrecognized
              (do (swap! givens conj (:ir parsed))
                  (swap! i inc))

              (swap! i inc))))))
    {:givens @givens :context @context}))

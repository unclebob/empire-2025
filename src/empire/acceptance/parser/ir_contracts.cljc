(ns empire.acceptance.parser.ir-contracts
  (:require [clojure.spec.alpha :as s]))

(defn- coords2? [v]
  (and (vector? v)
       (= 2 (count v))
       (int? (first v))
       (int? (second v))))

(defn- non-empty-string? [v]
  (and (string? v) (not (empty? v))))

(s/def ::type keyword?)
(s/def ::unit non-empty-string?)
(s/def ::city non-empty-string?)
(s/def ::target (s/or :str non-empty-string? :kw keyword?))
(s/def ::rows (s/coll-of string? :kind vector?))
(s/def ::coords coords2?)
(s/def ::value any?)
(s/def ::count int?)
(s/def ::moves int?)
(s/def ::steps int?)
(s/def ::hits int?)
(s/def ::text string?)
(s/def ::ref non-empty-string?)
(s/def ::property keyword?)
(s/def ::expected any?)
(s/def ::config-key keyword?)
(s/def ::area keyword?)
(s/def ::item keyword?)
(s/def ::remaining-rounds int?)
(s/def ::excluded keyword?)
(s/def ::lookup keyword?)
(s/def ::owner keyword?)
(s/def ::prop keyword?)
(s/def ::country-id int?)
(s/def ::var non-empty-string?)
(s/def ::bindings (s/coll-of (s/keys :req-un [::var ::value]) :kind vector?))
(s/def ::props map?)
(s/def ::set-mode boolean?)
(s/def ::key keyword?)
(s/def ::input-fn keyword?)
(s/def ::outcome keyword?)
(s/def ::combat-type keyword?)
(s/def ::mouse-cell ::coords)
(s/def ::ship-type keyword?)
(s/def ::items (s/coll-of string? :kind vector?))
(s/def ::unit-type keyword?)
(s/def ::at-next-round boolean?)
(s/def ::at-next-step boolean?)

;; GIVEN
(defmulti given-ir-spec :type)

(defmethod given-ir-spec :map [_]
  (s/keys :req-un [::type ::target ::rows]))

(defmethod given-ir-spec :unit-props [_]
  (s/keys :req-un [::type ::unit ::props]))

(defmethod given-ir-spec :container-state [_]
  (s/keys :req-un [::type ::target ::props]))

(defmethod given-ir-spec :waiting-for-input [_]
  (s/keys :req-un [::type ::unit ::set-mode]))

(defmethod given-ir-spec :production [_]
  (s/keys :req-un [::type ::city ::item]
          :opt-un [::remaining-rounds]))

(defmethod given-ir-spec :no-production [_]
  (s/keys :req-un [::type]))

(defmethod given-ir-spec :round [_]
  (s/keys :req-un [::type ::value]))

(defmethod given-ir-spec :destination [_]
  (s/keys :req-un [::type ::coords]))

(defmethod given-ir-spec :cell-props [_]
  (s/keys :req-un [::type ::coords ::props]))

(defmethod given-ir-spec :player-items [_]
  (s/keys :req-un [::type ::items]))

(defmethod given-ir-spec :waiting-for-input-state [_]
  (s/keys :req-un [::type]))

(defmethod given-ir-spec :unit-target [_]
  (s/keys :req-un [::type ::unit ::target]))

(defmethod given-ir-spec :city-unit [_]
  (s/keys :req-un [::type ::city ::unit-type ::owner]))

(defmethod given-ir-spec :shipyard-state [_]
  (s/keys :req-un [::type ::city ::ship-type ::hits]))

(defmethod given-ir-spec :stub [_]
  (s/keys :req-un [::type ::bindings]))

(defmethod given-ir-spec :visible-to-computer [_]
  (s/keys :req-un [::type ::ref]))

(defmethod given-ir-spec :city-prop [_]
  (s/keys :req-un [::type ::city ::prop ::value]))

(defmethod given-ir-spec :territory-around [_]
  (s/keys :req-un [::type ::city ::country-id]))

(defmethod given-ir-spec :game-over-check-enabled [_]
  (s/keys :req-un [::type]))

(defmethod given-ir-spec :game-paused [_]
  (s/keys :req-un [::type]))

(defmethod given-ir-spec :pause-requested [_]
  (s/keys :req-un [::type]))

(defmethod given-ir-spec :load-menu-open [_]
  (s/keys :req-un [::type]))

(defmethod given-ir-spec :map-display-setup [_]
  (s/keys :req-un [::type ::value]))

(defmethod given-ir-spec :unrecognized [_]
  (s/keys :req-un [::type ::text]))

(s/def ::given-ir (s/multi-spec given-ir-spec :type))
(s/def ::givens (s/coll-of ::given-ir :kind vector?))
(s/def ::given-result (s/keys :req-un [::givens]))

;; WHEN
(defmulti when-ir-spec :type)

(defmethod when-ir-spec :key-press [_]
  (s/keys :req-un [::type ::key ::input-fn]))

(defmethod when-ir-spec :backtick [_]
  (s/keys :req-un [::type ::key ::mouse-cell]))

(defmethod when-ir-spec :mouse-at-key [_]
  (s/keys :req-un [::type ::coords ::key]))

(defmethod when-ir-spec :waiting-for-input [_]
  (s/keys :req-un [::type ::unit ::set-mode]))

(defmethod when-ir-spec :battle [_]
  (s/keys :req-un [::type ::key ::outcome ::combat-type]))

(defmethod when-ir-spec :advance-until-waiting [_]
  (s/keys :req-un [::type ::unit]))

(defmethod when-ir-spec :start-new-round [_] (s/keys :req-un [::type]))
(defmethod when-ir-spec :advance-game-batch [_] (s/keys :req-un [::type]))
(defmethod when-ir-spec :advance-game [_] (s/keys :req-un [::type]))
(defmethod when-ir-spec :process-player-items [_] (s/keys :req-un [::type]))
(defmethod when-ir-spec :cell-visibility-update [_] (s/keys :req-un [::type ::unit]))
(defmethod when-ir-spec :visibility-update [_] (s/keys :req-un [::type]))
(defmethod when-ir-spec :evaluate-production [_] (s/keys :req-un [::type ::city]))
(defmethod when-ir-spec :process-computer-transport [_] (s/keys :req-un [::type ::unit]))
(defmethod when-ir-spec :process-computer-fighter [_] (s/keys :req-un [::type ::unit]))
(defmethod when-ir-spec :process-computer-ship [_] (s/keys :req-un [::type ::ship-type ::unit]))
(defmethod when-ir-spec :computer-rounds [_] (s/keys :req-un [::type ::count]))
(defmethod when-ir-spec :save-game [_] (s/keys :req-un [::type]))
(defmethod when-ir-spec :open-load-menu [_] (s/keys :req-un [::type]))
(defmethod when-ir-spec :unrecognized [_] (s/keys :req-un [::type ::text]))

(s/def ::when-ir (s/multi-spec when-ir-spec :type))
(s/def ::whens (s/coll-of ::when-ir :kind vector?))
(s/def ::when-result (s/keys :req-un [::whens]))

;; THEN
(defmulti then-ir-spec :type)

(defmethod then-ir-spec :unit-at [_]
  (s/or :coords (s/keys :req-un [::type ::unit ::coords])
        :target (s/keys :req-un [::type ::unit ::target])))

(defmethod then-ir-spec :unit-prop [_]
  (s/keys :req-un [::type ::unit ::property ::expected]))

(defmethod then-ir-spec :unit-prop-absent [_]
  (s/keys :req-un [::type ::unit ::property]))

(defmethod then-ir-spec :unit-present [_]
  (s/or :coords (s/keys :req-un [::type ::unit ::coords])
        :target (s/keys :req-un [::type ::unit ::target])))

(defmethod then-ir-spec :unit-absent [_]
  (s/keys :req-un [::type ::unit]))

(defmethod then-ir-spec :unit-waiting-for-input [_]
  (s/keys :req-un [::type ::unit]))

(defmethod then-ir-spec :unit-after-moves [_]
  (s/keys :req-un [::type ::unit ::moves ::target]))

(defmethod then-ir-spec :unit-after-steps [_]
  (s/or :coords (s/keys :req-un [::type ::unit ::steps ::coords])
        :target (s/keys :req-un [::type ::unit ::steps ::target])))

(defmethod then-ir-spec :unit-at-next-round [_]
  (s/or :coords (s/keys :req-un [::type ::unit ::coords]
                        :opt-un [::at-next-round ::at-next-step])
        :target (s/keys :req-un [::type ::unit ::target]
                        :opt-un [::at-next-round ::at-next-step])))

(defmethod then-ir-spec :unit-eventually-at [_]
  (s/keys :req-un [::type ::unit ::target]))

(defmethod then-ir-spec :unit-occupies-cell [_]
  (s/keys :req-un [::type ::unit]
          :opt-un [::target]))

(defmethod then-ir-spec :unit-unmoved [_]
  (s/keys :req-un [::type ::unit]
          :opt-un [::at-next-round ::at-next-step]))

(defmethod then-ir-spec :message-contains [_]
  (s/or :text (s/keys :req-un [::type ::area ::text])
        :key (s/keys :req-un [::type ::area ::config-key])))

(defmethod then-ir-spec :message-for-unit [_]
  (s/keys :req-un [::type ::area ::unit ::config-key]))

(defmethod then-ir-spec :message-is [_]
  (s/keys :req-un [::type ::area]
          :opt-un [::config-key]))

(defmethod then-ir-spec :no-message [_]
  (s/keys :req-un [::type ::area]))

(defmethod then-ir-spec :cell-prop [_]
  (s/keys :req-un [::type ::coords ::property ::expected]))

(defmethod then-ir-spec :cell-type [_]
  (s/keys :req-un [::type ::coords ::expected]))

(defmethod then-ir-spec :waiting-for-input [_]
  (s/keys :req-un [::type ::expected]))

(defmethod then-ir-spec :game-paused [_]
  (s/keys :req-un [::type ::expected]))

(defmethod then-ir-spec :game-not-paused [_]
  (s/keys :req-un [::type]))

(defmethod then-ir-spec :round [_]
  (s/keys :req-un [::type ::expected]))

(defmethod then-ir-spec :destination [_]
  (s/keys :req-un [::type ::expected]))

(defmethod then-ir-spec :production [_]
  (s/keys :req-un [::type ::city ::expected]))

(defmethod then-ir-spec :production-with-rounds [_]
  (s/keys :req-un [::type ::city ::expected ::remaining-rounds]))

(defmethod then-ir-spec :production-not [_]
  (s/keys :req-un [::type ::city ::excluded]))

(defmethod then-ir-spec :no-production [_]
  (s/keys :req-un [::type ::city]))

(defmethod then-ir-spec :container-prop [_]
  (s/keys :req-un [::type ::target ::property ::expected ::lookup]))

(defmethod then-ir-spec :no-unit-at [_]
  (s/keys :req-un [::type ::coords]))

(defmethod then-ir-spec :refueling-position-near [_]
  (s/keys :req-un [::type ::unit ::target]))

(defmethod then-ir-spec :shipyard-has-ship [_]
  (s/keys :req-un [::type ::city ::ship-type ::hits]))

(defmethod then-ir-spec :shipyard-empty [_]
  (s/keys :req-un [::type ::city]))

(defmethod then-ir-spec :map-is [_]
  (s/keys :req-un [::type ::expected]))

(defmethod then-ir-spec :map-display [_]
  (s/keys :req-un [::type ::expected]))

(defmethod then-ir-spec :load-menu-state [_]
  (s/keys :req-un [::type ::expected]))

(defmethod then-ir-spec :player-map-visibility [_]
  (s/keys :req-un [::type ::rows]))

(defmethod then-ir-spec :territory-map [_]
  (s/keys :req-un [::type ::rows]))

(defmethod then-ir-spec :player-map-cell-not-nil [_]
  (s/keys :req-un [::type ::coords]))

(defmethod then-ir-spec :player-map-cell-nil [_]
  (s/keys :req-un [::type ::coords]))

(defmethod then-ir-spec :computer-map-cell-not-nil [_]
  (s/keys :req-un [::type ::coords]))

(defmethod then-ir-spec :computer-army-count [_]
  (s/keys :req-un [::type ::expected]))

(defmethod then-ir-spec :unrecognized [_]
  (s/keys :req-un [::type ::text]))

(s/def ::then-ir (s/multi-spec then-ir-spec :type))
(s/def ::thens (s/coll-of ::then-ir :kind vector?))
(s/def ::then-result (s/keys :req-un [::thens]))

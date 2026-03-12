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
(defmulti ^:private given-ir-spec :type)

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
(defmulti ^:private when-ir-spec :type)

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
(defmethod when-ir-spec :rounds-complete [_] (s/keys :req-un [::type ::count]))
(defmethod when-ir-spec :save-game [_] (s/keys :req-un [::type]))
(defmethod when-ir-spec :open-load-menu [_] (s/keys :req-un [::type]))
(defmethod when-ir-spec :unrecognized [_] (s/keys :req-un [::type ::text]))

(s/def ::when-ir (s/multi-spec when-ir-spec :type))
(s/def ::whens (s/coll-of ::when-ir :kind vector?))
(s/def ::when-result (s/keys :req-un [::whens]))

;; THEN
(defmulti ^:private then-ir-spec :type)

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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T11:56:47.645664-05:00", :module-hash "848173637", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 2, :hash "1479050436"} {:id "defn-/coords2?", :kind "defn-", :line 4, :end-line 8, :hash "1288996736"} {:id "defn-/non-empty-string?", :kind "defn-", :line 10, :end-line 11, :hash "2014185912"} {:id "form/3/s/def", :kind "s/def", :line 13, :end-line 13, :hash "1499278192"} {:id "form/4/s/def", :kind "s/def", :line 14, :end-line 14, :hash "923312758"} {:id "form/5/s/def", :kind "s/def", :line 15, :end-line 15, :hash "1668567401"} {:id "form/6/s/def", :kind "s/def", :line 16, :end-line 16, :hash "122830294"} {:id "form/7/s/def", :kind "s/def", :line 17, :end-line 17, :hash "-1771242611"} {:id "form/8/s/def", :kind "s/def", :line 18, :end-line 18, :hash "-741097086"} {:id "form/9/s/def", :kind "s/def", :line 19, :end-line 19, :hash "1083837596"} {:id "form/10/s/def", :kind "s/def", :line 20, :end-line 20, :hash "-837748669"} {:id "form/11/s/def", :kind "s/def", :line 21, :end-line 21, :hash "-1831509437"} {:id "form/12/s/def", :kind "s/def", :line 22, :end-line 22, :hash "793067727"} {:id "form/13/s/def", :kind "s/def", :line 23, :end-line 23, :hash "1875822669"} {:id "form/14/s/def", :kind "s/def", :line 24, :end-line 24, :hash "-1973521837"} {:id "form/15/s/def", :kind "s/def", :line 25, :end-line 25, :hash "1852482873"} {:id "form/16/s/def", :kind "s/def", :line 26, :end-line 26, :hash "261875199"} {:id "form/17/s/def", :kind "s/def", :line 27, :end-line 27, :hash "-1514976180"} {:id "form/18/s/def", :kind "s/def", :line 28, :end-line 28, :hash "1687337287"} {:id "form/19/s/def", :kind "s/def", :line 29, :end-line 29, :hash "1178111033"} {:id "form/20/s/def", :kind "s/def", :line 30, :end-line 30, :hash "720137701"} {:id "form/21/s/def", :kind "s/def", :line 31, :end-line 31, :hash "606284193"} {:id "form/22/s/def", :kind "s/def", :line 32, :end-line 32, :hash "949136299"} {:id "form/23/s/def", :kind "s/def", :line 33, :end-line 33, :hash "145902897"} {:id "form/24/s/def", :kind "s/def", :line 34, :end-line 34, :hash "-1049487860"} {:id "form/25/s/def", :kind "s/def", :line 35, :end-line 35, :hash "-430105708"} {:id "form/26/s/def", :kind "s/def", :line 36, :end-line 36, :hash "-701094742"} {:id "form/27/s/def", :kind "s/def", :line 37, :end-line 37, :hash "-1989831090"} {:id "form/28/s/def", :kind "s/def", :line 38, :end-line 38, :hash "631003161"} {:id "form/29/s/def", :kind "s/def", :line 39, :end-line 39, :hash "-307627493"} {:id "form/30/s/def", :kind "s/def", :line 40, :end-line 40, :hash "2021871033"} {:id "form/31/s/def", :kind "s/def", :line 41, :end-line 41, :hash "464705936"} {:id "form/32/s/def", :kind "s/def", :line 42, :end-line 42, :hash "-535270810"} {:id "form/33/s/def", :kind "s/def", :line 43, :end-line 43, :hash "635780144"} {:id "form/34/s/def", :kind "s/def", :line 44, :end-line 44, :hash "-1455520711"} {:id "form/35/s/def", :kind "s/def", :line 45, :end-line 45, :hash "1558444725"} {:id "form/36/s/def", :kind "s/def", :line 46, :end-line 46, :hash "-1115829271"} {:id "form/37/s/def", :kind "s/def", :line 47, :end-line 47, :hash "1397145244"} {:id "form/38/s/def", :kind "s/def", :line 48, :end-line 48, :hash "-2051241587"} {:id "form/39/s/def", :kind "s/def", :line 49, :end-line 49, :hash "132031653"} {:id "form/40/s/def", :kind "s/def", :line 50, :end-line 50, :hash "1426675361"} {:id "defmulti/given-ir-spec", :kind "defmulti", :line 53, :end-line 53, :hash "1832711508"} {:id "defmethod/given-ir-spec/:map", :kind "defmethod", :line 55, :end-line 56, :hash "-132521440"} {:id "defmethod/given-ir-spec/:unit-props", :kind "defmethod", :line 58, :end-line 59, :hash "-197473026"} {:id "defmethod/given-ir-spec/:container-state", :kind "defmethod", :line 61, :end-line 62, :hash "1440530941"} {:id "defmethod/given-ir-spec/:waiting-for-input", :kind "defmethod", :line 64, :end-line 65, :hash "1461027627"} {:id "defmethod/given-ir-spec/:production", :kind "defmethod", :line 67, :end-line 69, :hash "2137320191"} {:id "defmethod/given-ir-spec/:no-production", :kind "defmethod", :line 71, :end-line 72, :hash "-147695488"} {:id "defmethod/given-ir-spec/:round", :kind "defmethod", :line 74, :end-line 75, :hash "-542087568"} {:id "defmethod/given-ir-spec/:destination", :kind "defmethod", :line 77, :end-line 78, :hash "-2023376054"} {:id "defmethod/given-ir-spec/:cell-props", :kind "defmethod", :line 80, :end-line 81, :hash "-1755354366"} {:id "defmethod/given-ir-spec/:player-items", :kind "defmethod", :line 83, :end-line 84, :hash "-1179108005"} {:id "defmethod/given-ir-spec/:waiting-for-input-state", :kind "defmethod", :line 86, :end-line 87, :hash "621049829"} {:id "defmethod/given-ir-spec/:unit-target", :kind "defmethod", :line 89, :end-line 90, :hash "161156766"} {:id "defmethod/given-ir-spec/:city-unit", :kind "defmethod", :line 92, :end-line 93, :hash "-613512791"} {:id "defmethod/given-ir-spec/:shipyard-state", :kind "defmethod", :line 95, :end-line 96, :hash "-1022316537"} {:id "defmethod/given-ir-spec/:stub", :kind "defmethod", :line 98, :end-line 99, :hash "1736060634"} {:id "defmethod/given-ir-spec/:visible-to-computer", :kind "defmethod", :line 101, :end-line 102, :hash "1219287387"} {:id "defmethod/given-ir-spec/:city-prop", :kind "defmethod", :line 104, :end-line 105, :hash "105874051"} {:id "defmethod/given-ir-spec/:territory-around", :kind "defmethod", :line 107, :end-line 108, :hash "-279002659"} {:id "defmethod/given-ir-spec/:game-over-check-enabled", :kind "defmethod", :line 110, :end-line 111, :hash "970488121"} {:id "defmethod/given-ir-spec/:game-paused", :kind "defmethod", :line 113, :end-line 114, :hash "1858767172"} {:id "defmethod/given-ir-spec/:pause-requested", :kind "defmethod", :line 116, :end-line 117, :hash "1352904683"} {:id "defmethod/given-ir-spec/:load-menu-open", :kind "defmethod", :line 119, :end-line 120, :hash "139327649"} {:id "defmethod/given-ir-spec/:map-display-setup", :kind "defmethod", :line 122, :end-line 123, :hash "525992773"} {:id "defmethod/given-ir-spec/:unrecognized", :kind "defmethod", :line 125, :end-line 126, :hash "1087210440"} {:id "form/66/s/def", :kind "s/def", :line 128, :end-line 128, :hash "154444717"} {:id "form/67/s/def", :kind "s/def", :line 129, :end-line 129, :hash "1290143933"} {:id "form/68/s/def", :kind "s/def", :line 130, :end-line 130, :hash "1336538701"} {:id "defmulti/when-ir-spec", :kind "defmulti", :line 133, :end-line 133, :hash "-2092618241"} {:id "defmethod/when-ir-spec/:key-press", :kind "defmethod", :line 135, :end-line 136, :hash "651076186"} {:id "defmethod/when-ir-spec/:backtick", :kind "defmethod", :line 138, :end-line 139, :hash "1156825882"} {:id "defmethod/when-ir-spec/:mouse-at-key", :kind "defmethod", :line 141, :end-line 142, :hash "425983564"} {:id "defmethod/when-ir-spec/:waiting-for-input", :kind "defmethod", :line 144, :end-line 145, :hash "1864217965"} {:id "defmethod/when-ir-spec/:battle", :kind "defmethod", :line 147, :end-line 148, :hash "-807358329"} {:id "defmethod/when-ir-spec/:advance-until-waiting", :kind "defmethod", :line 150, :end-line 151, :hash "2008375437"} {:id "defmethod/when-ir-spec/:start-new-round", :kind "defmethod", :line 153, :end-line 153, :hash "-758225132"} {:id "defmethod/when-ir-spec/:advance-game-batch", :kind "defmethod", :line 154, :end-line 154, :hash "1270968652"} {:id "defmethod/when-ir-spec/:advance-game", :kind "defmethod", :line 155, :end-line 155, :hash "1635885166"} {:id "defmethod/when-ir-spec/:process-player-items", :kind "defmethod", :line 156, :end-line 156, :hash "1858950791"} {:id "defmethod/when-ir-spec/:cell-visibility-update", :kind "defmethod", :line 157, :end-line 157, :hash "-1963658388"} {:id "defmethod/when-ir-spec/:visibility-update", :kind "defmethod", :line 158, :end-line 158, :hash "-859672218"} {:id "defmethod/when-ir-spec/:evaluate-production", :kind "defmethod", :line 159, :end-line 159, :hash "-601683117"} {:id "defmethod/when-ir-spec/:process-computer-transport", :kind "defmethod", :line 160, :end-line 160, :hash "214698082"} {:id "defmethod/when-ir-spec/:process-computer-fighter", :kind "defmethod", :line 161, :end-line 161, :hash "1231482276"} {:id "defmethod/when-ir-spec/:process-computer-ship", :kind "defmethod", :line 162, :end-line 162, :hash "227953984"} {:id "defmethod/when-ir-spec/:computer-rounds", :kind "defmethod", :line 163, :end-line 163, :hash "722096493"} {:id "defmethod/when-ir-spec/:rounds-complete", :kind "defmethod", :line 164, :end-line 164, :hash "-141996995"} {:id "defmethod/when-ir-spec/:save-game", :kind "defmethod", :line 165, :end-line 165, :hash "792008734"} {:id "defmethod/when-ir-spec/:open-load-menu", :kind "defmethod", :line 166, :end-line 166, :hash "1165902086"} {:id "defmethod/when-ir-spec/:unrecognized", :kind "defmethod", :line 167, :end-line 167, :hash "1293664163"} {:id "form/91/s/def", :kind "s/def", :line 169, :end-line 169, :hash "-842221128"} {:id "form/92/s/def", :kind "s/def", :line 170, :end-line 170, :hash "-1916811177"} {:id "form/93/s/def", :kind "s/def", :line 171, :end-line 171, :hash "29509155"} {:id "defmulti/then-ir-spec", :kind "defmulti", :line 174, :end-line 174, :hash "-988470928"} {:id "defmethod/then-ir-spec/:unit-at", :kind "defmethod", :line 176, :end-line 178, :hash "-139558585"} {:id "defmethod/then-ir-spec/:unit-prop", :kind "defmethod", :line 180, :end-line 181, :hash "309147693"} {:id "defmethod/then-ir-spec/:unit-prop-absent", :kind "defmethod", :line 183, :end-line 184, :hash "1588469349"} {:id "defmethod/then-ir-spec/:unit-present", :kind "defmethod", :line 186, :end-line 188, :hash "1955703249"} {:id "defmethod/then-ir-spec/:unit-absent", :kind "defmethod", :line 190, :end-line 191, :hash "757245162"} {:id "defmethod/then-ir-spec/:unit-waiting-for-input", :kind "defmethod", :line 193, :end-line 194, :hash "1973663219"} {:id "defmethod/then-ir-spec/:unit-after-moves", :kind "defmethod", :line 196, :end-line 197, :hash "-1704908080"} {:id "defmethod/then-ir-spec/:unit-after-steps", :kind "defmethod", :line 199, :end-line 201, :hash "-236364706"} {:id "defmethod/then-ir-spec/:unit-at-next-round", :kind "defmethod", :line 203, :end-line 207, :hash "480516018"} {:id "defmethod/then-ir-spec/:unit-eventually-at", :kind "defmethod", :line 209, :end-line 210, :hash "187055150"} {:id "defmethod/then-ir-spec/:unit-occupies-cell", :kind "defmethod", :line 212, :end-line 214, :hash "280207652"} {:id "defmethod/then-ir-spec/:unit-unmoved", :kind "defmethod", :line 216, :end-line 218, :hash "-3087810"} {:id "defmethod/then-ir-spec/:message-contains", :kind "defmethod", :line 220, :end-line 222, :hash "1015514845"} {:id "defmethod/then-ir-spec/:message-for-unit", :kind "defmethod", :line 224, :end-line 225, :hash "339668609"} {:id "defmethod/then-ir-spec/:message-is", :kind "defmethod", :line 227, :end-line 229, :hash "-1147009189"} {:id "defmethod/then-ir-spec/:no-message", :kind "defmethod", :line 231, :end-line 232, :hash "-464570828"} {:id "defmethod/then-ir-spec/:cell-prop", :kind "defmethod", :line 234, :end-line 235, :hash "-1079811605"} {:id "defmethod/then-ir-spec/:cell-type", :kind "defmethod", :line 237, :end-line 238, :hash "278254206"} {:id "defmethod/then-ir-spec/:waiting-for-input", :kind "defmethod", :line 240, :end-line 241, :hash "-1242641833"} {:id "defmethod/then-ir-spec/:game-paused", :kind "defmethod", :line 243, :end-line 244, :hash "930265972"} {:id "defmethod/then-ir-spec/:game-not-paused", :kind "defmethod", :line 246, :end-line 247, :hash "2006543253"} {:id "defmethod/then-ir-spec/:round", :kind "defmethod", :line 249, :end-line 250, :hash "1623594503"} {:id "defmethod/then-ir-spec/:destination", :kind "defmethod", :line 252, :end-line 253, :hash "1039492085"} {:id "defmethod/then-ir-spec/:production", :kind "defmethod", :line 255, :end-line 256, :hash "1307036837"} {:id "defmethod/then-ir-spec/:production-with-rounds", :kind "defmethod", :line 258, :end-line 259, :hash "403549002"} {:id "defmethod/then-ir-spec/:production-not", :kind "defmethod", :line 261, :end-line 262, :hash "-2144056162"} {:id "defmethod/then-ir-spec/:no-production", :kind "defmethod", :line 264, :end-line 265, :hash "1272967702"} {:id "defmethod/then-ir-spec/:container-prop", :kind "defmethod", :line 267, :end-line 268, :hash "1427643581"} {:id "defmethod/then-ir-spec/:no-unit-at", :kind "defmethod", :line 270, :end-line 271, :hash "1844497740"} {:id "defmethod/then-ir-spec/:refueling-position-near", :kind "defmethod", :line 273, :end-line 274, :hash "-999129389"} {:id "defmethod/then-ir-spec/:shipyard-has-ship", :kind "defmethod", :line 276, :end-line 277, :hash "396593029"} {:id "defmethod/then-ir-spec/:shipyard-empty", :kind "defmethod", :line 279, :end-line 280, :hash "940074011"} {:id "defmethod/then-ir-spec/:map-is", :kind "defmethod", :line 282, :end-line 283, :hash "-772215086"} {:id "defmethod/then-ir-spec/:map-display", :kind "defmethod", :line 285, :end-line 286, :hash "-635090613"} {:id "defmethod/then-ir-spec/:load-menu-state", :kind "defmethod", :line 288, :end-line 289, :hash "-2066436050"} {:id "defmethod/then-ir-spec/:player-map-visibility", :kind "defmethod", :line 291, :end-line 292, :hash "-1231592321"} {:id "defmethod/then-ir-spec/:territory-map", :kind "defmethod", :line 294, :end-line 295, :hash "746717619"} {:id "defmethod/then-ir-spec/:player-map-cell-not-nil", :kind "defmethod", :line 297, :end-line 298, :hash "2088141732"} {:id "defmethod/then-ir-spec/:player-map-cell-nil", :kind "defmethod", :line 300, :end-line 301, :hash "769919317"} {:id "defmethod/then-ir-spec/:computer-map-cell-not-nil", :kind "defmethod", :line 303, :end-line 304, :hash "-286138681"} {:id "defmethod/then-ir-spec/:computer-army-count", :kind "defmethod", :line 306, :end-line 307, :hash "976859243"} {:id "defmethod/then-ir-spec/:unrecognized", :kind "defmethod", :line 309, :end-line 310, :hash "-1949795863"} {:id "form/137/s/def", :kind "s/def", :line 312, :end-line 312, :hash "-955089070"} {:id "form/138/s/def", :kind "s/def", :line 313, :end-line 313, :hash "595797936"} {:id "form/139/s/def", :kind "s/def", :line 314, :end-line 314, :hash "359371379"}]}
;; clj-mutate-manifest-end

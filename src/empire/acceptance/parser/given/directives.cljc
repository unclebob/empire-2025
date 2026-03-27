(ns empire.acceptance.parser.given.directives
  (:require [clojure.string :as str]
            [empire.acceptance.parser.helpers :as h]
            [empire.acceptance.parser.given.props :as props]))

(defn- given-handle-game-map [_] {:directive :map-start :target :game-map})
(defn- given-handle-player-map [_] {:directive :map-start :target :player-map})
(defn- given-handle-computer-map [_] {:directive :map-start :target :computer-map})

(defn- given-handle-waiting-for-input [[_ unit] ctx]
  (let [mode-already-set (contains? (:units-with-mode ctx) unit)]
    {:directive :waiting-for-input
     :ir {:type :waiting-for-input :unit unit :set-mode (not mode-already-set)}}))

(defn- given-handle-production-with-rounds [[_ city item n] _ctx]
  {:directive :production
   :ir {:type :production :city city :item (keyword item) :remaining-rounds (Integer/parseInt n)}})

(defn- given-handle-production [[_ city item] _ctx]
  {:directive :production
   :ir {:type :production :city city :item (keyword item)}})

(defn- given-handle-no-production [_ _ctx]
  {:directive :no-production :ir {:type :no-production}})

(defn- given-handle-round [[_ n] _ctx]
  {:directive :round :ir {:type :round :value (Integer/parseInt n)}})

(defn- given-handle-destination [[_ x y] _ctx]
  {:directive :destination
   :ir {:type :destination :coords [(Integer/parseInt x) (Integer/parseInt y)]}})

(defn- given-handle-cell-props [[_ x y rest-str] _ctx]
  (let [pairs (str/split rest-str #"\s+and\s+")
        props (into {}
                    (for [pair pairs
                          :let [[_ k v] (re-find #"(\S+)\s+(.*\S)" (str/trim pair))]
                          :when k]
                      [(h/resolve-cell-prop k) (or (h/parse-number v)
                                                   (h/parse-coords v)
                                                   (keyword v))]))]
    {:directive :cell-props
     :ir {:type :cell-props :coords [(Integer/parseInt x) (Integer/parseInt y)] :props props}}))

(defn- given-handle-player-items-multi [[_ items-str] _ctx]
  (let [items (mapv str/trim (str/split items-str #",\s*"))]
    {:directive :player-items :ir {:type :player-items :items items}}))

(defn- given-handle-player-items-single [[_ item] _ctx]
  {:directive :player-items :ir {:type :player-items :items [item]}})

(defn- given-handle-waiting-for-input-bare [_ _ctx]
  {:directive :waiting-for-input-bare :ir {:type :waiting-for-input-state}})

(defn- given-handle-unit-target [[_ unit target] _ctx]
  {:directive :unit-target :ir {:type :unit-target :unit unit :target target}})

(defn- given-handle-city-unit [[_ city owner unit-type] _ctx]
  {:directive :city-unit
   :ir {:type :city-unit :city city :unit-type (keyword unit-type) :owner (keyword owner)}})

(defn- given-handle-shipyard-state [[_ city ship-type hits] _ctx]
  {:directive :shipyard-state
   :ir {:type :shipyard-state :city city :ship-type (keyword ship-type) :hits (Integer/parseInt hits)}})

(def given-map-patterns
  [{:regex #"(?i)^(?:GIVEN\s+)?(?:game\s+)?map\s*$"
    :handler given-handle-game-map}
   {:regex #"(?i)^(?:GIVEN\s+)?game\s+map"
    :handler given-handle-game-map}
   {:regex #"(?i)^(?:GIVEN\s+)?player\s+map"
    :handler given-handle-player-map}
   {:regex #"(?i)^(?:GIVEN\s+)?computer\s+map"
    :handler given-handle-computer-map}])

(def given-directive-patterns
  [{:regex #"(?:the\s+)?game\s+is\s+waiting\s+for\s+input"
    :handler (fn [_ _ctx]
               {:directive :waiting-for-input-bare :ir {:type :waiting-for-input-state}})}
   {:regex #"(\w+)\s+is\s+waiting\s+for\s+input"
    :handler given-handle-waiting-for-input}
   {:regex #"production\s+at\s+(\w+)\s+is\s+(\w+)\s+with\s+(\d+)\s+rounds?\s+remaining"
    :handler given-handle-production-with-rounds}
   {:regex #"production\s+at\s+(\w+)\s+is\s+(\w+)"
    :handler given-handle-production}
   {:regex #"no\s+production"
    :handler given-handle-no-production}
   {:regex #"(?:round|round-number\s+is)\s+(\d+)"
    :handler given-handle-round}
   {:regex #"destination\s+\[(\d+)\s+(\d+)\]"
    :handler given-handle-destination}
   {:regex #"cell\s+\[(\d+)\s+(\d+)\]\s+has\s+(.*)"
    :handler given-handle-cell-props}
   {:regex #"(?:player-items|player\s+units?)\s+are\s+(.*)"
    :handler given-handle-player-items-multi}
   {:regex #"(?:player-items|player\s+units?)\s+(\w+)"
    :handler given-handle-player-items-single}
   {:regex #"^waiting-for-input$"
    :handler given-handle-waiting-for-input-bare}
   {:regex #"(\w+)'s\s+target\s+is\s+(\S+)"
    :handler given-handle-unit-target}
   {:regex #"(\w+)\s+has\s+(?:a|an)\s+(player|computer)\s+(\w+)"
    :handler given-handle-city-unit}
   {:regex #"(\w+)\s+has\s+(?:a|an)\s+(\w+)\s+with\s+(\d+)\s+hits?\s+in\s+its\s+shipyard"
    :handler given-handle-shipyard-state}
   {:regex #"(?:the\s+)?computer\s+controls?\s+(\d+)\s+cit(?:y|ies)"
    :handler (fn [[_ n] _ctx]
               {:directive :stub
                :ir {:type :stub
                     :bindings [{:var "empire.computer.production/count-computer-cities"
                                 :value (str "(constantly " n ")")}
                                {:var "empire.computer.production.stats/count-computer-cities"
                                 :value (str "(constantly " n ")")}]}})}
   {:regex #"(?:a\s+)?valid\s+carrier\s+position\s+exists"
    :handler (fn [_ _ctx]
               {:directive :stub
                :ir {:type :stub
                     :bindings [{:var "empire.computer.ship/find-carrier-position"
                                 :value "(constantly [0 0])"}]}})}
   {:regex #"([+\w]+)\s+is\s+visible\s+to\s+computer"
    :handler (fn [[_ ref] _ctx]
               {:directive :visible-to-computer
                :ir {:type :visible-to-computer :ref ref}})}
   {:regex #"(\w+)\s+has\s+city-status\s+(\w+)"
    :handler (fn [[_ ref status] _ctx]
               (when (contains? h/city-chars ref)
                 {:directive :city-prop
                  :ir {:type :city-prop :city ref :prop :city-status :value (keyword status)}}))}
   {:regex #"territory\s+around\s+(\w+)\s+belongs\s+to\s+country\s+(\d+)"
    :handler (fn [[_ ref n] _ctx]
               {:directive :territory-around
                :ir {:type :territory-around :city ref :country-id (Integer/parseInt n)}})}
   {:regex #"(\w+)\s+belongs\s+to\s+country\s+(\d+)"
    :handler (fn [[_ ref n] _ctx]
               (let [country-id (Integer/parseInt n)]
                 (if (contains? h/city-chars ref)
                   {:directive :city-prop
                    :ir {:type :city-prop :city ref :prop :country-id :value country-id}}
                   {:directive :unit-props
                    :ir {:type :unit-props :unit ref :props {:country-id country-id}}})))}
   {:regex #"(\w+)\s+patrols\s+(?:for\s+)?country\s+(\d+)"
    :handler (fn [[_ ref n] _ctx]
               {:directive :unit-props
                :ir {:type :unit-props :unit ref :props {:country-id (Integer/parseInt n)
                                                         :patrol-mode :crawling}}})}
   {:regex #"game-over-check\s+enabled"
    :handler (fn [_ _ctx]
               {:directive :game-over-check-enabled
                :ir {:type :game-over-check-enabled}})}
   {:regex #"(?:the\s+)?game\s+is\s+paused"
    :handler (fn [_ _ctx]
               {:directive :game-paused
                :ir {:type :game-paused}})}
   {:regex #"pause\s+requested"
    :handler (fn [_ _ctx]
               {:directive :pause-requested
                :ir {:type :pause-requested}})}
   {:regex #"load\s+menu\s+is\s+open"
    :handler (fn [_ _ctx]
               {:directive :load-menu-open
                :ir {:type :load-menu-open}})}
   {:regex #"map\s+display\s+is\s+([\w-]+)"
    :handler (fn [[_ value] _ctx]
               {:directive :map-display-setup
                :ir {:type :map-display-setup :value (keyword value)}})}])

(defn parse-given-line [line context]
  (let [clean (str/trim line)
        stripped (h/strip-trailing-period clean)
        given-text (h/strip-keyword-prefix stripped)]
    (or (h/first-matching-pattern given-map-patterns stripped)
        (h/first-matching-pattern-with-context given-directive-patterns given-text context)
        (when-let [ir (props/parse-container-state-line line)]
          {:directive :container-state :ir ir})
        (when-let [ir (props/parse-unit-props-line line)]
          {:directive :unit-props :ir ir})
        {:directive :unrecognized
         :ir {:type :unrecognized :text clean}})))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T22:02:57.65103-05:00", :module-hash "-446563236", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 4, :hash "1133684467"} {:id "defn-/given-handle-game-map", :kind "defn-", :line 6, :end-line 6, :hash "333031082"} {:id "defn-/given-handle-player-map", :kind "defn-", :line 7, :end-line 7, :hash "1264130732"} {:id "defn-/given-handle-computer-map", :kind "defn-", :line 8, :end-line 8, :hash "1593887375"} {:id "defn-/given-handle-waiting-for-input", :kind "defn-", :line 10, :end-line 13, :hash "2121736818"} {:id "defn-/given-handle-production-with-rounds", :kind "defn-", :line 15, :end-line 17, :hash "408939428"} {:id "defn-/given-handle-production", :kind "defn-", :line 19, :end-line 21, :hash "-1518920973"} {:id "defn-/given-handle-no-production", :kind "defn-", :line 23, :end-line 24, :hash "-1568396292"} {:id "defn-/given-handle-round", :kind "defn-", :line 26, :end-line 27, :hash "210359631"} {:id "defn-/given-handle-destination", :kind "defn-", :line 29, :end-line 31, :hash "1871937735"} {:id "defn-/given-handle-cell-props", :kind "defn-", :line 33, :end-line 43, :hash "-1155635992"} {:id "defn-/given-handle-player-items-multi", :kind "defn-", :line 45, :end-line 47, :hash "-258251918"} {:id "defn-/given-handle-player-items-single", :kind "defn-", :line 49, :end-line 50, :hash "399851903"} {:id "defn-/given-handle-waiting-for-input-bare", :kind "defn-", :line 52, :end-line 53, :hash "1598257538"} {:id "defn-/given-handle-unit-target", :kind "defn-", :line 55, :end-line 56, :hash "-1628898526"} {:id "defn-/given-handle-city-unit", :kind "defn-", :line 58, :end-line 60, :hash "-2145567419"} {:id "defn-/given-handle-shipyard-state", :kind "defn-", :line 62, :end-line 64, :hash "325416864"} {:id "def/given-map-patterns", :kind "def", :line 66, :end-line 74, :hash "666163464"} {:id "def/given-directive-patterns", :kind "def", :line 76, :end-line 165, :hash "568711168"} {:id "defn/parse-given-line", :kind "defn", :line 167, :end-line 178, :hash "888693943"}]}
;; clj-mutate-manifest-end

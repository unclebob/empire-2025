(ns empire.acceptance.parser.then.patterns
  (:require [empire.acceptance.parser.helpers :as h]
            [empire.acceptance.parser.then.handlers :as handlers]))

(def then-bare-patterns
  [{:regex #"^after\s+(\w+)\s+moves?\s+(\w+)\s+will\s+be\s+at\s+(\S+)"
    :handler handlers/then-handle-after-moves}
   {:regex #"^after\s+(\w+)\s+steps?\s+there\s+is\s+an?\s+(\w+)\s+at\s+\[(\d+)\s+(\d+)\]"
    :handler handlers/then-handle-after-steps-coords}
   {:regex #"^after\s+(\w+)\s+steps?\s+there\s+is\s+an?\s+(\w+)\s+at\s+(\S+)"
    :handler handlers/then-handle-after-steps-target}
   {:regex #"^(\w+)\s+(?:is\s+waiting\s+for\s+input|wakes\s+up\s+and\s+asks\s+for\s+input)"
    :handler handlers/then-handle-unit-waiting-for-input}
   {:regex #"^(\w+)\s+is\s+at\s+\[(\d+)\s+(\d+)\]\s+in\s+mode\s+([\w-]+)"
    :handler handlers/then-handle-unit-at-position-with-mode}
   {:regex #"^(\w+)\s+is\s+at\s+\[(\d+)\s+(\d+)\]"
    :handler handlers/then-handle-unit-at-coords}
   {:regex #"^(\w+)\s+is\s+at\s+(\S+)$"
    :handler handlers/then-handle-unit-at-target}
   {:regex #"eventually\s+(\w+)\s+will\s+be\s+at\s+(\S+)"
    :handler handlers/then-handle-eventually-at}
   {:regex #"there\s+is\s+no\s+(\w+)\s+on\s+the\s+map"
    :handler handlers/then-handle-unit-absent-on-map}
   {:regex #"there\s+is\s+no\s+(attention|turn|error)\s+message"
    :handler handlers/then-handle-no-message}
   {:regex #"(?:the\s+)?(attention|turn|error)\s+message\s+for\s+(\w+)\s+contains\s+:(\S+)"
    :handler handlers/then-handle-message-for-unit}
   {:regex #"(?:the\s+)?(attention|turn|error)\s+message\s+contains\s+\"([^\"]+)\""
    :handler handlers/then-handle-message-contains-literal}
   {:regex #"(?:the\s+)?(attention|turn|error)\s+message\s+contains\s+:(\S+)"
    :handler handlers/then-handle-message-contains-key}
   {:regex #"(?:the\s+)?(attention|turn|error)\s+message\s+is\s+\(fmt\s+:(\S+)\s+(.*)\)"
    :handler handlers/then-handle-message-is-format}
   {:regex #"(?:the\s+)?(attention|turn|error)\s+message\s+is\s+:(\S+)"
    :handler handlers/then-handle-message-is-key}
   {:regex #"(?:the\s+)?message\s+contains\s+\"([^\"]+)\""
    :handler handlers/then-handle-bare-message-literal}
   {:regex #"(?:the\s+)?message\s+contains\s+:(\S+)"
    :handler handlers/then-handle-bare-message-key}
   {:regex #"out-of-fuel\s+message\s+is\s+displayed"
    :handler handlers/then-handle-out-of-fuel}
   {:regex #"(?:player-map\s+cell|(?:the\s+)?player\s+can\s+see)\s+\[(\d+)\s+(\d+)\](?:\s+is\s+not\s+nil)?$"
    :handler handlers/then-handle-player-map-not-nil}
   {:regex #"(?:player-map\s+cell|(?:the\s+)?player\s+cannot\s+see)\s+\[(\d+)\s+(\d+)\](?:\s+is\s+nil)?$"
    :handler handlers/then-handle-player-map-nil}
   {:regex #"(?:computer-map\s+cell|(?:the\s+)?computer\s+can\s+see)\s+\[(\d+)\s+(\d+)\](?:\s+is\s+not\s+nil)?$"
    :handler handlers/then-handle-computer-map-not-nil}
   {:regex #"^(\w+)\s+has\s+(?:a|an)\s+(\w+)\s+with\s+(\d+)\s+hits?\s+in\s+its\s+shipyard"
    :handler handlers/then-handle-shipyard-has-ship}
   {:regex #"^(\w+)\s+has\s+no\s+ships?\s+in\s+its\s+shipyard"
    :handler handlers/then-handle-shipyard-empty}
   {:regex #"^(?:the\s+)?map\s+is\s+(\S+)"
    :handler handlers/then-handle-map-is}
   {:regex #"cell\s+\[(\d+)\s+(\d+)\]\s+has\s+(\S+)\s+(.+)"
    :handler handlers/then-handle-cell-prop}
   {:regex #"on\s+(?:the\s+)?computer-map\s+cell\s+\[(\d+)\s+(\d+)\]\s+is\s+a\s+(player|computer)\s+city"
    :handler (fn [[_ x y status]]
               {:type :cell-prop :coords [(Integer/parseInt x) (Integer/parseInt y)]
                :property :city-status :expected (keyword status) :target :computer-map})}
   {:regex #"cell\s+\[(\d+)\s+(\d+)\]\s+is\s+a\s+(player|computer)\s+city"
    :handler (fn [[_ x y status]]
               {:type :cell-prop :coords [(Integer/parseInt x) (Integer/parseInt y)]
                :property :city-status :expected (keyword status)})}
   {:regex #"cell\s+\[(\d+)\s+(\d+)\]\s+is\s+(?:a\s+)?(\w+)"
    :handler handlers/then-handle-cell-type}
   {:regex #"(?:^waiting-for-input$|(?:the\s+)?game\s+is\s+waiting\s+for\s+input)"
    :handler handlers/then-handle-waiting-for-input}
   {:regex #"(?:^not\s+waiting-for-input$|(?:the\s+)?game\s+is\s+not\s+waiting\s+for\s+input)"
    :handler handlers/then-handle-not-waiting-for-input}
   {:regex #"game\s+is\s+not\s+paused"
    :handler (fn [_] {:type :game-not-paused})}
   {:regex #"game\s+is\s+paused"
    :handler handlers/then-handle-game-paused}
   {:regex #"map\s+display\s+is\s+(\w[\w-]*)"
    :handler (fn [[_ v]] {:type :map-display :expected (keyword v)})}
   {:regex #"load\s+menu\s+is\s+not\s+open"
    :handler (fn [_] {:type :load-menu-state :expected false})}
   {:regex #"load\s+menu\s+is\s+open"
    :handler (fn [_] {:type :load-menu-state :expected true})}
   {:regex #"round\s+is\s+(\d+)"
    :handler handlers/then-handle-round}
   {:regex #"destination\s+is\s+\[(\d+)\s+(\d+)\]"
    :handler handlers/then-handle-destination}
   {:regex #"production\s+at\s+(\w+)\s+is\s+([\w-]+)\s+with\s+(\d+)\s+rounds?\s+remaining"
    :handler handlers/then-handle-production-with-rounds}
   {:regex #"production\s+at\s+(\w+)\s+is\s+not\s+([\w-]+)"
    :handler handlers/then-handle-production-not}
   {:regex #"production\s+at\s+(\w+)\s+is\s+([\w-]+)"
    :handler handlers/then-handle-production}
   {:regex #"(?:there\s+is\s+)?no\s+production\s+at\s+(\w+)"
    :handler handlers/then-handle-no-production}
   {:regex #"no\s+unit\s+at\s+\[(\d+)\s+(\d+)\]"
    :handler handlers/then-handle-no-unit-at}
   {:regex #"there\s+are\s+(\d+)\s+computer\s+armies\s+on\s+the\s+map"
    :handler (fn [[_ n]]
               {:type :computer-army-count :expected (Integer/parseInt n)})}])

(def then-timed-patterns
  [{:regex #"^(\w+)\s+will\s+be\s+at\s+(\S+)$"
    :handler handlers/then-handle-will-be-at}
   {:regex #"^(\w+)\s+occupies\s+the\s+(\w+)\s+cell"
    :handler handlers/then-handle-occupies-cell}
   {:regex #"^(\w+)\s+remains\s+unmoved"
    :handler handlers/then-handle-remains-unmoved}
   {:regex #"^(\w+)\s+has\s+one\s+fighter\s+in\s+its\s+airport"
    :handler handlers/then-handle-airport-fighter}
   {:regex #"^(\w+)\s+has\s+one\s+fighter\s+aboard"
    :handler handlers/then-handle-fighter-aboard}
   {:regex #"^(\w+)\s+has\s+no\s+fighters"
    :handler handlers/then-handle-no-fighters}
   {:regex #"^(\w+)\s+has\s+(\w+)\s+awake\s+fighters?"
    :handler handlers/then-handle-awake-fighters}
   {:regex #"there\s+is\s+no\s+(\w+)$"
    :handler handlers/then-handle-unit-absent-short}
   {:regex #"there\s+is\s+an?\s+(\w+)\s+at\s+\[(\d+)\s+(\d+)\]"
    :handler handlers/then-handle-unit-present-coords}
   {:regex #"there\s+is\s+an?\s+(\w+)\s+at\s+(\S+)"
    :handler handlers/then-handle-unit-present-target}
   {:regex #"^(\w+)\s+has\s+no\s+mission$"
    :handler (fn [[_ unit]]
               {:type :unit-prop-absent :unit unit :property :transport-mission})}
   {:regex #"^(\w+)\s+has\s+(\w+)\s+(?:army|armies)$"
    :handler (fn [[_ unit n]]
               (when-let [cnt (h/parse-count n)]
                 {:type :unit-prop :unit unit :property :army-count :expected cnt}))}
   {:regex #"^(\w+)\s+has\s+(\d+)\s+turns?\s+remaining$"
    :handler (fn [[_ unit n]]
               {:type :unit-prop :unit unit :property :turns-remaining :expected (Integer/parseInt n)})}
   {:regex #"^(\w+)\s+has\s+mission\s+(\w+)$"
    :handler handlers/then-handle-unit-has-mission}
   {:regex #"^(\w+)\s+has\s+refueling\s+position\s+near\s+(\S+)$"
    :handler handlers/then-handle-refueling-position-near}
   {:regex #"^(\w+)\s+(?:has\s+no|does\s+not\s+have)\s+([\w-]+)$"
    :handler handlers/then-handle-unit-prop-absent}
   {:regex #"^(\w+)\s+has\s+(\w[\w-]*)\s+(.+)$"
    :handler handlers/then-handle-unit-has-prop}
   {:regex #"^(\w+)\s+(?:has\s+mode|is)\s+([\w-]+)$"
    :handler handlers/then-handle-unit-is-mode}])

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T11:57:00.096568-05:00", :module-hash "-1469556091", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 3, :hash "-1218231702"} {:id "def/then-bare-patterns", :kind "def", :line 5, :end-line 96, :hash "838431004"} {:id "def/then-timed-patterns", :kind "def", :line 98, :end-line 138, :hash "-973123473"}]}
;; clj-mutate-manifest-end

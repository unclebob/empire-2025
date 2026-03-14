(ns empire.acceptance.parser.given.props
  (:require [clojure.string :as str]
            [clojure.edn :as edn]
            [empire.acceptance.parser.helpers :as h]))

(def unit-prop-extractors
  [{:regex #"(?:is|has mode|mode)\s+([\w-]+)"
    :extract-fn (fn [[_ mode]] {:props {:mode (keyword mode)}})}
   {:regex #"(?:has\s+)?fuel\s+(\d+)"
    :extract-fn (fn [[_ n]] {:props {:fuel (Integer/parseInt n)}})}
   {:regex #"with\s+fuel\s+(\d+)"
    :extract-fn (fn [[_ n]] {:props {:fuel (Integer/parseInt n)}})}
   {:regex #"army-count\s+(\d+)"
    :extract-fn (fn [[_ n]] {:props {:army-count (Integer/parseInt n)}})}
   {:regex #"(\w+)\s+(?:army|armies)"
    :extract-fn (fn [[_ n]]
                  (when-let [cnt (h/parse-count n)]
                    {:props {:army-count cnt}}))}
   {:regex #"hits\s+(\d+)"
    :extract-fn (fn [[_ n]] {:props {:hits (Integer/parseInt n)}})}
   {:regex #"fighter-count\s+(\d+)"
    :extract-fn (fn [[_ n]] {:container-props {:fighter-count (Integer/parseInt n)}})}
   {:regex #"awake-fighters\s+(\d+)"
    :extract-fn (fn [[_ n]] {:container-props {:awake-fighters (Integer/parseInt n)}})}
   {:regex #"(\w+)\s+fighters?\b(?!\s+in)"
    :extract-fn (fn [[_ n]]
                  (when-let [cnt (h/parse-count n)]
                    {:container-props {:fighter-count cnt}}))}
   {:regex #"no\s+awake\s+fighters?"
    :extract-fn (fn [_] {:container-props {:awake-fighters 0}})}
   {:regex #"(\w+)\s+awake\s+fighters?"
    :extract-fn (fn [[_ n]]
                  (when (not= n "no")
                    (when-let [cnt (h/parse-count n)]
                      {:container-props {:awake-fighters cnt}})))}
   {:regex #"(?:with|has)\s+mission\s+(\w+)"
    :extract-fn (fn [[_ v]]
                  {:props {:transport-mission (keyword v)}})}
   {:regex #"(?:with|has)\s+(?:an?\s+)?escort\s+destroyer"
    :extract-fn (fn [_] {:props {:escort-destroyer-id 1}})}
   {:regex #"(?:with|has)\s+heading\s+(\d+)"
    :extract-fn (fn [[_ n]] {:props {:heading (Integer/parseInt n)}})}
   {:regex #"(?:with|has)\s+([\w-]*path)\s+(\[.*\])"
    :extract-fn (fn [[_ k v]]
                  {:props {(keyword k) (edn/read-string v)}})}
   {:regex #"(?:with|has)\s+([\w][\w-]*)\s+\[(\d+)\s+(\d+)\]"
    :extract-fn (fn [[_ k x y]]
                  {:props {(keyword k) [(Integer/parseInt x) (Integer/parseInt y)]}})}
   {:regex #"(?:with|has)\s+([\w]+-[\w-]+)\s+([^\[\s]+)"
    :extract-fn (fn [[_ k v]]
                  (when-not (#{"army-count" "fighter-count" "awake-fighters"} k)
                    {:props {(keyword k) (or (h/parse-number v)
                                             (case v "true" true "false" false nil)
                                             (keyword v))}}))}])

(defn parse-unit-props-line [line]
  (let [clean (h/strip-trailing-period (str/trim line))
        clean (h/strip-keyword-prefix clean)]
    (when-let [[_ unit rest-str] (re-matches #"(\w+)\s+(.*)" clean)]
      (when (h/city-or-unit-char? unit)
        (let [{:keys [props container-props]}
              (reduce (fn [acc {:keys [regex extract-fn]}]
                        (if-let [match (re-find regex rest-str)]
                          (if-let [extracted (extract-fn match)]
                            (merge-with merge acc extracted)
                            acc)
                          acc))
                      {:props {} :container-props {}}
                      unit-prop-extractors)
              result {:type :unit-props :unit unit :props props}]
          (when (or (seq props) (seq container-props))
            (if (seq container-props)
              (assoc result :container-props container-props)
              result)))))))

(defn parse-container-state-line [line]
  (let [clean (h/strip-trailing-period (str/trim line))
        clean (h/strip-keyword-prefix clean)]
    (cond
      (re-find #"(\w+)\s+has\s+one\s+fighter\s+in\s+its\s+airport" clean)
      (let [[_ target] (re-find #"(\w+)\s+has\s+one\s+fighter" clean)]
        {:type :container-state :target target :props {:fighter-count 1 :awake-fighters 1}})

      (re-find #"(\w+)\s+has\s+no\s+fighters" clean)
      (let [[_ target] (re-find #"(\w+)\s+has\s+no\s+fighters" clean)]
        {:type :container-state :target target :props {:fighter-count 0}})

      (re-find #"(\w+)\s+has\s+(\w+)\s+fighters?" clean)
      (let [[_ target n] (re-find #"(\w+)\s+has\s+(\w+)\s+fighters?" clean)]
        (when-let [count (h/parse-count n)]
          {:type :container-state :target target :props {:fighter-count count}}))

      :else nil)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-13T21:29:23.795996-05:00", :module-hash "-2037724323", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 4, :hash "1223534466"} {:id "def/unit-prop-extractors", :kind "def", :line 6, :end-line 54, :hash "-1185448182"} {:id "defn/parse-unit-props-line", :kind "defn", :line 56, :end-line 74, :hash "-1551467611"} {:id "defn/parse-container-state-line", :kind "defn", :line 76, :end-line 93, :hash "-2029154966"}]}
;; clj-mutate-manifest-end

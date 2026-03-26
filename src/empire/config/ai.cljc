(ns empire.config.ai)

(def armies-before-transport 6)
(def max-patrol-boats-per-country 4)
(def max-patrol-boats 6)
(def max-transports 10)
(def carrier-city-threshold 10)
(def max-live-carriers 8)
(def max-carrier-producers 2)
(def satellite-city-threshold 15)
(def max-satellites 1)
(def advances-per-frame 10)

(defn opening-exploration-profile
  [coastal-count]
  (if (< coastal-count 3)
    {:coast-walk-limit 3
     :random-explore-chance 1/5}
    {:coast-walk-limit 1
     :random-explore-chance 1/2}))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T11:59:14.628321-05:00", :module-hash "-1390043323", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "-19696421"} {:id "def/armies-before-transport", :kind "def", :line 3, :end-line 3, :hash "72086913"} {:id "def/max-patrol-boats-per-country", :kind "def", :line 4, :end-line 4, :hash "-60231693"} {:id "def/carrier-city-threshold", :kind "def", :line 5, :end-line 5, :hash "837000797"} {:id "def/max-live-carriers", :kind "def", :line 6, :end-line 6, :hash "-1956493915"} {:id "def/max-carrier-producers", :kind "def", :line 7, :end-line 7, :hash "1653600368"} {:id "def/satellite-city-threshold", :kind "def", :line 8, :end-line 8, :hash "862342228"} {:id "def/max-satellites", :kind "def", :line 9, :end-line 9, :hash "-616509960"} {:id "def/advances-per-frame", :kind "def", :line 10, :end-line 10, :hash "-2047415898"}]}
;; clj-mutate-manifest-end

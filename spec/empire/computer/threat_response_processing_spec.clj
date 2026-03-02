(ns empire.computer.threat-response-processing-spec
  (:require [speclj.core :refer :all]
            [empire.computer.threat-response.processing :as processing]
            [empire.computer.fighter-movement :as fm]))

(defn- fighter-world
  []
  [[{:type :sea
     :contents {:type :fighter
                :owner :computer
                :threat-mission :fighter-sweep
                :fuel 8}}]])

(describe "process-fighter-threat (processing)"
  (it "returns nil when unit is not on fighter-sweep mission"
    (let [calls (atom 0)
          ctx {:current-world fighter-world}]
      (with-redefs [processing/fighter-step-threat (fn [& _] (swap! calls inc) nil)]
        (should-be-nil (processing/process-fighter-threat ctx [0 0] {:threat-mission :none}))
        (should= 0 @calls))))

  (it "iterates until fighter-step-threat returns nil"
    (let [calls (atom [])
          ctx {:current-world fighter-world}]
      (with-redefs [processing/fighter-step-threat
                    (fn [_ pos unit]
                      (swap! calls conj {:pos pos :unit unit})
                      (if (= 1 (count @calls))
                        {:pos [0 0] :steps-used 2}
                        nil))
                    fm/fighter-speed 4]
        (should (processing/process-fighter-threat ctx [0 0] {:threat-mission :fighter-sweep}))
        (should= 2 (count @calls))
        (should= [0 0] (:pos (first @calls)))
        (should= :fighter (get-in (first @calls) [:unit :type])))))

  (it "stops when remaining speed is consumed"
    (let [calls (atom 0)
          ctx {:current-world fighter-world}]
      (with-redefs [processing/fighter-step-threat (fn [& _] (swap! calls inc) {:pos [0 0] :steps-used 3})
                    fm/fighter-speed 3]
        (should (processing/process-fighter-threat ctx [0 0] {:threat-mission :fighter-sweep}))
        (should= 1 @calls))))

  (it "returns true when sweep mission is active even if no step executes"
    (let [ctx {:current-world fighter-world}]
      (with-redefs [processing/fighter-step-threat (fn [& _] nil)
                    fm/fighter-speed 2]
        (should (processing/process-fighter-threat ctx [0 0] {:threat-mission :fighter-sweep}))))))

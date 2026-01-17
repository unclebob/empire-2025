(ns empire.test-utils)

(defn- char->cell [c]
  (case c
    \s {:type :sea}
    \L {:type :land}
    \. nil
    \+ {:type :city :city-status :free}
    \O {:type :city :city-status :player}
    \X {:type :city :city-status :computer}
    \* {:type :land :waypoint true}
    \A {:type :land :contents {:type :army :owner :player}}
    \T {:type :sea :contents {:type :transport :owner :player}}
    \D {:type :sea :contents {:type :destroyer :owner :player}}
    \P {:type :sea :contents {:type :patrol-boat :owner :player}}
    \C {:type :sea :contents {:type :carrier :owner :player}}
    \B {:type :sea :contents {:type :battleship :owner :player}}
    \S {:type :sea :contents {:type :submarine :owner :player}}
    \F {:type :land :contents {:type :fighter :owner :player}}
    \J {:type :sea :contents {:type :fighter :owner :player}}
    (throw (ex-info (str "Unknown map char: " c) {:char c}))))

(defn build-test-map [strings]
  (atom (mapv (fn [row-str]
                (mapv char->cell row-str))
              strings)))

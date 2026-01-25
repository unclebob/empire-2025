(ns empire.fsm.engine-spec
  (:require [speclj.core :refer :all]
            [empire.fsm.engine :as engine]))

(describe "Event Queue"

  (describe "post-event"
    (it "adds event to empty queue"
      (let [entity {:event-queue []}
            event {:type :test :priority :normal :data {} :from :unit-1}
            result (engine/post-event entity event)]
        (should= 1 (count (:event-queue result)))
        (should= event (first (:event-queue result)))))

    (it "adds multiple events maintaining insertion order for same priority"
      (let [entity {:event-queue []}
            event1 {:type :first :priority :normal :data {} :from :unit-1}
            event2 {:type :second :priority :normal :data {} :from :unit-2}
            result (-> entity
                       (engine/post-event event1)
                       (engine/post-event event2))]
        (should= 2 (count (:event-queue result)))
        (should= :first (:type (first (:event-queue result))))
        (should= :second (:type (second (:event-queue result))))))

    (it "inserts high priority before normal priority"
      (let [entity {:event-queue []}
            normal-event {:type :normal-evt :priority :normal :data {} :from :unit-1}
            high-event {:type :high-evt :priority :high :data {} :from :unit-2}
            result (-> entity
                       (engine/post-event normal-event)
                       (engine/post-event high-event))]
        (should= :high-evt (:type (first (:event-queue result))))
        (should= :normal-evt (:type (second (:event-queue result))))))

    (it "inserts normal priority before low priority"
      (let [entity {:event-queue []}
            low-event {:type :low-evt :priority :low :data {} :from :unit-1}
            normal-event {:type :normal-evt :priority :normal :data {} :from :unit-2}
            result (-> entity
                       (engine/post-event low-event)
                       (engine/post-event normal-event))]
        (should= :normal-evt (:type (first (:event-queue result))))
        (should= :low-evt (:type (second (:event-queue result))))))

    (it "maintains FIFO within same priority level"
      (let [entity {:event-queue []}
            high1 {:type :high-1 :priority :high :data {} :from :unit-1}
            high2 {:type :high-2 :priority :high :data {} :from :unit-2}
            high3 {:type :high-3 :priority :high :data {} :from :unit-3}
            result (-> entity
                       (engine/post-event high1)
                       (engine/post-event high2)
                       (engine/post-event high3))]
        (should= [:high-1 :high-2 :high-3]
                 (mapv :type (:event-queue result)))))

    (it "correctly orders mixed priorities"
      (let [entity {:event-queue []}
            events [{:type :low-1 :priority :low :data {} :from :a}
                    {:type :high-1 :priority :high :data {} :from :b}
                    {:type :normal-1 :priority :normal :data {} :from :c}
                    {:type :high-2 :priority :high :data {} :from :d}
                    {:type :low-2 :priority :low :data {} :from :e}
                    {:type :normal-2 :priority :normal :data {} :from :f}]
            result (reduce engine/post-event entity events)]
        (should= [:high-1 :high-2 :normal-1 :normal-2 :low-1 :low-2]
                 (mapv :type (:event-queue result))))))

  (describe "pop-event"
    (it "returns nil and unchanged entity for empty queue"
      (let [entity {:event-queue []}
            [event updated] (engine/pop-event entity)]
        (should-be-nil event)
        (should= [] (:event-queue updated))))

    (it "returns first event and removes it from queue"
      (let [event1 {:type :first :priority :high :data {} :from :unit-1}
            event2 {:type :second :priority :normal :data {} :from :unit-2}
            entity {:event-queue [event1 event2]}
            [event updated] (engine/pop-event entity)]
        (should= event1 event)
        (should= 1 (count (:event-queue updated)))
        (should= event2 (first (:event-queue updated)))))

    (it "returns highest priority event first"
      (let [high-event {:type :high-evt :priority :high :data {} :from :unit-1}
            normal-event {:type :normal-evt :priority :normal :data {} :from :unit-2}
            entity (-> {:event-queue []}
                       (engine/post-event normal-event)
                       (engine/post-event high-event))
            [event _] (engine/pop-event entity)]
        (should= :high-evt (:type event)))))

  (describe "peek-events"
    (it "returns empty vector for empty queue"
      (let [entity {:event-queue []}]
        (should= [] (engine/peek-events entity))))

    (it "returns queue without modifying entity"
      (let [event1 {:type :first :priority :high :data {} :from :unit-1}
            event2 {:type :second :priority :normal :data {} :from :unit-2}
            entity {:event-queue [event1 event2]}
            result (engine/peek-events entity)]
        (should= [event1 event2] result)
        (should= [event1 event2] (:event-queue entity))))

    (it "returns events in priority order"
      (let [entity (-> {:event-queue []}
                       (engine/post-event {:type :low :priority :low :data {} :from :a})
                       (engine/post-event {:type :high :priority :high :data {} :from :b})
                       (engine/post-event {:type :normal :priority :normal :data {} :from :c}))]
        (should= [:high :normal :low]
                 (mapv :type (engine/peek-events entity)))))))

(describe "FSM Execution"

  (describe "terminal?"
    (it "returns false for keyword state"
      (let [entity {:fsm-state :exploring}]
        (should-not (engine/terminal? entity))))

    (it "returns true for vector state starting with :terminal"
      (let [entity {:fsm-state [:terminal :mission-complete]}]
        (should (engine/terminal? entity))))

    (it "returns false for other vector states"
      (let [entity {:fsm-state [:some :other :state]}]
        (should-not (engine/terminal? entity))))

    (it "returns false for nil state"
      (let [entity {:fsm-state nil}]
        (should-not (engine/terminal? entity)))))

  (describe "find-matching-transition"
    (it "returns nil when no transitions match current state"
      (let [fsm [[:state-a (constantly true) :state-b identity]]
            result (engine/find-matching-transition fsm :state-x {})]
        (should-be-nil result)))

    (it "returns nil when guard returns false"
      (let [fsm [[:state-a (constantly false) :state-b identity]]
            result (engine/find-matching-transition fsm :state-a {})]
        (should-be-nil result)))

    (it "returns transition when state matches and guard returns true"
      (let [transition [:state-a (constantly true) :state-b identity]
            fsm [transition]
            result (engine/find-matching-transition fsm :state-a {})]
        (should= transition result)))

    (it "returns first matching transition when multiple match"
      (let [transition1 [:state-a (constantly true) :state-b identity]
            transition2 [:state-a (constantly true) :state-c identity]
            fsm [transition1 transition2]
            result (engine/find-matching-transition fsm :state-a {})]
        (should= transition1 result)))

    (it "skips non-matching guards and finds later match"
      (let [transition1 [:state-a (constantly false) :state-b identity]
            transition2 [:state-a (constantly true) :state-c identity]
            fsm [transition1 transition2]
            result (engine/find-matching-transition fsm :state-a {})]
        (should= transition2 result)))

    (it "passes context to guard function"
      (let [guard-called (atom nil)
            guard-fn (fn [ctx] (reset! guard-called ctx) true)
            fsm [[:state-a guard-fn :state-b identity]]
            context {:some :data}]
        (engine/find-matching-transition fsm :state-a context)
        (should= {:some :data} @guard-called))))

  (describe "step"
    (it "returns unchanged entity when no transition matches"
      (let [fsm [[:other-state (constantly true) :state-b identity]]
            entity {:fsm fsm :fsm-state :state-a :fsm-data {} :event-queue []}
            result (engine/step entity {})]
        (should= :state-a (:fsm-state result))))

    (it "returns unchanged entity when already terminal"
      (let [fsm [[:state-a (constantly true) :state-b identity]]
            entity {:fsm fsm :fsm-state [:terminal :done] :fsm-data {} :event-queue []}
            result (engine/step entity {})]
        (should= [:terminal :done] (:fsm-state result))))

    (it "transitions to new state when guard passes"
      (let [fsm [[:state-a (constantly true) :state-b (constantly nil)]]
            entity {:fsm fsm :fsm-state :state-a :fsm-data {} :event-queue []}
            result (engine/step entity {})]
        (should= :state-b (:fsm-state result))))

    (it "executes action and merges returned data into fsm-data"
      (let [action-fn (fn [_] {:counter 42 :flag true})
            fsm [[:state-a (constantly true) :state-b action-fn]]
            entity {:fsm fsm :fsm-state :state-a :fsm-data {:existing :value} :event-queue []}
            result (engine/step entity {})]
        (should= :state-b (:fsm-state result))
        (should= {:existing :value :counter 42 :flag true} (:fsm-data result))))

    (it "preserves fsm-data when action returns nil"
      (let [fsm [[:state-a (constantly true) :state-b (constantly nil)]]
            entity {:fsm fsm :fsm-state :state-a :fsm-data {:keep :this} :event-queue []}
            result (engine/step entity {})]
        (should= {:keep :this} (:fsm-data result))))

    (it "passes context to action function"
      (let [action-called (atom nil)
            action-fn (fn [ctx] (reset! action-called ctx) nil)
            fsm [[:state-a (constantly true) :state-b action-fn]]
            entity {:fsm fsm :fsm-state :state-a :fsm-data {} :event-queue []}
            context {:game-map :test}]
        (engine/step entity context)
        (should= {:game-map :test} @action-called)))

    (it "can transition to terminal state"
      (let [fsm [[:state-a (constantly true) [:terminal :complete] (constantly nil)]]
            entity {:fsm fsm :fsm-state :state-a :fsm-data {} :event-queue []}
            result (engine/step entity {})]
        (should= [:terminal :complete] (:fsm-state result))
        (should (engine/terminal? result))))))

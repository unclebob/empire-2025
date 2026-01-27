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

(describe "State-Grouped FSM Format"

  (describe "parse-state-group"
    (it "parses simple state header (keyword)"
      (let [group [:state-a
                   [(constantly true) :state-b identity]
                   [(constantly false) :state-c identity]]
            [state super-state config transitions] (engine/parse-state-group group)]
        (should= :state-a state)
        (should-be-nil super-state)
        (should-be-nil config)
        (should= 2 (count transitions))))

    (it "parses 2-tuple state header [sub-state super-state]"
      (let [group [[:seeking-coast :exploring]
                   [(constantly true) :following-coast identity]]
            [state super-state config transitions] (engine/parse-state-group group)]
        (should= :seeking-coast state)
        (should= :exploring super-state)
        (should-be-nil config)
        (should= 1 (count transitions))))

    (it "parses super-state with config map"
      (let [entry-fn (fn [_] {:entered true})
            exit-fn (fn [_] {:exited true})
            group [:exploring
                   {:entry entry-fn :exit exit-fn}
                   [(constantly true) :state-b identity]]
            [state super-state config transitions] (engine/parse-state-group group)]
        (should= :exploring state)
        (should-be-nil super-state)
        (should= entry-fn (:entry config))
        (should= exit-fn (:exit config))
        (should= 1 (count transitions)))))

  (describe "build-transition-index"
    (it "builds index from state-grouped FSM"
      (let [fsm [[:state-a
                  [(constantly true) :state-b identity]]
                 [:state-b
                  [(constantly true) :state-c identity]]]
            index (engine/build-transition-index fsm)]
        (should-contain :state-a index)
        (should-contain :state-b index)
        (should= 1 (count (:transitions (get index :state-a))))
        (should-be-nil (:super-state (get index :state-a)))))

    (it "includes super-state in index when specified"
      (let [fsm [[[:seeking-coast :exploring]
                  [(constantly true) :following-coast identity]]
                 [[:following-coast :exploring]
                  [(constantly true) :skirting-city identity]]]
            index (engine/build-transition-index fsm)]
        (should= :exploring (:super-state (get index :seeking-coast)))
        (should= :exploring (:super-state (get index :following-coast))))))

  (describe "get-super-state"
    (it "returns nil when FSM is flat"
      (let [fsm [[:state-a [(constantly true) :state-b identity]]]
            index (engine/build-transition-index fsm)]
        (should-be-nil (engine/get-super-state index :state-a))))

    (it "returns super-state when present"
      (let [fsm [[[:sub-state :super-state]
                  [(constantly true) :other identity]]]
            index (engine/build-transition-index fsm)]
        (should= :super-state (engine/get-super-state index :sub-state)))))

  (describe "find-matching-transition-grouped"
    (it "finds transition in grouped FSM"
      (let [fsm [[:state-a
                  [(constantly true) :state-b identity]]]
            index (engine/build-transition-index fsm)
            result (engine/find-matching-transition-grouped index :state-a {})]
        (should-not-be-nil result)
        (should= :state-b (second result))))

    (it "returns nil when no match"
      (let [fsm [[:state-a
                  [(constantly false) :state-b identity]]]
            index (engine/build-transition-index fsm)
            result (engine/find-matching-transition-grouped index :state-a {})]
        (should-be-nil result)))

    (it "passes context to guard"
      (let [guard-called (atom nil)
            guard (fn [ctx] (reset! guard-called ctx) true)
            fsm [[:state-a [guard :state-b identity]]]
            index (engine/build-transition-index fsm)]
        (engine/find-matching-transition-grouped index :state-a {:test :data})
        (should= {:test :data} @guard-called))))

  (describe "step with grouped FSM"
    (it "transitions using grouped format"
      (let [fsm [[:state-a
                  [(constantly true) :state-b (constantly {:data 1})]]]
            entity {:fsm fsm :fsm-state :state-a :fsm-data {} :event-queue []}
            result (engine/step entity {})]
        (should= :state-b (:fsm-state result))
        (should= {:data 1} (:fsm-data result))))

    (it "executes action from grouped transition"
      (let [action-called (atom false)
            action (fn [_] (reset! action-called true) {:done true})
            fsm [[:state-a
                  [(constantly true) :state-b action]]]
            entity {:fsm fsm :fsm-state :state-a :fsm-data {} :event-queue []}]
        (engine/step entity {})
        (should @action-called)))))

(describe "Super-State Inherited Transitions"

  (describe "super-state transitions"
    (it "inherits transitions from super-state"
      (let [;; Super-state :exploring has a stuck? transition
            ;; Sub-states :seeking-coast and :following-coast inherit it
            fsm [[:exploring
                  [(constantly true) [:terminal :stuck] (constantly {:reason :stuck})]]
                 [[:seeking-coast :exploring]
                  [(constantly false) :following-coast identity]]
                 [[:following-coast :exploring]
                  [(constantly false) :seeking-coast identity]]]
            entity {:fsm fsm :fsm-state :seeking-coast :fsm-data {} :event-queue []}
            result (engine/step entity {})]
        ;; Should match the inherited stuck? transition from :exploring
        (should= [:terminal :stuck] (:fsm-state result))
        (should= {:reason :stuck} (:fsm-data result))))

    (it "sub-state transitions take priority over super-state"
      (let [fsm [[:exploring
                  [(constantly true) [:terminal :stuck] (constantly {:from :super})]]
                 [[:seeking-coast :exploring]
                  [(constantly true) :following-coast (constantly {:from :sub})]]]
            entity {:fsm fsm :fsm-state :seeking-coast :fsm-data {} :event-queue []}
            result (engine/step entity {})]
        ;; Sub-state transition should win
        (should= :following-coast (:fsm-state result))
        (should= {:from :sub} (:fsm-data result))))

    (it "falls through to super-state when sub-state guards fail"
      (let [fsm [[:exploring
                  [(constantly true) [:terminal :stuck] (constantly {:from :super})]]
                 [[:seeking-coast :exploring]
                  [(constantly false) :following-coast (constantly {:from :sub})]]]
            entity {:fsm fsm :fsm-state :seeking-coast :fsm-data {} :event-queue []}
            result (engine/step entity {})]
        ;; Sub-state guard fails, falls through to super-state
        (should= [:terminal :stuck] (:fsm-state result))
        (should= {:from :super} (:fsm-data result))))

    (it "works with multiple sub-states sharing same super-state"
      (let [stuck-action (fn [_] {:terminated true})
            fsm [[:exploring
                  [(constantly true) [:terminal :stuck] stuck-action]]
                 [[:state-a :exploring]
                  [(constantly false) :state-b identity]]
                 [[:state-b :exploring]
                  [(constantly false) :state-a identity]]
                 [[:state-c :exploring]
                  [(constantly false) :state-a identity]]]
            ;; All three sub-states should inherit the stuck transition
            result-a (engine/step {:fsm fsm :fsm-state :state-a :fsm-data {} :event-queue []} {})
            result-b (engine/step {:fsm fsm :fsm-state :state-b :fsm-data {} :event-queue []} {})
            result-c (engine/step {:fsm fsm :fsm-state :state-c :fsm-data {} :event-queue []} {})]
        (should= [:terminal :stuck] (:fsm-state result-a))
        (should= [:terminal :stuck] (:fsm-state result-b))
        (should= [:terminal :stuck] (:fsm-state result-c)))))

  (describe "entry/exit actions"
    (it "executes exit action when leaving super-state"
      (let [exit-called (atom false)
            fsm [[:exploring
                  {:exit (fn [_] (reset! exit-called true) nil)}
                  [(constantly false) :exploring identity]]
                 [[:seeking-coast :exploring]
                  [(constantly true) :moving (constantly nil)]]  ;; transitions OUT of :exploring
                 [:moving
                  [(constantly false) :moving identity]]]
            entity {:fsm fsm :fsm-state :seeking-coast :fsm-data {} :event-queue []}]
        (engine/step entity {})
        (should @exit-called)))

    (it "executes entry action when entering super-state"
      (let [entry-called (atom false)
            fsm [[:exploring
                  {:entry (fn [_] (reset! entry-called true) nil)}
                  [(constantly false) :exploring identity]]
                 [[:seeking-coast :exploring]  ;; define seeking-coast as sub-state of exploring
                  [(constantly false) :seeking-coast identity]]
                 [:moving
                  [(constantly true) :seeking-coast (constantly nil)]]]  ;; transitions INTO :exploring
            entity {:fsm fsm :fsm-state :moving :fsm-data {} :event-queue []}]
        (engine/step entity {})
        (should @entry-called)))

    (it "does not execute entry/exit when staying in same super-state"
      (let [entry-called (atom false)
            exit-called (atom false)
            fsm [[:exploring
                  {:entry (fn [_] (reset! entry-called true) nil)
                   :exit (fn [_] (reset! exit-called true) nil)}
                  [(constantly false) :exploring identity]]
                 [[:seeking-coast :exploring]
                  [(constantly true) :following-coast (constantly nil)]]
                 [[:following-coast :exploring]
                  [(constantly false) :following-coast identity]]]
            entity {:fsm fsm :fsm-state :seeking-coast :fsm-data {} :event-queue []}]
        (engine/step entity {})
        (should-not @entry-called)
        (should-not @exit-called)))

    (it "merges entry action result into fsm-data"
      (let [fsm [[:exploring
                  {:entry (fn [_] {:entered-at 42})}
                  [(constantly false) :exploring identity]]
                 [[:seeking-coast :exploring]  ;; define seeking-coast as sub-state of exploring
                  [(constantly false) :seeking-coast identity]]
                 [:moving
                  [(constantly true) :seeking-coast (constantly {:moved true})]]]
            entity {:fsm fsm :fsm-state :moving :fsm-data {} :event-queue []}
            result (engine/step entity {})]
        (should= :seeking-coast (:fsm-state result))
        (should= {:moved true :entered-at 42} (:fsm-data result))))))

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

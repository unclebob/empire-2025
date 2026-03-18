(ns empire.state.api-spec
  (:require [speclj.core :refer :all]
            [empire.state.api :as sa]
            [empire.test.utils :as tu]))

(defn- frame
  [class-name method-name file-name line-number]
  (StackTraceElement. class-name method-name file-name line-number))

(describe "state-access"
  (before (tu/reset-all-atoms!))

  (context "current-world"
    (it "returns the game map"
      (tu/set-test-world! (tu/build-test-map ["~"]))
      (should= (tu/read-test-world) (sa/current-world))))

  (context "read-state / write-state!"
    (it "reads and writes runtime state"
      (sa/write-state! :round-number 42)
      (should= 42 (sa/read-state :round-number))))

  (context "update-state!"
    (it "applies f to current value"
      (sa/write-state! :round-number 10)
      (sa/update-state! :round-number inc)
      (should= 11 (sa/read-state :round-number))))

  (context "update-world!"
    (it "applies f to the world and saves"
      (tu/set-test-world! (tu/build-test-map ["~"]))
      (sa/update-world! assoc-in [0 0 :test-key] :test-val)
      (should= :test-val (get-in (sa/current-world) [0 0 :test-key]))))

  (context "AI game-map access instrumentation"
    (before
      (@#'sa/clear-ai-game-map-access-violations!)
      (sa/write-state! :integrity-check-enabled true))

    (it "logs when current-world is called from the computer component"
      (tu/set-test-world! (tu/build-test-map ["~"]))
      (let [logged (atom nil)
            frames [(frame "empire.state.api" "current_world" "api.cljc" 70)
                    (frame "empire.computer.transport" "process_transport" "transport.cljc" 12)
                    (frame "clojure.lang.RestFn" "invoke" "RestFn.java" 408)]]
        (with-redefs [empire.state.api/current-stacktrace (fn [] frames)
                      empire.state.api/append-ai-game-map-violation-log!
                      (fn [violation _frames]
                        (reset! logged violation))]
          (sa/current-world))
        (should= {:access-kind :current-world
                  :frame "empire.computer.transport"}
                 @logged)))

    (it "logs when read-state accesses game-map from the computer component"
      (tu/set-test-world! (tu/build-test-map ["~"]))
      (let [logged (atom nil)
            frames [(frame "empire.state.api" "read_state" "api.cljc" 76)
                    (frame "empire.computer.ship_patrol" "process_patrol" "ship_patrol.cljc" 42)]]
        (with-redefs [empire.state.api/current-stacktrace (fn [] frames)
                      empire.state.api/append-ai-game-map-violation-log!
                      (fn [violation _frames]
                        (reset! logged violation))]
          (sa/read-state :game-map))
        (should= {:access-kind :read-state-game-map
                  :frame "empire.computer.ship_patrol"}
                 @logged)))

    (it "does not log for non-computer callers"
      (tu/set-test-world! (tu/build-test-map ["~"]))
      (let [logged (atom 0)
            frames [(frame "empire.state.api" "current_world" "api.cljc" 70)
                    (frame "empire.player.commands" "move_unit" "commands.cljc" 42)]]
        (with-redefs [empire.state.api/current-stacktrace (fn [] frames)
                      empire.state.api/append-ai-game-map-violation-log!
                      (fn [& _]
                        (swap! logged inc))]
          (sa/current-world))
        (should= 0 @logged)))))

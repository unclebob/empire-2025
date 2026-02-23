(ns empire.acceptance.parser.helpers-spec
  (:require [speclj.core :refer :all]
            [empire.acceptance.parser.helpers :as h]))

(describe "first-matching-pattern"
  (it "returns nil for empty patterns"
    (should-be-nil (h/first-matching-pattern [] "hello")))

  (it "returns nil when no pattern matches"
    (let [patterns [{:regex #"^foo" :handler (fn [_] :foo)}
                    {:regex #"^bar" :handler (fn [_] :bar)}]]
      (should-be-nil (h/first-matching-pattern patterns "hello"))))

  (it "returns first matching handler result"
    (let [patterns [{:regex #"^hello" :handler (fn [_] :first)}
                    {:regex #"^hello" :handler (fn [_] :second)}]]
      (should= :first (h/first-matching-pattern patterns "hello world"))))

  (it "passes match to handler"
    (let [patterns [{:regex #"(\d+)\s+(\w+)" :handler (fn [[_ n w]] {:n n :w w})}]]
      (should= {:n "42" :w "things"} (h/first-matching-pattern patterns "42 things")))))

(describe "first-matching-pattern-with-context"
  (it "passes match and context to handler"
    (let [patterns [{:regex #"(\w+)" :handler (fn [[_ w] ctx] {:word w :ctx ctx})}]]
      (should= {:word "hi" :ctx {:x 1}} (h/first-matching-pattern-with-context patterns "hi" {:x 1}))))

  (it "returns nil when no pattern matches"
    (let [patterns [{:regex #"^zzz" :handler (fn [_ _] :nope)}]]
      (should-be-nil (h/first-matching-pattern-with-context patterns "hello" {})))))

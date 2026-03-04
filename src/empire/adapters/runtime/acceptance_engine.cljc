;; mutation-tested: no
(ns empire.adapters.runtime.acceptance-engine
  "Runtime adapter functions used by the application acceptance harness."
  (:require []))

(def ^:private reset-all-atoms-fn
  (delay
    (try
      (requiring-resolve 'empire.test-utils/reset-all-atoms!)
      (catch #?(:clj Throwable :cljs :default) _
        nil))))

(defn- resolve-input-fn
  [sym]
  (or (try
        (requiring-resolve (symbol "empire.ui.util.input.dispatch" (name sym)))
        (catch #?(:clj Throwable :cljs :default) _
          nil))
      (throw (ex-info (str "Unable to resolve UI input function: " (name sym))
                      {:symbol sym}))))

(defn- resolve-game-loop-fn
  [sym]
  (or (try
        (requiring-resolve (symbol "empire.game-loop" (name sym)))
        (catch #?(:clj Throwable :cljs :default) _
          nil))
      (throw (ex-info (str "Unable to resolve game-loop function: " (name sym))
                      {:symbol sym}))))

(defn reset-runtime!
  []
  (if-let [f @reset-all-atoms-fn]
    (f)
    (throw (ex-info "Unable to resolve empire.test-utils/reset-all-atoms!" {}))))

(defn handle-input!
  [in]
  ((resolve-input-fn 'handle-key) in))

(defn start-round!
  []
  ((resolve-game-loop-fn 'start-new-round)))

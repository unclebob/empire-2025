(ns empire.ui.quil.core-spec
  (:require [empire.ui.quil.core :as quil-core]
            [empire.ui.util.core :as util-core]
            [speclj.core :refer :all]))

(describe "-main"
  (it "prints usage and returns before startup when help is requested"
    (with-redefs [util-core/help-requested? (constantly true)
                  util-core/usage-text (constantly "Usage text")
                  empire.ui.quil.core/screen-dimensions (fn [] (throw (ex-info "should not run" {})))]
      (should= "Usage text\n"
               (with-out-str
                 (quil-core/-main "--help"))))))

(ns cljs.user
  (:require
    [fulcro.inspect.helpers-spec]
    [fulcro-spec.selectors :as sel]
    [fulcro-spec.suite :as suite]))

(suite/def-test-suite on-load {:ns-regex #"fulcro.inspect\..*-spec"}
  {:default #{::sel/none :focused}
   :available #{:focused :should-fail}})

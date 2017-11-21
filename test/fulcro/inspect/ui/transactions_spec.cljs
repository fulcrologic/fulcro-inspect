(ns fulcro.inspect.ui.transactions-spec
  (:require
    [fulcro-spec.core :refer [specification behavior component assertions]]
    [fulcro.inspect.ui.transactions :as transactions]))

(defn call-mutation [env k params]
  ((:action (fulcro.client.mutations/mutate env k params))))

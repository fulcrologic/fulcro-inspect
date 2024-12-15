(ns fulcro.inspect.api.target-api
  (:require
    [com.fulcrologic.devtools.common.resolvers :refer [remote-mutations]]))

(remote-mutations
  reset-app
  run-transaction
  run-network-request)

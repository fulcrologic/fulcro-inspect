(ns fulcro.inspect.api.target-api
  (:require
    [com.fulcrologic.devtools.common.resolvers :refer [remote-mutations]]))

(remote-mutations
  reset-app
  restart-websockets
  run-transaction
  run-network-request)

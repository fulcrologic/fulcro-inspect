(ns fulcro.inspect.devcards
  (:require [devcards.core]
            [fulcro.client.logging :as log]
            [fulcro.inspect.ui.data-viewer-cards]))

(log/set-level :none)
(devcards.core/start-devcard-ui!)

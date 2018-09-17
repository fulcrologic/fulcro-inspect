(ns fulcro.inspect.workspaces.main
  (:require [nubank.workspaces.core :as ws]
            [com.wsscode.pathom.viz.trace-cards]
            [fulcro.inspect.workspaces.ui.network-cards]
            [fulcro.inspect.workspaces.ui.pathom-trace-cards]))

(ws/mount)

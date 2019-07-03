(ns fulcro.inspect.workspaces.main
  (:require [nubank.workspaces.core :as ws]
            [fulcro.inspect.workspaces.ui.index-explorer-cards]
            [fulcro.inspect.workspaces.ui.network-cards]
            [fulcro.inspect.workspaces.ui.pathom-trace-cards]
            [fulcro.inspect.workspaces.ui.transactions-cards]
            [fulcro.inspect.client.lib.diff-spec]))

(ws/mount)

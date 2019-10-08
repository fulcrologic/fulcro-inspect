(ns fulcro.inspect.workspaces.main
  (:require [fulcro.inspect.client.lib.diff-spec]
            [fulcro.inspect.workspaces.ui.data-viewer-cards]
            [fulcro.inspect.workspaces.ui.index-explorer-cards]
            [fulcro.inspect.workspaces.ui.network-cards]
            [fulcro.inspect.workspaces.ui.pathom-trace-cards]
            [fulcro.inspect.workspaces.ui.transactions-cards]
            [nubank.workspaces.core :as ws]))

(ws/mount)

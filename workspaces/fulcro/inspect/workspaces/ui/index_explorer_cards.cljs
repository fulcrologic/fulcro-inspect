(ns fulcro.inspect.workspaces.ui.index-explorer-cards
  (:require [nubank.workspaces.lib.fulcro-portal :as f.portal]
            [nubank.workspaces.card-types.fulcro :as ct.fulcro]
            [com.wsscode.pathom.fulcro.network :as f.network]
            [nubank.workspaces.core :as ws]
            [nubank.workspaces.model :as wsm]
            [fulcro.inspect.ui-parser :as ui-parser]
            [fulcro.inspect.ui.index-explorer :as fi.iex]))

(ws/defcard index-explorer-panel-card
  {::wsm/align ::wsm/stretch-flex}
  (ct.fulcro/fulcro-card
    {::f.portal/root fi.iex/IndexExplorer
     ::f.portal/app  {:networking (f.network/pathom-remote (ui-parser/parser))}}))

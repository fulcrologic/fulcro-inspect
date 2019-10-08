(ns fulcro.inspect.workspaces.ui.debounce-input-cards
  (:require [fulcro.client.localized-dom :as dom]
            [fulcro.client.primitives :as fp]
            [fulcro.inspect.ui.debounce-input :as di]
            [nubank.workspaces.card-types.fulcro :as ct.fulcro]
            [nubank.workspaces.core :as ws]
            [nubank.workspaces.lib.fulcro-portal :as f.portal]
            [nubank.workspaces.model :as wsm]
            [fulcro.client.mutations :as fm]))

(fp/defsc DataViewerDemo
  [this {::keys [text]}]
  {:pre-merge (fn [{:keys [current-normalized data-tree]}]
                (merge {::id   (random-uuid)
                        ::text ""}
                  current-normalized data-tree))
   :ident     [::id ::id]
   :query     [::id ::text]
   :css       [[:.container {:display        "flex"
                             :flex           "1"
                             :flex-direction "column"}]]}
  (dom/div :.container
    (dom/div (str text))
    (di/debounce-input {:value    text
                        :onChange #(fm/set-string! this ::text :event %)})))

(ws/defcard debounce-input-card
  {::wsm/align ::wsm/stretch-flex}
  (ct.fulcro/fulcro-card
    {::f.portal/root          DataViewerDemo
     ::f.portal/initial-state (fn [_] {::text "initial text"})}))

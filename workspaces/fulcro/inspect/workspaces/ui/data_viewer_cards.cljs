(ns fulcro.inspect.workspaces.ui.data-viewer-cards
  (:require [fulcro.client.localized-dom :as dom]
            [fulcro.client.primitives :as fp]
            [fulcro.inspect.ui.data-viewer :as dv]
            [nubank.workspaces.card-types.fulcro :as ct.fulcro]
            [nubank.workspaces.core :as ws]
            [nubank.workspaces.lib.fulcro-portal :as f.portal]
            [nubank.workspaces.model :as wsm]))

(fp/defsc DataViewerDemo
  [this {::keys [viewer]}]
  {:pre-merge   (fn [{:keys [current-normalized data-tree]}]
                  (merge {::id     (random-uuid)
                          ::viewer {}}
                    current-normalized data-tree))
   :ident       [::id ::id]
   :query       [::id
                 {::viewer (fp/get-query dv/DataViewer)}]
   :css         [[:.container {:display        "flex"
                               :flex           "1"
                               :flex-direction "column"}]]
   :css-include [dv/DataViewer]}
  (dom/div :.container
    (dom/div)
    (dv/data-viewer viewer)))

(def sample-data
  '{:nubank.workspaces.ui/workspace-root
                                  {"singleton"
                                   {:nubank.workspaces.ui/cards
                                                                           [[:nubank.workspaces.model/card-id
                                                                             fulcro.inspect.client.lib.diff-spec/test-updates]
                                                                            [:nubank.workspaces.model/card-id
                                                                             fulcro.inspect.workspaces.ui.network-cards/network-card]
                                                                            [:nubank.workspaces.model/card-id
                                                                             fulcro.inspect.workspaces.ui.network-cards/network-sampler]
                                                                            [:nubank.workspaces.model/card-id
                                                                             fulcro.inspect.workspaces.ui.network-cards/network-sampler-remote-i]
                                                                            [:nubank.workspaces.model/card-id
                                                                             fulcro.inspect.workspaces.ui.transactions-cards/transactions-demo-card]
                                                                            [:nubank.workspaces.model/card-id
                                                                             nubank.workspaces.card-types.test/test-all]
                                                                            [:nubank.workspaces.model/card-id
                                                                             fulcro.inspect.workspaces.ui.network-cards/multi-network]
                                                                            [:nubank.workspaces.model/card-id
                                                                             fulcro.inspect.client.lib.diff-spec]
                                                                            [:nubank.workspaces.model/card-id
                                                                             fulcro.inspect.workspaces.ui.data-viewer-cards/data-viewer-card]
                                                                            [:nubank.workspaces.model/card-id
                                                                             fulcro.inspect.workspaces.ui.index-explorer-cards/index-explorer-panel-card]
                                                                            [:nubank.workspaces.model/card-id
                                                                             fulcro.inspect.client.lib.diff-spec/test-patch-data]
                                                                            [:nubank.workspaces.model/card-id
                                                                             fulcro.inspect.client.lib.diff-spec/test-removals]
                                                                            [:nubank.workspaces.model/card-id
                                                                             fulcro.inspect.workspaces.ui.pathom-trace-cards/parallel-run]],
                                    :nubank.workspaces.ui/workspaces
                                                                           [[:nubank.workspaces.ui/workspace-id
                                                                             #uuid "98aae9f3-d798-4a0c-bd8a-47a89214a566"]],
                                    :nubank.workspaces.ui/expanded
                                                                           {:card-ns
                                                                                     {"fulcro.inspect.workspaces.ui.index-explorer-cards" true,
                                                                                      "fulcro.inspect.workspaces.ui.network-cards"        true,
                                                                                      "fulcro.inspect.workspaces.ui.transactions-cards"   true,
                                                                                      "fulcro.inspect.workspaces.ui.pathom-trace-cards"   true,
                                                                                      "fulcro.inspect.workspaces.ui.data-viewer-cards"    true},
                                                                            :test-ns {"fulcro.inspect.client.lib.diff-spec" true}},
                                    :nubank.workspaces.ui/ws-tabs
                                                                           [:nubank.workspaces.ui/workspace-tabs "singleton"],
                                    :nubank.workspaces.ui/spotlight
                                                                           [:nubank.workspaces.ui.spotlight/id
                                                                            #uuid "8f2dfb97-9c05-4dc2-bab1-f07560ccbd20"],
                                    :nubank.workspaces.ui/show-spotlight?  false,
                                    :nubank.workspaces.ui/show-help-modal? false,
                                    :nubank.workspaces.ui/settings
                                                                           {:nubank.workspaces.ui/show-index? false}}},
    :nubank.workspaces.model/card-id
                                  {fulcro.inspect.client.lib.diff-spec/test-updates
                                   {:nubank.workspaces.model/card-id
                                                                   fulcro.inspect.client.lib.diff-spec/test-updates,
                                    :nubank.workspaces.model/test? true},
                                   fulcro.inspect.workspaces.ui.network-cards/network-card
                                   {:nubank.workspaces.model/card-id
                                    fulcro.inspect.workspaces.ui.network-cards/network-card},
                                   fulcro.inspect.workspaces.ui.network-cards/network-sampler
                                   {:nubank.workspaces.model/card-id
                                    fulcro.inspect.workspaces.ui.network-cards/network-sampler},
                                   fulcro.inspect.workspaces.ui.network-cards/network-sampler-remote-i
                                   {:nubank.workspaces.model/card-id
                                    fulcro.inspect.workspaces.ui.network-cards/network-sampler-remote-i},
                                   fulcro.inspect.workspaces.ui.transactions-cards/transactions-demo-card
                                   {:nubank.workspaces.model/card-id
                                    fulcro.inspect.workspaces.ui.transactions-cards/transactions-demo-card},
                                   nubank.workspaces.card-types.test/test-all
                                   {:nubank.workspaces.model/card-id
                                                                            nubank.workspaces.card-types.test/test-all,
                                    :nubank.workspaces.model/test?          true,
                                    :nubank.workspaces.model/card-unlisted? true},
                                   fulcro.inspect.workspaces.ui.network-cards/multi-network
                                   {:nubank.workspaces.model/card-id
                                    fulcro.inspect.workspaces.ui.network-cards/multi-network},
                                   fulcro.inspect.client.lib.diff-spec
                                   {:nubank.workspaces.model/card-id
                                                                            fulcro.inspect.client.lib.diff-spec,
                                    :nubank.workspaces.model/test?          true,
                                    :nubank.workspaces.model/card-unlisted? true},
                                   fulcro.inspect.workspaces.ui.data-viewer-cards/data-viewer-card
                                   {:nubank.workspaces.model/card-id
                                    fulcro.inspect.workspaces.ui.data-viewer-cards/data-viewer-card},
                                   fulcro.inspect.workspaces.ui.index-explorer-cards/index-explorer-panel-card
                                   {:nubank.workspaces.model/card-id
                                    fulcro.inspect.workspaces.ui.index-explorer-cards/index-explorer-panel-card},
                                   fulcro.inspect.client.lib.diff-spec/test-patch-data
                                   {:nubank.workspaces.model/card-id
                                                                   fulcro.inspect.client.lib.diff-spec/test-patch-data,
                                    :nubank.workspaces.model/test? true},
                                   fulcro.inspect.client.lib.diff-spec/test-removals
                                   {:nubank.workspaces.model/card-id
                                                                   fulcro.inspect.client.lib.diff-spec/test-removals,
                                    :nubank.workspaces.model/test? true},
                                   fulcro.inspect.workspaces.ui.pathom-trace-cards/parallel-run
                                   {:nubank.workspaces.model/card-id
                                    fulcro.inspect.workspaces.ui.pathom-trace-cards/parallel-run}},
    :ui/locale                    :en,
    :invalid
                                  {"ident"
                                   {:nubank.workspaces.ui/workspace-title "new workspace",
                                    :nubank.workspaces.ui/cards           [],
                                    :nubank.workspaces.ui/layouts         {},
                                    :nubank.workspaces.ui/breakpoint      ""}},
    :fulcro.inspect.core/app-uuid
                                  #uuid "1c91b0cc-3b99-4a77-bfb4-f3920914cec9",
    :nubank.workspaces.ui/workspace-id
                                  {#uuid "98aae9f3-d798-4a0c-bd8a-47a89214a566"
                                   {:nubank.workspaces.ui/workspace-id
                                                                          #uuid "98aae9f3-d798-4a0c-bd8a-47a89214a566",
                                    :nubank.workspaces.ui/workspace-title "new workspace",
                                    :nubank.workspaces.ui/layouts
                                                                          {"c10"
                                                                           [{"i"
                                                                                    fulcro.inspect.workspaces.ui.transactions-cards/transactions-demo-card,
                                                                             "w"    2,
                                                                             "h"    4,
                                                                             "x"    0,
                                                                             "y"    0,
                                                                             "minH" 2}
                                                                            {"i"
                                                                                    fulcro.inspect.workspaces.ui.data-viewer-cards/data-viewer-card,
                                                                             "w"    2,
                                                                             "h"    4,
                                                                             "x"    2,
                                                                             "y"    0,
                                                                             "minH" 2}],
                                                                           "c8"
                                                                           [{"w"    2,
                                                                             "x"    0,
                                                                             "i"
                                                                                    fulcro.inspect.workspaces.ui.transactions-cards/transactions-demo-card,
                                                                             "y"    0,
                                                                             "minH" 2,
                                                                             "h"    4}
                                                                            {"w"    3,
                                                                             "x"    2,
                                                                             "i"
                                                                                    fulcro.inspect.workspaces.ui.data-viewer-cards/data-viewer-card,
                                                                             "y"    0,
                                                                             "minH" 2,
                                                                             "h"    9}],
                                                                           "c16"
                                                                           [{"i"
                                                                                    fulcro.inspect.workspaces.ui.transactions-cards/transactions-demo-card,
                                                                             "w"    2,
                                                                             "h"    4,
                                                                             "x"    0,
                                                                             "y"    0,
                                                                             "minH" 2}
                                                                            {"i"
                                                                                    fulcro.inspect.workspaces.ui.data-viewer-cards/data-viewer-card,
                                                                             "w"    2,
                                                                             "h"    4,
                                                                             "x"    2,
                                                                             "y"    0,
                                                                             "minH" 2}],
                                                                           "c14"
                                                                           [{"w"    2,
                                                                             "x"    0,
                                                                             "i"
                                                                                    fulcro.inspect.workspaces.ui.transactions-cards/transactions-demo-card,
                                                                             "y"    0,
                                                                             "minH" 2,
                                                                             "h"    4}
                                                                            {"w"    2,
                                                                             "x"    2,
                                                                             "i"
                                                                                    fulcro.inspect.workspaces.ui.data-viewer-cards/data-viewer-card,
                                                                             "y"    0,
                                                                             "minH" 2,
                                                                             "h"    4}],
                                                                           "c2"
                                                                           [{"i"
                                                                                    fulcro.inspect.workspaces.ui.transactions-cards/transactions-demo-card,
                                                                             "w"    2,
                                                                             "h"    4,
                                                                             "x"    0,
                                                                             "y"    0,
                                                                             "minH" 2}
                                                                            {"i"
                                                                                    fulcro.inspect.workspaces.ui.data-viewer-cards/data-viewer-card,
                                                                             "w"    2,
                                                                             "h"    4,
                                                                             "x"    0,
                                                                             "y"    4,
                                                                             "minH" 2}],
                                                                           "c12"
                                                                           [{"w"    7,
                                                                             "x"    0,
                                                                             "i"
                                                                                    fulcro.inspect.workspaces.ui.transactions-cards/transactions-demo-card,
                                                                             "y"    0,
                                                                             "minH" 2,
                                                                             "h"    16}
                                                                            {"i"
                                                                                    fulcro.inspect.workspaces.ui.data-viewer-cards/data-viewer-card,
                                                                             "w"    2,
                                                                             "h"    4,
                                                                             "x"    7,
                                                                             "y"    0,
                                                                             "minH" 2}],
                                                                           "c4"
                                                                           [{"w"    2,
                                                                             "x"    0,
                                                                             "i"
                                                                                    fulcro.inspect.workspaces.ui.transactions-cards/transactions-demo-card,
                                                                             "y"    0,
                                                                             "minH" 2,
                                                                             "h"    4}
                                                                            {"w"    2,
                                                                             "x"    1,
                                                                             "i"
                                                                                    fulcro.inspect.workspaces.ui.data-viewer-cards/data-viewer-card,
                                                                             "y"    4,
                                                                             "minH" 2,
                                                                             "h"    4}],
                                                                           "c18"
                                                                           [{"i"
                                                                                    fulcro.inspect.workspaces.ui.transactions-cards/transactions-demo-card,
                                                                             "w"    2,
                                                                             "h"    4,
                                                                             "x"    0,
                                                                             "y"    0,
                                                                             "minH" 2}
                                                                            {"i"
                                                                                    fulcro.inspect.workspaces.ui.data-viewer-cards/data-viewer-card,
                                                                             "w"    2,
                                                                             "h"    4,
                                                                             "x"    2,
                                                                             "y"    0,
                                                                             "minH" 2}],
                                                                           "c20"
                                                                           [{"i"
                                                                                    fulcro.inspect.workspaces.ui.transactions-cards/transactions-demo-card,
                                                                             "w"    2,
                                                                             "h"    4,
                                                                             "x"    0,
                                                                             "y"    0,
                                                                             "minH" 2}
                                                                            {"i"
                                                                                    fulcro.inspect.workspaces.ui.data-viewer-cards/data-viewer-card,
                                                                             "w"    2,
                                                                             "h"    4,
                                                                             "x"    2,
                                                                             "y"    0,
                                                                             "minH" 2}],
                                                                           "c6"
                                                                           [{"i"
                                                                                    fulcro.inspect.workspaces.ui.transactions-cards/transactions-demo-card,
                                                                             "w"    2,
                                                                             "h"    4,
                                                                             "x"    0,
                                                                             "y"    0,
                                                                             "minH" 2}
                                                                            {"i"
                                                                                    fulcro.inspect.workspaces.ui.data-viewer-cards/data-viewer-card,
                                                                             "w"    2,
                                                                             "h"    4,
                                                                             "x"    2,
                                                                             "y"    0,
                                                                             "minH" 2}]},
                                    :nubank.workspaces.ui/cards
                                                                          [[:nubank.workspaces.model/card-id
                                                                            fulcro.inspect.workspaces.ui.transactions-cards/transactions-demo-card]
                                                                           [:nubank.workspaces.model/card-id
                                                                            fulcro.inspect.workspaces.ui.data-viewer-cards/data-viewer-card]],
                                    :nubank.workspaces.ui/breakpoint      "c8"}},
    :ui/root                      [:nubank.workspaces.ui/workspace-root "singleton"],
    :nubank.workspaces.ui.spotlight/id
                                  {#uuid "8f2dfb97-9c05-4dc2-bab1-f07560ccbd20"
                                   {:nubank.workspaces.ui.spotlight/id
                                                                                     #uuid "8f2dfb97-9c05-4dc2-bab1-f07560ccbd20",
                                    :nubank.workspaces.ui.spotlight/options          (),
                                    :nubank.workspaces.ui.spotlight/value            nil,
                                    :nubank.workspaces.ui.spotlight/filter           "",
                                    :nubank.workspaces.ui.spotlight/filtered-options []}},
    :fulcro.client.network/status {:remote :idle},
    :nubank.workspaces.ui/workspace-tabs
                                  {"singleton"
                                   {:nubank.workspaces.ui/open-workspaces
                                    [[:nubank.workspaces.ui/workspace-id
                                      #uuid "98aae9f3-d798-4a0c-bd8a-47a89214a566"]
                                     [:nubank.workspaces.model/card-id
                                      fulcro.inspect.workspaces.ui.index-explorer-cards/index-explorer-panel-card]
                                     [:nubank.workspaces.model/card-id
                                      fulcro.inspect.workspaces.ui.transactions-cards/transactions-demo-card]],
                                    :nubank.workspaces.ui/active-workspace
                                    [:nubank.workspaces.ui/workspace-id
                                     #uuid "98aae9f3-d798-4a0c-bd8a-47a89214a566"]}}})

(ws/defcard data-viewer-card
  {::wsm/align ::wsm/stretch-flex}
  (ct.fulcro/fulcro-card
    {::f.portal/root          DataViewerDemo
     ::f.portal/initial-state (fn [_] {::viewer {::dv/content sample-data}})}))

(ns fulcro.inspect.ui.index-explorer
  (:require [com.wsscode.pathom.viz.index-explorer :as iex]
            [fulcro.client.primitives :as fp]
            [fulcro.client.localized-dom :as dom]
            [fulcro.client.data-fetch :as df]
            [fulcro.inspect.helpers :as db.h]))

(defn load-index [this id]
  (let [app-id (try (db.h/comp-app-uuid this) (catch :default _))]
    (df/load this [::iex/id id] iex/IndexExplorer
      {:target       (conj (fp/get-ident this) ::explorer)
       :update-query (fn [query]
                       (into (with-meta [] (meta query))
                             (map (fn [x]
                                    (if (= x ::iex/index)
                                      (list ::iex/index {:fulcro.inspect.core/app-uuid app-id
                                                         :fulcro.inspect.client/remote :remote})
                                      x)))
                             query))})))

(fp/defsc IndexExplorer
  [this {::keys [explorer] :as props}]
  {:initial-state (fn [{:keys [app-uuid remotes]}]
                    (let [explorers (mapv
                                      #(-> {::iex/id [:fulcro.inspect.core/app-uuid app-uuid %]
                                            :remote  %})
                                      remotes)]
                      {::id        [:fulcro.inspect.core/app-uuid app-uuid]
                       ::active    (first explorers)
                       ::explorers explorers}))
   :ident         [::id ::id]
   :query         [::id
                   {::explorer (fp/get-query iex/IndexExplorer)}
                   {::explorers (fp/get-query iex/IndexExplorer)}]
   :css           [[:.container {:display        "flex"
                                 :flex           "1"
                                 :flex-direction "column"
                                 :width          "100%"}]
                   [:.title {:grid-area     "title"
                             :display       "flex"
                             :align-items   "center"
                             :padding       "3px"
                             :margin-bottom "5px"
                             :border-bottom "1px solid #e0e0e0"}]
                   [:.flex {:flex "1"}]
                   [:.empty {:flex            "1"
                             :background      "#777"
                             :margin-top      "-5px"
                             :display         "flex"
                             :align-items     "center"
                             :justify-content "center"
                             :color           "#fff"
                             :font-size       "21px"}]]}
  (dom/div :.container
    (dom/div :.title
      #_(if (> (count remotes) 1)
          (dom/div :.remote-selector
            (dom/div :.remote-selector-label "Remote:")
            (dom/select {:onChange #(on-switch-remote (read-string (.. % -target -value)))
                         :value    (pr-str remote)}
              (for [r remotes]
                (dom/option {:key (pr-str r) :value (pr-str r)} (pr-str r))))))
      (dom/div :.flex)
      (dom/button {:onClick #(load-index this (random-uuid))}
        "Load index"))
    (if explorer
      (if (::iex/index explorer)
        (iex/index-explorer explorer)
        (dom/div :.empty "Seems like the index is not available."))
      (dom/div :.empty "No data"))))

(def index-explorer (fp/factory IndexExplorer))

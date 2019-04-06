(ns fulcro.inspect.ui.index-explorer
  (:require [com.wsscode.pathom.viz.index-explorer :as iex]
            [fulcro.client.primitives :as fp]
            [fulcro.client.localized-dom :as dom]
            [fulcro.client.data-fetch :as df]))

(defn load-index [this]
  (let [id (random-uuid)]
    (df/load this [::iex/id id] iex/IndexExplorer
      {:target (conj (fp/get-ident this) ::explorer)})))

(fp/defsc IndexExplorer
  [this {::keys [explorer]}]
  {:pre-merge (fn [{:keys [current-normalized data-tree]}]
                (merge
                  {::id (random-uuid)}
                  current-normalized
                  data-tree))
   :ident     [::id ::id]
   :query     [::id
               {::explorer (fp/get-query iex/IndexExplorer)}]
   :css       [[:.container {:display        "flex"
                             :flex           "1"
                             :flex-direction "column"}]
               [:.header {:margin-bottom "5px"}]]}
  (dom/div :.container
    (dom/div :.header
      (dom/button {:onClick #(load-index this)} "Load index"))
    (if explorer
      (iex/index-explorer explorer)
      "No data")))

(def index-explorer (fp/factory IndexExplorer))

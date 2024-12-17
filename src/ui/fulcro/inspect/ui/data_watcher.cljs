(ns fulcro.inspect.ui.data-watcher
  (:require
    [com.fulcrologic.fulcro-css.css :as css]
    [com.fulcrologic.fulcro.algorithms.normalized-state :as fns]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as fp]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.mutations :as mutations :refer-macros [defmutation]]
    [taoensso.timbre :as log]
    [fulcro.inspect.helpers :as db.h]
    [fulcro.inspect.lib.local-storage :as storage]
    [fulcro.inspect.ui.core :as ui]
    [fulcro.inspect.ui.data-viewer :as f.data-viewer]
    [fulcro.inspect.ui.helpers :as h]))

(declare WatchPin)

(defmutation add-data-watch [{:keys [path]}]
  (action [{:keys [ref state] :as env}]
    (let [app-id (db.h/ref-app-id @state ref)]
      (db.h/create-entity! (assoc env :ref [:data-watcher/id [:x app-id]])
        WatchPin {:path path}
        :prepend :data-watcher/watches)
      (storage/update! [:data-watcher/watches app-id] #(into [path] %)))))

(defmutation remove-data-watch [{:keys [index]
                                 :watch-pin/keys [path id]}]
  (action [{:keys [ref state] :as env}]
    (let [app-uuid (db.h/ref-app-id @state ref)]
      (swap! state fns/remove-entity [:watch-pin/id id])
      (storage/remove! [:data-watcher/watches-expanded app-uuid path])
      (storage/update! [:data-watcher/watches app-uuid] #(db.h/vec-remove-index index %)))))

(defmutation update-watcher-expanded [{:keys [path expanded]}]
  (action [{:keys [ref state]}]
    (let [app-id (db.h/ref-app-id @state ref)]
      (storage/set! [:data-watcher/watches-expanded app-id path] expanded))))

(fp/defsc WatchPin
  [this {:watch-pin/keys [data-viewer path] :ui/keys [expanded?]}
   {:keys             [raw history-step]
    :watch-pin/keys   [delete-item]
    :data-viewer/keys [path-action on-expand-change]}]
  {:initial-state (fn [{:keys [path expanded] :as params}]
                    {:ui/expanded?          true
                     :watch-pin/id          (random-uuid)
                     :watch-pin/path        path
                     :watch-pin/data-viewer (assoc (fp/get-initial-state f.data-viewer/DataViewer {:id (random-uuid)})
                                              :data-viewer/expanded (or expanded {}))})
   :ident         :watch-pin/id
   :query         [:watch-pin/id :watch-pin/path :ui/expanded?
                   {:watch-pin/data-viewer (fp/get-query f.data-viewer/DataViewer)}]
   :css           [[:.container {:margin "6px 0"}]
                   [:.toggle-row {:display       "flex"
                                  :align-items   "center"
                                  :margin-bottom "10px"}]
                   [:.toggle-button ui/css-triangle]
                   [:.path (merge ui/css-code-font
                             {:background "#fafafa"
                              :border     "1px solid #efeef1"
                              :margin     "0 5px"
                              :padding    "3px 3px 1px"
                              :color      "#222"
                              :cursor     "pointer"})
                    [:&:hover {:text-decoration "line-through"}]]]
   :css-include   [f.data-viewer/DataViewer]}
  (let [css (css/get-classnames WatchPin)]
    (dom/div {:className (:container css)}
      (dom/div {:className (:toggle-row css)}
        (dom/div {:className (:toggle-button css)
                  :onClick   #(mutations/set-value! this :ui/expanded? (not expanded?))}
          (if expanded? ui/arrow-down ui/arrow-right))
        (dom/div {:className (:path css)
                  :onClick   #(if delete-item (delete-item %))}
          (pr-str path)))
      (if expanded?
        (f.data-viewer/ui-data-viewer
          data-viewer
          {:history-step                 history-step
           :raw                          raw
           :data-viewer/path             path
           :data-viewer/path-action      path-action
           :data-viewer/on-expand-change on-expand-change})))))

(def ui-watch-pin (fp/computed-factory WatchPin {:keyfn :watch-pin/id}))

(fp/defsc DataWatcher
  [this {:keys [:data-watcher/data-viewer :data-watcher/watches] :as props} {:keys [history-step search] :as computed}]
  {:initial-state (fn [{:keys [id]}] {:data-watcher/id          [:x id]
                                      :data-watcher/data-viewer (fp/get-initial-state f.data-viewer/DataViewer {:id (random-uuid)})
                                      :data-watcher/watches     []})
   :ident         :data-watcher/id
   :query         [:data-watcher/id
                   {:data-watcher/data-viewer (fp/get-query f.data-viewer/DataViewer)}
                   {:data-watcher/watches (fp/get-query WatchPin)}]
   :css-include   [f.data-viewer/DataViewer WatchPin]}
  (dom/div (h/props->html props)
    (map-indexed
      (fn [idx watch]
        (ui-watch-pin watch
          (merge computed
            {:watch-pin/delete-item
             (fn [_]
               (fp/transact! this [(remove-data-watch (assoc watch :index idx))]))

             :data-viewer/on-expand-change
             (fn [path expanded] (fp/transact! this [(update-watcher-expanded {:path     (:watch-pin/path watch)
                                                                               :expanded expanded})]))

             :data-viewer/path-action
             #(fp/transact! this [(add-data-watch {:path (vec (concat (:watch-pin/path watch) %))})])})))
      watches)

    (f.data-viewer/ui-data-viewer data-viewer
      {:history-step            history-step
       :data-viewer/search      search
       :data-viewer/path-action #(fp/transact! this [(add-data-watch {:path %})])})))

(def ui-data-watcher (fp/computed-factory DataWatcher))

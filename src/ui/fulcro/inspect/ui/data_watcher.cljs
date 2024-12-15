(ns fulcro.inspect.ui.data-watcher
  (:require
    [com.fulcrologic.fulcro-css.css :as css]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as fp]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.mutations :as mutations :refer-macros [defmutation]]
    [fulcro.inspect.helpers :as db.h]
    [fulcro.inspect.lib.local-storage :as storage]
    [fulcro.inspect.ui.core :as ui]
    [fulcro.inspect.ui.data-viewer :as f.data-viewer]
    [fulcro.inspect.ui.helpers :as h]))

(declare WatchPin)

(defmutation add-data-watch [{:keys [path]}]
  (action [{:keys [ref state] :as env}]
    (let [app-id (db.h/ref-app-id @state ref)]
      (doseq [app-uuid (db.h/matching-apps @state app-id)]
        (db.h/create-entity! (assoc env :ref [::id [::app/id app-uuid]])
          WatchPin {:path path}
          :prepend ::watches))
      (storage/update! [::watches app-id] #(into [path] %)))))

(defmutation remove-data-watch [{:keys [index path]}]
  (action [{:keys [ref state] :as env}]
    (let [app-id (db.h/ref-app-id @state ref)]
      (doseq [app-uuid (db.h/matching-apps @state app-id)
              :let [ref       [::id [::app/id app-uuid]]
                    watch-ref (get-in @state (conj ref ::watches index))
                    env'      (assoc env :ref ref)]]
        (swap! state db.h/deep-remove-ref watch-ref)
        (db.h/swap-entity! env' update ::watches #(db.h/vec-remove-index index %)))

      (storage/remove! [::watches-expanded app-id path])
      (storage/update! [::watches app-id] #(db.h/vec-remove-index index %)))))

(defmutation update-watcher-expanded [{:keys [path expanded]}]
  (action [{:keys [ref state]}]
    (let [app-id (db.h/ref-app-id @state ref)]
      (storage/set! [::watches-expanded app-id path] expanded))))

(fp/defsc WatchPin
  [this {::keys   [watch-path data-viewer]
         :ui/keys [expanded?]}
   {:keys                [root-data]
    ::keys               [delete-item]
    ::f.data-viewer/keys [path-action on-expand-change]}]
  {:initial-state (fn [{:keys [path expanded] :as params}]
                    {:ui/expanded? true
                     ::watch-id    (random-uuid)
                     ::watch-path  path
                     ::data-viewer (assoc (fp/get-initial-state f.data-viewer/DataViewer {})
                                     ::f.data-viewer/expanded (or expanded {}))})
   :ident         [::watch-id ::watch-id]
   :query         [:ui/expanded? ::watch-id ::watch-path
                   {::data-viewer (fp/get-query f.data-viewer/DataViewer)}]
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
          (pr-str watch-path)))
      (if expanded?
        (f.data-viewer/data-viewer
          (assoc data-viewer :ui/raw (or root-data {}))
          {::f.data-viewer/path             watch-path
           ::f.data-viewer/path-action      path-action
           ::f.data-viewer/on-expand-change on-expand-change})))))

(def ui-watch-pin (fp/computed-factory WatchPin {:keyfn ::watch-id}))

(fp/defsc DataWatcher
  [this {::keys [root-data watches] :as props} {:keys [history-step search]}]
  {:initial-state (fn [data-viewer-state] {::id        (random-uuid)
                                           ::root-data (fp/get-initial-state f.data-viewer/DataViewer data-viewer-state)
                                           ::watches   []})
   :ident         [::id ::id]
   :query         [::id
                   {::root-data (fp/get-query f.data-viewer/DataViewer)}
                   {::watches (fp/get-query WatchPin)}]
   :css-include   [f.data-viewer/DataViewer WatchPin]}
  (dom/div (h/props->html props)
    (map-indexed
      (fn [idx watch]
        (ui-watch-pin watch
          {:root-data (:history/value history-step)
           ::delete-item
           (fn [_] (fp/transact! this [(remove-data-watch {:path  (::watch-path watch)
                                                           :index idx})]))

           ::f.data-viewer/on-expand-change
           (fn [path expanded] (fp/transact! this [(update-watcher-expanded {:path     (::watch-path watch)
                                                                             :expanded expanded})]))

           ::f.data-viewer/path-action
           #(fp/transact! this [(add-data-watch {:path (vec (concat (::watch-path watch) %))})])}))
      watches)

    (f.data-viewer/data-viewer
      (assoc root-data :ui/history-step history-step)
      {::f.data-viewer/search      search
       ::f.data-viewer/path-action #(fp/transact! this [(add-data-watch {:path %})])})))

(def ui-data-watcher (fp/computed-factory DataWatcher))

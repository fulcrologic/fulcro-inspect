(ns fulcro.inspect.ui.data-watcher
  (:require [fulcro.client.mutations :as mutations :refer-macros [defmutation]]
            [fulcro.inspect.ui.data-viewer :as f.data-viewer]
            [fulcro.inspect.helpers :as db.h]
            [fulcro-css.css :as css]
            [fulcro.client.dom :as dom]
            [fulcro.client.primitives :as fp]
            [fulcro.inspect.lib.local-storage :as storage]
            [fulcro.inspect.ui.helpers :as h]
            [fulcro.inspect.ui.core :as ui]))

(declare WatchPin)

(defn update-watchers [state new-state watches]
  (reduce
    (fn [s watcher-ref]
      (let [{::keys [data-viewer watch-path]} (get-in state watcher-ref)]
        (assoc-in s (conj data-viewer ::f.data-viewer/content)
          (get-in new-state watch-path))))
    state
    watches))

(declare update-state)

(defn update-state* [env new-state]
  (let [{:keys [ref state]} env
        watches     (get-in @state (conj ref ::watches))
        content-ref (-> (get-in @state (conj ref ::root-data))
                        (conj ::f.data-viewer/content))]
    (swap! state (comp #(assoc-in % content-ref new-state)
                       #(update-watchers % new-state watches)))))

(defmutation update-state [new-state]
  (action [env]
    (update-state* env new-state)))

(defmutation add-data-watch [{:keys [path]}]
  (action [{:keys [ref state] :as env}]
    (let [app-id (db.h/ref-app-id @state ref)]
      (doseq [app-uuid (db.h/matching-apps @state app-id)
              :let [ref     [::id [:fulcro.inspect.core/app-uuid app-uuid]]
                    content (as-> (get-in @state (conj ref ::root-data)) <>
                              (get-in @state (conj <> ::f.data-viewer/content))
                              (get-in <> path))]]
        (db.h/create-entity! (assoc env :ref [::id [:fulcro.inspect.core/app-uuid app-uuid]])
          WatchPin {:path path :content content}
          :prepend ::watches))

      (storage/update! [::watches app-id] #(into [path] %)))))

(defmutation remove-data-watch [{:keys [index]}]
  (action [{:keys [ref state] :as env}]
    (let [app-id (db.h/ref-app-id @state ref)]
      (doseq [app-uuid (db.h/matching-apps @state app-id)
              :let [ref       [::id [:fulcro.inspect.core/app-uuid app-uuid]]
                    watch-ref (get-in @state (conj ref ::watches index))
                    env'      (assoc env :ref ref)]]
        (swap! state db.h/deep-remove-ref watch-ref)
        (db.h/swap-entity! env' update ::watches #(db.h/vec-remove-index index %)))

      (storage/update! [::watches app-id] #(db.h/vec-remove-index index %)))))

(fp/defsc WatchPin
  [this {::keys   [watch-path data-viewer]
         :ui/keys [expanded?]}
   {::keys               [delete-item]
    ::f.data-viewer/keys [path-action]}]
  {:initial-state (fn [{:keys [path content]}]
                    {:ui/expanded? true
                     ::watch-id    (random-uuid)
                     ::watch-path  path
                     ::data-viewer (fp/get-initial-state f.data-viewer/DataViewer content)})
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
    (dom/div #js {:className (:container css)}
      (dom/div #js {:className (:toggle-row css)}
        (dom/div #js {:className (:toggle-button css)
                      :onClick   #(mutations/set-value! this :ui/expanded? (not expanded?))}
          (if expanded? ui/arrow-down ui/arrow-right))
        (dom/div #js {:className (:path css)
                      :onClick   #(if delete-item (delete-item %))}
          (pr-str watch-path)))
      (if expanded?
        (f.data-viewer/data-viewer data-viewer
          {::f.data-viewer/path-action path-action})))))

(def watch-pin (fp/factory WatchPin {:keyfn ::watch-id}))

(fp/defsc DataWatcher
  [this {::keys [root-data watches] :as props} {:keys [search]}]
  {:initial-state (fn [state] {::id        (random-uuid)
                               ::root-data (fp/get-initial-state f.data-viewer/DataViewer state)
                               ::watches   []})
   :ident         [::id ::id]
   :query         [::id
                   {::root-data (fp/get-query f.data-viewer/DataViewer)}
                   {::watches (fp/get-query WatchPin)}]
   :css-include   [f.data-viewer/DataViewer WatchPin]}
  (let [content (::f.data-viewer/content root-data)]
    (dom/div (h/props->html props)
      (mapv (comp watch-pin
                  (fn [[x i]]
                    (-> (assoc x ::content content)
                        (fp/computed {::delete-item
                                      (fn [_]
                                        (fp/transact! this [`(remove-data-watch {:index ~i})]))

                                      ::f.data-viewer/path-action
                                      #(fp/transact! this [`(add-data-watch {:path ~(vec (concat (::watch-path x) %))})])})))
                  vector)
        watches
        (range))
      (f.data-viewer/data-viewer root-data
        {::f.data-viewer/search search
         ::f.data-viewer/path-action
         #(fp/transact! this [`(add-data-watch {:path ~%})])}))))

(def data-watcher (fp/factory DataWatcher))

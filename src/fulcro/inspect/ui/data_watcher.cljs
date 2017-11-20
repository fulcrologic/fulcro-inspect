(ns fulcro.inspect.ui.data-watcher
  (:require [fulcro.client.core :as fulcro]
            [fulcro.client.mutations :as mutations]
            [fulcro.inspect.ui.data-viewer :as f.data-viewer]
            [fulcro.inspect.helpers :as h]
            [fulcro-css.css :as css]
            [om.dom :as dom]
            [om.next :as om]))

(declare WatchPin)

(defn update-watchers [state new-state watches]
  (reduce
    (fn [s watcher-ref]
      (let [{::keys [data-viewer watch-path]} (get-in state watcher-ref)]
        (assoc-in s (conj data-viewer ::f.data-viewer/content)
          (get-in new-state watch-path))))
    state
    watches))

(defmethod mutations/mutate `update-state [{:keys [ref state]} _ new-state]
  {:action
   (fn []
     (let [watches     (get-in @state (conj ref ::watches))
           content-ref (-> (get-in @state (conj ref ::root-data))
                           (conj ::f.data-viewer/content))]
       (swap! state (comp #(assoc-in % content-ref new-state)
                          #(update-watchers % new-state watches)))))})

(defmethod mutations/mutate `add-data-watch [{:keys [ref state]} _ {:keys [path]}]
  {:action
   (fn []
     (let [content   (as-> (get-in @state (conj ref ::root-data)) <>
                       (get-in @state (conj <> ::f.data-viewer/content))
                       (get-in <> path))
           {::keys [watch-id] :as watcher}
           (fulcro/get-initial-state WatchPin {:path    path
                                               :content content})
           watch-ref [::watch-id watch-id]]
       (swap! state (comp #(h/merge-entity % WatchPin watcher)
                          #(update-in % (conj ref ::watches) (fn [x] (-> (cons watch-ref x) vec)))))))})

(defmethod mutations/mutate `remove-data-watch [{:keys [ref state]} _ {:keys [index]}]
  {:action
   (fn []
     ;; TODO clean up watch data
     (swap! state update-in (conj ref ::watches) #(h/vec-remove-index index %)))})

(om/defui ^:once WatchPin
  static fulcro/InitialAppState
  (initial-state [_ {:keys [path content]}]
    {:ui/expanded? true
     ::watch-id    (random-uuid)
     ::watch-path  path
     ::data-viewer (fulcro/get-initial-state f.data-viewer/DataViewer content)})

  static om/IQuery
  (query [_] [:ui/expanded? ::watch-id ::watch-path
              {::data-viewer (om/get-query f.data-viewer/DataViewer)}])

  static om/Ident
  (ident [_ props] [::watch-id (::watch-id props)])

  static css/CSS
  (local-rules [_] [[:.container {:margin "6px 0"}]
                    [:.toggle-row {:display       "flex"
                                   :align-items   "center"
                                   :margin-bottom "10px"}]
                    [:.toggle-button f.data-viewer/css-triangle]
                    [:.path (merge f.data-viewer/css-code-font
                                   {:background "#fafafa"
                                    :border     "1px solid #efeef1"
                                    :margin     "0 5px"
                                    :padding    "3px 3px 1px"
                                    :color      "#222"
                                    :cursor     "pointer"})
                     [:&:hover {:text-decoration "line-through"}]]])
  (include-children [_] [f.data-viewer/DataViewer])

  Object
  (render [this]
    (let [{::keys   [watch-path data-viewer]
           :ui/keys [expanded?]
           :as      props} (om/props this)
          {::keys               [delete-item]
           ::f.data-viewer/keys [path-action]} (om/get-computed props)
          css (css/get-classnames WatchPin)]
      (dom/div #js {:className (:container css)}
        (dom/div #js {:className (:toggle-row css)}
          (dom/div #js {:className (:toggle-button css)
                        :onClick   #(mutations/set-value! this :ui/expanded? (not expanded?))}
            (if expanded? "▼" "▶"))
          (dom/div #js {:className (:path css)
                        :onClick   #(if delete-item (delete-item %))}
            (pr-str watch-path)))
        (if expanded?
          (f.data-viewer/data-viewer data-viewer
            {::f.data-viewer/path-action path-action}))))))

(def watch-pin (om/factory WatchPin))

(om/defui ^:once PinnableDataViewer
  static fulcro/InitialAppState
  (initial-state [_ state] {::id        (random-uuid)
                            ::root-data (fulcro/get-initial-state f.data-viewer/DataViewer state)
                            ::watches   []})

  static om/IQuery
  (query [_] [::id
              {::root-data (om/get-query f.data-viewer/DataViewer)}
              {::watches (om/get-query WatchPin)}])

  static om/Ident
  (ident [_ props] [::id (::id props)])

  static css/CSS
  (local-rules [_] [])
  (include-children [_] [f.data-viewer/DataViewer WatchPin])

  Object
  (render [this]
    (let [{::keys [root-data watches]} (om/props this)
          content (::f.data-viewer/content root-data)]
      (dom/div nil
        (mapv (comp watch-pin
                    (fn [[x i]]
                      (-> (assoc x ::content content)
                          (om/computed {::delete-item
                                        (fn [_]
                                          (om/transact! this [`(remove-data-watch {:index ~i})]))

                                        ::f.data-viewer/path-action
                                        #(om/transact! this [`(add-data-watch {:path ~(vec (concat (::watch-path x) %))})])})))
                    vector)
          watches
          (range))
        (f.data-viewer/data-viewer root-data
          {::f.data-viewer/path-action
           #(om/transact! this [`(add-data-watch {:path ~%})])})))))

(def pinnable-data-viewer (om/factory PinnableDataViewer))

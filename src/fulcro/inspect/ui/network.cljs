(ns fulcro.inspect.ui.network
  (:require [clojure.string :as str]
            [garden.core :as garden]
            [garden.selectors :as gs]
            [fulcro.client.core :as fulcro :refer-macros [defsc]]
            [fulcro.client.mutations :refer-macros [defmutation]]
            [fulcro-css.css :as css]
            [fulcro.inspect.helpers :as h]
            [fulcro.inspect.ui.core :as ui]
            [fulcro.inspect.ui.data-viewer :as data-viewer]
            [om.dom :as dom]
            [om.next :as om]))

(declare Request)

(defmutation request-start [req]
  (action [env]
    (h/create-entity! env Request req :append ::requests)))

(defmutation request-update [req]
  (action [env]
    (let [{:keys [state]} env]
      (swap! state h/merge-entity Request (assoc req ::request-finished-at (js/Date.))))))

(defn response-status [{::keys [response-edn error]}]
  (cond
    response-edn
    ::status.success

    error
    ::status.error

    :else
    ::status.pending))

(om/defui ^:once Request
  static fulcro/InitialAppState
  (initial-state [_ {::keys [request-edn] :as props}]
    (merge (cond-> {::request-id         (random-uuid)
                    ::request-started-at (js/Date.)}
             request-edn
             (assoc ::request-edn-view (fulcro/get-initial-state data-viewer/DataViewer request-edn)))
           props))

  static om/IQuery
  (query [_] [::request-id ::request-edn ::request-edn-view ::response-edn
              ::request-started-at ::request-finished-at ::error])

  static om/Ident
  (ident [_ props] [::request-id (::request-id props)])

  static css/CSS
  (local-rules [_]
    (let [border (str "1px solid " ui/color-bg-light-border)]
      [[:.row {:cursor  "pointer"
               :display "flex"}
        [(gs/& (gs/nth-child :odd)) {:background ui/color-bg-light}]
        [:&:hover {:background "#eef3fa !important"}]
        [:&.error {:color "#e80000"}]]
       [:.pending {:color ui/color-text-faded}]

       [:.table-cell {:border-right  border
                      :border-bottom border
                      :padding       "2px 4px"
                      :overflow      "hidden"}
        [:$fulcro_inspect_ui_data-viewer_DataViewer__container {:max-width "100"}]
        [:&.flex {:flex 1}]
        [(gs/& gs/last-child) {:border-right "0"}]]

       [:.timestamp ui/css-timestamp]]))
  (include-children [_] [data-viewer/DataViewer])

  Object
  (render [this]
    (let [{::keys [request-edn-view response-edn error
                   request-started-at request-finished-at] :as props} (om/props this)
          columns (om/get-computed props :columns)
          css     (css/get-classnames Request)]
      (dom/div #js {:className (cond-> (:row css)
                                 error (str " " (:error css)))}
        (dom/div #js {:className (:table-cell css)
                      :style     #js {:width (get columns 0)}}
          (dom/span #js {:className (:timestamp css)} (ui/print-timestamp request-started-at)))
        (dom/div #js {:className (str (:table-cell css) " " (:flex css))}
          (data-viewer/data-viewer (assoc request-edn-view ::data-viewer/static? true)))
        (dom/div #js {:className (:table-cell css)
                      :style     #js {:width (get columns 1)}}
          (cond
            response-edn
            "Success"

            error
            "Error"

            :else
            (dom/span #js {:className (:pending css)} "(pending...)")))
        (dom/div #js {:className (:table-cell css)
                      :style     #js {:width (get columns 2)}}
          (if (and request-started-at request-finished-at)
            (str (- (.getTime request-finished-at) (.getTime request-started-at)) " ms")
            (dom/span #js {:className (:pending css)} "(pending...)")))))))

(def request (om/factory Request))

(om/defui ^:once NetworkHistory
  static fulcro/InitialAppState
  (initial-state [_ _]
    {::history-id (random-uuid)
     ::requests   []})

  static om/IQuery
  (query [_] [::history-id
              {::requests (om/get-query Request)}])

  static om/Ident
  (ident [_ props] [::history-id (::history-id props)])

  static css/CSS
  (local-rules [_]
    (let [border (str "1px solid " ui/color-bg-medium-border)]
      [[:.container {:flex           1
                     :display        "flex"
                     :flex-direction "column"}
        [:* {:box-sizing "border-box"}]]
       [:.table {:font-family     ui/label-font-family
                 :font-size       ui/label-font-size
                 :width           "100%"
                 :border-collapse "collapse"
                 :color           "#313942"
                 :flex            "1"
                 :display         "flex"
                 :flex-direction  "column"}]

       [:.table-header {:display       "flex"
                        :overflow-y    "scroll"
                        :border-bottom border}]

       [(gs/> :.table-header "div") {:font-weight  "normal"
                                     :text-align   "left"
                                     :padding      "5px 4px"
                                     :border-right border}
        [:&.flex {:flex 1}]
        [(gs/& gs/last-child) {:border-right "0"}]]

       [:.table-body {:flex       1
                      :overflow-y "scroll"}]]))
  (include-children [_] [Request])

  Object
  (render [this]
    (let [{::keys [requests]} (om/props this)
          css     (css/get-classnames NetworkHistory)
          columns [100 90 70]]
      (dom/div #js {:className (:container css)}
        (dom/div #js {:className (:table css)}
          (dom/div #js {:className (:table-header css)}
            (dom/div #js {:style #js {:width (get columns 0)}} "Started")
            (dom/div #js {:className (:flex css)} "Request")
            (dom/div #js {:style #js {:width (get columns 1)}} "Status")
            (dom/div #js {:style #js {:width (get columns 2)}} "Time"))

          (dom/div #js {:className (:table-body css)}
            (if (seq requests)
              (mapv (comp request #(om/computed % {:columns columns})) requests))))))))

(def network-history (om/factory NetworkHistory))

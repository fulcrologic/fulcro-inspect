(ns fulcro.inspect.ui.network
  (:require [garden.selectors :as gs]
            [fulcro.client.core :as fulcro :refer-macros [defsc]]
            [fulcro.client.mutations :as mutations :refer-macros [defmutation]]
            [fulcro-css.css :as css]
            [fulcro.inspect.helpers :as h]
            [fulcro.inspect.ui.core :as ui]
            [fulcro.inspect.ui.data-viewer :as data-viewer]
            [om.dom :as dom]
            [om.next :as om]))

(declare Request)

(defmutation request-start [request]
  (action [env]
    (h/create-entity! env Request request :append ::requests)))

(defmutation request-finish [{::keys [response-edn error] :as request}]
  (action [env]
    (let [{:keys [ref state] :as env} env]
      (when (get-in @state (conj ref :ui/request-edn-view))
        (if response-edn
          (h/create-entity! env data-viewer/DataViewer response-edn :set :ui/response-edn-view))
        (if error
          (h/create-entity! env data-viewer/DataViewer error :set :ui/error-view)))

      (swap! state h/merge-entity Request (assoc request ::request-finished-at (js/Date.))))))

(defmutation select-request [{::keys [request-edn response-edn error] :as request}]
  (action [env]
    (let [{:keys [state ref] :as env} env
          req-ref (om/ident Request request)]
      (if-not (get-in @state (conj req-ref :ui/request-edn-view))
        (let [env' (assoc env :ref req-ref)]
          (h/create-entity! env' data-viewer/DataViewer request-edn :set :ui/request-edn-view)
          (if response-edn
            (h/create-entity! env' data-viewer/DataViewer response-edn :set :ui/response-edn-view))
          (if error
            (h/create-entity! env' data-viewer/DataViewer error :set :ui/error-view))))
      (swap! state update-in ref assoc ::active-request req-ref))))

(defmutation clear-requests [_]
  (action [env]
    (let [{:keys [state ref] :as env} env]
      (swap! state update-in ref assoc ::active-request nil)
      (h/remove-all env ::requests))))

(om/defui ^:once RequestDetails
  static fulcro/InitialAppState
  (initial-state [_ _] {})

  static om/Ident
  (ident [_ props] [::request-id (::request-id props)])

  static om/IQuery
  (query [_] [::request-id ::request-edn ::response-edn ::request-started-at ::request-finished-at ::error
              {:ui/request-edn-view (om/get-query data-viewer/DataViewer)}
              {:ui/response-edn-view (om/get-query data-viewer/DataViewer)}
              {:ui/error-view (om/get-query data-viewer/DataViewer)}])

  static css/CSS
  (local-rules [_] [[:.group ui/css-info-group]
                    [:.label ui/css-info-label]])
  (include-children [_] [])

  Object
  (render [this]
    (let [{:ui/keys [request-edn-view response-edn-view error-view]} (om/props this)
          css (css/get-classnames RequestDetails)]
      (dom/div #js {:className (:container css)}
        (dom/div #js {:className (:group css)}
          (dom/div #js {:className (:label css)} "Request")
          (data-viewer/data-viewer request-edn-view))

        (if response-edn-view
          (dom/div #js {:className (:group css)}
            (dom/div #js {:className (:label css)} "Response")
            (data-viewer/data-viewer response-edn-view)))

        (if error-view
          (dom/div #js {:className (:group css)}
            (dom/div #js {:className (:label css)} "Error")
            (data-viewer/data-viewer error-view)))))))

(def request-details (om/factory RequestDetails))

(om/defui ^:once Request
  static fulcro/InitialAppState
  (initial-state [_ {::keys [request-edn] :as props}]
    (merge (cond-> {::request-id         (random-uuid)
                    ::request-started-at (js/Date.)}
             request-edn
             (assoc ::request-edn-row-view (fulcro/get-initial-state data-viewer/DataViewer request-edn)))
           props))

  static om/Ident
  (ident [_ props] [::request-id (::request-id props)])

  static om/IQuery
  (query [_] [::request-id ::request-edn ::request-edn-row-view ::response-edn
              ::request-started-at ::request-finished-at ::error])

  static css/CSS
  (local-rules [_]
    (let [border (str "1px solid " ui/color-bg-light-border)]
      [[:.row {:cursor  "pointer"
               :display "flex"}
        [(gs/& (gs/nth-child :odd)) {:background ui/color-bg-light}]
        [:&:hover {:background (str ui/color-row-hover "!important")}]
        [:&.error {:color "#e80000"}]
        [:&.selected {:background (str ui/color-row-selected "!important")}]]
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
    (let [{::keys [request-edn-row-view response-edn error
                   request-started-at request-finished-at] :as props} (om/props this)
          {::keys [columns on-select selected?]} (om/get-computed props)
          css (css/get-classnames Request)]
      (dom/div (cond-> {:className (cond-> (:row css)
                                     error (str " " (:error css))
                                     selected? (str " " (:selected css)))}
                 on-select (assoc :onClick #(on-select (h/query-component this)))
                 true clj->js)
        (dom/div #js {:className (:table-cell css)
                      :style     #js {:width (get columns 0)}}
          (dom/span #js {:className (:timestamp css)} (ui/print-timestamp request-started-at)))
        (dom/div #js {:className (str (:table-cell css) " " (:flex css))}
          (data-viewer/data-viewer (assoc request-edn-row-view ::data-viewer/static? true)))
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

  static om/Ident
  (ident [_ props] [::history-id (::history-id props)])

  static om/IQuery
  (query [_] [::history-id
              {::requests (om/get-query Request)}
              {::active-request (om/get-query RequestDetails)}])

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
                      :overflow-y "scroll"}]

       [:.tools {:border-bottom "1px solid #dadada"
                 :display       "flex"
                 :align-items   "center"}]

       [:.tool-separator {:background "#ccc"
                          :width      "1px"
                          :height     "16px"
                          :margin     "0 6px"}]

       [:.active-request ui/css-dock-details-container]
       [:.active-container ui/css-dock-details-item-container]
       [:.active-tools ui/css-dock-details-tools]
       [:.icon ui/css-icon
        [:&:hover {:text-shadow (str "0 0 0 " ui/color-icon-strong)}]]
       [:.icon-close ui/css-icon-close]]))
  (include-children [_] [Request RequestDetails])

  Object
  (render [this]
    (let [{::keys [requests active-request]} (om/props this)
          css     (css/get-classnames NetworkHistory)
          columns [100 90 70]]
      (dom/div #js {:className (:container css)}
        (dom/div #js {:className (:tools css)}
          (dom/div #js {:className (:icon css)
                        :title     "Clear requests"
                        :style     #js {:fontSize "15px"}
                        :onClick   #(om/transact! this [`(clear-requests {})])}
            "🚫"))

        (dom/div #js {:className (:table css)}
          (dom/div #js {:className (:table-header css)}
            (dom/div #js {:style #js {:width (get columns 0)}} "Started")
            (dom/div #js {:className (:flex css)} "Request")
            (dom/div #js {:style #js {:width (get columns 1)}} "Status")
            (dom/div #js {:style #js {:width (get columns 2)}} "Time"))

          (dom/div #js {:className (:table-body css)}
            (if (seq requests)
              (mapv (comp request
                          #(om/computed %
                             {::columns
                              columns

                              ::selected?
                              (= (::request-id active-request) (::request-id %))

                              ::on-select
                              (fn [r] (om/transact! this `[(select-request ~r)]))}))
                requests))))

        (if active-request
          (dom/div #js {:className (:active-request css)}
            (dom/div #js {:className (:active-tools css)}
              (dom/div #js {:style #js {:flex 1}})
              (dom/div #js {:className (str (css :icon) " " (css :icon-close))
                            :title     "Close panel"
                            :onClick   #(mutations/set-value! this ::active-request nil)}
                "❌"))
            (dom/div #js {:className (:active-container css)}
              (request-details active-request))))))))

(def network-history (om/factory NetworkHistory))

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

(defmutation request-start [{::keys [remote] :as request}]
  (action [env]
    (h/create-entity! env Request request :append ::requests)
    (h/swap-entity! env update ::remotes conj remote)
    (h/swap-entity! env update ::requests #(->> (take-last 50 %) vec))))

(defmutation request-finish [{::keys [response-edn error] :as request}]
  (action [env]
    (let [{:keys [ref state] :as env} env]
      (when (get-in @state (om/ident Request request)) ; prevent adding back a done request
        (when (get-in @state (conj ref :ui/request-edn-view))
          (if response-edn
            (h/create-entity! env data-viewer/DataViewer response-edn :set :ui/response-edn-view))
          (if error
            (h/create-entity! env data-viewer/DataViewer error :set :ui/error-view)))

        (swap! state h/merge-entity Request (assoc request ::request-finished-at (js/Date.)))))))

(defmutation select-request [{::keys [request-edn response-edn error] :as request}]
  (action [env]
    (let [{:keys [state] :as env} env
          req-ref (om/ident Request request)]
      (if-not (get-in @state (conj req-ref :ui/request-edn-view))
        (let [env' (assoc env :ref req-ref)]
          (h/create-entity! env' data-viewer/DataViewer request-edn :set :ui/request-edn-view)
          (if response-edn
            (h/create-entity! env' data-viewer/DataViewer response-edn :set :ui/response-edn-view))
          (if error
            (h/create-entity! env' data-viewer/DataViewer error :set :ui/error-view))))
      (h/swap-entity! env assoc ::active-request req-ref))))

(defmutation clear-requests [_]
  (action [env]
    (h/swap-entity! env assoc ::active-request nil ::remotes #{})
    (h/remove-edge! env ::requests)))

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
  (local-rules [_] [])
  (include-children [_] [])

  Object
  (render [this]
    (let [{:ui/keys [request-edn-view response-edn-view error-view]} (om/props this)
          css (css/get-classnames RequestDetails)]
      (dom/div #js {:className (:container css)}
        (ui/info {::ui/title "Request"}
          (data-viewer/data-viewer request-edn-view))

        (if response-edn-view
          (ui/info {::ui/title "Response"}
            (data-viewer/data-viewer response-edn-view)))

        (if error-view
          (ui/info {::ui/title "Error"}
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
  (query [_] [::request-id ::request-edn ::request-edn-row-view ::response-edn ::remote
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
    (let [{::keys [request-edn-row-view response-edn error remote
                   request-started-at request-finished-at] :as props} (om/props this)
          {::keys [columns on-select selected? show-remote?]} (om/get-computed props)
          css (css/get-classnames Request)]
      (dom/div (cond-> {:className (cond-> (:row css)
                                     error (str " " (:error css))
                                     selected? (str " " (:selected css)))}
                 on-select (assoc :onClick #(on-select (h/query-component this)))
                 true clj->js)
        (dom/div #js {:className (:table-cell css)
                      :style     #js {:width (:started columns)}}
          (dom/span #js {:className (:timestamp css)} (ui/print-timestamp request-started-at)))
        (dom/div #js {:className (str (:table-cell css) " " (:flex css))}
          (data-viewer/data-viewer (assoc request-edn-row-view ::data-viewer/static? true)))
        (if show-remote?
          (dom/div #js {:className (:table-cell css)
                        :style     #js {:width (:remote columns)}}
            (str remote)))
        (dom/div #js {:className (:table-cell css)
                      :style     #js {:width (:status columns)}}
          (cond
            response-edn
            "Success"

            error
            "Error"

            :else
            (dom/span #js {:className (:pending css)} "(pending...)")))
        (dom/div #js {:className (:table-cell css)
                      :style     #js {:width (:time columns)}}
          (if (and request-started-at request-finished-at)
            (str (- (.getTime request-finished-at) (.getTime request-started-at)) " ms")
            (dom/span #js {:className (:pending css)} "(pending...)")))))))

(def request (om/factory Request))

(om/defui ^:once NetworkHistory
  static fulcro/InitialAppState
  (initial-state [_ _]
    {::history-id (random-uuid)
     ::remotes    #{}
     ::requests   []})

  static om/Ident
  (ident [_ props] [::history-id (::history-id props)])

  static om/IQuery
  (query [_] [::history-id ::remotes
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
                      :overflow-y "scroll"}]]))
  (include-children [_] [Request RequestDetails ui/CSS])

  Object
  (render [this]
    (let [{::keys [requests active-request remotes]} (om/props this)
          css          (css/get-classnames NetworkHistory)
          show-remote? (> (count remotes) 1)
          columns      {:started 100
                        :remote  80
                        :status  90
                        :time    70}]
      (dom/div #js {:className (:container css)}
        (ui/toolbar {}
          (ui/toolbar-action {:title   "Clear requests"
                              :onClick #(om/transact! this [`(clear-requests {})])}
            (ui/icon :do_not_disturb)))

        (dom/div #js {:className (:table css)}
          (dom/div #js {:className (:table-header css)}
            (dom/div #js {:style #js {:width (:started columns)}} "Started")
            (dom/div #js {:className (:flex css)} "Request")
            (if show-remote?
              (dom/div #js {:style #js {:width (:remote columns)}} "Remote"))
            (dom/div #js {:style #js {:width (:status columns)}} "Status")
            (dom/div #js {:style #js {:width (:time columns)}} "Time"))

          (dom/div #js {:className (:table-body css)}
            (if (seq requests)
              (->> requests
                   rseq
                   (mapv (comp request
                               #(om/computed %
                                  {::show-remote?
                                   show-remote?

                                   ::columns
                                   columns

                                   ::selected?
                                   (= (::request-id active-request) (::request-id %))

                                   ::on-select
                                   (fn [r] (om/transact! this `[(select-request ~r)]))})))))))

        (if active-request
          (ui/focus-panel {}
            (ui/toolbar {::ui/classes [:details]}
              (ui/toolbar-spacer)
              (ui/toolbar-action {:title   "Close panel"
                                  :onClick #(mutations/set-value! this ::active-request nil)}
                (ui/icon :clear)))
            (ui/focus-panel-content {}
              (request-details active-request))))))))

(def network-history (om/factory NetworkHistory))

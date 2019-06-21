(ns fulcro.inspect.ui.network
  (:require [com.wsscode.oge.ui.flame-graph :as ui.flame]
            [com.wsscode.pathom.profile :as pp]
            [com.wsscode.pathom.viz.trace :as trace]
            [fulcro-css.css :as css]
            [fulcro-css.css-protocols :as cssp]
            [fulcro.client.localized-dom :as dom]
            [fulcro.client.mutations :as fm :refer-macros [defmutation]]
            [fulcro.client.primitives :as fp]
            [fulcro.inspect.helpers :as h]
            [fulcro.inspect.ui.core :as ui]
            [fulcro.inspect.ui.data-viewer :as data-viewer]
            [garden.selectors :as gs]
            [fulcro.inspect.ui.transactions :as transactions]))

(declare Request)

(defmutation request-start [{::keys [remote] :as request}]
  (action [env]
    (h/create-entity! env Request request :append ::requests)
    (h/swap-entity! env update ::remotes conj remote)
    (h/swap-entity! env update ::requests #(->> (take-last 50 %) vec))))

(defmutation request-finish [{::keys [response-edn error request-finished-at] :as request}]
  (action [env]
    (let [{:keys [state] :as env} env
          request-ref (fp/ident Request request)
          env'        (assoc env :ref request-ref)]
      (when (get-in @state request-ref)                     ; prevent adding back a cleared request
        (when (get-in @state (conj request-ref :ui/request-edn-view))
          (if response-edn
            (h/create-entity! env' data-viewer/DataViewer response-edn :set :ui/response-edn-view))
          (if error
            (h/create-entity! env' data-viewer/DataViewer error :set :ui/error-view)))

        (swap! state h/merge-entity Request (cond-> request
                                              (not request-finished-at)
                                              (assoc ::request-finished-at (js/Date.))))))))

(defmutation select-request [{::keys [request-edn response-edn error] :as request}]
  (action [env]
    (let [{:keys [state] :as env} env
          req-ref (fp/ident Request request)]
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

(defn send-to-query [this app-uuid query]
  (fp/transact! (fp/get-reconciler this)
    [:fulcro.inspect.ui.multi-oge/id [:fulcro.inspect.core/app-uuid app-uuid]]
    [`(fulcro.inspect.ui.multi-oge/set-active-query {:query ~(h/pprint query)})])

  (fp/transact! (fp/get-reconciler this)
    [:fulcro.inspect.ui.inspector/id app-uuid]
    [`(fm/set-props {:fulcro.inspect.ui.inspector/tab :fulcro.inspect.ui.inspector/page-oge})]))

(fm/defmutation log-trace-details [_]
  (remote [env]
    (h/remote-mutation env 'console-log)))

(fp/defsc RequestDetails
  [this
   {:ui/keys [request-edn-view response-edn-view error-view]}
   {:fulcro.inspect.core/keys [app-uuid]
    :keys                     [parent]}]
  {:ident       [::request-id ::request-id]
   :query       [::request-id ::request-edn ::response-edn ::request-started-at ::request-finished-at ::error
                 {:ui/request-edn-view (fp/get-query data-viewer/DataViewer)}
                 {:ui/response-edn-view (fp/get-query data-viewer/DataViewer)}
                 {:ui/error-view (fp/get-query data-viewer/DataViewer)}]
   :css         [[:.container ui/css-flex-column]
                 [:.flame {:background "#f6f7f8"
                           :width      "400px"}]
                 [:.trace {:display     "flex"
                           :min-height  "300px"
                           :flex        "1"
                           :margin-top  "4px"
                           :padding-top "18px"
                           :border-top  "1px solid #eee"}]
                 [:.send-query {:margin-left "5px"}]]
   :css-include [trace/D3Trace]}
  (dom/div :.container
    (ui/info {::ui/title (dom/div
                           "Request"
                           (dom/button :.send-query {:onClick #(send-to-query this app-uuid (::data-viewer/content request-edn-view))}
                             "Send to query"))}
      (data-viewer/data-viewer request-edn-view))

    (if response-edn-view
      (ui/info {::ui/title "Response"}
        (data-viewer/data-viewer response-edn-view)))

    (if error-view
      (ui/info {::ui/title "Error"}
        (data-viewer/data-viewer error-view)))

    (if-let [profile (-> response-edn-view ::data-viewer/content ::pp/profile)]
      (ui/info {::ui/title "Profile"}
        (dom/div :.flame (ui.flame/flame-graph {:profile profile}))))

    (if-let [trace (-> response-edn-view ::data-viewer/content :com.wsscode.pathom/trace)]
      (dom/div :.trace (trace/d3-trace {::trace/trace-data      trace
                                        ::trace/on-show-details #(fp/transact! parent [`(log-trace-details {:log-js ~%})])})))))

(def request-details (fp/factory RequestDetails))

(fp/defui ^:once Request
  static fp/InitialAppState
  (initial-state [_ {::keys [request-edn request-started-at] :as props}]
    (merge (cond-> {::request-id         (random-uuid)
                    ::request-started-at (js/Date.)}
             request-edn
             (assoc ::request-edn-row-view (fp/get-initial-state data-viewer/DataViewer request-edn))

             request-started-at
             (assoc ::request-started-at request-started-at))
      props))

  static fp/Ident
  (ident [_ props] [::request-id (::request-id props)])

  static fp/IQuery
  (query [_] [::request-id ::request-edn ::request-edn-row-view ::response-edn ::remote
              ::request-started-at ::request-finished-at ::error])

  static cssp/CSS
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
                      :padding       "2px 2px"
                      :overflow      "hidden"}
        [:$fulcro_inspect_ui_data-viewer_DataViewer__container {:max-width "100"}]
        [:&.flex {:flex 1}]
        [(gs/& gs/last-child) {:border-right "0"}]]

       [:.timestamp ui/css-timestamp]]))
  (include-children [_] [data-viewer/DataViewer])

  Object
  (render [this]
    (let [{::keys [request-edn-row-view response-edn error remote
                   request-started-at request-finished-at] :as props} (fp/props this)
          {::keys [columns on-select selected? show-remote?]} (fp/get-computed props)
          css (css/get-classnames Request)]
      (dom/div (cond-> {:className (cond-> (:row css)
                                     error (str " " (:error css))
                                     selected? (str " " (:selected css)))}
                 on-select (assoc :onClick #(on-select (h/query-component this)))
                 true clj->js)
        (dom/div {:className (:table-cell css)
                  :style     {:width    (:started columns)
                              :position "relative"}}
          (dom/span {:style     {:position  "absolute"
                                 :transform "translate(-50%, -50%)"
                                 :left      "50%"
                                 :top       "50%"}
                     :className (:timestamp css)} (ui/print-timestamp request-started-at)))
        (dom/div :.table-cell.flex {}
          (let [{::data-viewer/keys [content]} request-edn-row-view]
            (transactions/format-tx content)))
        (if show-remote?
          (dom/div {:className (:table-cell css)
                    :style     {:width (:remote columns)}}
            (str remote)))
        (dom/div {:className (:table-cell css)
                  :style     {:width (:status columns)}}
          (cond
            response-edn
            "Success"

            error
            "Error"

            :else
            (dom/span {:className (:pending css)} "(pending...)")))
        (dom/div {:className (:table-cell css)
                  :style     {:width (:time columns)}}
          (if (and request-started-at request-finished-at)
            (str (- (.getTime request-finished-at) (.getTime request-started-at)) " ms")
            (dom/span {:className (:pending css)} "(pending...)")))))))

(def request (fp/factory Request {:keyfn ::request-id}))

(fp/defui ^:once NetworkHistory
  static fp/InitialAppState
  (initial-state [_ _]
    {::history-id (random-uuid)
     ::remotes    #{}
     ::requests   []})

  static fp/Ident
  (ident [_ props] [::history-id (::history-id props)])

  static fp/IQuery
  (query [_] [::history-id ::remotes
              {::requests (fp/get-query Request)}
              {::active-request (fp/get-query RequestDetails)}])

  static cssp/CSS
  (local-rules [_]
    (let [border (str "1px solid " ui/color-bg-medium-border)]
      [[:.container {:flex           1
                     :display        "flex"
                     :flex-direction "column"
                     :width          "100%"}
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
    (let [{::keys [requests active-request remotes]} (fp/props this)
          css          (css/get-classnames NetworkHistory)
          show-remote? (> (count remotes) 1)
          columns      {:started 100
                        :remote  80
                        :status  90
                        :time    70}]
      (dom/div {:className (:container css)}
        (ui/toolbar {}
          (ui/toolbar-action {:title   "Clear requests"
                              :onClick #(fp/transact! this [`(clear-requests {})])}
            (ui/icon :do_not_disturb)))

        (dom/div {:className (:table css)}
          (dom/div {:className (:table-header css)}
            (dom/div {:style {:width (:started columns)}} "Started")
            (dom/div {:className (:flex css)} "Request")
            (if show-remote?
              (dom/div {:style {:width (:remote columns)}} "Remote"))
            (dom/div {:style {:width (:status columns)}} "Status")
            (dom/div {:style {:width (:time columns)}} "Time"))

          (dom/div {:className (:table-body css)}
            (if (seq requests)
              (->> requests
                rseq
                (mapv (comp request
                        #(fp/computed %
                           {::show-remote?
                            show-remote?

                            ::columns
                            columns

                            ::selected?
                            (= (::request-id active-request) (::request-id %))

                            ::on-select
                            (fn [r] (fp/transact! this `[(select-request ~r)]))})))))))

        (if active-request
          (ui/focus-panel {:style {:height (str (or (fp/get-state this :detail-height) 400) "px")}}
            (ui/drag-resize this {:attribute :detail-height :default 400}
              (ui/toolbar {::ui/classes [:details]}
                (ui/toolbar-spacer)
                (ui/toolbar-action {:title   "Close panel"
                                    :onClick #(fm/set-value! this ::active-request nil)}
                  (ui/icon :clear))))
            (ui/focus-panel-content {}
              (request-details (fp/computed active-request {:fulcro.inspect.core/app-uuid (h/comp-app-uuid this)
                                                            :parent                       this})))))))))

(def network-history (fp/factory NetworkHistory))

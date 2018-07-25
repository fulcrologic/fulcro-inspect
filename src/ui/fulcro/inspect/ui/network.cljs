(ns fulcro.inspect.ui.network
  (:require [garden.selectors :as gs]
            [fulcro.client.mutations :as mutations :refer-macros [defmutation]]
            [fulcro-css.css :as css]
            [fulcro.inspect.helpers :as h]
            [fulcro.inspect.ui.core :as ui :refer [colors]]
            [fulcro.inspect.ui.data-viewer :as data-viewer]
            [fulcro.client.localized-dom :as dom]
            [fulcro.client.primitives :as fp]
            [com.wsscode.oge.ui.flame-graph :as ui.flame]
            [com.wsscode.pathom.profile :as pp]))

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
          env' (assoc env :ref request-ref)]
      (when (get-in @state request-ref) ; prevent adding back a cleared request
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

(fp/defsc RequestDetails
  [this
   {:ui/keys [request-edn-view response-edn-view error-view]}]
  {:ident [::request-id ::request-id]
   :query [::request-id ::request-edn ::response-edn ::request-started-at ::request-finished-at ::error
           {:ui/request-edn-view (fp/get-query data-viewer/DataViewer)}
           {:ui/response-edn-view (fp/get-query data-viewer/DataViewer)}
           {:ui/error-view (fp/get-query data-viewer/DataViewer)}]
   :css   [[:.flame {:background (:chart-bg-flame colors)
                     :width      "400px"}]]}
  (dom/div
    (ui/info {::ui/title "Request"}
      (data-viewer/data-viewer request-edn-view))

    (if response-edn-view
      (ui/info {::ui/title "Response"}
        (data-viewer/data-viewer response-edn-view)))

    (if error-view
      (ui/info {::ui/title "Error"}
        (data-viewer/data-viewer error-view)))

    (if-let [profile (-> response-edn-view ::data-viewer/content ::pp/profile)]
      (ui/info {::ui/title "Profile"}
        (dom/div :.flame (ui.flame/flame-graph {:profile profile}))))))

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

  static css/CSS
  (local-rules [_]
    (let [border (str "1px solid " (:bg-light-border colors))]
      [[:.row {:cursor  "pointer"
               :display "flex"
               :color   (:text colors)}
        [(gs/& (gs/nth-child :odd)) {:background (:bg-light colors)}]
        [:&:hover {:background (str (:row-hover colors) "!important")}]
        [:&.error {:color (:error colors)}]
        [:&.selected {:background (str (:row-selected colors) "!important")}]]
       [:.pending {:color (:text-faded colors)}]

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
                   request-started-at request-finished-at] :as props} (fp/props this)
          {::keys [columns on-select selected? show-remote?]} (fp/get-computed props)
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

  static css/CSS
  (local-rules [_]
    (let [border (str "1px solid " (:bg-medium-border colors))]
      [[:.container {:flex           1
                     :display        "flex"
                     :flex-direction "column"
                     :width          "100%"}
        [:* {:box-sizing "border-box"}]]
       [:.table {:font-family     ui/label-font-family
                 :font-size       ui/label-font-size
                 :width           "100%"
                 :border-collapse "collapse"
                 :color           (:text-table colors)
                 :flex            "1"
                 :display         "flex"
                 :flex-direction  "column"}]

       [:.table-header {:display       "flex"
                        :overflow-y    "scroll"
                        :border-bottom border
                        :color         (:text-secondary colors)}]

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
          (ui/focus-panel {}
            (ui/toolbar {::ui/classes [:details]}
              (ui/toolbar-spacer)
              (ui/toolbar-action {:title   "Close panel"
                                  :onClick #(mutations/set-value! this ::active-request nil)}
                (ui/icon :clear)))
            (ui/focus-panel-content {}
              (request-details active-request))))))))

(def network-history (fp/factory NetworkHistory))

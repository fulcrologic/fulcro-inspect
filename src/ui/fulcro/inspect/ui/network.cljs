(ns fulcro.inspect.ui.network
  (:require
    [com.fulcrologic.fulcro-css.localized-dom :as dom]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.components :as fp]
    [com.fulcrologic.fulcro.mutations :as fm :refer-macros [defmutation]]
    [com.wsscode.oge.ui.flame-graph :as ui.flame]
    [com.wsscode.pathom.profile :as pp]
    [fulcro.inspect.helpers :as h]
    [fulcro.inspect.ui.core :as ui]
    [fulcro.inspect.ui.data-viewer :as data-viewer]
    [fulcro.inspect.ui.transactions :as transactions]
    [garden.selectors :as gs]
    [taoensso.timbre :as log]))

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
            (h/create-entity! env' data-viewer/DataViewer {:content response-edn} :replace :ui/response-edn-view))
          (if error
            (h/create-entity! env' data-viewer/DataViewer {:content error} :replace :ui/error-view)))

        (swap! state merge/merge-component Request (cond-> request
                                                     (not request-finished-at)
                                                     (assoc ::request-finished-at (js/Date.))))))))

(defmutation select-request [{::keys [request-edn response-edn error] :as request}]
  (action [env]
    (let [{:keys [state] :as env} env
          req-ref (log/spy :info (fp/ident Request request))]
      (if-not (get-in @state (conj req-ref :ui/request-edn-view))
        (let [env' (assoc env :ref req-ref)]
          (h/create-entity! env' data-viewer/DataViewer {:content request-edn} :replace :ui/request-edn-view)
          (if (log/spy :info response-edn)
            (h/create-entity! env' data-viewer/DataViewer {:content response-edn} :replace :ui/response-edn-view))
          (if error
            (h/create-entity! env' data-viewer/DataViewer {:content error} :replace :ui/error-view))))
      (h/swap-entity! env assoc ::active-request req-ref))))

(defmutation clear-requests [_]
  (action [env]
    (h/swap-entity! env assoc ::active-request nil ::remotes #{})
    (h/remove-edge! env ::requests)))

(defn send-to-query [this app-uuid query]
  (fp/transact! this
    `[(fulcro.inspect.ui.multi-oge/set-active-query {:query ~(h/pprint query)})]
    {:ref [:fulcro.inspect.ui.multi-oge/id [:fulcro.inspect.core/app-uuid app-uuid]]})

  (fp/transact! this
    [(fm/set-props {:fulcro.inspect.ui.inspector/tab :fulcro.inspect.ui.inspector/page-oge})]
    {:ref [:fulcro.inspect.ui.inspector/id app-uuid]}))

(fp/defsc RequestDetails
  [this
   {:ui/keys [request-edn-view response-edn-view error-view]}
   {:fulcro.inspect.core/keys [app-uuid]}]
  {:ident [::request-id ::request-id]
   :query [::request-id ::request-edn ::response-edn ::request-started-at ::request-finished-at ::error
           {:ui/request-edn-view (fp/get-query data-viewer/DataViewer)}
           {:ui/response-edn-view (fp/get-query data-viewer/DataViewer)}
           {:ui/error-view (fp/get-query data-viewer/DataViewer)}]
   :css   [[:.container ui/css-flex-column]
           [:.flame {:background "#f6f7f8"
                     :width      "400px"}]
           [:.trace {:display     "flex"
                     :min-height  "300px"
                     :flex        "1"
                     :margin-top  "4px"
                     :padding-top "18px"
                     :border-top  "1px solid #eee"}]
           [:.send-query {:margin-left "5px"}]]}
  (dom/div :.container
    (ui/info {::ui/title (dom/div
                           "Request"
                           (dom/button :.send-query
                             {:onClick #(send-to-query this app-uuid (::data-viewer/content request-edn-view))}
                             "Send to query"))}
      (dom/pre {:style {:fontSize "10pt"}}
        (h/pprint (::data-viewer/content request-edn-view))))

    (if response-edn-view
      (ui/info {::ui/title "Response"}
        (dom/pre {:style {:fontSize "10pt"}}
          (h/pprint (::data-viewer/content response-edn-view)))))

    (if error-view
      (ui/info {::ui/title "Error"}
        (dom/pre {:style {:fontSize "10pt"}}
          (h/pprint (::data-viewer/content error-view)))))

    (if-let [profile (-> response-edn-view ::data-viewer/content ::pp/profile)]
      (ui/info {::ui/title "Profile"}
        (dom/div :.flame (ui.flame/flame-graph {:profile profile}))))))

(def request-details (fp/factory RequestDetails))

(fp/defsc Request
  [this
   {::keys [request-edn-row-view response-edn error remote
            request-started-at request-finished-at]}
   {::keys [columns on-select selected? show-remote?]}
   css]
  {:initial-state (fn [{::keys [request-edn request-started-at] :as props}]
                    (merge (cond-> {::request-id         (random-uuid)
                                    ::request-started-at (js/Date.)}
                             request-edn
                             (assoc ::request-edn-row-view (fp/get-initial-state data-viewer/DataViewer {:content request-edn}))

                             request-started-at
                             (assoc ::request-started-at request-started-at))
                      props))
   :ident         [::request-id ::request-id]
   :query         [::request-id ::request-edn ::request-edn-row-view ::response-edn ::remote
                   ::request-started-at ::request-finished-at ::error]
   :css           (fn []
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

                       [:.request {:overflow   "auto"
                                   :max-height "250px"}]

                       [:.timestamp ui/css-timestamp
                        {:margin "0 4px 0 2px"}]]))
   :css-include   [data-viewer/DataViewer]}
  (dom/div (cond-> {:className (cond-> (:row css)
                                 error (str " " (:error css))
                                 selected? (str " " (:selected css)))}
             on-select (assoc :onClick #(on-select (h/query-component this)))
             true clj->js)
    (dom/div :.table-cell {:style {:width    (:started columns)
                                   :position "relative"}}
      (dom/span :.timestamp {:style {:position "absolute" :transform "translate(-50%, -50%)"
                                     :left     "50%"
                                     :top      "50%"}}
        (ui/print-timestamp request-started-at)))
    (dom/div :.table-cell.flex.request {}
      (let [{::data-viewer/keys [content]} request-edn-row-view]
        (transactions/tx-printer {::transactions/content content})))
    (if show-remote?
      (dom/div :.table-cell {:style {:width (:remote columns)}}
        (str remote)))
    (dom/div :.table-cell {:style {:width (:status columns)}}
      (cond
        response-edn
        "Success"

        error
        "Error"

        :else
        (dom/span :.pending {} "(pending...)")))
    (dom/div :.table-cell {:style {:width (:time columns)}}
      (if (and request-started-at request-finished-at)
        (str (- (.getTime request-finished-at) (.getTime request-started-at)) " ms")
        (dom/span :.pending {} "(pending...)")))))
(def request (fp/computed-factory Request {:keyfn ::request-id}))

(fp/defsc NetworkHistory
  [this {::keys [requests active-request remotes]} _ css]
  {:initial-state (fn [_]
                    {::history-id (random-uuid)
                     ::remotes    #{}
                     ::requests   []})
   :ident         [::history-id ::history-id]
   :query         [::history-id ::remotes
                   {::requests (fp/get-query Request)}
                   {::active-request (fp/get-query RequestDetails)}]
   :css           (fn []
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
                                 :flex-direction  "column"
                                 :min-height      "80px"}]

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
   :css-include   [Request RequestDetails ui/CSS]}
  (let [show-remote? (> (count remotes) 1)
        columns      {:started 100
                      :remote  80
                      :status  90
                      :time    70}]
    (dom/div :.container
      (ui/toolbar {}
        (ui/toolbar-action {:title   "Clear requests"
                            :onClick #(fp/transact! this [`(clear-requests {})])}
          (ui/icon :do_not_disturb)))

      (dom/div :.table
        (dom/div :.table-header
          (dom/div {:style {:width (:started columns)}} "Started")
          (dom/div :.flex "Request")
          (if show-remote?
            (dom/div {:style {:width (:remote columns)}} "Remote"))
          (dom/div {:style {:width (:status columns)}} "Status")
          (dom/div {:style {:width (:time columns)}} "Time"))

        (dom/div :.table-body
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
                                                          :parent                       this}))))))))

(def network-history (fp/factory NetworkHistory))

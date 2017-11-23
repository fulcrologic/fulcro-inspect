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

(defn now []
  (.getTime (js/Date.)))

(defmutation request-start [req]
  (action [env]
    (h/create-entity! env Request req :append ::requests)))

(defmutation request-update [req]
  (action [env]
    (let [{:keys [state]} env]
      (swap! state h/merge-entity Request (assoc req ::request-finished-at (now))))))

(defn pprint [s]
  (with-out-str (cljs.pprint/pprint s)))

(defn pretty-first-line [text]
  (-> text pprint str/split-lines first))

(defn request-type [edn]
  (if edn
    (let [types (->> (om/query->ast edn) :children
                     (mapv :type))]
      (cond
        (every? #{:call} types)
        ::type.mutation

        (some #{:call} types)
        ::type.mixed

        :else
        ::type.query))))

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
                    ::request-started-at (now)}
             request-edn
             (assoc ::request-edn-view (fulcro/get-initial-state data-viewer/DataViewer request-edn)
                    ::request-type (request-type request-edn)))
           props))

  static om/IQuery
  (query [_] [::request-id ::request-edn ::request-edn-view ::response-edn ::request-type
              ::request-started-at ::request-finished-at ::error])

  static om/Ident
  (ident [_ props] [::request-id (::request-id props)])

  static css/CSS
  (local-rules [_]
    (let [border (str "1px solid " ui/color-bg-light-border)]
      [[:.row {:cursor  "pointer"
               :display "flex"}
        [(gs/& (gs/nth-child :odd)) {:background ui/color-bg-light}]
        [:&:hover {:background "#eef3fa !important"}]]
       [:.pending {:color ui/color-text-faded}]

       [:.table-cell {:border-left   border
                      :border-bottom border
                      :padding       "2px 4px"
                      :overflow      "hidden"}
        [:$fulcro_inspect_ui_data-viewer_DataViewer__container {:max-width "100"}]
        [(gs/& gs/first-child) {:flex 1}]
        [(gs/& gs/last-child) {:border-right border}]]]))
  (include-children [_] [data-viewer/DataViewer])

  Object
  (render [this]
    (let [{::keys [request-edn-view response-edn error request-type
                   request-started-at request-finished-at] :as props} (om/props this)
          columns (om/get-computed props :columns)
          css     (css/get-classnames Request)]
      (dom/div #js {:className (:row css)}
        (dom/div #js {:className (:table-cell css)}
          (data-viewer/data-viewer (assoc request-edn-view ::data-viewer/static? true)))
        (dom/div #js {:className (:table-cell css)
                      :style     #js {:width (get columns 0)}}
          (case request-type
            ::type.mutation "Mutation"
            ::type.mixed "Mixed"
            ::type.query "Query"

            "Unknown"))
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
            (str (- request-finished-at request-started-at) " ms")
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
    (let [border (str "1px solid " ui/color-bg-light-border)]
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

       [:.table-header {:display    "flex"
                        :overflow-y "scroll"}]

       [(gs/> :.table-header "div") {:font-weight  "normal"
                                     :text-align   "left"
                                     :padding      "5px 4px"
                                     :border       border
                                     :border-right "0"}
        [(gs/& gs/first-child) {:flex 1}]
        [(gs/& gs/last-child) {:border-right border}]]

       [:.table-body {:flex       1
                      :overflow-y "scroll"}]]))
  (include-children [_] [Request])

  Object
  (render [this]
    (let [{::keys [requests]} (om/props this)
          css     (css/get-classnames NetworkHistory)
          columns [60 90 70]]
      (dom/div #js {:className (:container css)}
        (if (seq requests)
          (dom/div #js {:className (:table css)}
            (dom/div #js {:className (:table-header css)}
              (dom/div nil "Request")
              (dom/div #js {:style #js {:width (get columns 0)}} "Type")
              (dom/div #js {:style #js {:width (get columns 1)}} "Status")
              (dom/div #js {:style #js {:width (get columns 2)}} "Time"))
            (dom/div #js {:className (:table-body css)}
              (mapv (comp request #(om/computed % {:columns columns})) requests))))))))

(def network-history (om/factory NetworkHistory))

(comment
  (println
    (garden/css [["a" {:color "#000"}]
                 [(gs/> "a" "b") {:color "#fff"}
                  [:div
                   [(gs/& gs/last-child) {:flex 1}]]]])))

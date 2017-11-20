(ns fulcro.inspect.ui.network
  (:require [clojure.string :as str]
            [fulcro.client.core :as fulcro :refer-macros [defsc]]
            [fulcro.client.mutations :refer-macros [defmutation]]
            [fulcro-css.css :as css]
            [fulcro.inspect.helpers :as h]
            [fulcro.inspect.ui.core :as f.i.ui]
            [fulcro.inspect.ui.data-viewer :as f.data-viewer]
            [om.dom :as dom]
            [om.next :as om]))

(declare Request)

(defn now []
  (.getTime (js/Date.)))

(defmutation request-start [req]
  (action [env]
    (let [{:keys [ref state]} env
          req (fulcro/get-initial-state Request req)]
      (swap! state (comp #(h/merge-entity % Request req)
                         #(update-in % ref update ::requests conj (om/ident Request req)))))))

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

(comment
  (om/query->ast [:a '(b)]))

(om/defui ^:once Request
  static fulcro/InitialAppState
  (initial-state [_ {::keys [request-edn] :as props}]
    (merge (cond-> {::request-id         (random-uuid)
                    ::request-started-at (now)}
             request-edn
             (assoc ::request-edn-short (pretty-first-line request-edn)
                    ::request-edn-pretty (pprint request-edn)
                    ::request-type (request-type request-edn)))
           props))

  static om/IQuery
  (query [_] [::request-id ::request-edn ::request-edn-pretty ::request-edn-short ::response-edn ::request-type
              ::request-started-at ::request-finished-at ::error])

  static om/Ident
  (ident [_ props] [::request-id (::request-id props)])

  static css/CSS
  (local-rules [_] [])
  (include-children [_] [f.data-viewer/DataViewer])

  Object
  (render [this]
    (let [{::keys [request-edn-short request-edn-pretty response-edn error request-type
                   request-started-at request-finished-at]} (om/props this)
          css (css/get-classnames Request)]
      (dom/tr nil
        (dom/td #js {:title request-edn-pretty} request-edn-short)
        (dom/td nil
          (case request-type
            ::type.mutation "Mutation"
            ::type.mixed "Mixed"
            ::type.query "Query"

            "Unknown"))
        (dom/td nil
          (cond
            response-edn
            "Success"

            error
            "Error"

            :else
            (dom/span #js {:className (:pending css)} "(pending...)")))
        (dom/td nil
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
  (local-rules [_] [[:.table (merge f.i.ui/label-font
                                    {})
                     [:th {:font-weight "normal"
                           :text-align  "left"}]]])
  (include-children [_] [Request])

  Object
  (render [this]
    (let [{::keys [requests]} (om/props this)
          css (css/get-classnames NetworkHistory)]
      (dom/div nil
        (if (seq requests)
          (dom/table #js {:className (:table css)}
            (dom/thead nil
              (dom/tr nil
                (dom/th nil "Request")
                (dom/th nil "Type")
                (dom/th nil "Status")
                (dom/th nil "Time")))
            (dom/tbody nil
              (mapv request requests))))))))

(def network-history (om/factory NetworkHistory))

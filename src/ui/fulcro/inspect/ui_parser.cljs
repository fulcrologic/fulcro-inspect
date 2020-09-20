(ns fulcro.inspect.ui-parser
  (:require [cljs.core.async :as async]
            [cljs.spec.alpha :as s]
            [com.wsscode.common.async-cljs :refer [go-catch <?]]
            [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.core :as p]
            [com.wsscode.pathom.profile :as pp]
            [com.wsscode.pathom.viz.index-explorer :as iex]
            [taoensso.timbre :as log]))

;; LANDMARK: This is the general code that abstract communication between the Fulcro UI and whatever environment it
;; happens to be embedded within (Chrome browser plugin or Electron app)

(s/def ::msg-id uuid?)

(def indexes (atom {}))

(defmulti resolver-fn pc/resolver-dispatch)
(def defresolver (pc/resolver-factory resolver-fn indexes))

(defmulti mutation-fn pc/mutation-dispatch)
(def defmutation (pc/mutation-factory mutation-fn indexes))

(defn client-request [{:keys [responses* send-message]} name data]
  (let [res-chan (async/promise-chan)
        msg-id   (random-uuid)]
    (swap! responses* assoc msg-id res-chan)
    (send-message name (assoc data ::msg-id msg-id))
    (async/go
      (let [[x _] (async/alts! [res-chan (async/timeout 30000)] :priority true)]
        (or x (throw (ex-info "Client request timeout"
                       {:name    name
                        :data    data
                        ::msg-id msg-id})))))))

(defresolver 'settings
  {::pc/output [:fulcro.inspect/settings]}
  (fn [{:keys [query] :as env} _]
    (go-catch
      (let [params   (-> env :ast :params)
            response (async/<! (client-request env :fulcro.inspect.client/load-settings
                                 (assoc params :query query)))]
        {:fulcro.inspect/settings response}))))

(defresolver 'oge
  {::pc/output [:>/oge ::pp/profile]}
  (fn [{:keys [query] :as env} _]
    (async/go
      (let [params    (-> env :ast :params)
            response  (async/<! (client-request env :fulcro.inspect.client/network-request
                                  (-> (select-keys params [:fulcro.inspect.core/app-uuid
                                                           :fulcro.inspect.client/remote])
                                      (assoc :query query))))
            response' (dissoc response ::pp/profile)]
        {::pp/profile (::pp/profile response)
         :>/oge       response'}))))

(defresolver 'oge-index
  {::pc/output [::pc/indexes]}
  (fn [{:keys [query] :as env} _]
    (let [params (-> env :ast :params)]
      (async/go
        (let [response (async/<! (client-request env :fulcro.inspect.client/network-request
                                   (-> (select-keys params [:fulcro.inspect.core/app-uuid
                                                            :fulcro.inspect.client/remote])
                                       (assoc :query [{::pc/indexes query}]))))]
          response)))))

(defresolver 'index-explorer
  {::pc/input  #{::iex/id}
   ::pc/output [::iex/index]}
  (fn [env {::iex/keys [id]}]
    (go-catch
      (let [params  (-> env ::p/parent-query meta :remote-data)
            indexes (<? (client-request env :fulcro.inspect.client/network-request
                          (assoc params :query [{[::iex/id id] [::iex/id ::iex/index]}])))]
        {::iex/index (get-in indexes [[::iex/id id] ::iex/index])}))))

(defmutation 'reset-app
  {}
  (fn [{:keys [send-message]} input]
    (send-message :fulcro.inspect.client/reset-app-state input)))

(defmutation 'transact
  {}
  (fn [{:keys [send-message]} input]
    (send-message :fulcro.inspect.client/transact input)))

(defmutation 'pick-element
  {}
  (fn [{:keys [send-message]} input]
    (send-message :fulcro.inspect.client/pick-element input)))

(defmutation 'fetch-history-step
  {}
  (fn [{:keys [send-message]} input]
    (log/info "Running pathom mutation to post fetch message" input)
    (send-message :fulcro.inspect.client/fetch-history-step input)))

(defmutation 'save-settings
  {}
  (fn [{:keys [send-message]} input]
    (send-message :fulcro.inspect.client/save-settings input)))

(defmutation 'show-dom-preview
  {}
  (fn [{:keys [send-message]} input]
    (send-message :fulcro.inspect.client/show-dom-preview input)))

(defmutation 'hide-dom-preview
  {}
  (fn [{:keys [send-message]} input]
    (send-message :fulcro.inspect.client/hide-dom-preview input)))

(defmutation 'console-log
  {::pc/params [:log :log-js :warn :error]}
  (fn [{:keys [send-message]} input]
    (send-message :fulcro.inspect.client/console-log input)))

(defn ident-passthrough [{:keys [ast] :as env}]
  (if (p/ident? (:key ast))
    (p/join (atom {}) (assoc env ::parent-params (:params ast)))
    ::p/continue))

(defn parser []
  (p/async-parser {::p/env     {::p/reader             [p/map-reader
                                                        pc/async-reader2
                                                        pc/ident-reader
                                                        ident-passthrough]
                                ::pc/resolver-dispatch resolver-fn
                                ::pc/mutate-dispatch   mutation-fn
                                ::pc/indexes           @indexes
                                :responses*            (atom {})
                                :send-message          (fn [msg-name data]
                                                         (js/console.warn "Send message missing implementation." msg-name data))}
                   ::p/mutate  pc/mutate-async
                   ::p/plugins [p/error-handler-plugin
                                p/request-cache-plugin]}))

(ns fulcro.inspect.ui-parser
  (:require [com.wsscode.pathom.core :as p]
            [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.profile :as pp]
            [cljs.core.async :as async]
            [cljs.spec.alpha :as s]))

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
      (let [[x c] (async/alts! [res-chan (async/timeout 10000)] :priority true)]
        (case c
          res-chan
          x

          (throw (ex-info "Client request timeout"
                   {:name    name
                    :data    data
                    ::msg-id msg-id})))))))

(defresolver 'oge
  {::pc/output [:>/oge]}
  (fn [{:keys [query] :as env} _]
    (async/go
      {:>/oge (async/<! (client-request env :fulcro.inspect.client/network-request
                          {:query                        query
                           :fulcro.inspect.core/app-uuid (-> env :ast :params :fulcro.inspect.core/app-uuid)}))})))

(defresolver 'oge-index
  {::pc/output [::pc/indexes]}
  (fn [{:keys [query] :as env} _]
    (client-request env :fulcro.inspect.client/network-request
      {:query                        [{::pc/indexes query}]
       :fulcro.inspect.core/app-uuid (-> env :ast :params :fulcro.inspect.core/app-uuid)})))

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

(defmutation 'show-dom-preview
  {}
  (fn [{:keys [send-message]} input]
    (send-message :fulcro.inspect.client/show-dom-preview input)))

(defmutation 'hide-dom-preview
  {}
  (fn [{:keys [send-message]} input]
    (send-message :fulcro.inspect.client/hide-dom-preview input)))

(def parser
  (p/async-parser {::p/env     {::p/reader             [p/map-reader pc/all-async-readers]
                                ::pc/resolver-dispatch resolver-fn
                                ::pc/mutate-dispatch   mutation-fn
                                ::pc/indexes           @indexes}
                   ::p/mutate  pc/mutate-async
                   ::p/plugins [p/error-handler-plugin
                                p/request-cache-plugin
                                pp/profile-plugin]}))

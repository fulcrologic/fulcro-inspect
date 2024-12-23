(ns fulcro.inspect.ui-parser
  (:require
    [cljs.core.async :as async]
    [cljs.spec.alpha :as s]
    [com.wsscode.common.async-cljs :refer [<? go-catch]]
    [com.wsscode.pathom.connect :as pc]
    [com.wsscode.pathom.core :as p]))

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

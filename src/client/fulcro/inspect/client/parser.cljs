(ns fulcro.inspect.client.parser
  (:require [com.wsscode.pathom.core :as p]
            [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.profile :as pp]))

(def indexes (atom {}))

(defmulti resolver-fn pc/resolver-dispatch)
(def defresolver (pc/resolver-factory resolver-fn indexes))

(defmulti mutation-fn pc/mutation-dispatch)
(def defmutation (pc/mutation-factory mutation-fn indexes))

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
  (p/parser {::p/env     {::p/reader             [p/map-reader pc/all-async-readers]
                          ::pc/resolver-dispatch resolver-fn
                          ::pc/mutate-dispatch   mutation-fn
                          ::pc/indexes           @indexes}
             ::p/mutate  pc/mutate-async
             ::p/plugins [p/error-handler-plugin
                          p/request-cache-plugin
                          pp/profile-plugin]}))

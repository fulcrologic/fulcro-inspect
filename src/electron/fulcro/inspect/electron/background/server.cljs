(ns fulcro.inspect.electron.background.server
  (:require
    [cljs.nodejs :as nodejs]))

(defonce http (nodejs/require "http"))
(defonce express (nodejs/require "express"))
(defonce socket.io (nodejs/require "socket.io"))

(defn start! [{::keys [on-ready on-connect]}]
  (let [app    (express)
        server (.Server http app)
        io     (socket.io server)]

    (.get app "/" (fn [req res] (.send res "Hello World")))
    (.on io "connection" on-connect)

    {::socket (.listen server 9421 #(on-ready server))
     ::send   (fn [msg])}))

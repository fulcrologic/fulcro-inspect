(ns fulcro.inspect.remote.transit
  (:require [cognitect.transit :as t]
            [com.cognitect.transit.types :as ty]
            [fulcro.transit :as ft]))

(deftype ErrorHandler []
  Object
  (tag [this v] "js-error")
  (rep [this v] [(ex-message v) (ex-data v)])
  (stringRep [this v] (ex-message v)))

(def unknown-tag "unknown")

(deftype DefaultHandler []
  Object
  (tag [this v] unknown-tag)
  (rep [this v] (pr-str v)))

(def write-handlers
  {cljs.core/ExceptionInfo (ErrorHandler.)
   "default"               (DefaultHandler.)})

(def read-handlers
  {"js-error" (fn [[msg data]] (ex-info msg data))})

(defn read [str]
  (let [reader (ft/reader {:handlers read-handlers})]
    (t/read reader str)))

(defn write [x]
  (let [writer (ft/writer {:handlers write-handlers})]
    (t/write writer x)))

(extend-type ty/UUID IUUID)

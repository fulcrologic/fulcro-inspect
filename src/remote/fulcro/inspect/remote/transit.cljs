(ns fulcro.inspect.remote.transit
  (:require [cognitect.transit :as t]
            [com.cognitect.transit.types :as ty]
            [fulcro.transit :as ft]))

(defn read [str]
  (let [reader (ft/reader)]
    (t/read reader str)))

(defn write [x]
  (let [writer (ft/writer)]
    (t/write writer x)))

(extend-type ty/UUID IUUID)

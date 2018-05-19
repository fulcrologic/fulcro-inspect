(ns fulcro.inspect.remote.transit
  (:require [cognitect.transit :as t]
            [fulcro.transit :as ft]))

(defn read [str]
  (let [reader (ft/reader)]
    (t/read reader str)))

(defn write [x]
  (let [writer (ft/writer)]
    (t/write writer x)))

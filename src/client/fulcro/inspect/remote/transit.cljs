(ns fulcro.inspect.remote.transit
  (:require [cognitect.transit :as t]
            [com.cognitect.transit.types :as ty]
            [fulcro.transit :as ft]
            [clojure.walk :as walk]))

(defn read [str]
  (let [reader (ft/reader)]
    (t/read reader str)))

(defn sanitize [x]
  (walk/prewalk
    (fn [x]
      (if (fn? x)
        "(fn ...)"
        x))
    x))

(defn write [x]
  (let [writer (ft/writer)]
    (t/write writer x)))

(extend-type ty/UUID IUUID)

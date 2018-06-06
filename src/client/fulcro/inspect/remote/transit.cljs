(ns fulcro.inspect.remote.transit
  (:require [cognitect.transit :as t]
            [com.cognitect.transit.types :as ty]
            [fulcro.transit :as ft]
            [clojure.walk :as walk]))

(deftype ErrorHandler []
  Object
  (tag [this v] "js-error")
  (rep [this v] [(ex-message v) (ex-data v)])
  (stringRep [this v] (ex-message v)))

(def write-handlers
  {cljs.core/ExceptionInfo (ErrorHandler.)})

(def read-handlers
  {"js-error" (fn [[msg data]] (ex-info msg data))})

(defn read [str]
  (let [reader (ft/reader {:handlers read-handlers})]
    (t/read reader str)))

(defn sanitize-fns [x]
  (walk/prewalk
    (fn [x]
      (if (fn? x)
        "(fn ...)"
        x))
    x))

(defn sanitize [writer x]
  (let [warned (atom #{})]
    (walk/postwalk
      (fn [x]
        (if (coll? x)
          x
          (try
            (t/write writer x)
            x
            (catch :default _
              (when-not (contains? @warned x)
                (js/console.warn "Fulcro inspect failed to encode" x "\nThis means Fulcro Inspect had to walk your data to sanitize information, remove non serializable values on your app db to avoid slow encodings.")
                (swap! warned conj x))

              (pr-str x)))))
      x)))

(defn write [x]
  (let [writer (ft/writer {:handlers write-handlers})]
    (try
      (t/write writer x)
      (catch :default _
        (t/write writer (sanitize writer x))))))

(extend-type ty/UUID IUUID)

(ns fulcro.inspect.lib.local-storage
  (:refer-clojure :exclude [get set!])
  (:require [cljs.reader :refer [read-string]]
            [cognitect.transit :as t]
            [fulcro.transit :as f.transit]))

(defn read-transit [s]
  (t/read (f.transit/reader) s))

(defn write-transit [x]
  (t/write (f.transit/writer) x))

(def local-storage (.-localStorage js/window))

(defn get
  ([key] (get key nil))
  ([key default]
   (if-let [value (.getItem local-storage (pr-str key))]
     (read-string value)
     default)))

(defn set! [key value]
  (.setItem local-storage (pr-str key) (pr-str value)))

(defn tget
  ([key] (tget key nil))
  ([key default]
   (if-let [value (.getItem local-storage (pr-str key))]
     (read-transit value)
     default)))

(defn tset! [key value]
  (.setItem local-storage (pr-str key) (write-transit value)))

(defn remove! [key]
  (.removeItem local-storage key))

(defn update! [key f]
  (.setItem local-storage (pr-str key) (pr-str (f (get key)))))

(comment
  (tset! [:sample "value"] {:hello "World"}))

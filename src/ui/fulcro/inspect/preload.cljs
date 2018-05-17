(ns fulcro.inspect.preload
  (:require [fulcro.inspect.remote]
            [fulcro.inspect.prefs :as prefs]))

(fulcro.inspect.remote/install (or @prefs/external-config {}))

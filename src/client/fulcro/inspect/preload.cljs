(ns fulcro.inspect.preload
  (:require [fulcro.inspect.client]
            [fulcro.inspect.prefs :as prefs]))

(fulcro.inspect.client/install (or @prefs/external-config {}))

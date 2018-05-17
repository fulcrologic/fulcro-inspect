(ns fulcro.inspect.preload
  (:require [fulcro.inspect.core]
            [fulcro.inspect.prefs :as prefs]))

(fulcro.inspect.core/install (or @prefs/external-config {}))

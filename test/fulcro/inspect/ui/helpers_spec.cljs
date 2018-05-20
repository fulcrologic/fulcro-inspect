(ns fulcro.inspect.ui.helpers-spec
  (:require [fulcro-spec.core :refer [specification behavior component assertions]]
            [fulcro.inspect.ui.helpers :as ui.h]))

(specification "normalize-id"
  (assertions
    (ui.h/normalize-name 'sym) => 'sym
    (ui.h/normalize-name :key) => :key
    (ui.h/normalize-name "str") => "str"
    (ui.h/normalize-name 'sym-0) => 'sym
    (ui.h/normalize-name :key-10) => :key
    (ui.h/normalize-name "str-42") => "str"))

(specification "ref-app-id"
  (assertions
    (ui.h/ref-app-id [:x [:y "abc"]]) => "abc"))

(ns fulcro.inspect.ui.helpers-spec
  (:require [fulcro-spec.core :refer [specification behavior component assertions]]
            [fulcro.inspect.ui.helpers :as ui.h]))

(specification "normalize-id"
  (assertions
    (ui.h/normalize-id 'sym) => 'sym
    (ui.h/normalize-id :key) => :key
    (ui.h/normalize-id "str") => "str"
    (ui.h/normalize-id 'sym-0) => 'sym
    (ui.h/normalize-id :key-10) => :key
    (ui.h/normalize-id "str-42") => "str"))

(specification "ref-app-id"
  (assertions
    (ui.h/ref-app-id [:x [:y "abc"]]) => "abc"))

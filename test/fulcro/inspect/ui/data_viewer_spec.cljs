(ns fulcro.inspect.ui.data-viewer-spec
  (:require [fulcro-spec.core :refer [specification behavior component assertions]]
            [fulcro.inspect.ui.data-viewer :as data-viewer]))

(specification "children-expandable-paths"
  (assertions
    (data-viewer/children-expandable-paths 3)
    => []

    (data-viewer/children-expandable-paths [2 3 4])
    => []

    (data-viewer/children-expandable-paths [2 {:a 1} 4])
    => [[1]]

    (data-viewer/children-expandable-paths [2 {:a 1 :b [4]} [4 [4 5]]])
    => [[1] [2] [1 :b] [2 1]]))

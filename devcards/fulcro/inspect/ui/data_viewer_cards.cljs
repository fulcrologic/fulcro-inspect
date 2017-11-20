(ns fulcro.inspect.ui.data-viewer-cards
  (:require
    [cljs.spec.alpha :as s]
    [clojure.test.check.generators :as gen]
    [devcards.core :refer-macros [defcard]]
    [fulcro-css.css :as css]
    [fulcro.client.cards :refer-macros [defcard-fulcro]]
    [fulcro.client.core :as fulcro]
    [fulcro.inspect.ui.data-viewer :as f.i.data-viewer]
    [om.dom :as dom]
    [om.next :as om]))

(defn make-root [Root app-id]
  (om/ui
    static fulcro/InitialAppState
    (initial-state [_ params] {:fulcro.inspect.core/app-id app-id
                               :ui/react-key (random-uuid)
                               :ui/root      (fulcro/get-initial-state Root params)})

    static om/IQuery
    (query [_] [:ui/react-key
                {:ui/root (om/get-query Root)}])

    static css/CSS
    (local-rules [_] [])
    (include-children [_] [Root])

    Object
    (render [this]
      (let [{:ui/keys [react-key root]} (om/props this)
            factory (om/factory Root)]
        (dom/div #js {:key react-key}
          (factory root))))))

(def DataViewerRoot (make-root f.i.data-viewer/DataViewer ::data-viewer))

(defn init-state-atom [comp data]
  (atom (om/tree->db comp (fulcro/get-initial-state comp data) true)))

(comment
  (fulcro/get-initial-state DataViewerRoot {::f.i.data-viewer/id       :root
                                            ::f.i.data-viewer/content  "Some string"
                                            ::f.i.data-viewer/expanded {}}))

(defcard-fulcro scalar-nil
  DataViewerRoot
  (init-state-atom DataViewerRoot
    nil))

(defcard-fulcro scalar-string
  DataViewerRoot
  (init-state-atom DataViewerRoot
    (gen/generate gen/string-alphanumeric)))

(defcard-fulcro scalar-number
  DataViewerRoot
  (init-state-atom DataViewerRoot
    (gen/generate (s/gen number?))))

(defcard-fulcro scalar-keyword
  DataViewerRoot
  (init-state-atom DataViewerRoot
    (gen/generate (s/gen keyword?))))

(defcard-fulcro scalar-boolean
  DataViewerRoot
  (init-state-atom DataViewerRoot
    (gen/generate gen/boolean)))

(defcard-fulcro vector-empty
  DataViewerRoot
  (init-state-atom DataViewerRoot
    []))

(defcard-fulcro vector-few
  DataViewerRoot
  (init-state-atom DataViewerRoot
    [4 2]))

(defcard-fulcro vector-many
  DataViewerRoot
  (init-state-atom DataViewerRoot
    [1 2 3 4 5 6 62 22 42 4 5 2 4 6]))

(defcard-fulcro vector-nested
  DataViewerRoot
  (init-state-atom DataViewerRoot
    [1 [3 5 12 [4 21 4 5] 25 1 4 [[[4 3 4 5] 5]]]]))

(defcard-fulcro list-empty
  DataViewerRoot
  (init-state-atom DataViewerRoot
    '()))

(defcard-fulcro list-few
  DataViewerRoot
  (init-state-atom DataViewerRoot
    '(4 2)))

(defcard-fulcro list-many
  DataViewerRoot
  (init-state-atom DataViewerRoot
    '(1 2 3 4 5 6 62 22 42 4 5 2 4 6)))

(defcard-fulcro list-nested
  DataViewerRoot
  (init-state-atom DataViewerRoot
    '(1 (3 5 12 (4 21 4 5) 25 1 4 (((4 3 4 5) 5))))))

(defcard-fulcro set-empty
  DataViewerRoot
  (init-state-atom DataViewerRoot
    #{}))

(defcard-fulcro set-few
  DataViewerRoot
  (init-state-atom DataViewerRoot
    #{"some" :data 3 "value"}))

(defcard-fulcro set-many
  DataViewerRoot
  (init-state-atom DataViewerRoot
    #{"some" :data 3 "value" 2 4.455 false nil "that" :thing}))

(defcard-fulcro map-empty
  DataViewerRoot
  (init-state-atom DataViewerRoot
    {}))

(defcard-fulcro map-data
  DataViewerRoot
  (init-state-atom DataViewerRoot
    {:a 3 :b 10 :foo {:barr ["baz" "there"]}}))

(css/upsert-css "data-viewer" f.i.data-viewer/DataViewer)

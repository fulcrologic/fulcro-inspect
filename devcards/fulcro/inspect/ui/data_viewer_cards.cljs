(ns fulcro.inspect.ui.data-viewer-cards
  (:require
    [cljs.spec.alpha :as s]
    [clojure.test.check.generators :as gen]
    [devcards.core :refer-macros [defcard]]
    [fulcro-css.css :as css]
    [fulcro.client.cards :refer-macros [defcard-fulcro]]
    [fulcro.inspect.ui.data-viewer :as f.i.data-viewer]
    [fulcro.client.dom :as dom]
    [fulcro.client.primitives :as fp]
    [fulcro.client.mutations :as m :refer [defmutation]]))

(defn make-root [Root app-id]
  (fp/ui
    static fp/InitialAppState
    (initial-state [_ params] {:fulcro.inspect.core/app-id app-id
                               :ui/react-key               (random-uuid)
                               :ui/root                    (fp/get-initial-state Root params)})

    static fp/IQuery
    (query [_] [:ui/react-key
                {:ui/root (fp/get-query Root)}])

    static css/CSS
    (local-rules [_] [])
    (include-children [_] [Root])

    Object
    (render [this]
      (let [{:ui/keys [react-key root]} (fp/props this)
            factory (fp/factory Root)]
        (dom/div #js {:key react-key}
          (factory root))))))

(def DataViewerRoot (make-root f.i.data-viewer/DataViewer ::data-viewer))

(fp/defsc SearchDisplay [this {:keys [ui/search viewer]}]
  {:query         [:ui/search {:viewer (fp/get-query f.i.data-viewer/DataViewer)}]
   :ident         (fn [] [:X 1])
   :initial-state (fn [params] {:ui/search "" :viewer (fp/get-initial-state f.i.data-viewer/DataViewer params)})}
  (dom/div
    (dom/input {:value search :onChange #(m/set-string! this :ui/search :event %)})
    (dom/button {:onClick #(fp/transact! this `[(f.i.data-viewer/search-expand ~{:viewer viewer :search search})])} "Recursive Search")
    (f.i.data-viewer/data-viewer viewer {::f.i.data-viewer/search search})))

(def SearchRoot (make-root SearchDisplay ::data-search))

(defn init-search-state-atom [data]
  (atom (fp/tree->db SearchRoot (fp/get-initial-state SearchRoot data) true)))

(defn init-state-atom [comp data]
  (atom (fp/tree->db comp (fp/get-initial-state comp data) true)))

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

(defcard-fulcro search-against-map
  SearchRoot
  (init-search-state-atom {:a "hello" :b {:x "dude" :y {:z [{:ac "123"} {:ac "456"}]}}}))

(css/upsert-css "data-viewer" f.i.data-viewer/DataViewer)

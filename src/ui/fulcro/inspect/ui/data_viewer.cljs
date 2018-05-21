(ns fulcro.inspect.ui.data-viewer
  (:require [fulcro.client.mutations :as mutations]
            [fulcro-css.css :as css]
            [fulcro.inspect.ui.core :as ui]
            [fulcro.inspect.helpers :as h]
            [fulcro.client.dom :as dom]
            [fulcro.client.primitives :as fp]))

(declare DataViewer)

(def vec-max-inline 2)
(def sequential-max-inline 5)
(def map-max-inline 10)

(defn children-expandable-paths [x]
  (loop [lookup [{:e x :p []}]
         paths  []]
    (if (seq lookup)
      (let [[{:keys [e p]} & t] lookup]
        (cond
          (or (sequential? e) (set? e))
          (let [sub-paths (keep-indexed (fn [i x] (if (coll? x) {:e x :p (conj p i)})) e)]
            (recur (into [] (concat t sub-paths))
              (into paths (map :p) sub-paths)))

          (map? e)
          (let [sub-paths (keep (fn [[k x]] (if (coll? x) {:e x :p (conj p k)})) e)]
            (recur (into [] (concat t sub-paths))
              (into paths (map :p) sub-paths)))

          :else
          (recur t paths)))
      paths)))

(mutations/defmutation toggle [{::keys [path propagate?]}]
  (action [env]
    (let [{:keys [state ref]} env
          open?   (get-in @state (conj ref ::expanded path))
          content (get-in @state (concat ref [::content] path))
          toggled (not open?)
          paths   (cond-> {path toggled}
                    propagate? (into (map #(vector (into path %) toggled)) (children-expandable-paths
                                                                             content)))]
      (swap! state update-in ref update ::expanded merge paths))))

(defn keyable? [x]
  (or (nil? x)
      (string? x)
      (keyword? x)
      (number? x)
      (boolean? x)
      (symbol? x)
      (uuid? x)
      (fp/tempid? x)
      (and (vector? x)
           (<= (count x) vec-max-inline))))

(declare render-data)

(defn render-ordered-list [{:keys [css path path-action linkable?] :as input} content]
  (for [[x i] (map vector content (range))]
    (dom/div #js {:key i :className (:list-item css)}
      (dom/div #js {:className (:list-item-index css)}
        (if (and linkable? path-action)
          (dom/div #js {:className (:path-action css)
                        :onClick   #(path-action (conj path i))}
            (str i))
          (str i)))
      (render-data (update input :path conj i) x))))

(defn render-sequential [{:keys [css expanded path toggle open-close static?] :as input} content]
  (dom/div #js {:className (:data-row css)}
    (if (and (not static?) (> (count content) vec-max-inline))
      (dom/div #js {:onClick   #(toggle % path)
                    :className (:toggle-button css)}
        (if (expanded path)
          ui/arrow-down
          ui/arrow-right)))

    (cond
      (expanded path)
      (dom/div #js {:className (:list-container css)}
        (render-ordered-list input content))

      :else
      (dom/div #js {:className (:list-inline css)}
        (first open-close)
        (for [[x i] (map vector (take sequential-max-inline content) (range))]
          (dom/div #js {:key i :className (:list-inline-item css)}
            (render-data (update input :path conj i) x)))
        (if (> (count content) sequential-max-inline)
          (dom/div #js {:className (:list-inline-item css)} "..."))
        (second open-close)))))

(defn render-vector [input content]
  (render-sequential (assoc input :open-close ["[" "]"] :linkable? true) content))

(defn render-list [input content]
  (render-sequential (assoc input :open-close ["(" ")"] :linkable? true) content))

(defn render-set [input content]
  (render-sequential (assoc input :open-close ["#{" "}"]) content))

(defn render-map [{:keys [css expanded path toggle path-action elide-one? static?] :as input} content]
  (dom/div #js {:className (:data-row css)}
    (if (and (not static?)
             (or (not elide-one?)
                 (> 1 (count content))))
      (dom/div #js {:onClick   #(toggle % path)
                    :className (:toggle-button css)}
        (if (expanded path)
          ui/arrow-down
          ui/arrow-right)))

    (cond
      (empty? content)
      "{}"

      (expanded path)
      (if (every? keyable? (keys content))
        (dom/div #js {:className (:map-container css)}
          (->> content
               (sort-by (comp str first))
               (mapv (fn [[k v]]
                       (if (expanded (conj path k))
                         [(dom/div #js {:key (str k "-key")}
                            (dom/div #js {:className (:list-item-index css)}
                              (if path-action
                                (dom/div #js {:className (:path-action css)
                                              :onClick   #(path-action (conj path k))}
                                  (render-data input k))
                                (render-data input k))))
                          (dom/div #js {:key (str k "-key-space")})
                          (dom/div #js {:className (:map-expanded-item css)
                                        :key       (str k "-value")} (render-data (update input :path conj k) v))]
                         [(dom/div #js {:key (str k "-key")}
                            (dom/div #js {:className (:list-item-index css)}
                              (if path-action
                                (dom/div #js {:className (:path-action css)
                                              :onClick   #(path-action (conj path k))}
                                  (render-data input k))
                                (render-data input k))))
                          (dom/div #js {:key (str k "-value")} (render-data (update input :path conj k) v))])))
               (apply concat)))

        (dom/div #js {:className (:list-container css)}
          (render-ordered-list input content)))

      (or (expanded (vec (butlast path)))
          (empty? path))
      (dom/div #js {:className (:list-inline css)}
        "{"
        (->> content
             (sort-by (comp str first))
             (take map-max-inline)
             (mapv (fn [[k v]]
                     [(dom/div #js {:className (:map-inline-key-item css) :key (str k "-key")} (render-data input k))
                      (dom/div #js {:className (:map-inline-value-item css) :key (str k "-value")} (render-data (update input :path conj k) v))]))
             (interpose ", ")
             (apply concat))
        (if (> (count content) map-max-inline)
          ", ")
        (if (> (count content) map-max-inline)
          (dom/div #js {:className (:list-inline-item css)} "..."))
        "}")

      :else
      "{...}")))

(defn render-data [{:keys [css] :as input} content]
  (let [input (update input :expanded #(or % {}))]
    (cond
      (nil? content)
      (dom/div #js {:className (:nil css)} "nil")

      (string? content)
      (dom/div #js {:className (:string css)} (pr-str content))

      (keyword? content)
      (dom/div #js {:className (:keyword css)} (str content))

      (symbol? content)
      (dom/div #js {:className (:symbol css)} (str content))

      (number? content)
      (dom/div #js {:className (:number css)} (str content))

      (boolean? content)
      (dom/div #js {:className (:boolean css)} (str content))

      (uuid? content)
      (dom/div #js {:className (:uuid css)} "#uuid " (dom/span {:className (:string css)} (str "\"" content "\"")))

      (map? content)
      (render-map input content)

      (vector? content)
      (render-vector input content)

      (list? content)
      (render-list input content)

      (set? content)
      (render-set input content)

      :else
      (dom/div #js {:className (:unknown css)} (str content)))))

(def css-triangle
  {:font-family    ui/label-font-family
   :font-size      ui/label-font-size
   :color          "#8f8f8f"
   :cursor         "pointer"
   :vertical-align "middle"
   :margin-right   "3px"})

(def css-code-font
  {:font-family "'courier new', monospace"
   :font-size   "12px"
   :white-space "nowrap"})

(fp/defui ^:once DataViewer
  static fp/InitialAppState
  (initial-state [_ content] {::id       (random-uuid)
                              ::content  content
                              ::expanded {}})

  static fp/Ident
  (ident [_ props] [::id (::id props)])

  static fp/IQuery
  (query [_] [::id ::content ::expanded])

  static css/CSS
  (local-rules [_]
    [[:.container css-code-font]
     [:.nil {:color "#808080"}]
     [:.string {:color "#c41a16"}]
     [:.keyword {:color "#881391"}]
     [:.symbol {:color "#134f91"}]
     [:.number {:color "#1c00cf"}]
     [:.boolean {:color "#009999"}]

     [:.data-row {:display     "flex"
                  :margin-left "3px"}]

     [:.list-inline {:display "flex"}]
     [:.list-inline-item {:margin "0 4px"}]

     [:.list-container {:padding          "3px 12px"
                        :border-top       "2px solid rgba(60, 90, 60, 0.1)"
                        :margin           "0px 1px 1px"
                        :background-color "rgba(100, 255, 100, 0.08)"}]

     [:.toggle-button css-triangle]

     [:.list-item {:display     "flex"
                   :align-items "flex-start"}]
     [:.list-item-index {:background    "#dddddd"
                         :border-right  "2px solid rgba(100, 100, 100, 0.2)"
                         :min-width     "35px"
                         :margin-bottom "1px"
                         :margin-right  "5px"
                         :padding       "0 3px"}]

     [:.map-container {:padding               "3px 12px"
                       :border-top            "2px solid rgba(60, 90, 60, 0.1)"
                       :margin                "0px 1px 1px"
                       :background-color      "rgba(100, 255, 100, 0.08)"

                       :display               "grid"
                       :grid-template-columns "max-content 1fr"}]

     [:.map-expanded-item {:grid-column-start "1"
                           :grid-column-end   "3"}]

     [:.map-inline-key-item {:margin-left "4px"}]
     [:.map-inline-value-item {:margin-left "8px"}]

     [:.path-action {:cursor "pointer"}
      [:&:hover
       [:div {:text-decoration "underline"}]]]])
  (include-children [_] [])

  Object
  (render [this]
    (let [{::keys [content expanded elide-one? static?] :as props} (fp/props this)
          {::keys [path-action]} (fp/get-computed props)
          css (css/get-classnames DataViewer)]
      (dom/div #js {:className (:container css)}
        (render-data {:expanded    expanded
                      :static?     static?
                      :elide-one?  elide-one?
                      :toggle      #(fp/transact! this [`(toggle {::path       ~%2
                                                                  ::propagate? ~(or (.-altKey %)
                                                                                    (.-metaKey %))})])
                      :css         css
                      :path        []
                      :path-action path-action}
          content)))))

(let [factory (fp/factory DataViewer)]
  (defn data-viewer [props & [computed]]
    (factory (fp/computed props computed))))

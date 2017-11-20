(ns fulcro.inspect.ui.data-viewer
  (:require [fulcro.client.core :as fulcro]
            [fulcro.client.mutations :as mutations]
            [fulcro-css.css :as css]
            [om.dom :as dom]
            [om.next :as om]))

(declare DataViewer)

(def vec-max-inline 2)
(def sequential-max-inline 5)

(defn keyable? [x]
  (or (nil? x)
      (string? x)
      (keyword? x)
      (number? x)
      (boolean? x)
      (uuid? x)
      (om/tempid? x)
      (and (vector? x)
           (<= (count x) vec-max-inline))))

(declare render-data)

(defn render-ordered-list [{:keys [css] :as input} content]
  (for [[x i] (map vector content (range))]
    (dom/div #js {:key i :className (:list-item css)}
      (dom/div #js {:className (:list-item-index css)} (str i))
      (render-data (update input :path conj i) x))))

(defn render-sequential [{:keys [css expanded path toggle open-close] :as input} content]
  (dom/div #js {:className (:data-row css)}
    (if (> (count content) vec-max-inline)
      (dom/div #js {:onClick   #(toggle path)
                    :className (:toggle-button css)}
        (if (expanded path)
          "▼"
          "▶")))

    (cond
      (and (expanded path)
           (> (count content) vec-max-inline))
      (dom/div #js {:className (:list-container css)}
        (render-ordered-list input content))

      :else
      (dom/div #js {:className (:list-inline css)}
        (first open-close)
        (for [[x i] (map vector (take sequential-max-inline content) (range))]
          (dom/div #js {:key i :className (:list-inline-item css)}
            (render-data (update input :path conj i) x)))
        (if (> (count content) sequential-max-inline)
          (dom/div #js {:className (:list-inline-item css)} "…"))
        (second open-close)))))

(defn render-vector [input content]
  (render-sequential (assoc input :open-close ["[" "]"]) content))

(defn render-list [input content]
  (render-sequential (assoc input :open-close ["(" ")"]) content))

(defn render-set [input content]
  (render-sequential (assoc input :open-close ["#{" "}"]) content))

(defn render-map [{:keys [css expanded path toggle path-action elide-one?] :as input} content]
  (dom/div #js {:className (:data-row css)}
    (if (or (not elide-one?)
            (> 1 (count content)))
      (dom/div #js {:onClick   #(toggle path)
                    :className (:toggle-button css)}
        (if (expanded path)
          "▼"
          "▶")))

    (cond
      (empty? content)
      "{}"

      (expanded path)
      (if (every? keyable? (keys content))
        (dom/div #js {:className (:map-container css)}
          (->> content
               (sort-by (comp str first))
               (mapv (fn [[k v]]
                       [(dom/div #js {:key (str k "-key")}
                          (dom/div #js {:className (:list-item-index css)}
                            (if path-action
                              (dom/div #js {:className (:path-action css)
                                            :onClick   #(path-action (conj path k))}
                                (render-data input k))
                              (render-data input k))))
                        (dom/div #js {:key (str k "-value")} (render-data (update input :path conj k) v))]))
               (apply concat)))

        (dom/div #js {:className (:list-container css)}
          (render-ordered-list input content)))

      (or (expanded (vec (butlast path)))
          (empty? path))
      (dom/div #js {:className (:list-inline css)}
        "{"
        (->> content
             (sort-by (comp str first))
             (mapv (fn [[k v]]
                     [(dom/div #js {:className (:map-inline-key-item css) :key (str k "-key")} (render-data input k))
                      (dom/div #js {:className (:map-inline-value-item css) :key (str k "-value")} (render-data (update input :path conj k) v))]))
             (interpose ", ")
             (apply concat))
        "}")

      :else
      "{…}")))

(defn render-data [{:keys [css] :as input} content]
  (let [input (update input :expanded #(or % {}))]
    (cond
      (nil? content)
      (dom/div #js {:className (:nil css)} "nil")

      (string? content)
      (dom/div #js {:className (:string css)} (pr-str content))

      (keyword? content)
      (dom/div #js {:className (:keyword css)} (str content))

      (number? content)
      (dom/div #js {:className (:number css)} (str content))

      (boolean? content)
      (dom/div #js {:className (:boolean css)} (str content))

      (vector? content)
      (render-vector input content)

      (list? content)
      (render-list input content)

      (set? content)
      (render-set input content)

      (map? content)
      (render-map input content)

      :else
      (dom/div #js {:className (:unknown css)} (str content)))))

(def css-triangle
  {:color          "#8f8f8f"
   :cursor         "pointer"
   :font-family    "sans-serif"
   :font-size      "12px"
   :vertical-align "middle"
   :margin-right   "3px"})

(def css-code-font
  {:font-family "'courier new', monospace"
   :font-size   "12px"
   :white-space "nowrap"})

(om/defui ^:once DataViewer
  static fulcro/InitialAppState
  (initial-state [_ content] {::id       (random-uuid)
                              ::content  content
                              ::expanded {[] true}})

  static om/IQuery
  (query [_] [::id ::content ::expanded])

  static om/Ident
  (ident [_ props] [::id (::id props)])

  static css/CSS
  (local-rules [_] [[:.container (merge {:background "#fff"}
                                        css-code-font)]
                    [:.nil {:color "#808080"}]
                    [:.string {:color "#c41a16"}]
                    [:.keyword {:color "#881391"}]
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
                                        :text-align    "right"
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

                    [:.map-inline-key-item {:margin-left "4px"}]
                    [:.map-inline-value-item {:margin-left "8px"}]

                    [:.path-action {:cursor "pointer"}
                     [:&:hover
                      [:div {:text-decoration "underline"}]]]])
  (include-children [_] [])

  Object
  (render [this]
    (let [{::keys [content expanded elide-one?] :as props} (om/props this)
          {::keys [path-action]} (om/get-computed props)
          css (css/get-classnames DataViewer)]
      (dom/div #js {:className (:container css)}
        (render-data {:expanded    expanded
                      :elide-one?  elide-one?
                      :toggle      #(mutations/set-value! this ::expanded (update expanded % not))
                      :css         css
                      :path        []
                      :path-action path-action}
          content)))))

(let [factory (om/factory DataViewer)]
  (defn data-viewer [props & [computed]]
    (factory (om/computed props computed))))

(ns com.wsscode.oge.core
  (:require
    [cljs.reader :refer [read-string]]
    [cognitect.transit :as transit]
    [com.wsscode.oge.ui.codemirror :as codemirror]
    [com.wsscode.oge.ui.common :as ui]
    [com.wsscode.oge.ui.flame-graph :as ui.flame]
    [com.wsscode.oge.ui.helpers :as helpers]
    [com.wsscode.pathom.connect :as pc]
    [com.wsscode.pathom.core :as p]
    [com.wsscode.pathom.profile :as pp]
    [fulcro.client.data-fetch :as fetch]
    [fulcro.client.localized-dom :as dom]
    [fulcro.client.mutations :as mutations]
    [fulcro.client.primitives :as fp]
    [fulcro.inspect.helpers :as db.h]
    [fulcro.inspect.ui.core :as cui]
    [fulcro.inspect.ui.helpers :as h]
    [fulcro.tempid :as tempid]
    [taoensso.timbre :as log]))

(mutations/defmutation clear-errors [_]
  (action [{:keys [state]}]
    (swap! state dissoc ::p/errors)))

(mutations/defmutation normalize-result [_]
  (action [{:keys [ref state]}]
    (let [result' (cond-> (-> @state (get-in ref) :oge/result')
                    (get @state ::p/errors) (assoc ::p/errors (->> (get @state ::p/errors)
                                                                (into {} (map (fn [[k v]] [(vec (next k)) v]))))))
          profile (some-> result' ::pp/profile)
          result  (db.h/pprint (dissoc result' ::pp/profile))]
      (swap! state update-in ref merge {:oge/result  result
                                        :oge/profile profile}))))

(mutations/defmutation normalize-mutation-result [_]
  (action [{:keys [ref state]}]
    (let [result' (cond-> (-> @state (get-in ref) :oge/result')
                    (get @state ::p/errors) (assoc ::p/errors (->> (get @state ::p/errors)
                                                                (into {} (map (fn [[k v]] [(vec (next k)) v]))))))
          result  (db.h/pprint (dissoc result' ::pp/profile))]
      (swap! state update-in ref merge {:oge/result  result
                                        :oge/profile nil}))))

(defn transit-tagged-reader [tag value] (transit/tagged-value tag value))

(defn oge-query [this string-expression]
  (let [{:oge/keys [remote] :as props} (fp/props this)
        {:fulcro.inspect.core/keys [app-uuid]} (fp/get-computed props)
        ident (fp/get-ident this)]
    (try
      (let [eql       (read-string {:default transit-tagged-reader} string-expression)
            {:keys [children]} (fp/query->ast eql)
            mutation? (= :call (some-> children first :type))]
        (if mutation?
          (do
            (fp/transact! this [`(clear-errors {})])
            (fetch/load this :oge/mutation-result nil {:target        (conj ident :oge/result')
                                                       :post-mutation `normalize-mutation-result
                                                       :marker        (keyword "oge-query" (p/ident-value* ident))
                                                       :params        {:fulcro.inspect.core/app-uuid app-uuid
                                                                       :fulcro.inspect.client/remote remote
                                                                       :mutation                     eql}}))
          (fp/transact! this [`(clear-errors {})
                              (list 'fulcro/load {:target        (conj ident :oge/result')
                                                  :query         [{(list :>/oge {:fulcro.inspect.core/app-uuid app-uuid
                                                                                 :fulcro.inspect.client/remote remote})
                                                                   eql}]
                                                  :refresh       [:oge/result]
                                                  :marker        (keyword "oge-query" (p/ident-value* ident))
                                                  :post-mutation `normalize-result})])))
      ; for some reason the load marker was missing to refresh ui sometimes without the next line
      (js/setTimeout #(fp/transact! this [:oge/id]) 10)
      (catch :default e
        (js/console.error "Invalid EQL" e)))))

(declare Oge)

(defn update-index [this]
  (let [{:oge/keys [remote]} (fp/props this)
        ident (fp/get-ident this)]
    (fetch/load-field this ::pc/indexes {:marker  (keyword "oge-index" (p/ident-value* ident))
                                         :refresh #{:ui/editor}
                                         :params  {:fulcro.inspect.core/app-uuid (db.h/ref-app-uuid ident)
                                                   :fulcro.inspect.client/remote remote}})))

(fp/defsc Oge
  [this
   {:oge/keys [id query result profile remote]
    ::pc/keys [indexes]
    :as       props}
   {:fulcro.inspect.client/keys [remotes]
    ::keys                      [on-switch-remote]}
   css]
  {:initial-state (fn [remote] {:oge/id      (random-uuid)
                                :oge/query   "[]"
                                :oge/result  "{}"
                                :oge/remote  remote
                                :oge/profile nil})
   :ident         [:oge/id :oge/id]
   :query         [:oge/id :oge/query :oge/result :oge/profile :oge/remote
                   {::pc/indexes [::pc/idents ::pc/index-io ::pc/autocomplete-ignore]}
                   [fetch/marker-table '_]]
   :css           [[:.container {:display               "grid"
                                 :width                 "100%"
                                 :font-size             "12px"
                                 :grid-template-rows    "auto 1fr 12px 180px"
                                 :grid-template-columns "400px 12px 1fr"
                                 :grid-template-areas   (helpers/strings ["title title title"
                                                                          "editor divisor result"
                                                                          "hdiv divisor result"
                                                                          "flame divisor result"])}
                    [:&.simple {:grid-template-rows  "auto 1fr"
                                :grid-template-areas (helpers/strings ["title title title"
                                                                       "editor divisor result"])}]
                    [:$CodeMirror {:height "100%" :width "100%" :position "absolute"}
                     [:$cm-atom-composite {:color "#ab890d"}]
                     [:$cm-atom-ident {:color       "#219"
                                       :font-weight "bold"}]]]

                   [:$CodeMirror-hint {:font-size "10px"}]

                   [:.title {:grid-area     "title"
                             :display       "flex"
                             :align-items   "center"
                             :padding       "2px"
                             :border-bottom "1px solid #e0e0e0"}]
                   [:.title-oge {:margin "0" :margin-bottom "8px"}]
                   [:.divisor {:grid-area     "divisor"
                               :background    "#eee"
                               :border        "1px solid #e0e0e0"
                               :border-top    "0"
                               :border-bottom "0"}]
                   [:.editor {:grid-area "editor"
                              :position  "relative"}]
                   [:.result {:grid-area "result"
                              :position  "relative"}
                    [:$CodeMirror {:background "#f6f7f8"}]]
                   [:.hdiv {:grid-area    "hdiv"
                            :background   "#eee"
                            :border       "1px solid #e0e0e0"
                            :border-width "1px 0"}]
                   [:.flame {:grid-area  "flame"
                             :background "#f6f7f8"}]
                   [:.remote-selector {:display     "flex"
                                       :align-items "center"
                                       :font-family cui/label-font-family
                                       :font-size   cui/label-font-size
                                       :color       cui/color-text-normal}]
                   [:.remote-selector-label {:margin-right "8px"
                                             :margin-left  "4px"}]
                   [:.index-state {:background    "#000"
                                   :border-radius "100%"
                                   :cursor        "pointer"
                                   :width         "14px"
                                   :height        "14px"
                                   :margin        "0px 4px"
                                   :transition    "background 150ms"}
                    [:&.index-ready {:background "#36c74b"}]
                    [:&.index-loading {:background "#efe43b"}]
                    [:&.index-unavailable {:background "#848484"}]]
                   [:.flex {:flex "1"}]]
   :css-include   [ui/CSS]}

  (let [index-marker (get-in props [fetch/marker-table (keyword "oge-index" id)])
        query-marker (get-in props [fetch/marker-table (keyword "oge-query" id)])
        run-query    (fn [_]
                       (when-not (fetch/loading? query-marker)
                         (oge-query this (-> this fp/props :oge/query))))]
    (dom/div :.container {:className (if-not profile (:simple css))
                          :style     {:gridTemplateColumns (str (or (fp/get-state this :query-width) 400) "px 12px 1fr")}}
      (dom/div :.title
        (if (> (count remotes) 1)
          (dom/div :.remote-selector
            (dom/div :.remote-selector-label "Remote:")
            (dom/select {:onChange #(on-switch-remote (read-string (.. % -target -value)))
                         :value    (pr-str remote)}
              (for [r remotes]
                (dom/option {:key (pr-str r) :value (pr-str r)} (pr-str r))))))
        (dom/div :.flex)
        (dom/button :.run-button {:style   {:display    "inline-flex"
                                            :alignItems "center"
                                            :marginRight "3px"}
                                  :onClick #(if-not (fetch/loading? index-marker) (update-index this))}
          "(Re)load Pathom Index"
          (dom/div :.index-state
            {:classes [(cond
                         (fetch/loading? index-marker)
                         :.index-loading

                         (::pc/index-io indexes)
                         :.index-ready

                         :else
                         :.index-unavailable)]
             :title   (cond
                        (fetch/loading? index-marker)
                        "Loading index..."

                        (::pc/index-io indexes)
                        "Index ready"

                        :else
                        "Index unavailable")}))
        (dom/button :.run-button
          {:onClick  run-query
           :disabled (fetch/loading? query-marker)}
          "Run EQL"))
      (codemirror/oge {:className           (:editor css)
                       :value               (or (str query) "")
                       ::pc/indexes         (p/elide-not-found indexes)
                       ::codemirror/options {::codemirror/extraKeys
                                             {"Cmd-Enter"   run-query
                                              "Ctrl-Enter"  run-query
                                              "Shift-Enter" run-query
                                              "Cmd-J"       "ogeJoin"
                                              "Ctrl-Space"  "autocomplete"}}
                       :onChange            #(mutations/set-value! this :oge/query %)})
      (cui/drag-resize this {:attribute :query-width
                             :axis      "x"
                             :default   400
                             :props     {:className (:divisor css)}}
        (dom/div))
      (codemirror/clojure {:className           (:result css)
                           :value               (or (str result) "")
                           ::codemirror/options {::codemirror/readOnly    true
                                                 ::codemirror/lineNumbers true}})
      (if profile (dom/div :.hdiv))
      (if profile (dom/div :.flame (ui.flame/flame-graph {:profile profile}))))))

(def oge (h/computed-factory Oge))

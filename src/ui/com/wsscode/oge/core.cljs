(ns com.wsscode.oge.core
  (:require [fulcro.client.data-fetch :as fetch]
            [fulcro.client.mutations :as mutations]
            [fulcro-css.css :as css]
            [com.wsscode.pathom.core :as p]
            [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.profile :as pp]
            [com.wsscode.oge.ui.codemirror :as codemirror]
            [com.wsscode.oge.ui.flame-graph :as ui.flame]
            [com.wsscode.oge.ui.helpers :as helpers]
            [fulcro.client.primitives :as fp]
            [fulcro.client.localized-dom :as dom]
            [cljs.pprint :refer [pprint]]
            [cljs.reader :refer [read-string]]
            [com.wsscode.oge.ui.common :as ui]
            [fulcro.client.primitives :as fp]
            [fulcro.inspect.helpers :as db.h]
            [fulcro.inspect.ui.helpers :as h]
            [fulcro.inspect.ui.core :as cui]))

(mutations/defmutation clear-errors [_]
  (action [{:keys [state]}]
    (swap! state dissoc ::p/errors)))

(mutations/defmutation normalize-result [_]
  (action [{:keys [ref state]}]
    (let [result' (cond-> (-> @state (get-in ref) :oge/result')
                    (get @state ::p/errors) (assoc ::p/errors (->> (get @state ::p/errors)
                                                                   (into {} (map (fn [[k v]] [(vec (next k)) v]))))))
          profile (some-> result' ::pp/profile :>/oge)
          result  (with-out-str (cljs.pprint/pprint (dissoc result' ::pp/profile)))]
      (swap! state update-in ref merge {:oge/result  result
                                        :oge/profile profile}))))

(defn oge-query [this query]
  (let [{:oge/keys [remote] :as props} (fp/props this)
        {:fulcro.inspect.core/keys [app-uuid]} (fp/get-computed props)]
    (try
      (fp/transact! this [`(clear-errors {})
                          (list 'fulcro/load {:target        (conj (fp/get-ident this) :oge/result')
                                              :query         [{(list :>/oge {:fulcro.inspect.core/app-uuid app-uuid
                                                                             :fulcro.inspect.client/remote remote})
                                                               (conj (read-string query) ::pp/profile)}]
                                              :refresh       [:oge/result]
                                              :post-mutation `normalize-result})])
      (catch :default e
        (js/console.error "Invalid query" e)))))

(declare Oge)

(defn trigger-index-load [reconciler ident remote]
  (let [index-query (-> Oge fp/get-query (fp/focus-query [::pc/indexes])
                        first ::pc/indexes)]
    (fp/transact! reconciler ident
      [(list 'fulcro/load {:target  (conj ident ::pc/indexes)
                           :query   (with-meta
                                      [{(list ::pc/indexes {:fulcro.inspect.core/app-uuid (db.h/ref-app-uuid ident)
                                                            :fulcro.inspect.client/remote remote})
                                        index-query}]
                                      (meta (fp/get-query Oge)))
                           :marker  (keyword "oge-index" (p/ident-value* ident))
                           :refresh #{:ui/editor}})])))

(defn update-index [this]
  (let [{:oge/keys [remote]} (fp/props this)]
    (trigger-index-load (fp/get-reconciler this)
      (fp/get-ident this)
      remote)))

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
                   {::pc/indexes [::pc/idents ::pc/index-io ::pc/autocomplete-ignore :ui/fetch-state]}
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
                     [:$cm-atom-composite {:color "#ab890d"}]]]

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
                                   :margin        "3px 8px"
                                   :transition    "background 150ms"}
                    [:&.index-ready {:background "#36c74b"}]
                    [:&.index-loading {:background "#efe43b"}]
                    [:&.index-unavailable {:background "#848484"}]]
                   [:.flex {:flex "1"}]]
   :css-include   [ui/CSS]}

  (let [index-marker (get-in props [fetch/marker-table (keyword "oge-index" id)])]
    (dom/div :.container {:className (if-not profile (:simple css))}
      (dom/div :.title
        (if (> (count remotes) 1)
          (dom/div :.remote-selector
            (dom/div :.remote-selector-label "Remote:")
            (dom/select {:onChange #(on-switch-remote (read-string (.. % -target -value)))
                         :value    (pr-str remote)}
              (for [r remotes]
                (dom/option {:key (pr-str r) :value (pr-str r)} (pr-str r))))))
        (dom/div :.flex)
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
                      "Index unavailable")
           :onClick #(if-not (fetch/loading? index-marker) (update-index this))}))
      (codemirror/oge {:className           (:editor css)
                       :value               (or (str query) "")
                       ::pc/indexes         (p/elide-not-found indexes)
                       ::codemirror/options {::codemirror/extraKeys
                                             {"Cmd-Enter"   (fn [_] (oge-query this (-> this fp/props :oge/query)))
                                              "Ctrl-Enter"  (fn [_] (oge-query this (-> this fp/props :oge/query)))
                                              "Shift-Enter" (fn [_] (oge-query this (-> this fp/props :oge/query)))
                                              "Cmd-J"       "ogeJoin"
                                              "Ctrl-Space"  "autocomplete"}}
                       :onChange            #(mutations/set-value! this :oge/query %)})
      (dom/div :.divisor)
      (codemirror/clojure {:className           (:result css)
                           :value               (or (str result) "")
                           ::codemirror/options {::codemirror/readOnly    true
                                                 ::codemirror/lineNumbers true}})
      (if profile (dom/div :.hdiv))
      (if profile (dom/div :.flame (ui.flame/flame-graph {:profile profile}))))))

(def oge (h/computed-factory Oge))

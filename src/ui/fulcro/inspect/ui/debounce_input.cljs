(ns fulcro.inspect.ui.debounce-input
  (:require [com.fulcrologic.fulcro-css.localized-dom :as dom]
            [com.fulcrologic.fulcro.components :as fp]
            [com.fulcrologic.fulcro.mutations]
            [goog.functions :as gfun]))

(fp/defsc DebounceInput
  [this props]
  {:initLocalState (fn [_]
                     (let [{:keys  [value onChange]
                            ::keys [delay]} (fp/props this)
                           debounced (gfun/debounce (or onChange identity) (or delay 500))]
                       {:text        value
                        :update-self (fn [e]
                                       (.persist e)
                                       (fp/set-state! this {:text (fm/target-value e)})
                                       (debounced e))}))}
  (dom/input
    (merge (dissoc props ::delay)
      {:value    (fp/get-state this :text)
       :onChange (fp/get-state this :update-self)})))

(def debounce-input (fp/factory DebounceInput))

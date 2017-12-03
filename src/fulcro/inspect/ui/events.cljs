(ns fulcro.inspect.ui.events
  (:require [cljs.spec.alpha :as s]
            [clojure.string :as str]
            [goog.object :as gobj]
            [fulcro.client.dom :as dom]
            [fulcro.client.primitives :as fp]))

(def KEYS
  {"backspace" 8
   "tab"       9
   "return"    13
   "escape"    27
   "space"     32
   "left"      37
   "up"        38
   "right"     39
   "down"      40
   "slash"     191
   "a"         65
   "b"         66
   "c"         67
   "d"         68
   "e"         69
   "f"         70
   "g"         71
   "h"         72
   "i"         73
   "j"         74
   "k"         75
   "l"         76
   "m"         77
   "n"         78
   "o"         79
   "p"         80
   "q"         81
   "r"         82
   "s"         83
   "t"         84
   "u"         85
   "v"         86
   "w"         87
   "x"         88
   "y"         89
   "z"         90})

(s/def ::key-string (set (keys KEYS)))
(s/def ::modifier #{"ctrl" "alt" "meta" "shift"})
(s/def ::keystroke
  (s/and string?
         (s/conformer #(str/split % #"-") #(str/join "-" %))
         (s/cat :modifiers (s/* ::modifier) :key ::key-string)))
(s/def ::key-code pos-int?)

(defn parse-keystroke [keystroke]
  (if-let [{:keys [modifiers key]} (s/conform ::keystroke keystroke)]
    {::key-code  (get KEYS key)
     ::modifiers modifiers}
    (js/console.warn (str "Keystroke `" keystroke "` is not valid."))))

(defn match-modifiers? [e {::keys [modifiers]}]
  (every? #(gobj/get e (str % "Key")) modifiers))

(defn match-key? [e {::keys [key-code]}]
  (= (gobj/get e "keyCode") key-code))

(defn handle-event [this e]
  (let [{::keys [action]} (fp/props this)
        {:keys [matcher]} (gobj/get this "matcher")]
    (if (and (match-key? e matcher)
             (match-modifiers? e matcher))
      (action e))))

(fp/defui ^:once KeyListener
  Object
  (componentDidMount [this]
    (if-let [matcher (parse-keystroke (-> this fp/props ::keystroke))]
      (let [handler #(handle-event this %)
            {::keys [target event]} (fp/props this)
            target (or target js/document.body)
            event  (or event "keydown")]
        (gobj/set this "matcher" {:handler handler
                                  :matcher matcher})
        (.addEventListener target event handler))))

  (componentWillUnmount [this]
    (if-let [{:keys [handler]} (gobj/get this "matcher")]
      (let [{::keys [target event]} (fp/props this)
            target (or target js/document.body)
            event  (or event "keydown")]
        (.removeEventListener target event handler))))

  (render [_]
    (dom/noscript nil)))

(def key-listener (fp/factory KeyListener))

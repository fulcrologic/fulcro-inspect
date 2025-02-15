(ns fulcro.inspect.ui.events
  (:require
    [cljs.spec.alpha :as s]
    [clojure.string :as str]
    [com.fulcrologic.fulcro.components :as fp]
    [com.fulcrologic.fulcro.dom :as dom]
    [goog.object :as gobj]))

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

(defn key-code [name] {::key-code (get KEYS name)})

(defn shift-key? [e] (gobj/get e "shiftKey"))
(defn ctrl-key? [e] (gobj/get e "ctrlKey"))

(defn stop-event [e]
  (.preventDefault e)
  (.stopPropagation e))

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
    (when (and (match-key? e matcher)
            (match-modifiers? e matcher))
      (action e))))

(defn read-target [target]
  (cond
    (fn? target) (target)
    (nil? target) js/document.body
    :else target))

(defn start-handler [this]
  (if-let [matcher (parse-keystroke (-> this fp/props ::keystroke))]
    (let [handler #(handle-event this %)
          {::keys [target event]} (fp/props this)
          target  (read-target target)
          event   (or event "keydown")]
      (gobj/set this "matcher" {:handler handler
                                :matcher matcher})
      (if target
        (.addEventListener target event handler)))))

(defn dispose-handler [this]
  (if-let [{:keys [handler]} (gobj/get this "matcher")]
    (let [{::keys [target event]} (fp/props this)
          target (read-target target)
          event  (or event "keydown")]
      (if target
        (.removeEventListener target event handler)))))

(fp/defsc KeyListener [this props]
  {:componentDidMount    (fn [this] (start-handler this))
   :componentWillUnmount (fn [this] (dispose-handler this))
   :componentWillUpdate  (fn [this _ _] (dispose-handler this))
   :componentDidUpdate   (fn [this _ _] (start-handler this))}
  (dom/noscript nil))

(def key-listener (fp/factory KeyListener))

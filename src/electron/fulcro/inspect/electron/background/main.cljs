(ns fulcro.inspect.electron.background.main
  (:require
    ["electron" :as electron]
    ["path" :as path]
    ["electron-settings" :as settings]
    ["url" :as url]
    [cljs.core.async :as async]
    [goog.functions :as g.fns]
    [fulcro.inspect.electron.background.server :as server]
    [taoensso.timbre :as log]))

(defn get-setting [k default]
  (let [c (async/chan)]
    (-> (.get settings k)
      (.then
        (fn [v]
          (async/go (async/>! c (if (nil? v) default v))))))
    c))
(defn set-setting! [k v] (.set settings k v))

(defn save-state! [^js window]
  (mapv (fn [[k v]] (set-setting! (str "BrowserWindow/" k) v))
    (js->clj (.getBounds window))))

(defn toggle-settings-window! []
  (server/send-message-to-renderer!
    {:type :fulcro.inspect.client/toggle-settings :data {}}))

(defn create-window []
  (async/go
    (let [width   (async/<! (get-setting "BrowserWindow/width" 800))
          height  (async/<! (get-setting "BrowserWindow/height" 600))
          x       (async/<! (get-setting "BrowserWindow/x" 0))
          y       (async/<! (get-setting "BrowserWindow/y" 0))
          ^js win (electron/BrowserWindow.
                    #js {:width          width
                         :height         height
                         :x              x
                         :y              y
                         :webPreferences #js {:contextIsolation true
                                              :preload          (path/join js/__dirname "preload.js")}})]
      (.loadURL win (url/format #js {:pathname (path/join js/__dirname ".." ".." "index.html")
                                     :protocol "file:"
                                     :slashes  "true"}))
      (let [save-window-state! (g.fns/debounce #(save-state! win) 500)]
        (doto win
          (.on "resize" save-window-state!)
          (.on "move" save-window-state!)
          (.on "close" save-window-state!)))
      (.setApplicationMenu electron/Menu
        (.buildFromTemplate electron/Menu
          ;;FIXME: cmd only if is osx
          (clj->js [{:label   (.-name electron/app)
                     :submenu [{:role "about"}
                               {:type "separator"}
                               {:label       "Settings"
                                :accelerator "cmd+,"
                                :click       #(toggle-settings-window!)}
                               {:type "separator"}
                               {:role "quit"}]}
                    {:label   "Edit"
                     :submenu [{:label "Undo" :accelerator "CmdOrCtrl+Z" :selector "undo:"}
                               {:label "Redo" :accelerator "Shift+CmdOrCtrl+Z" :selector "redo:"}
                               {:type "separator"}
                               {:label "Cut" :accelerator "CmdOrCtrl+X" :selector "cut:"}
                               {:label "Copy" :accelerator "CmdOrCtrl+C" :selector "copy:"}
                               {:label "Paste" :accelerator "CmdOrCtrl+V" :selector "paste:"}
                               {:label "Select All" :accelerator "CmdOrCtrl+A" :selector "selectAll:"}]}
                    {:label   "View"
                     :submenu [{:role "reload"}
                               {:role "forcereload"}
                               {:role "toggledevtools"}
                               {:type "separator"}
                               {:role "resetzoom"}
                               {:role "zoomin" :accelerator "cmd+="}
                               {:role "zoomout"}
                               {:type "separator"}
                               {:role "togglefullscreen"}]}])))
      (server/start! (.-webContents win))))
  nil)

(defn init []
  (electron/app.on "ready" create-window))

(defn done []
  (js/console.log "Done reloading"))

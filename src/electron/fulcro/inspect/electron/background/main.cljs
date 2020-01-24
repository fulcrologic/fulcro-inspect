(ns fulcro.inspect.electron.background.main
  (:require
    ["electron" :as electron]
    ["path" :as path]
    ["electron-settings" :as settings]
    ["url" :as url]
    [goog.functions :as g.fns]
    [fulcro.inspect.electron.background.server :as server]))

(defn get-setting [k default] (or (.get settings k) default))
(defn set-setting! [k v] (.set settings k v))

(defn save-state! [window]
  (mapv (fn [[k v]] (set-setting! (str "BrowserWindow/" k) v))
    (js->clj (.getBounds window))))

(defn toggle-settings-window! []
  (server/send-message-to-renderer!
    {:type :fulcro.inspect.client/toggle-settings :data {}}))

(defn create-window []
  (let [win (electron/BrowserWindow.
              #js {:width          (get-setting "BrowserWindow/width" 800)
                   :height         (get-setting "BrowserWindow/height" 600)
                   :x              (get-setting "BrowserWindow/x" js/undefined)
                   :y              (get-setting "BrowserWindow/y" js/undefined)
                   :webPreferences #js {:nodeIntegration true}})]
    (.loadURL win (url/format #js {:pathname (path/join js/__dirname ".." ".." "index.html")
                                   :protocol "file:"
                                   :slashes  "true"}))
    (when (get-setting "BrowserWindow/OpenDevTools?" false)
      (.. win -webContents openDevTools))
    (.on (.-webContents win) "devtools-opened" #(set-setting! "BrowserWindow/OpenDevTools?" true))
    (.on (.-webContents win) "devtools-closed" #(set-setting! "BrowserWindow/OpenDevTools?" false))
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

(defn init []
  (js/console.log "init")
  (electron/app.on "ready" create-window))

(defn done []
  (js/console.log "Done reloading"))


# Fulcro Inspect - Developer Reference

## Table of Contents

1. [Project Overview](#project-overview)
2. [Architecture](#architecture)
3. [Communication Protocol](#communication-protocol)
4. [Key Components](#key-components)
5. [File Structure](#file-structure)
6. [Development Workflow](#development-workflow)
7. [Integration Guide](#integration-guide)
8. [Message Flow Examples](#message-flow-examples)

---

## Project Overview

**Fulcro Inspect** is a comprehensive debugging and inspection tool for Fulcro applications. It provides real-time visibility into application state, transactions, network activity, and component behavior.

### Key Features

- **Database (State) Inspection**: Browse normalized Fulcro state tree with expandable navigation
- **Transaction Monitoring**: Track all mutations with timeline view and replay capability
- **Network Inspection**: Request/response tracking with Pathom execution profiling (flame graphs)
- **Query Building (OGE)**: Visual EQL query builder with Pathom index exploration
- **State Machines**: Registered statechart visualization and session state tracking
- **Component Inspection**: Element picker with props and query display
- **Time-Travel Debugging**: Navigate through 80+ historical state snapshots

### Distribution Targets

1. **Chrome Extension**: Integrates into Chrome DevTools panel
2. **Electron App**: Standalone desktop application
3. **Client Library**: Embedded in target applications (elided in production builds)

### Dependencies

**Core Runtime**:
- `com.fulcrologic/fulcro` (3.9.0-rc11)
- `com.fulcrologic/fulcro-devtools-remote` (0.2.6) - Communication abstraction
- `com.wsscode/pathom` (2.4.0) - EQL processing
- `com.fulcrologic/statecharts` (1.2.22)
- `cognitect/transit-cljs` (0.8.280) - Serialization

**Build**:
- `shadow-cljs` (2.28.19)
- Multiple build targets for Chrome and Electron

---

## Architecture

### System Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│ Target Application (Inspected Fulcro App)                          │
│                                                                     │
│  fulcro.inspect.tool/add-fulcro-inspect!                           │
│    ├─ State atom watch (db-changed!)                              │
│    ├─ Tool registration (handle-inspect-event)                    │
│    └─ Connection via fulcro-devtools-remote                       │
└──────────────────────────┬──────────────────────────────────────────┘
                           │
                           │ EQL Queries/Mutations
                           │ via DevToolConnection
                           │
┌──────────────────────────▼──────────────────────────────────────────┐
│ Fulcro-DevTools-Remote (Abstraction Layer)                         │
│                                                                     │
│  Protocol: DevToolConnection (transmit!/connect!)                  │
│    ├─ Chrome: window.postMessage → content script → background    │
│    ├─ Electron: WebSocket (Sente) → IPC bridge                    │
│    └─ Message format: Transit-encoded EQL with request/response   │
│                                                                     │
│  Key Abstractions:                                                 │
│    - Connection: Request/response lifecycle with timeouts          │
│    - Factory: Environment-specific connection creation             │
│    - Transit: Serialization/deserialization                        │
│    - Resolvers: Pathom mutation/query macros                       │
└──────────────────────────┬──────────────────────────────────────────┘
                           │
                           │ Messages (Transit strings)
                           │
┌──────────────────────────▼──────────────────────────────────────────┐
│ Inspector Application (This Project)                               │
│                                                                     │
│  Global Fulcro App with Multi-Inspector UI                         │
│    ├─ UI Parser: Pathom-based query processing (30s timeout)      │
│    ├─ Multi-Inspector: Manages multiple app inspections           │
│    ├─ Normalized State: History, requests, transactions           │
│    └─ DevTool API Implementation: Mutation handlers               │
│                                                                     │
│  Main UI Components:                                               │
│    ├─ DB Explorer: State tree navigation with path watching       │
│    ├─ Transactions: Mutation timeline and replay                  │
│    ├─ Network: Request/response tracking with flame graphs        │
│    ├─ OGE: EQL query builder and executor                         │
│    ├─ Statecharts: State machine visualization                    │
│    └─ Settings: Preferences (WebSocket port, compact keywords)    │
└─────────────────────────────────────────────────────────────────────┘
```

### Chrome Extension Communication Flow

```
Target App (Web Page)
    ↓ window.postMessage
Content Script (chrome/content_script.cljc)
    ↓ chrome.runtime.sendMessage
Background Worker (chrome/background_worker.cljs)
    ↓ routing based on target field
DevTools Panel (chrome/devtool.cljc)
    ↓ processes messages
Inspector UI (fulcro-inspect app)
```

**Key Security Feature**: Messages traverse multiple hops due to Chrome's content script isolation. The `fulcro-devtools-remote` library completely hides this complexity.

### Electron Communication Flow

```
Target App (Electron)
    ↓ WebSocket (Sente client)
Background Server (electron/background/websocket_server.cljs)
    ├─ Express + Sente server (port 8237)
    └─ IPC (via electronAPI preload)
Electron Renderer (electron/devtool.cljc)
    ↓ creates connection
Inspector UI (fulcro-inspect app)
```

**Advantage**: Direct WebSocket connection; no multi-hop routing needed.

---

## Communication Protocol

### Core Protocol: DevToolConnection

Defined in `com.fulcrologic.devtools.common.protocols`:

```clojure
(defprotocol DevToolConnection
  (-transmit! [this target-id edn] "Private version. Use transmit!"))

;; Public API
(defn transmit!
  "Low-level method that can send data to the other end of the connection.
   Returns a core.async channel that will contain the response, or will close on timeout."
  [conn target-id edn]
  ...)
```

**Key Points**:
- Single-method protocol for bi-directional communication
- `target-id`: UUID identifying the target in multi-target environment
- `edn`: EQL query/mutation to send
- Returns: `core.async` channel with response
- **Timeout**: 10 seconds for responses

### Message Format

All messages are serialized to **Transit strings**. Structure:

```clojure
{::message-keys/request      [EQL]           ; the query/mutation
 ::message-keys/target-id    uuid            ; which target (or tool if response)
 ::message-keys/request-id   uuid            ; for request/response pairing
 ::message-keys/response     data            ; response data
 ::message-keys/error        string          ; error message if failed
 ::message-keys/connected?   boolean}        ; connection event indicator
```

### Request/Response Lifecycle

1. **Tool calls `transmit!`** with target-id and EQL
   ```clojure
   (async/<! (protocols/transmit! conn target-id [{:query [:data]}]))
   ```

2. **Connection generates request-id**, creates response channel
   - Stores channel in `active-requests` map
   - Sets 10-second timeout

3. **Message sent** to async processor (transport-specific)
   - Chrome: via `window.postMessage`
   - Electron: via WebSocket/IPC

4. **Target receives**, processes with async-processor (Pathom)
   - Resolvers handle the EQL
   - Return result

5. **Response comes back** with matching request-id
   - Retrieved from `active-requests` map
   - Sent to waiting channel

6. **Caller receives result**
   - Timeout triggers if no response in 10s
   - Returns error map with `:error` key

### Connection Establishment

1. **Tool/Target starts**: No connection yet
2. **Preload loads factory**: Creates factory instance via `set-factory!`
3. **App calls `connect!`**: Creates `Connection`, starts message loops
4. **Both sides receive**: `devtool-connected` mutation with `open? = true`
5. **Target sends**: `target-started` mutation with ID and description

### Built-in Mutations

Defined in `com.fulcrologic.devtools.common.built-in-mutations`:

- `devtool-connected`: Connection status changed (`connected?` + `target-id`)
- `target-started`: New target registered (includes `target-id` + `target-description`)

---

## Key Components

### 1. Target Integration (`fulcro.inspect.tool`)

**File**: `src/lib/fulcro/inspect/tool.cljc` (41 lines)

**Purpose**: Main API for target applications to enable inspection.

**Key Function**:
```clojure
(defn add-fulcro-inspect!
  "Adds Fulcro Inspect monitoring to your fulcro application.
   This function is a noop if Fulcro Inspect is disabled by compiler flags"
  [app]
  ...)
```

**What it does**:
1. Generates UUID for app (`app-uuid`)
2. Creates connection via `ct/connect!`
3. Provides `async-processor` for EQL queries (Pathom)
4. Registers watch on state atom (`db-changed!`)
5. Records initial history entry
6. Registers tool handler (`handle-inspect-event`)

**Important**: Uses `ilet` macro - entire block elided in production builds.

### 2. DevTool API Implementation (`fulcro.inspect.devtool-api-impl`)

**File**: `src/ui/fulcro/inspect/devtool_api_impl.cljs` (72 lines)

**Purpose**: Implements mutations that handle events from inspected applications.

**Key Mutations**:

```clojure
;; New app connected
(defmutation app-started [{:fulcro/keys [app]} params]
  {::pc/sym `dapi/app-started}
  (fp/transact! app `[(fulcro.inspect.common/start-app ~params)]))

;; Connection status changed
(defmutation connect-mutation [{:fulcro/keys [app]} {:keys [connected? target-id]}]
  {::pc/sym `bi/devtool-connected}
  (cond
    (and target-id (not connected?)) (dispose-app app {::app/id target-id})
    (not connected?) (fp/transact! app [(multi-inspector/remove-all-inspectors {})])))

;; Network request lifecycle
(defmutation send-started [{:fulcro/keys [app]} params]
  {::pc/sym `dapi/send-started}
  (fp/transact! app [(network/request-start params)] ...))

(defmutation send-finished [{:fulcro/keys [app]} params]
  {::pc/sym `dapi/send-finished}
  (fp/transact! app [(network/request-finish params)] ...))

;; Transaction tracking
(defmutation optimistic-action [{:fulcro/keys [app]} params]
  {::pc/sym `dapi/optimistic-action}
  (new-client-tx app params))

;; State updates
(defmutation update-client-db [{:fulcro/keys [app]} history-step]
  {::pc/sym `dapi/db-changed}
  (fp/transact! app [(hist/save-history-step history-step)]))

;; Focus inspector
(defmutation focus-target [{:fulcro/keys [app]} {::app/keys [id]}]
  {::pc/sym `dapi/focus-target}
  (fp/transact! app [(multi-inspector/set-app {::inspector/id [:x id]})] ...))
```

### 3. Multi-Inspector Container (`fulcro.inspect.ui.multi-inspector`)

**File**: `src/ui/fulcro/inspect/ui/multi_inspector.cljs` (144 lines)

**Purpose**: Manages multiple simultaneous app inspections.

**Key Component**:
```clojure
(defsc MultiInspector [this {::keys [current-app inspectors show-settings? settings]}]
  ...)
```

**Key Mutations**:
- `add-inspector`: Register new app
- `remove-inspector`: Clean up app inspection
- `set-app`: Switch focus between apps
- `toggle-settings`: Show/hide settings panel

### 4. Individual Inspector (`fulcro.inspect.ui.inspector`)

**File**: `src/ui/fulcro/inspect/ui/inspector.cljs` (172 lines)

**Purpose**: Main component for individual app inspection with tab-based UI.

**Tabs**:
- `::page-db`: Database Explorer
- `::page-transactions`: Transaction History
- `::page-network`: Network Inspector
- `::page-oge`: Object Graph Explorer (query builder)
- `::page-statecharts`: State Machine Visualization
- `::page-settings`: Settings

### 5. Database Explorer (`fulcro.inspect.ui.db-explorer`)

**File**: `src/ui/fulcro/inspect/ui/db_explorer.cljs` (454 lines)

**Features**:
- Path-based navigation through state tree
- Search with highlighting
- State snapshots from history
- Expandable/collapsible tree view
- Compact keywords option
- Data watching

**Key Mutations**:
- `set-path`: Navigate to specific location
- `append-to-path`: Drill down into nested structures

### 6. Data Viewer (`fulcro.inspect.ui.data-viewer`)

**File**: `src/ui/fulcro/inspect/ui/data_viewer.cljs` (405 lines)

**Purpose**: Recursive rendering of arbitrary Clojure data structures.

**Features**:
- Expandable/collapsible nodes
- Smart inline display (truncates large structures)
- Copy-to-clipboard
- Path linking for navigation
- Handles special types: tempids, sets, vectors, maps

### 7. History Management (`fulcro.inspect.lib.history`)

**File**: `src/client/fulcro/inspect/lib/history.cljs`

**Purpose**: Manages historical state snapshots for time-travel debugging.

**Key Data Structure**:
```clojure
{:history/version      1           ; Incrementing version number
 :history/value        {...}       ; Full state snapshot
 :history/based-on     0           ; Base version for diff
 :history/diff         {...}       ; Diff from based-on
 :fulcro.inspect.core/app-id "App"} ; App name
```

**Constants**:
- `DB_HISTORY_BUFFER_SIZE`: 200 entries
- History is diff-based for efficient storage

**Key Functions**:
- `history-step-ident`: Creates identifiers for snapshots
- `prune-history*`: Manages history size
- `version-of-state-map`: Retrieves historical state values

### 8. Diff Algorithm (`fulcro.inspect.lib.diff`)

**File**: `src/client/fulcro/inspect/lib/diff.cljc` (65 lines)

**Purpose**: Efficient state change tracking.

**Key Functions**:
```clojure
(defn updates [a b]
  "Calculates what changed between two maps")

(defn removals [a b]
  "Calculates what was removed")

(defn patch [base diff]
  "Applies diffs to reconstruct historical states")
```

Supports nested map diffing for efficient storage.

### 9. Network Inspector (`fulcro.inspect.ui.network`)

**File**: `src/ui/fulcro/inspect/ui/network.cljs` (272 lines)

**Features**:
- Request/response tracking
- Flame graph visualization (Pathom profiling)
- Error viewing
- Duration tracking
- Keeps last 50 requests

**Request Data Structure**:
```clojure
{:fulcro.inspect.ui.network/request-id    uuid
 :fulcro.inspect.ui.network/request-edn   {...}
 :fulcro.inspect.ui.network/response-edn  {...}
 :fulcro.inspect.ui.network/error         {...}
 :fulcro.inspect.ui.network/remote        :remote
 ::request-started-at                      (js/Date)
 ::request-finished-at                     (js/Date)}
```

### 10. Transaction Viewer (`fulcro.inspect.ui.transactions`)

**File**: `src/ui/fulcro/inspect/ui/transactions.cljs` (363 lines)

**Features**:
- Timeline visualization
- Transaction replay
- Diff visualization
- Format-specific handlers for:
  - State machine events
  - Load operations
  - Generic mutations

---

## File Structure

```
/Users/tonykay/fulcrologic/fulcro-inspect/
├── src/
│   ├── lib/                                    # Core library code
│   │   ├── fulcro/inspect/tool.cljc           # Main API: add-fulcro-inspect!
│   │   └── fulcro/inspect/api/target_api.cljc # Remote mutations
│   │
│   ├── client/                                 # Client-side utilities
│   │   └── fulcro/inspect/
│   │       ├── lib/history.cljs               # History management (200 buffer)
│   │       ├── lib/diff.cljc                  # State diffing algorithm
│   │       ├── lib/local_storage.cljs         # Preferences persistence
│   │       └── helpers.cljs                   # Entity helpers
│   │
│   ├── ui/                                     # Inspector UI components
│   │   └── fulcro/inspect/
│   │       ├── ui_parser.cljs                 # Pathom-based query parser
│   │       ├── common.cljs                    # GlobalRoot, event handling
│   │       ├── devtool_api_impl.cljs          # Mutation handlers
│   │       ├── ui/
│   │       │   ├── multi_inspector.cljs       # Multi-app container
│   │       │   ├── inspector.cljs             # Single app inspector (tabs)
│   │       │   ├── db_explorer.cljs           # State browser (454 lines)
│   │       │   ├── data_viewer.cljs           # Data renderer (405 lines)
│   │       │   ├── data_watcher.cljs          # Path watchers
│   │       │   ├── data_history.cljs          # Time-travel UI
│   │       │   ├── transactions.cljs          # Transaction history (363 lines)
│   │       │   ├── network.cljs               # Network inspector (272 lines)
│   │       │   ├── multi_oge.cljs             # Query builder wrapper
│   │       │   ├── statecharts.cljs           # State machine viewer
│   │       │   ├── element.cljs               # Component picker
│   │       │   ├── settings.cljs              # Settings panel
│   │       │   ├── core.cljs                  # Design system (554 lines)
│   │       │   └── helpers/clipboard.cljs     # Clipboard utilities
│   │       └── com/wsscode/oge/               # Object Graph Explorer
│   │           └── ui/
│   │               ├── codemirror.cljs        # EQL editor
│   │               ├── flame_graph.cljs       # Pathom profiling
│   │               └── network.cljs           # Network viz
│   │
│   ├── chrome/                                 # Chrome extension specific
│   │   └── fulcro/inspect/chrome/devtool/main.cljs  # (70 lines)
│   │
│   └── electron/                               # Electron app specific
│       └── fulcro/inspect/electron/
│           ├── background/main.cljs           # Main process (88 lines)
│           └── renderer/main.cljs             # Renderer process (72 lines)
│
├── shells/                                     # Build templates
│   ├── chrome/                                # Chrome extension shell
│   └── electron/                              # Electron app shell
│
├── deps.edn                                    # Clojure dependencies
├── shadow-cljs.edn                            # ClojureScript build config
└── pom.xml                                    # Maven build config
```

### Key File Counts

- **Total source files**: ~40 ClojureScript files
- **UI components**: ~3,280 lines (core.cljs: 554, db_explorer: 454, data_viewer: 405, etc.)
- **Chrome integration**: 70 lines
- **Electron integration**: 160 lines (88 + 72)

---

## Development Workflow

### Build Targets (shadow-cljs.edn)

1. **Chrome Extension**:
   - `:chrome-background` - Service worker
   - `:chrome-content-script` - Page communication
   - `:chrome-devtool` - Inspector UI panel

2. **Electron App**:
   - `:electron-main` - Background process
   - `:electron-renderer` - Renderer process UI

3. **Development**:
   - Hot reload support
   - Source maps enabled
   - Guardrails assertions (compact mode)

### Common Development Tasks

**Start Chrome Extension Development**:
```bash
npx shadow-cljs watch chrome-background chrome-content-script chrome-devtool
# Load unpacked extension from shells/chrome/build
```

**Start Electron App Development**:
```bash
npx shadow-cljs watch electron-main electron-renderer
# Start electron from shells/electron
```

**Build Production**:
```bash
npx shadow-cljs release chrome-background chrome-content-script chrome-devtool
# or
npx shadow-cljs release electron-main electron-renderer
```

### Testing Integration

**In Target App** (add to `shadow-cljs.edn`):
```clojure
{:builds
 {:app {:devtools {:preloads [com.fulcrologic.devtools.chrome-preload]  ; or websocket-preload
                   :after-load my.app/refresh}}}}
```

**In Application Code**:
```clojure
(ns my.app
  (:require
    [com.fulcrologic.devtools.common.target :refer [ido]]
    [fulcro.inspect.tool :as it]))

(defonce app (fulcro-app {...}))

;; Elided in production builds
(ido (it/add-fulcro-inspect! app))
```

**For Electron**: Set WebSocket port in settings (default: 8237)

---

## Integration Guide

### Target Application Setup

#### 1. Add Dependencies

**deps.edn**:
```clojure
{:aliases
 {:dev {:extra-deps
        {com.fulcrologic/fulcro-inspect {:mvn/version "3.1.0"}
         com.fulcrologic/fulcro-devtools-remote {:mvn/version "0.2.6"}}}}}
```

#### 2. Configure Preload

**shadow-cljs.edn** (for Chrome):
```clojure
{:builds
 {:app {:devtools {:preloads [com.fulcrologic.devtools.chrome-preload]}}}}
```

**shadow-cljs.edn** (for Electron):
```clojure
{:builds
 {:app {:devtools {:preloads [com.fulcrologic.devtools.electron-preload]}}}}
```

#### 3. Enable in Application

```clojure
(ns myapp.main
  (:require
    [com.fulcrologic.devtools.common.target :refer [ido]]
    [com.fulcrologic.fulcro.application :as app]
    [fulcro.inspect.tool :as it]))

(defonce app (app/fulcro-app {...}))

;; Automatically elided in production
(ido (it/add-fulcro-inspect! app))

(defn ^:export init []
  (app/mount! app RootComponent "app"))
```

#### 4. Start Inspector

**Chrome Extension**:
1. Load unpacked extension from `shells/chrome/build`
2. Open DevTools in your app's tab
3. Click "Fulcro Inspect" panel

**Electron App**:
1. Start Electron inspector: `npm start` (in electron shell)
2. Set WebSocket port if different from 8237
3. Inspector auto-connects to running apps

### Remote Mutations from Inspector to Target

**Define API** (shared namespace):
```clojure
(ns myapp.target-api
  (:require
    [com.fulcrologic.devtools.common.target :refer [ido]]
    [com.fulcrologic.devtools.common.resolvers :as res]))

(ido
  (res/remote-mutations restart-app reset-data))
```

**Implement in Target**:
```clojure
(ns myapp.target-impl
  (:require
    [com.fulcrologic.devtools.common.resolvers :as res]
    [myapp.target-api :as api]))

(res/defmutation restart-app [{:fulcro/keys [app]} params]
  {::pc/sym `api/restart-app}
  ;; Reset app state
  (let [initial-state (comp/get-initial-state RootComponent {})]
    (reset! (::app/state-atom app) initial-state)
    (app/force-root-render! app)))
```

**Call from Inspector** (or programmatically):
```clojure
(fulcro.inspect.tool/focus-inspector! app-uuid)
```

---

## Message Flow Examples

### Example 1: State Change Notification

**Target App**:
1. User performs mutation that changes state
2. State atom watch triggers `db-changed!`
3. `db-changed!` creates history entry with diff
4. Calls `dp/transmit!` with `dapi/db-changed` mutation

```clojure
(dp/transmit! connection target-id
  [(dapi/db-changed {:history/version version
                     :history/value   new-state
                     :history/based-on prev-version
                     :history/diff    diff})])
```

**Inspector**:
1. Receives message via `async-processor`
2. `devtool-api-impl/update-client-db` handles mutation
3. Calls `(hist/save-history-step history-step)`
4. Updates normalized state with new history entry
5. UI re-renders showing new state

### Example 2: Network Request Tracking

**Target App** (send starts):
1. Fulcro processes remote request
2. Tool handler `handle-inspect-event` receives `:fulcro.inspect.client/send-started`
3. Transmits `dapi/send-started` mutation

```clojure
(dp/transmit! connection target-id
  [(dapi/send-started {::app/id app-uuid
                       :fulcro.inspect.ui.network/request-id req-id
                       :fulcro.inspect.ui.network/request-edn edn
                       ::request-started-at (js/Date.)})])
```

**Inspector**:
1. `devtool-api-impl/send-started` handles mutation
2. Calls `(network/request-start params)`
3. Adds request to network history

**Target App** (send finishes):
1. Remote returns response
2. Transmits `dapi/send-finished` with response data

**Inspector**:
1. `devtool-api-impl/send-finished` handles mutation
2. Updates request with response and finish time
3. UI shows completed request with duration

### Example 3: Inspector Queries Target

**Inspector** (user navigates to path in DB Explorer):
1. User clicks on nested path in state tree
2. Inspector needs to fetch that part of state
3. Calls remote query (if not already cached)

**Target App**:
1. Receives EQL query via `async-processor`
2. Pathom resolvers process query
3. Returns requested state data

**Inspector**:
1. Receives response via `core.async` channel
2. Updates UI with fetched data
3. Renders expanded view

### Example 4: Transaction Replay

**Inspector** (user clicks "Replay" on transaction):
1. Inspector calls `api/run-transaction` mutation

```clojure
(dev/transact! inspector target-id
  [(api/run-transaction {:transaction tx-data})])
```

**Target App**:
1. Receives mutation via resolver
2. Executes `(comp/transact! app tx-data)`
3. Transaction runs as if user performed it
4. State changes trigger new history entries
5. Inspector automatically receives updates

---

## Inspector State Schema

The inspector maintains normalized Fulcro state:

```clojure
{
  ;; Multi-inspector root
  :fulcro.inspect.ui.multi-inspector/multi-inspector {
    "main" {
      ::current-app        [::inspector/id [:x app-uuid]]  ; Currently viewed app
      ::inspectors         [[::inspector/id [:x uuid-1]]   ; List of connected apps
                            [::inspector/id [:x uuid-2]]]
      ::show-settings?     true                            ; Settings panel visible
      ::settings           {...}                           ; Settings component
    }
  }

  ;; Individual inspector instances
  :fulcro.inspect.ui.inspector/id {
    [:x app-uuid] {
      ::id                 [:x app-uuid]
      ::name               "My App"
      ::tab                ::page-db                       ; Current tab
      ::app-state          {:data-history {...}}          ; App state component
      ::db-explorer        {...}                           ; DB explorer state
      ::network            {...}                           ; Network panel state
      ::transactions       {...}                           ; Transaction panel state
      ::oge                {...}                           ; Query builder state
      ::statecharts        {...}                           ; State machine state
    }
  }

  ;; History entries (diff-based)
  :history/id {
    [app-uuid 0] {
      :history/version      0
      :history/value        {:root {...}}                  ; Full initial state
    }
    [app-uuid 1] {
      :history/version      1
      :history/based-on     0                              ; Base version
      :history/diff         {:updates {...} :removals [...]} ; Diff from v0
    }
  }

  ;; Data watchers
  :data-watcher/id {
    [:x app-uuid] {
      :data-watcher/watches [
        {:watch-pin/id     "pin-uuid-1"
         :watch-pin/path   [:user 123]
         :watch-pin/data-viewer {...}}                     ; Expanded state
      ]
    }
  }

  ;; Network requests
  :network-history/id {
    [:x app-uuid] {
      :fulcro.inspect.ui.network/requests [
        {:fulcro.inspect.ui.network/request-id    req-uuid
         :fulcro.inspect.ui.network/request-edn   [...]
         :fulcro.inspect.ui.network/response-edn  {...}
         :fulcro.inspect.ui.network/remote        :remote
         ::request-started-at                      #inst "2025-10-22T..."
         ::request-finished-at                     #inst "2025-10-22T..."}
      ]
    }
  }

  ;; Transactions
  :fulcro.inspect.ui.transactions/tx-list-id {
    [:x app-uuid] {
      :fulcro.inspect.ui.transactions/tx-list [
        {::app/id         app-uuid
         :tx/tx           [(some/mutation {})]
         :tx/timestamp    #inst "2025-10-22T..."
         :tx/db-before    [app-uuid 5]                     ; History ref
         :tx/db-after     [app-uuid 6]}
      ]
    }
  }
}
```

---

## Key Architectural Patterns

### 1. Diff-Based History

Instead of storing full state for every version:
- **v0**: Full state snapshot
- **v1+**: Diff from previous version

**Storage**: `{:based-on 0, :diff {:updates {...} :removals [...]}}`

**Reconstruction**: Apply patches sequentially to rebuild any version

**Benefits**: Reduced memory usage (200 history entries ~= manageable size)

### 2. Volatile Atom for Circular Dependency

Resolvers need connection reference, but connection creation needs processor function:

```clojure
(let [c (volatile! nil)
      conn (connect! {:async-processor (fn [EQL]
                                          (process-request {:devtool/connection @c} EQL))})]
  (vreset! c conn))
```

### 3. Environment-Based Implementation Selection

Same namespace provides different functionality based on preload:

```clojure
;; Choose ONE preload:
[com.fulcrologic.devtools.chrome-preload]    ; → Chrome support
[com.fulcrologic.devtools.electron-preload]  ; → Electron support
```

Preload sets factory via `set-factory!`, making `connect!` work transparently.

### 4. Conditional Code Inclusion (`ido`/`ilet`)

```clojure
(ido
  ;; This entire block vanishes in release builds
  (add-fulcro-inspect! app))
```

**Macro expansion**:
- **Development**: Code included
- **Production**: Entire form replaced with `nil`

Ensures zero runtime overhead in production.

### 5. Multi-Level Async Abstraction

- **Level 1**: core.async channels (internal)
- **Level 2**: Fulcro remote abstraction (external API)
- **Level 3**: Fulcro mutations/loads (developer API)

Developer writes:
```clojure
(comp/transact! app [(mutations/restart {})])
```

Maps internally to `transmit!` → core.async → response channel.

---

## Performance Considerations

### Memory Management

- **History Pruning**: 200 entry limit (DB_HISTORY_BUFFER_SIZE)
- **Request Limiting**: Last 50 requests kept
- **Diff-Based Storage**: Reduces memory for historical states

### Network Efficiency

- **Request Timeouts**: 10 seconds prevents hanging
- **Transit Serialization**: Compact binary format
- **Lambda Stripping**: Functions replaced with names/`"FN"` (can't serialize)

### Rendering Optimization

- **Lazy Expansion**: Data viewer only renders visible nodes
- **Selective History Fetching**: Fetch historical states on-demand
- **Component-Based State**: Normalized references prevent duplication

---

## Troubleshooting

### Connection Issues

**Chrome Extension**:
- Check preload: `com.fulcrologic.devtools.chrome-preload`
- Verify content script loads (check Console)
- Ensure page isn't sandboxed (blocks `window.postMessage`)

**Electron**:
- Check WebSocket port matches (default: 8237)
- Verify background server running
- Check firewall/security settings

### State Not Updating

- Verify watch registered on state atom
- Check `add-fulcro-inspect!` called after app creation
- Ensure `ido` macro not elided (check build config)

### Mutations Not Working

- Check resolver implementation has correct `::pc/sym`
- Verify shared namespace imported in both target and inspector
- Check timeout (30s for inspector, 10s for devtools-remote)

### Performance Issues

- Prune history if too large (adjust DB_HISTORY_BUFFER_SIZE)
- Disable compact keywords if rendering slow
- Reduce number of watched paths

---

## Additional Resources

### Related Projects

- **Fulcro**: https://github.com/fulcrologic/fulcro
- **Fulcro DevTools Remote**: https://github.com/fulcrologic/fulcro-devtools-remote
- **Pathom**: https://github.com/wilkerlucio/pathom
- **Statecharts**: https://github.com/fulcrologic/statecharts

### Documentation

- Fulcro Book: https://book.fulcrologic.com
- Pathom Docs: https://pathom3.wsscode.com
- Shadow-CLJS: https://shadow-cljs.github.io/docs/UsersGuide.html

---

## Summary

Fulcro Inspect is a sophisticated debugging tool that provides:

1. **Abstracted Communication**: Via `fulcro-devtools-remote` (Chrome/Electron/WebSocket)
2. **Comprehensive Inspection**: State, transactions, network, queries, state machines
3. **Time-Travel Debugging**: Diff-based history with 200+ snapshots
4. **Production-Safe**: `ido`/`ilet` macros ensure zero overhead
5. **Extensible**: Pathom-based, supports custom resolvers/mutations

The architecture cleanly separates concerns:
- **Target**: Monitors app state and events
- **Transport**: Abstracts Chrome/Electron complexity
- **Inspector**: Provides rich UI for debugging

Key insight: The same resolver/mutation code works identically on both target and inspector sides, with the transport layer handling all communication complexity transparently.

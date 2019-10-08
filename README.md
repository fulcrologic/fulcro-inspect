# fulcro-inspect

[![Clojars Project](https://img.shields.io/clojars/v/fulcrologic/fulcro-inspect.svg)](https://clojars.org/fulcrologic/fulcro-inspect)

Latest Release: [![CircleCI](https://circleci.com/gh/fulcrologic/fulcro-inspect/tree/master.svg?style=svg)](https://circleci.com/gh/fulcrologic/fulcro-inspect/tree/master)

Inspect Fulcro applications

## Usage for Fulcro 2.x with Chrome

Add the latest version of this library (see above) as a dependency:

```
[fulcrologic/fulcro-inspect "x.x.x"]
```

Add a preload to your compiler options:

```clojure
:compiler {...
           :preloads        [fulcro.inspect.preload]}
```

The inspector will find the running Fulcro application, and be ready to inspect it!

Next you have to install the Chrome Extension: https://chrome.google.com/webstore/detail/fulcro-inspect/meeijplnfjcihnhkpanepcaffklobaal

Be sure to reload your page after installing it. Now you can see the Fulcro logo get colors when it detects a Fulcro app, from then open Fulcro Inspect tab on the Chrome Devtools and happy inspecting!

## Usage for Fulcro 3.x with Chrome

Do *not* include this library as a dependency. Inspect is written in Fulcro 2.x, and adding it
to your dependencies will confuse you by having two different version of Fulcro (which can co-exist, but will cause confusion).
Instead just install the Chrome 
extension https://chrome.google.com/webstore/detail/fulcro-inspect/meeijplnfjcihnhkpanepcaffklobaal
and add the following preload to your shadow-cljs config:

```clojure
:compiler {...
           :preloads        [com.fulcrologic.fulcro.inspect.preload]}
```

That preload is actually part of Fulcro 3.x itself.

## Using the Fulcro 3.x Electron App (ALPHA)

Fulcro Inspect now has a standalone electron app. This only works with 
Fulcro 3, and there are no plans to back-port to 2.x. You can download
a release for your platform in the Releases section of this repository.

https://github.com/fulcrologic/fulcro-inspect/releases

NOTE: Fulcrologic does not currently have signing keys, so these binaries 
will complain that they are unsigned. 

The Electron app creates a well-known websocket server port that Fulcro 
applications can connect to for exchanging inspect messages. This means
you need to have your application configured with a different preload
that knows how to connect.

You will need to add `socket.io-client` to your `package.json` when
building with shadow-cljs. If you're not
using shadow-cljs you'll need to require the cljsjs version of that library.

Then make sure you add this preload to your preloads:

```clojure
:compiler {...
           :preloads        [com.fulcrologic.fulcro.inspect.websocket-preload]}
```

or call the function `com.fulcrologic.fulcro.inspect.inspect-client/install-ws` somewhere in your 
development startup.

At the moment the Electron App has some limitations:

* There is no way to set the websocket port. So, conflicts on that port will
just make it not work. There is a simple fix, we just haven't created the UI for it.
* Only one app can connect to Inspect at a time. The most recent-connecting 
  application will take precedence.
  If you're running something like workspaces then please use the Chrome extension.
* Start Inspect before running your app.

You should be able to reload your app at any time and have it reconnect.

The electron app is very new and lightly tested, but should mature 
rapidly.

### DB Tab

The DB tab contains the state of the app.

Use the triangles to expand/collapse data:

![Expand/collapse data](https://raw.githubusercontent.com/fulcrologic/fulcro-inspect/master/doc/db-expand.gif)

Click on expanded keys to watch their content:

![Watch DB](https://raw.githubusercontent.com/fulcrologic/fulcro-inspect/master/doc/db-watch.gif)

Use `cmd`/`meta` key + click to expand/collapse the whole sub-tree:

![Expand/collapse sub-tree](https://raw.githubusercontent.com/fulcrologic/fulcro-inspect/master/doc/db-expand-children.gif)

## Contributing

To run the development version of the extension, first install the npm packages:

```
npm install
```

And then run the shadow compilation:

```
npm run dev-chrome
```

The go in at Chrome extensions and add the unpackaged version from the path `shells/chrome`.

Remember to disable the chrome store version to avoid having multiple instances running.

To use the client versin on the app, install locally with `lein install` or use clojure
deps.edn to point the `:local/root` at this repository.

## Authors

Fulcro Inspect was written and contributed by Wilker Lucio.

## License

The MIT License (MIT)
Copyright (c) 2017, Fulcrologic, LLC

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit
persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the
Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

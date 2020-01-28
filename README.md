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

## Using the Fulcro 3.x Electron App

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

IMPORTANT: Inspect 2.3.0-RC1 requires Fulcro 3.1.5-RC1 or greater.
The electron app is built with sente version 1.15.0
(as of the latest electron release). If you use sente on your project for
websockets this version difference could cause problems depending on the
version difference.

WARNING: React Native builds will need to set an environment variable
(or JVM system property) `SENTE_ELIDE_JS_REQUIRE=true` in order for
sente to work inside of mobile runtimes.

To use the electron inspect make sure you add this preload to your preloads:

```clojure
:compiler {...
           :preloads        [com.fulcrologic.fulcro.inspect.websocket-preload]}
```

or call the function `com.fulcrologic.fulcro.inspect.inspect-client/install-ws` somewhere in your 
development startup.

### Choosing the Websocket Port

The Electron app includes an input field at the top of the UI for the websocket
port to use (default 8237). Pressing `Restart Websockets` will cause it to
save the port and restart the websocket server. This allows you to use an alternate port should
8237 already be bound by another process; however, in order to do this
you *must* change the port in *two* places: That input field, and in your
build configuration.

In your CLJS build, set closure-defines. In `shadow-cljs.edn` this is done
like this:

```
 :builds   {:main     {:target     :browser
                       ...
                       :dev        {:closure-defines  {com.fulcrologic.fulcro.inspect.inspect_ws/SERVER_PORT 3003}}
                       :devtools   {:preloads [com.fulcrologic.fulcro.inspect.websocket-preload]}}}
```

The value of `SERVER_PORT` must match what you wish to use in the Electron
app. The electron app itself will remember the port you last used between
restarts, so you can safely set some known good value for this in your
system (note: only admins can use ports 1-1024, so choose a number
bigger than that).

### Known Limitations

* Applications connect to inspect when they start. If you start
  the electron version of Inspect after your app, then you'll need to reload
  you app for it to connect.

## Inspect Features

### DB Tab

The DB tab contains the state of the app.

Use the triangles to expand/collapse data:

![Expand/collapse data](https://raw.githubusercontent.com/fulcrologic/fulcro-inspect/master/doc/db-expand.gif)

Click on expanded keys to watch their content:

![Watch DB](https://raw.githubusercontent.com/fulcrologic/fulcro-inspect/master/doc/db-watch.gif)

Use `cmd`/`meta` key + click to expand/collapse the whole sub-tree:

![Expand/collapse sub-tree](https://raw.githubusercontent.com/fulcrologic/fulcro-inspect/master/doc/db-expand-children.gif)

To copy contents from maps or sequences, expand it and them click on the copy button:

![Copy data](https://raw.githubusercontent.com/fulcrologic/fulcro-inspect/master/doc/db-copy.gif)

## Building Chrome Extension

If you want to build the extension yourself and run from it, first run the release builder:

```
./script/release-chrome
```

The extension release version will be ready at `releases/chrome`.

Now, in the chrome extensions page (`chrome://extensions/`), first make sure you have
`Developer mode` turned on, then look for a button saying `Load Unpacked`, click on it
and navigate to the `releases/chrome` diretory to load the extension from there.

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

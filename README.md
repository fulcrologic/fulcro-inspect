# fulcro-inspect

[![Clojars Project](https://img.shields.io/clojars/v/fulcrologic/fulcro-inspect.svg)](https://clojars.org/fulcrologic/fulcro-inspect)

Latest Release: [![CircleCI](https://circleci.com/gh/fulcrologic/fulcro-inspect/tree/master.svg?style=svg)](https://circleci.com/gh/fulcrologic/fulcro-inspect/tree/master)

Inspect Fulcro applications

## Usage

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

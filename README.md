# fulcro-inspect

[![Clojars Project](https://img.shields.io/clojars/v/fulcrologic/fulcro-inspect.svg)](https://clojars.org/fulcrologic/fulcro-inspect)

Latest Release: [![CircleCI](https://circleci.com/gh/fulcrologic/fulcro-inspect/tree/master.svg?style=svg)](https://circleci.com/gh/fulcrologic/fulcro-inspect/tree/master)

Inspect Fulcro applications

## Usage

Add the latest version of this library (see above) as a dependency:

```
[fulcrologic/fulcro-inspect "x.x.x"]
```

Note: for Fulcro 1.2.x use version `0.1.0-SNAPSHOT`, use newer numbers for Fulcro 2.x

Add a preload to your compiler options:

```clojure
:compiler {...
           :preloads        [fulcro.inspect.preload]
           ; ctrl-f is the default keystroke
           :external-config {:fulcro.inspect/config {:launch-keystroke "ctrl-f"}}}
```

The inspector will find the running Fulcro application, and be ready to inspect it!

To launch the inspector, use the `ctrl-f` keystroke on your keyboard (unless you changed the
configuration to something else).

### DB Tab

The DB tab contains the state of the app.

Use the triangles to expand/collapse data:

![Expand/collapse data](https://raw.githubusercontent.com/fulcrologic/fulcro-inspect/develop/doc/db-expand.gif)

Click on expanded keys to watch their content:

![Watch DB](https://raw.githubusercontent.com/fulcrologic/fulcro-inspect/develop/doc/db-watch.gif)

Use `cmd`/`meta` key + click to expand/collapse the whole sub-tree:

![Expand/collapse sub-tree](https://raw.githubusercontent.com/fulcrologic/fulcro-inspect/develop/doc/db-expand-children.gif)

## Contributing

Development is done against apps in dev cards, so run figwheel
via:

```
lein run -m clojure.main script/figwheel.clj
```

This will start a build for tests and devcards. Open
[http://localhost:3389](http://localhost:3389) for the
cards, and
[http://localhost:3389/test.html](http://localhost:3389/test.html) for the
tests.

You can run the tests (once) from the command line with `make tests`

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

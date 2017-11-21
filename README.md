# fulcro-inspect

[![Clojars Project](https://img.shields.io/clojars/v/fulcrologic/fulcro-inspect.svg)](https://clojars.org/fulcrologic/fulcro-inspect)

Inspect Fulcro applications

## Usage

Add this dependencies:

```
[fulcrologic/fulcro "1.2.0-SNAPSHOT"]
[fulcrologic/fulcro-inspect "0.1.0-SNAPSHOT"]
```

Setup the preloads on your compiler options:

```clojure
:compiler {...
           :preloads        [fulcro.inspect.preload]
           ; ctrl-f is the default keystroke
           :external-config {:fulcro.inspect/config {:launch-keystroke "ctrl-f"}}}
```

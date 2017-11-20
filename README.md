# fulcro-inspect

Inspect Fulcro applications

## Usage

The `fulcro-inspect` is not released on clojars yet, so you need to download this repository and run:

```
lein install
```

Then add this dependencies:

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

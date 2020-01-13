## Electron

Building MacOS releases is done as follows:

```
$ npm install
$ cd shells/electron
$ yarn
$ cd ../..
$ shadow-cljs release electron-main electron-renderer
$ cd shells/electron
$ electron-builder build
```

Building the Windows and Linux releases requires that you have Docker
installed, then you can run a Linux image with a wine-centric builder
via:

```
$ ./build-linux.sh
root@234987:/project# eletron-builder build -wl
```

and the resulting files will be in `dist`.


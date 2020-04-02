## Electron

Building MacOS releases is done as follows:

```
$ npm install
$ cd shells/electron
$ yarn
$ cd ../..
$ shadow-cljs release electron-main electron-renderer
$ cd shells/electron
$ electron-builder build -m
```

Building the Windows and Linux releases requires that you have Docker
installed, then you can run a Linux image with a wine-centric builder
via:

```
$ ./build-linux.sh
root@234987:/project# yarn
root@234987:/project# eletron-builder build -wl
root@234987:/project# exit
$ ls dist
```

and the resulting files will be in `dist`.

# Chrome

First, make sure to update the version number in `shells/chrome/manifest.edn`.

Then build the zip file that can be uploaded to the Chrome store:

```
npm install
script/release-chrome
```

The result will be in a zip file in `releases`.

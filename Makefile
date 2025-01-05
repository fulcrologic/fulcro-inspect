tests:
	npm install
	lein doo chrome automated-tests once

electron-dev:
	(cd shells/electron; electron .)

chrome:
	shadow-cljs release chrome-devtool chrome

releases:
	script/release-chrome
	shadow-cljs release electron-main electron-renderer
	(cd shells/electron; electron-builder -m)
	(cd shells/electron; electron-builder --win --x64)
	(cd shells/electron; electron-builder --linux --x64)

.PHONY: releases

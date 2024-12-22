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
	cd shells/electron
	electron-builder -m
	electron-builder --win --x64
	electron-builder --linux --x64

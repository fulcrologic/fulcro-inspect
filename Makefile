tests:
	npm install
	lein doo chrome automated-tests once
electron-dev:
	(cd shells/electron; electron .)

chrome:
	shadow-cljs release chrome-devtool chrome

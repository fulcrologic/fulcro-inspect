tests:
	pnpm install --frozen-lockfile
	lein doo chrome automated-tests once

electron-dev:
	(cd shells/electron; pnpm exec electron .)

chrome:
	pnpm exec shadow-cljs release chrome-devtool chrome

releases:
	script/release-chrome
	pnpm exec shadow-cljs release electron-main electron-renderer
	(cd shells/electron; pnpm exec electron-builder -m)
	(cd shells/electron; pnpm exec electron-builder --win --x64)
	(cd shells/electron; pnpm exec electron-builder --linux --x64)

.PHONY: releases

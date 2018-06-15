# Change Log

## [2.2.0-beta6]
- Fix event source check, prevent JS errors from externals messages
- Support disposing apps

## [2.2.0-beta5]
- Fix bug that prevented app to start when it was too quick
- Don't process events unless user has the extension installed

## [2.2.0-beta4]
- Support forcing app to re-render (use the same reset state button)
- Use transit default handlers to fallback encoding (fast finally!)

## [2.2.0-beta3]
- Don't load indexes on app start
- Optimised sanitization (still slow)

## [2.2.0-beta2]
- Fix devtool scroll going away from panel
- Fix network not updating when it's finished while open 
- Faster scrubbing 

## [2.2.0-beta1]
- New remote architecture, the client now lives outside of the browser.
- Add Query pane, allows for running queries on app remotes.
- Add i18n page: switch locales from inspector. Thanks @mitchelkuijpers!
- Improved element picker algorithm, now it should be better at picking items.
- Remember watches by app id.
- Add flamegraph profile on network details (when profile data is requested).

## [2.1.0]
- New feature: record/restore snapshots from app state
- Fix: improved function that resets app state

## [2.0.1]
- New feature: replay mutations from the transactions panel

## [2.0.0]
- Fix issues when transactions are called before inspector is ready, now any tx called before app is ready will be ignored.
- Fixed wrapper for new networking

## [2.0.0-alpha6]
- Fix multi-app selector bug related to normalization.
- Fix iframe subtree unmount
- Performance improvement: don't render react tree when inspect is inactive
- Performance improvement: limit number of inline items rendered via map
- Compatibility with new Fulcro `FulcroRemoteI` protocol (Fulcro 2.3+)

## [2.0.0-alpha5]
- Fix a new issue on inspect-tx being called before app-id is ready

## [2.0.0-alpha4]
- New dock mode: now inspect can be docked on page footer
- Current inspector state (open/closed, position and size) are recorded on local storage and restored on page reloads.

## [2.0.0-alpha3]
- Add history dom preview.

## [2.0.0-alpha2]
- Fix error that happens when fulcro fires transactions before app is initialized.

## [2.0.0-alpha1]
- Initial Release

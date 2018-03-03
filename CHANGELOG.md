# Change Log

## [2.0.0-alpha7]
- Fix issues when transactions are called before inspector is ready, now any tx called before app is ready will be ignored.

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

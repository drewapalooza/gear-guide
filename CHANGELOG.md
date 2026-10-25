# Changes

## 0.11.0

- Replaced direct filesystem APIs with RuneLite's scoped `Filepath` storage and chooser. Requested RuneLite's automatic migration from the legacy data directory; saved setup JSON remains compatible.
- Removed the local HTTP assistant server, discovery and bridge helper to comply with Plugin Hub rules. Added user-selected setup JSON import with account/revision/item validation and editor review before saving. Snapshot export now uses RuneLite's chooser.
- Kept the free manual gear/bank workflow and optional, user-triggered AI generation through RuneLite's injected transport.
- Added a production-bytecode policy check and tests for selected-file validation, storage replacement, migration metadata and the narrow sidebar's import button. Updated usage, privacy, format and submission documentation.

Validation: 118 automated tests passed against RuneLite 1.13.3. Plugin JAR, optional development client and sample sidebar previews built successfully. No external provider requests are made by tests.

## 0.10.2

- Replaced Java's HTTP client with RuneLite's injected OkHttpClient for optional AI requests. Retained the shared transport settings while keeping provider timeouts, redirects disabled and explicit cancellation scoped to the active request.
- Corrected the BSD-2-Clause licence to attribute all code authors directly. Kept the separate third-party data notices and source-file copyright headers.

Validation: 117 automated tests passed against RuneLite 1.13.3. New transport tests cover all four providers, UTF-8 payloads, inherited transport configuration, safe HTTP failures, response cleanup and cancellation. No provider requests are made by these tests.

## 0.10.1

- Renamed the plugin to Ayra's Gear Guide.

- Added editable rune supplies to saved loadouts, with required quantities and shortage checks in the sidebar.
- The loadout bank view now includes rune supplies in separate rows below equipment and inventory.
- Added optional `runes` metadata for the local API and AI-generated builds. Older setups remain compatible. Pouch filling and item charging remain manual.

Validation: 112 automated tests passed against RuneLite 1.13.3, including full inventories, rune rows, combined quantities, persistence, older setup JSON and sidebar quantities.

## 0.10.0

- Focused the public release on gear setups and bank organization. Removed Directions, its Slayer location data, path overlay and Shortest Path integration.
- Replaced overflowing tabs with always-visible Loadout, Notes and Bank buttons.
- Stopped rebuilding the saved-build selector and bank list on unchanged background updates. Preserved bank selection, scroll position and draft category assignments when data refreshes.
- Added sidebar interaction regressions; retained ownership, requirement, account, potion and API validation tests.
- Kept AI generation and the local assistant bridge optional. The bridge is off by default; a port conflict no longer prevents the plugin's gear and bank tools from starting.
- Retained the ornamented Void requirement fix.
- Updated development builds for RuneLite's current release, with release validation against 1.13.3.
- Added public usage/privacy documentation, BSD-2-Clause code licensing, retained data attribution, a source-only Plugin Hub configuration and build workflow.

Validation: 104 automated tests passed against RuneLite 1.13.3; the production Swing sidebar was rendered at its normal narrow width. No paid provider calls, automatic game actions or public submission were performed by these tests.

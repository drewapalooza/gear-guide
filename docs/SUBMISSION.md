# Plugin Hub submission candidate

The candidate contains gear setups and bank organization, user-selected JSON import/export and optional user-triggered AI generation. Manual editing, import/export, validation and all bank tools work without a provider account or payment.

## Submission format

RuneLite builds a public source repository pinned to an exact Git commit. The existing submission is [runelite/plugin-hub#19140](https://github.com/runelite/plugin-hub/pull/19140). Update that PR's `plugins/gear-guide` marker after publishing each source fix:

```text
repository=https://github.com/drewapalooza/gear-guide.git
commit=<exact published source commit>
```

The development client bundle is not the Hub submission artifact.

## Review notes

- All production disk access uses RuneLite `Filepath`. `getPluginDirectory()` supplies the scoped storage root; `legacyDataDirectory` requests RuneLite's migration of the previous storage directory. Imports/exports use `Filepath.Chooser` for explicitly selected files. There is no production `Filepath.Unchecked` use.
- The former HTTP assistant server, connection discovery and bridge helper have been removed. External advice uses file export/import with validation and editor review before saving.
- Optional AI requests use RuneLite's injected `OkHttpClient` and `Gson`. Requests require a data-sharing/cost dialog; provider keys are session-only and account identifiers/revisions are removed from requests. No automatic or periodic generation.
- No movement, withdrawal, equipping, combat or other game-action automation. Bank views use the built-in Bank Tags APIs. Potion-storage scripts only query observed dose counts and the selected dose size.
- No reflection, native libraries, subprocesses, downloaded code, own network clients or changes to other plugins' settings. Notes are static setup text, not live combat indicators.
- Code uses BSD-2-Clause with retained author credits. Bundled Wiki-derived data retains separate CC BY-NC-SA attribution and terms in `THIRD_PARTY_NOTICES.md`.
- Only production plugin classes, resources and licensing belong in the Hub JAR. Tests, previews, development launchers, client bundles, account files and snapshots are excluded.

## Validation

118 automated tests pass against RuneLite 1.13.3, including selected-file imports, atomic scoped storage, account/revision validation, rune quantities, intercepted AI transport/cancellation and narrow sidebar controls. The production-bytecode test checks prohibited file, server, runtime and transport API references. Generated screenshots use labelled sample data.

The published source and pinned Hub marker must both pass GitHub CI. Maintainers determine approval.

## Official rules reviewed

- [Submission instructions](https://github.com/runelite/plugin-hub#submitting-a-plugin)
- [Plugin Hub review process](https://github.com/runelite/runelite/wiki/Plugin-Hub-Review)
- [Rejected features and forbidden language features](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features#forbidden-language-features)
- [Filepath API](https://static.runelite.net/runelite-client/apidocs/net/runelite/client/util/Filepath.html)
- [Jagex third-party client guidelines](https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1)

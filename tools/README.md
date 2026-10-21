# Bank catalogue refresh

Run `python tools/build_bank_catalogue.py <data-directory> --fetch` from the plugin directory. Omit `--fetch` to require the saved HTTP cache and rebuild entirely offline. Requires Python 3; no extra packages.

Place these public inputs in the data directory:

- `reboxed-items-complete.json`: https://raw.githubusercontent.com/DayV-git/osrsreboxed-db/33ea73face3264264206b6c4fb8bb272982d6f0a/docs/items-complete.json
- `all-item-details.json`: https://raw.githubusercontent.com/osrsbox/osrsbox-db/master/docs/items-complete.json (legacy IDs)
- `all-item-names.json`: https://raw.githubusercontent.com/runelite/static.runelite.net/gh-pages/cache/item/names.json
- `all-item-notes.json`: https://raw.githubusercontent.com/runelite/static.runelite.net/gh-pages/cache/item/notes.json

The script caches selected Wiki category member lists in `wiki-bank-categories/`, follows pagination, retries failures and joins exact Wiki page identities from item URLs (without variant fragments). It does not recursively inherit broad skill/quest associations. Missing/empty categories grant no assignments. Remove selected cache files to fetch them again. Preserve inputs and cache for exact reproducibility; their hashes are in the generated sources manifest. Only the generated catalogue and manifest are needed at runtime.

Priority: explicit functional exceptions, ordered specific Wiki categories, structured equipment types/bonuses, unresolved. Notes and placeholders inherit their base item. Manual account overrides take precedence at runtime. Unresolved entries can show a name-based suggestion, always marked `?`; they are not presented as verified. Category 9 also contains positively identified quest/clue items without `?`.

The script writes a CSV audit into the data directory. `gradlew bankReport` audits a local bank snapshot separately. Never add private snapshots, tokens or account overrides to the release.


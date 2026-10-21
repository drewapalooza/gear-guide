# Local assistant API

Enable the local assistant connection in Gear Guide settings, restart, log in and open the bank. Open potion storage once per login.

Run the included helper from this project directory:

```powershell
.\bridge.ps1 status
.\bridge.ps1 snapshot -File bank-snapshot.json
.\bridge.ps1 setups
.\bridge.ps1 push -File setup.json
```

Snapshots and tokens are private data and must not be committed. The helper reads the token from `.runelite/gear-guide/connection.json` without displaying it.

Requests use `Authorization: Bearer TOKEN`. Mutations require `application/json`. The server binds only to loopback, rejects browser origins and limits request-body sizes. The token rotates on restart; discovery is removed on orderly shutdown.

| Method | Path | Purpose |
|---|---|---|
| GET | `/v1/status` | Connection and synchronization status |
| GET | `/v1/snapshot` | Current observed items, levels and known storage |
| GET | `/v1/setups` | Saved setups and selected ID |
| PUT | `/v1/setups` | Validate, save and select a setup |
| PUT | `/v1/active` | Select a saved setup or clear selection |
| DELETE | `/v1/setups/{id}` | Delete a saved setup |
| GET | `/v1/task` | Task and matching-build status |
| POST | `/v1/task/select` | Select the saved task/style match |
| PUT | `/v1/preferences` | Update task matching preferences |

Setup objects use `schemaVersion: 1`, an ID with 1–64 lowercase letters/digits/hyphens, the snapshot's exact `accountId` and `revision`, a name, notes, `enemyType`, `combatStyle`, equipment and inventory.

Equipment keys are `HEAD`, `CAPE`, `AMULET`, `WEAPON`, `BODY`, `SHIELD`, `LEGS`, `GLOVES`, `BOOTS`, `RING`, `AMMO`. Picks have `itemId`, `quantity`, and a string `note`. Inventory contains `{slot, item}` entries with unique zero-based slots 0–27. Non-stackable items require separate slots. Only observed owned IDs and sufficient quantities are accepted. Potion variants share total stored doses.

Existing enemy/style matches are reused by default. Set `replaceExisting: true` only for an intentional revision. A changed account, revision or requirement must be reconsidered against a fresh snapshot; do not merely substitute a new revision into stale advice.

The API cannot withdraw, equip, move, cast, fight, or execute game actions. Provider credentials are not part of the API.

# Importing a setup

Export a fresh bank snapshot with the sidebar's export icon. Share it only with someone you choose. Request one JSON setup object, save the response as UTF-8 `.json`, and choose it with the import icon. Imports open an editable draft; only **Save build** changes saved setups. Saving can replace an existing setup for the same enemy/style, so review the draft before saving.

The imported object must retain the exported `accountId` and use the snapshot's `revision` as `basedOnRevision`. If quantities or levels change, export again. Moving items between the bank, inventory and equipment does not change the revision. Potion storage must be observed during this login to use its dose pool.

## Example

These identifiers are placeholders. The item IDs and quantities must come from your actual exported snapshot.

```json
{
  "schemaVersion": 1,
  "id": "my-melee-setup",
  "accountId": "COPY_FROM_SNAPSHOT",
  "basedOnRevision": "COPY_SNAPSHOT_REVISION",
  "name": "My melee setup",
  "enemyType": "",
  "combatStyle": "melee",
  "equipment": {
    "WEAPON": {"itemId": 4151, "quantity": 1}
  },
  "inventory": [
    {"slot": 0, "item": {"itemId": 385, "quantity": 1}}
  ],
  "runes": [
    {"itemId": 565, "quantity": 1000}
  ],
  "notes": "# Preparation\nReview the equipment and supplies before saving."
}
```

## Fields and validation

- Maximum file size: 128 KiB. `schemaVersion` must be `1`.
- `id`: 1–64 lowercase letters, digits or hyphens, starting with a letter/digit. `name`: 1–100 characters. `notes`: up to 8,000 characters of Markdown.
- `combatStyle`: `melee`, `ranged`, `magic` or `hybrid`. `enemyType` is an optional target key, such as `dust-devils`, used for matching a saved Slayer setup.
- `equipment`: slot names `HEAD`, `CAPE`, `AMULET`, `WEAPON`, `BODY`, `SHIELD`, `LEGS`, `GLOVES`, `BOOTS`, `RING`, `AMMO`. Omit empty slots. Each pick uses `itemId`, positive `quantity` and optional `note` (up to 1,000 characters). Only ammunition may have an equipped quantity above one.
- `inventory`: at most 28 entries, each with a unique `slot` from `0` through `27` and an `item` pick. Non-stackable items need separate slots.
- `runes`: optional additional bank supplies, up to 32 distinct unnoted rune types. These are separate from inventory slots and are added to all other required quantities. Pouch filling and charging remain manual.
- All items must exist in the observed bank, inventory, equipment or known potion storage. Shared potion dose pools, known level requirements, equipment slots and two-handed conflicts are checked.
- An imported `replaceExisting` flag is ignored. The draft remains unsaved until the editor's Save action. Cancelling the chooser or editor changes nothing.

There are no file paths, commands, server endpoints or automatic game actions in this format. Optional upgrade metadata is display-only; it is not a purchase or ownership instruction. See `Models.Setup` and `GearUpgrades.validate` for its optional schema.

# Banned Items Mod
**NeoForge 21.1.220 | Minecraft 1.21.1 | Server-Side Only**

A lightweight server-side mod that lets operators ban specific items by their
full item fingerprint (registry ID + all data-components / NBT).  Banned items
are automatically destroyed wherever they appear: inventory, cursor, ground, crafting
table, or anvil.

---

## Commands  *(requires operator level 2)*

| Command | Description |
|---|---|
| `/banhanditem` | Ban the item currently in your main hand. Deletes the item from your hand and scans the rest of your inventory for copies. Saves to config immediately. |
| `/unbanhanditem` | Unban the item currently in your main hand. |
| `/banneditemslist` | Print all currently banned items to chat. |
| `/reloadbanneditems` | Reload `config/banneditems.json` without restarting the server. |

---

## How banning works

1. Hold an item and run `/banhanditem`.
2. The mod serialises the full item (item ID + every data-component such as
   enchantments, custom name, lore, potion effects, etc.) to SNBT and stores it
   in `config/banneditems.json`.
3. From that moment, any item stack that matches **both** the item type **and**
   all data-components is considered banned.
4. Banning is exact-match: a Diamond Sword with Sharpness V is a different entry
   from a plain Diamond Sword.  This lets you ban specific hacked items without
   affecting normal ones.

---

## Enforcement

| Trigger | Action |
|---|---|
| Inventory sweep (every 1 s) | Removes banned items from all 41 inventory slots and the cursor slot |
| Crafting output | Zeroes the result stack so the player receives nothing |
| Right-click on block / in air | Event cancelled; item cannot be used |
| Left-click / attacking | Event cancelled |
| Item pickup from ground | Item entity discarded immediately |
| Anvil (repair / rename) | Output blocked |

---

## Config file

`config/banneditems.json` — a JSON array of SNBT strings.

```json
[
  "{id:\"minecraft:diamond_sword\",count:1}",
  "{id:\"minecraft:tnt\",count:1,components:{\"minecraft:custom_name\":\"{\\\"text\\\":\\\"Super TNT\\\"}\"} }"
]
```

You can hand-edit this file and run `/reloadbanneditems` to apply changes without
restarting.

---

## Building from source

Requirements:
- Java 21 (JDK)
- Internet access (Gradle downloads NeoForge and dependencies automatically)

```bash
# Linux / macOS
./gradlew build

# Windows
gradlew.bat build
```

The compiled jar will be at:
```
build/libs/banneditemsmod-1.21.1-1.0.0.jar
```

If you do not have the Gradle wrapper binaries, run:
```bash
gradle wrapper --gradle-version 8.8
```
then run the build command above.

### First-time setup (NeoGradle downloads Minecraft + NeoForge)
The very first build takes several minutes and requires ~3–5 GB of disk space.
Subsequent builds are fast.

---

## Installation (pre-built jar)

1. Drop the jar into your server's `mods/` folder.
2. Clients do **not** need this mod installed — vanilla clients can connect normally.
3. Start the server.  The mod creates `config/banneditems.json` automatically.

---

## Notes

- The mod is **server-side only**.  `displayTest = "IGNORE_ALL_VERSION"` in
  `neoforge.mods.toml` ensures vanilla clients are not kicked.
- All enforcement runs on the server thread — there is no client component.
- The inventory sweep is staggered by player entity ID so multiple players are
  not all scanned on the exact same tick.

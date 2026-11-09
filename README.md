# EywaTools

EywaMC Paper plugin for custom tool protections and enchanting restrictions.

## Requirements
- Paper 26.3
- Java 25
- Maven 3.9+
- Oraxen for the custom Unobtanium Pickaxe

## Features
- Blocks configured tools from enchanting-table interactions.
- Cancels enchantment application to protected tools.
- Prevents protected tools from being modified through anvil recipes.
- Prevents protected tools from being used as the base item in smithing recipes.
- English messages and configurable protected tool lists.

## Configuration

Edit `plugins/EywaTools/config.yml`:

```yaml
enchanting-disabled:
  - unobtanium_pickaxe

match-display-name-fallback: true
blocked-display-names:
  - "Unobtanium Pickaxe"

messages:
  enchanting-disabled: "&cYou cannot enchant this tool."
```

Add Oraxen item IDs and their exact plain display names to protect further tools.

## Unobtanium Pickaxe (Oraxen)

Keep the base item as a diamond pickaxe. Oraxen supplies the preset enchantments:

```yaml
unobtanium_pickaxe:
  displayname: "<gradient:#00d9ff:#7af5ff><bold>Unobtanium Pickaxe</bold></gradient>"
  material: DIAMOND_PICKAXE
  unbreakable: true
  Enchantments:
    minecraft:fortune: 3
    minecraft:efficiency: 10
  Pack:
    generate_model: false
    model: tools/unobtanium_pickaxe
```

EywaTools prevents players from adding or changing enchantments on this configured tool; Oraxen gives it Fortune III and Efficiency X.

## Build

Use JDK 25 and Maven:

```bash
mvn clean verify
```

The plugin JAR is created at `target/EywaTools-1.0.0.jar`.

## Install

1. Copy the JAR into the server's `plugins/` directory.
2. Restart Paper.
3. Configure `plugins/EywaTools/config.yml`.
4. Restart after config changes.

## Compatibility note

The default item lock uses the display-name fallback so the supplied Unobtanium Pickaxe is recognized without relying on Oraxen's internal persistent-data key. Test against your exact Paper and Oraxen builds before production use.

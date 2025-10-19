# World Border Expander - Configuration Guide

## Location
The config file is located at: `config/worldborderexpander.json`

It will be automatically created with default values when you first run the mod.

## Configuration Options

### Border Settings

#### `startingBorderSize` (default: `1.0`)
- The initial size of the world border when the world starts
- Minimum value: 1.0 block
- Example: `"startingBorderSize": 5.0` for a 5x5 starting area

#### `expansionIncrement` (default: `1.0`)
- How much the border expands (in blocks) when a new unique item is collected
- Minimum value: 0.1 blocks
- Example: `"expansionIncrement": 2.0` to expand by 2 blocks per item

### Spawn Search Settings

#### `spawnSearchRadius` (default: `128`)
- Maximum radius (in blocks) to search for a suitable spawn point near trees
- Range: 32-512 blocks
- Lower values = faster world loading
- Higher values = better chance of spawning near resources
- **Recommended**: Keep at 128 or lower to prevent loading timeouts

### Item Filtering

#### `excludedItems` (default: `[]`)
- List of item IDs to exclude from the obtainable items list
- These items will NOT count toward border expansion
- Format: `"minecraft:item_name"`
- Example:
```json
"excludedItems": [
  "minecraft:dragon_egg",
  "minecraft:elytra",
  "minecraft:nether_star"
]
```

#### `includedItems` (default: `[]`)
- List of item IDs to forcefully include (overrides default exclusions)
- Useful if you want to make normally-excluded items count
- Format: same as excludedItems
- Example:
```json
"includedItems": [
  "minecraft:bedrock"
]
```

### Message Settings

#### `broadcastNewItems` (default: `true`)
- Whether to announce to all players when someone finds a new item
- Set to `false` for quieter gameplay

#### `showPlayerName` (default: `true`)
- Whether to show which player found the item in announcements
- Only applies if `broadcastNewItems` is true

## Default Excluded Items

The mod automatically excludes these items (unless overridden with `includedItems`):
- Command blocks and structure blocks
- Bedrock, barriers, spawners
- End portal frames
- Debug/creative-only items
- Infested blocks
- Budding amethyst
- Petrified oak slab
- Reinforced deepslate

## Example Configuration

```json
{
  "startingBorderSize": 3.0,
  "expansionIncrement": 1.5,
  "spawnSearchRadius": 96,
  "excludedItems": [
    "minecraft:totem_of_undying",
    "minecraft:heart_of_the_sea"
  ],
  "includedItems": [],
  "broadcastNewItems": true,
  "showPlayerName": true
}
```

## In-Game Commands

### Player Commands

#### `/wbe progress`
Shows your collection progress and current border size

#### `/wbe missing`
Lists the first 20 items you haven't collected yet

#### `/wbe collected`
Lists the first 20 items you have collected

### Admin Commands (OP Level 2+)

#### `/wbe config list`
Shows all current config values

#### `/wbe config startingBorderSize <value>`
Sets the starting border size (1.0 - 10000.0)
- Example: `/wbe config startingBorderSize 5.0`
- **Note**: Only applies to new worlds

#### `/wbe config expansionIncrement <value>`
Sets how much the border expands per unique item (0.1 - 1000.0)
- Example: `/wbe config expansionIncrement 2.0`
- **Note**: Takes effect immediately

#### `/wbe config spawnSearchRadius <value>`
Sets the search radius for finding trees at spawn (32 - 512)
- Example: `/wbe config spawnSearchRadius 96`
- **Note**: Only applies to new worlds

#### `/wbe config broadcastNewItems <true|false>`
Enable/disable announcing when new items are found
- Example: `/wbe config broadcastNewItems false`

#### `/wbe config showPlayerName <true|false>`
Enable/disable showing player names in announcements
- Example: `/wbe config showPlayerName false`

#### `/wbe reload`
Reloads the config file from disk

## Tips for Configuration

### Easy Mode
```json
{
  "startingBorderSize": 10.0,
  "expansionIncrement": 2.0,
  "spawnSearchRadius": 128
}
```

### Hard Mode
```json
{
  "startingBorderSize": 1.0,
  "expansionIncrement": 0.5,
  "spawnSearchRadius": 64
}
```

### Speedrun Mode (fewer items)
```json
{
  "startingBorderSize": 1.0,
  "expansionIncrement": 3.0,
  "excludedItems": [
    "minecraft:music_disc_13",
    "minecraft:music_disc_cat",
    "minecraft:music_disc_blocks",
    "minecraft:music_disc_chirp",
    "minecraft:music_disc_far",
    "minecraft:music_disc_mall",
    "minecraft:music_disc_mellohi",
    "minecraft:music_disc_stal",
    "minecraft:music_disc_strad",
    "minecraft:music_disc_ward",
    "minecraft:music_disc_11",
    "minecraft:music_disc_wait"
  ]
}
```

## Troubleshooting

**World takes forever to load?**
- Reduce `spawnSearchRadius` to 64 or 96
- The mod will fall back to (0, Y, 0) if no good spawn is found

**Border expanding too fast/slow?**
- Adjust `expansionIncrement`
- Or exclude/include specific items to change the total count

**Want to reset progress?**
- Delete the world and create a new one
- Config changes apply immediately to new worlds
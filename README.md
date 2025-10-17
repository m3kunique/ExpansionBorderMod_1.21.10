# 🌍 World Border Expander

A Minecraft Fabric mod that starts you in a 1x1 world border and expands it as you collect unique items!

## 🎮 Concept

You spawn in a tiny 1x1 block world border. The only way to expand your playable area is by collecting unique items. Each new item you find expands the border, allowing you to explore more of the world and find even more items!

### Features
- ✅ Starts with a 1x1 world border (configurable)
- ✅ Expands by 1 block per unique item (configurable)
- ✅ Tracks 1000+ obtainable items
- ✅ Per-player and global item tracking
- ✅ TAB list shows each player's unique item count
- ✅ Spawn guaranteed near trees for wood access
- ✅ Full configuration system
- ✅ In-game config editing with commands
- ✅ Progress tracking commands
- ✅ Filters out unobtainable items (bedrock, command blocks, etc.)

## 📦 Installation

1. Install [Fabric Loader](https://fabricmc.net/use/)
2. Install [Fabric API](https://modrinth.com/mod/fabric-api)
3. Download World Border Expander
4. Place the JAR file in your `mods` folder
5. Launch Minecraft!

## 🎯 Requirements

- **Minecraft**: 1.21.10
- **Fabric Loader**: 0.17.3 or higher
- **Fabric API**: Latest version

## 🕹️ Commands

### Player Commands

| Command | Description |
|---------|-------------|
| `/wbe progress` | Shows collection progress and border size |
| `/wbe missing` | Lists up to 20 items you haven't collected |
| `/wbe collected` | Lists up to 20 items you have collected |

### Admin Commands (OP Level 2+)

| Command | Description |
|---------|-------------|
| `/wbe reload` | Reloads the config file |
| `/wbe config <setting> <value>` | Change config settings in-game |

#### Config Command Examples:
```
/wbe config startingBorderSize 5
/wbe config expansionIncrement 2.0
/wbe config spawnSearchRadius 96
/wbe config broadcastNewItems true
/wbe config showPlayerName false
```

## ⚙️ Configuration

Config file location: `config/worldborderexpander.json`

### Quick Settings

| Setting | Default | Description |
|---------|---------|-------------|
| `startingBorderSize` | 1.0 | Initial border size in blocks |
| `expansionIncrement` | 1.0 | Border growth per unique item |
| `spawnSearchRadius` | 128 | Max radius to search for spawn |
| `broadcastNewItems` | true | Announce new item discoveries |
| `showPlayerName` | true | Show player names in announcements |

### Item Filtering

**Exclude specific items:**
```json
{
  "excludedItems": [
    "minecraft:dragon_egg",
    "minecraft:elytra"
  ]
}
```

**Force include items:**
```json
{
  "includedItems": [
    "minecraft:bedrock"
  ]
}
```

See [CONFIG_GUIDE.md](CONFIG_GUIDE.md) for detailed configuration options.

## 🎮 Difficulty Presets

### Easy Mode
```json
{
  "startingBorderSize": 10.0,
  "expansionIncrement": 2.0
}
```

### Normal Mode (Default)
```json
{
  "startingBorderSize": 1.0,
  "expansionIncrement": 1.0
}
```

### Hard Mode
```json
{
  "startingBorderSize": 1.0,
  "expansionIncrement": 0.5
}
```

### Speedrun Mode
Exclude music discs and other rare items:
```json
{
  "startingBorderSize": 1.0,
  "expansionIncrement": 2.0,
  "excludedItems": [
    "minecraft:music_disc_13",
    "minecraft:music_disc_cat",
    "minecraft:music_disc_blocks"
  ]
}
```

## 🌳 Spawn System

- Searches for a spawn point near trees (configurable radius)
- If no suitable location found within radius, **spawns a tree at (0, Y, 0)**
- Guarantees you have wood access from the start
- Avoids ocean biomes

## 📊 How It Works

1. **World Generation**: 
   - Border starts at configured size (default: 1x1)
   - Spawn point selected near trees or tree is generated
   - All dimensions (Overworld, Nether, End) share the same border

2. **Item Collection**:
   - Pick up any item for the first time globally
   - Border expands by configured increment (default: +1 block)
   - Everyone is notified
   - Your personal count updates in TAB list

3. **Progress Tracking**:
   - Global: All unique items collected by any player
   - Personal: Items you individually collected
   - TAB list shows your personal count

## 🚫 Excluded Items

The following items are automatically excluded (can be overridden in config):

### Creative/Unobtainable
- Command blocks
- Barriers & structure blocks
- Debug stick
- Bedrock
- Spawners
- End portal frames

### Technical Blocks
- Farmland (turns to dirt when broken)
- Infested blocks
- Budding amethyst
- Reinforced deepslate

## 🎯 Tips & Strategies

1. **Start Simple**: 
   - Break grass for seeds
   - Chop tree for wood tools
   - Collect basic blocks first

2. **Branch Out**:
   - Each new item expands your area
   - Plan your exploration route
   - Mark important locations

3. **Multiplayer**:
   - Work together to find items faster
   - Global expansion helps everyone
   - Track your personal contribution in TAB

4. **Late Game**:
   - Use `/wbe missing` to find what's left
   - Trade with villagers for rare items
   - Explore structures for unique loot

## 🐛 Troubleshooting

### World takes forever to load
- **Solution**: Reduce `spawnSearchRadius` to 64-96 in config
- The mod will fallback to generating a tree at (0,Y,0)

### Border expanding too fast/slow
- **Solution**: Adjust `expansionIncrement` in config
- Or use `/wbe config expansionIncrement <value>` in-game

### Want fewer required items
- **Solution**: Add items to `excludedItems` list
- Example: Exclude all music discs for faster completion

### Config not working
- **Solution**: Use `/wbe reload` or restart server
- Check JSON syntax is valid
- Some settings only apply to new worlds

## 🔧 Development

### Building from Source
```bash
git clone <repository>
cd worldborderexpander
./gradlew build
```

The compiled JAR will be in `build/libs/`

### Project Structure
```
src/main/java/com/example/worldborderexpander/
├── WorldBorderExpander.java    # Main mod class
├── WBEConfig.java              # Configuration system
└── mixin/
    ├── ItemEntityPickupMixin.java
    ├── PlayerInventoryMixin.java
    └── ScreenHandlerMixin.java
```

## 📝 License

This mod is open source. Feel free to modify and distribute!

## 🤝 Contributing

Contributions are welcome! Please feel free to submit pull requests or open issues.

### Ideas for Contributions
- Biome-specific spawn preferences
- Custom achievement system
- Border growth animations
- Team-based modes
- Item category filtering

## 📞 Support

- **Issues**: Open an issue on GitHub
- **Questions**: Check CONFIG_GUIDE.md first
- **Multiplayer**: Works on both clients and servers!

## 🎉 Credits

Created for players who love exploration challenges and unique gameplay twists!

---

**Enjoy your confined adventure! Every item counts! 🌍**

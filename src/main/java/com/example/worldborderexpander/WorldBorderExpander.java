package com.example.worldborderexpander;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.example.worldborderexpander.net.WBENetworking;
import com.example.worldborderexpander.net.WBEPayloads;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.border.WorldBorder;

import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.Set;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static net.minecraft.server.command.CommandManager.*;

public class WorldBorderExpander implements ModInitializer {
    public static final String MOD_ID = "worldborderexpander";

    private static MinecraftServer server;
    private static ScoreboardObjective OBJECTIVE;
    private static WBEConfig config;

    private static final Set<Identifier> GLOBAL_UNIQUE = new HashSet<>();
    private static final Map<UUID, Set<Identifier>> PER_PLAYER = new HashMap<>();
    private static final Set<Identifier> OBTAINABLE_ITEMS = new HashSet<>();

    private static BlockPos SPAWN_POS = null;

    @Override
    public void onInitialize() {
        WBENetworking.registerCommon();
        
        config = WBEConfig.load();
        initializeObtainableItems();
        
        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            server = s;
            
            ServerWorld overworld = s.getOverworld();
            
            // Load saved progress BEFORE initializing borders
            loadProgress(overworld);
            
            BlockPos treeSpawn = ensureSpawnWithTree(overworld);

            // Calculate border size based on collected items
            double borderSize = config.startingBorderSize + (GLOBAL_UNIQUE.size() * config.expansionIncrement);
            initBordersForAll(s, borderSize, treeSpawn.getX() + 0.5, treeSpawn.getZ() + 0.5);
            SPAWN_POS = treeSpawn;

            ensureObjective();
            setTabObjective();
            
            s.sendMessage(Text.literal("[WBE] World Border Expander initialized at " + treeSpawn.toShortString()));
            s.sendMessage(Text.literal("[WBE] Total obtainable items: " + OBTAINABLE_ITEMS.size()));
            s.sendMessage(Text.literal("[WBE] Loaded progress: " + GLOBAL_UNIQUE.size() + " items found"));
            s.sendMessage(Text.literal("[WBE] Border size: " + borderSize + " | Increment: " + config.expansionIncrement));
        });
        
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            saveProgress(s.getOverworld());
        });
        
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            // Auto-save when any player disconnects
            saveProgress(server.getOverworld());
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, srv) -> {
            ServerPlayerEntity player = handler.getPlayer();
            
            // Teleport player to spawn
            player.teleport(
                SPAWN_POS.getX() + 0.5,
                SPAWN_POS.getY(),
                SPAWN_POS.getZ() + 0.5,
                false
            );
            
            ensureObjective();
            setTabObjective();
            updatePlayerScore(player);
            
            // Send sync AFTER player is fully loaded (delay by 1 tick)
            srv.execute(() -> {
                if (player.networkHandler != null) {
                    sendFullSync(player);
                    updatePlayerScore(player);
                }
            });
        });

        // Register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("wbe")
                .then(literal("progress")
                    .executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayer();
                        if (player == null) return 0;
                        
                        int collected = GLOBAL_UNIQUE.size();
                        int total = OBTAINABLE_ITEMS.size();
                        int percent = (total > 0) ? (collected * 100 / total) : 0;
                        
                        player.sendMessage(Text.literal("§6=== World Border Expander Progress ==="), false);
                        player.sendMessage(Text.literal("§aCollected: §f" + collected + " §7/ §f" + total + " §7(" + percent + "%)"), false);
                        player.sendMessage(Text.literal("§aBorder Size: §f" + (int)server.getOverworld().getWorldBorder().getSize() + " blocks"), false);
                        
                        return 1;
                    })
                )
                .then(literal("missing")
                    .executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayer();
                        if (player == null) return 0;
                        
                        Set<Identifier> missing = new HashSet<>(OBTAINABLE_ITEMS);
                        missing.removeAll(GLOBAL_UNIQUE);
                        
                        player.sendMessage(Text.literal("§6=== Missing Items (" + missing.size() + ") ==="), false);
                        
                        List<String> sortedMissing = missing.stream()
                            .map(Identifier::toString)
                            .sorted()
                            .limit(20)
                            .collect(Collectors.toList());
                        
                        for (String id : sortedMissing) {
                            player.sendMessage(Text.literal("§7- §f" + id), false);
                        }
                        
                        if (missing.size() > 20) {
                            player.sendMessage(Text.literal("§7... and " + (missing.size() - 20) + " more"), false);
                        }
                        
                        return 1;
                    })
                )
                .then(literal("collected")
                    .executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayer();
                        if (player == null) return 0;
                        
                        player.sendMessage(Text.literal("§6=== Collected Items (" + GLOBAL_UNIQUE.size() + ") ==="), false);
                        
                        List<String> sortedCollected = GLOBAL_UNIQUE.stream()
                            .map(Identifier::toString)
                            .sorted()
                            .limit(20)
                            .collect(Collectors.toList());
                        
                        for (String id : sortedCollected) {
                            player.sendMessage(Text.literal("§7- §a" + id), false);
                        }
                        
                        if (GLOBAL_UNIQUE.size() > 20) {
                            player.sendMessage(Text.literal("§7... and " + (GLOBAL_UNIQUE.size() - 20) + " more"), false);
                        }
                        
                        return 1;
                    })
                )
                .then(literal("config")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(literal("startingBorderSize")
                        .then(argument("value", DoubleArgumentType.doubleArg(1.0, 10000.0))
                            .executes(ctx -> {
                                double value = DoubleArgumentType.getDouble(ctx, "value");
                                config.startingBorderSize = value;
                                config.save();
                                ctx.getSource().sendFeedback(() -> Text.literal("§a[WBE] startingBorderSize set to " + value + " (applies to new worlds)"), true);
                                return 1;
                            })
                        )
                    )
                    .then(literal("expansionIncrement")
                        .then(argument("value", DoubleArgumentType.doubleArg(0.1, 1000.0))
                            .executes(ctx -> {
                                double value = DoubleArgumentType.getDouble(ctx, "value");
                                config.expansionIncrement = value;
                                config.save();
                                ctx.getSource().sendFeedback(() -> Text.literal("§a[WBE] expansionIncrement set to " + value), true);
                                return 1;
                            })
                        )
                    )
                    .then(literal("spawnSearchRadius")
                        .then(argument("value", IntegerArgumentType.integer(32, 512))
                            .executes(ctx -> {
                                int value = IntegerArgumentType.getInteger(ctx, "value");
                                config.spawnSearchRadius = value;
                                config.save();
                                ctx.getSource().sendFeedback(() -> Text.literal("§a[WBE] spawnSearchRadius set to " + value + " (applies to new worlds)"), true);
                                return 1;
                            })
                        )
                    )
                    .then(literal("broadcastNewItems")
                        .then(argument("value", StringArgumentType.word())
                            .suggests((ctx, builder) -> {
                                builder.suggest("true");
                                builder.suggest("false");
                                return builder.buildFuture();
                            })
                            .executes(ctx -> {
                                String value = StringArgumentType.getString(ctx, "value");
                                config.broadcastNewItems = Boolean.parseBoolean(value);
                                config.save();
                                ctx.getSource().sendFeedback(() -> Text.literal("§a[WBE] broadcastNewItems set to " + config.broadcastNewItems), true);
                                return 1;
                            })
                        )
                    )
                    .then(literal("showPlayerName")
                        .then(argument("value", StringArgumentType.word())
                            .suggests((ctx, builder) -> {
                                builder.suggest("true");
                                builder.suggest("false");
                                return builder.buildFuture();
                            })
                            .executes(ctx -> {
                                String value = StringArgumentType.getString(ctx, "value");
                                config.showPlayerName = Boolean.parseBoolean(value);
                                config.save();
                                ctx.getSource().sendFeedback(() -> Text.literal("§a[WBE] showPlayerName set to " + config.showPlayerName), true);
                                return 1;
                            })
                        )
                    )
                    .then(literal("list")
                        .executes(ctx -> {
                            ctx.getSource().sendFeedback(() -> Text.literal("§6=== WBE Config ==="), false);
                            ctx.getSource().sendFeedback(() -> Text.literal("§estartingBorderSize: §f" + config.startingBorderSize), false);
                            ctx.getSource().sendFeedback(() -> Text.literal("§eexpansionIncrement: §f" + config.expansionIncrement), false);
                            ctx.getSource().sendFeedback(() -> Text.literal("§espawnSearchRadius: §f" + config.spawnSearchRadius), false);
                            ctx.getSource().sendFeedback(() -> Text.literal("§ebroadcastNewItems: §f" + config.broadcastNewItems), false);
                            ctx.getSource().sendFeedback(() -> Text.literal("§eshowPlayerName: §f" + config.showPlayerName), false);
                            return 1;
                        })
                    )
                )
                .then(literal("reload")
                    .requires(source -> source.hasPermissionLevel(2))
                    .executes(ctx -> {
                        config = WBEConfig.load();
                        ctx.getSource().sendFeedback(() -> Text.literal("§a[WBE] Config reloaded!"), true);
                        return 1;
                    })
                )
                .then(literal("save")
                    .requires(source -> source.hasPermissionLevel(2))
                    .executes(ctx -> {
                        saveProgress(server.getOverworld());
                        ctx.getSource().sendFeedback(() -> Text.literal("§a[WBE] Progress saved manually!"), true);
                        return 1;
                    })
                )
            );
        });
    }

    public static void onItemPickedUp(ServerPlayerEntity player, ItemStack stack) {
        if (stack.isEmpty()) return;
        
        // Check if player network handler is ready
        if (player.networkHandler == null) return;
        
        Identifier id = Registries.ITEM.getId(stack.getItem());
        if (!OBTAINABLE_ITEMS.contains(id)) return;

        boolean newForPlayer = PER_PLAYER.computeIfAbsent(player.getUuid(), k -> new HashSet<>()).add(id);
        
        // Only send personal update if it's new for this player
        if (newForPlayer) {
            sendAddPersonal(player, id);
        }

        boolean newForGlobal = GLOBAL_UNIQUE.add(id);
        updatePlayerScore(player);

        if (newForGlobal) {
            expandBorder(server);
            if (config.broadcastNewItems) {
                String itemName = stack.getName().getString();
                String playerName = config.showPlayerName ? player.getName().getString() : "Someone";
                broadcast(Text.literal("§6[WBE] §e" + playerName + " §ffound a new item: §a" + itemName
                        + " §7(" + GLOBAL_UNIQUE.size() + "/" + OBTAINABLE_ITEMS.size() + ")"));
                broadcast(Text.literal("§7→ Border expanded to §f" + (int)server.getOverworld().getWorldBorder().getSize() + " §7blocks!"));
            }
            updateAllScores();
            sendAddGlobalToAll(id);
        }
    }

    /* -------------------- Obtainable Items Filter -------------------- */

    private static void initializeObtainableItems() {
        Set<Item> excluded = new HashSet<>();
        
        // Add default excluded items
        excluded.addAll(Arrays.asList(
            Items.COMMAND_BLOCK, Items.CHAIN_COMMAND_BLOCK, Items.REPEATING_COMMAND_BLOCK,
            Items.COMMAND_BLOCK_MINECART, Items.BARRIER, Items.STRUCTURE_BLOCK,
            Items.STRUCTURE_VOID, Items.JIGSAW, Items.DEBUG_STICK, Items.KNOWLEDGE_BOOK,
            Items.LIGHT, Items.BEDROCK, Items.END_PORTAL_FRAME, Items.SPAWNER,
            Items.FARMLAND, Items.INFESTED_STONE, Items.INFESTED_COBBLESTONE,
            Items.INFESTED_STONE_BRICKS, Items.INFESTED_MOSSY_STONE_BRICKS,
            Items.INFESTED_CRACKED_STONE_BRICKS, Items.INFESTED_CHISELED_STONE_BRICKS,
            Items.INFESTED_DEEPSLATE, Items.REINFORCED_DEEPSLATE,
            Items.BUDDING_AMETHYST, Items.PETRIFIED_OAK_SLAB, Items.TIPPED_ARROW,
            Items.SPAWNER
        ));

        // Exclude all spawn eggs
        Registries.ITEM.stream()
            .filter(item -> item instanceof SpawnEggItem)
            .forEach(excluded::add);

        // Add custom excluded items from config
        for (String itemId : config.excludedItems) {
            Identifier id = Identifier.tryParse(itemId);
            if (id != null && Registries.ITEM.containsId(id)) {
                excluded.add(Registries.ITEM.get(id));
            }
        }

        // Build obtainable items set
        for (Item item : Registries.ITEM) {
            if (!excluded.contains(item)) {
                Identifier id = Registries.ITEM.getId(item);
                if (item != Items.AIR && !id.toString().equals("minecraft:air")) {
                    OBTAINABLE_ITEMS.add(id);
                }
            }
        }

        // Add custom included items from config (overrides exclusions)
        for (String itemId : config.includedItems) {
            Identifier id = Identifier.tryParse(itemId);
            if (id != null && Registries.ITEM.containsId(id)) {
                OBTAINABLE_ITEMS.add(id);
            }
        }
    }

    /* -------------------- Spawn with Tree Guarantee -------------------- */

    private static BlockPos ensureSpawnWithTree(ServerWorld world) {
        BlockPos naturalSpawn = findNaturalTreeSpawn(world);
        if (naturalSpawn != null) {
            world.getServer().sendMessage(Text.literal("[WBE] Found natural tree at " + naturalSpawn.toShortString()));
            return naturalSpawn;
        }
        
        world.getServer().sendMessage(Text.literal("[WBE] No natural tree found, generating tree at spawn..."));
        BlockPos spawnPos = generateTreeAtSpawn(world);
        world.getServer().sendMessage(Text.literal("[WBE] Tree generated at " + spawnPos.toShortString()));
        return spawnPos;
    }

    private static BlockPos findNaturalTreeSpawn(ServerWorld world) {
        int maxRadius = Math.min(config.spawnSearchRadius, 128);
        int step = 8;
        
        BlockPos center = checkForTreeNearby(world, 0, 0, 2);
        if (center != null) return center;
        
        for (int r = step; r <= maxRadius; r += step) {
            for (int x = -r; x <= r; x += step) {
                for (int z = -r; z <= r; z += step) {
                    if (Math.abs(x) != r && Math.abs(z) != r) continue;
                    
                    BlockPos found = checkForTreeNearby(world, x, z, 2);
                    if (found != null) return found;
                }
            }
        }
        
        return null;
    }

    private static BlockPos checkForTreeNearby(ServerWorld world, int x, int z, int radius) {
        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos center = new BlockPos(x, y, z);
        
        if (isOceanBiome(world, center)) return null;
        
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -1; dy <= 8; dy++) {
                    m.set(x + dx, y + dy, z + dz);
                    if (world.getBlockState(m).isIn(BlockTags.LOGS)) {
                        return getSafeSpotNearTree(world, m.toImmutable());
                    }
                }
            }
        }
        
        return null;
    }

    private static BlockPos getSafeSpotNearTree(ServerWorld world, BlockPos treePos) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) continue;
                
                int x = treePos.getX() + dx;
                int z = treePos.getZ() + dz;
                int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos pos = new BlockPos(x, y, z);
                
                BlockState ground = world.getBlockState(pos);
                BlockState above = world.getBlockState(pos.up());
                
                if (ground.isSolid() && above.isAir() && ground.getFluidState().isEmpty()) {
                    return pos;
                }
            }
        }
        
        return treePos;
    }

    private static BlockPos generateTreeAtSpawn(ServerWorld world) {
        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, 0, 0);
        BlockPos basePos = new BlockPos(0, y, 0);
        
        BlockState ground = world.getBlockState(basePos);
        if (ground.isAir() || !ground.getFluidState().isEmpty()) {
            world.setBlockState(basePos, Blocks.GRASS_BLOCK.getDefaultState());
        }
        
        BlockPos treeBase = basePos.up();
        generateSimpleOakTree(world, treeBase);
        
        return basePos;
    }

    private static void generateSimpleOakTree(ServerWorld world, BlockPos base) {
        Random random = new Random();
        int height = 4 + random.nextInt(3);
        
        for (int i = 0; i < height; i++) {
            world.setBlockState(base.up(i), Blocks.OAK_LOG.getDefaultState());
        }
        
        BlockPos leafBase = base.up(height - 2);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                    if (dx == 0 && dz == 0 && dy <= 0) continue;
                    
                    BlockPos leafPos = leafBase.add(dx, dy, dz);
                    if (world.getBlockState(leafPos).isAir()) {
                        world.setBlockState(leafPos, Blocks.OAK_LEAVES.getDefaultState());
                    }
                }
            }
        }
        
        world.setBlockState(base.up(height), Blocks.OAK_LEAVES.getDefaultState());
    }

    private static boolean isOceanBiome(ServerWorld world, BlockPos pos) {
        RegistryEntry<Biome> entry = world.getBiome(pos);
        return entry.isIn(BiomeTags.IS_OCEAN);
    }

    /* -------------------- Border Management -------------------- */

    private static void initBordersForAll(MinecraftServer s, double size, double centerX, double centerZ) {
        for (ServerWorld w : s.getWorlds()) {
            WorldBorder b = w.getWorldBorder();
            b.setCenter(centerX, centerZ);
            b.setSize(size);
        }
    }

    private static void expandBorder(MinecraftServer s) {
        double newSize = s.getOverworld().getWorldBorder().getSize() + config.expansionIncrement;
        for (ServerWorld w : s.getWorlds()) {
            w.getWorldBorder().setSize(newSize);
        }
    }

    /* -------------------- Scoreboard -------------------- */

    private static void ensureObjective() {
        if (server == null) return;
        Scoreboard sb = server.getScoreboard();
        OBJECTIVE = sb.getNullableObjective("wbe_unique");
        if (OBJECTIVE == null) {
            OBJECTIVE = sb.addObjective(
                "wbe_unique",
                ScoreboardCriterion.DUMMY,
                Text.literal("Unique Items"),
                ScoreboardCriterion.RenderType.INTEGER,
                false,
                null
            );
        }
    }

    private static void setTabObjective() {
        if (server == null || OBJECTIVE == null) return;
        server.getScoreboard().setObjectiveSlot(ScoreboardDisplaySlot.LIST, OBJECTIVE);
    }

    private static void updatePlayerScore(ServerPlayerEntity p) {
        if (server == null) return;
        ensureObjective();
        setTabObjective();
        int count = PER_PLAYER.getOrDefault(p.getUuid(), Collections.emptySet()).size();
        server.getScoreboard().getOrCreateScore(p, OBJECTIVE).setScore(count);
    }

    private static void updateAllScores() {
        if (server == null) return;
        ensureObjective();
        setTabObjective();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            updatePlayerScore(p);
        }
    }

    private static void broadcast(Text msg) {
        if (server == null) return;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.sendMessage(msg, false);
        }
    }

    /* -------------------- Networking -------------------- */

    private static void sendFullSync(ServerPlayerEntity player) {
        if (player.networkHandler == null) return;
        
        ServerPlayNetworking.send(player, new WBEPayloads.SyncAllGlobal(new ArrayList<>(GLOBAL_UNIQUE)));
        
        var personal = PER_PLAYER.getOrDefault(player.getUuid(), Collections.emptySet());
        ServerPlayNetworking.send(player, new WBEPayloads.SyncAllPersonal(new ArrayList<>(personal)));
    }

    private static void sendAddGlobalToAll(Identifier id) {
        if (server == null) return;
        var payload = new WBEPayloads.AddGlobal(id);
        for (ServerPlayerEntity pl : server.getPlayerManager().getPlayerList()) {
            if (pl.networkHandler != null) {
                ServerPlayNetworking.send(pl, payload);
            }
        }
    }

    private static void sendAddPersonal(ServerPlayerEntity player, Identifier id) {
        if (player.networkHandler == null) return;
        ServerPlayNetworking.send(player, new WBEPayloads.AddPersonal(id));
    }
    
    /* -------------------- Progress Persistence -------------------- */
    
    private static Path getProgressFile(ServerWorld world) {
        // Get the world save directory
        Path worldDir = world.getServer().getRunDirectory().resolve("saves")
            .resolve(world.getServer().getSaveProperties().getLevelName());
        return worldDir.resolve("wbe_progress.json");
    }
    
    private static void saveProgress(ServerWorld world) {
        try {
            Path progressFile = getProgressFile(world);
            
            // Ensure directory exists
            Files.createDirectories(progressFile.getParent());
            
            // Create save data structure
            var saveData = new HashMap<String, Object>();
            
            // Save global unique items
            List<String> globalList = GLOBAL_UNIQUE.stream()
                .map(Identifier::toString)
                .collect(Collectors.toList());
            saveData.put("global_unique", globalList);
            
            // Save per-player data
            Map<String, List<String>> perPlayerData = new HashMap<>();
            for (Map.Entry<UUID, Set<Identifier>> entry : PER_PLAYER.entrySet()) {
                String uuid = entry.getKey().toString();
                List<String> items = entry.getValue().stream()
                    .map(Identifier::toString)
                    .collect(Collectors.toList());
                perPlayerData.put(uuid, items);
            }
            saveData.put("per_player", perPlayerData);
            
            // Save spawn position
            if (SPAWN_POS != null) {
                var spawnData = new HashMap<String, Integer>();
                spawnData.put("x", SPAWN_POS.getX());
                spawnData.put("y", SPAWN_POS.getY());
                spawnData.put("z", SPAWN_POS.getZ());
                saveData.put("spawn_pos", spawnData);
            }
            
            // Write to file
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String json = gson.toJson(saveData);
            Files.writeString(progressFile, json);
            
            System.out.println("[WBE] Progress saved: " + GLOBAL_UNIQUE.size() + " global items, " + PER_PLAYER.size() + " players");
        } catch (IOException e) {
            System.err.println("[WBE] Failed to save progress: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void loadProgress(ServerWorld world) {
        GLOBAL_UNIQUE.clear();
        PER_PLAYER.clear();
        
        try {
            Path progressFile = getProgressFile(world);
            if (!Files.exists(progressFile)) {
                System.out.println("[WBE] No progress file found, starting fresh");
                return;
            }
            
            String json = Files.readString(progressFile);
            Gson gson = new Gson();
            
            @SuppressWarnings("unchecked")
            Map<String, Object> saveData = gson.fromJson(json, Map.class);
            
            // Load global unique items
            @SuppressWarnings("unchecked")
            List<String> globalList = (List<String>) saveData.get("global_unique");
            if (globalList != null) {
                for (String idStr : globalList) {
                    Identifier id = Identifier.tryParse(idStr);
                    if (id != null) {
                        GLOBAL_UNIQUE.add(id);
                    }
                }
            }
            
            // Load per-player data
            @SuppressWarnings("unchecked")
            Map<String, List<String>> perPlayerData = (Map<String, List<String>>) saveData.get("per_player");
            if (perPlayerData != null) {
                for (Map.Entry<String, List<String>> entry : perPlayerData.entrySet()) {
                    try {
                        UUID uuid = UUID.fromString(entry.getKey());
                        Set<Identifier> items = new HashSet<>();
                        for (String idStr : entry.getValue()) {
                            Identifier id = Identifier.tryParse(idStr);
                            if (id != null) {
                                items.add(id);
                            }
                        }
                        PER_PLAYER.put(uuid, items);
                    } catch (IllegalArgumentException e) {
                        System.err.println("[WBE] Invalid UUID in save data: " + entry.getKey());
                    }
                }
            }
            
            // Load spawn position
            @SuppressWarnings("unchecked")
            Map<String, Object> spawnData = (Map<String, Object>) saveData.get("spawn_pos");
            if (spawnData != null) {
                try {
                    int x = ((Number) spawnData.get("x")).intValue();
                    int y = ((Number) spawnData.get("y")).intValue();
                    int z = ((Number) spawnData.get("z")).intValue();
                    SPAWN_POS = new BlockPos(x, y, z);
                } catch (Exception e) {
                    System.err.println("[WBE] Failed to load spawn position: " + e.getMessage());
                }
            }
            
            System.out.println("[WBE] Progress loaded: " + GLOBAL_UNIQUE.size() + " global items, " + PER_PLAYER.size() + " players");
        } catch (IOException e) {
            System.err.println("[WBE] Failed to load progress: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
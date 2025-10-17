package com.example.worldborderexpander;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SaplingBlock;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
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
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.TreeFeatureConfig;

import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.Set;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

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
        // Load config
        config = WBEConfig.load();
        
        // Initialize obtainable items list
        initializeObtainableItems();

        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            server = s;
            GLOBAL_UNIQUE.clear();
            PER_PLAYER.clear();

            ServerWorld overworld = s.getOverworld();
            BlockPos treeSpawn = ensureSpawnWithTree(overworld);

            initBordersForAll(s, config.startingBorderSize, treeSpawn.getX() + 0.5, treeSpawn.getZ() + 0.5);
            SPAWN_POS = treeSpawn;

            ensureObjective();
            setTabObjective();
            
            s.sendMessage(Text.literal("[WBE] World Border Expander initialized at " + treeSpawn.toShortString()));
            s.sendMessage(Text.literal("[WBE] Total obtainable items: " + OBTAINABLE_ITEMS.size()));
            s.sendMessage(Text.literal("[WBE] Starting border: " + config.startingBorderSize + " | Increment: " + config.expansionIncrement));
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, srv) -> {
            ServerPlayerEntity player = handler.getPlayer();
            player.teleport(
                SPAWN_POS.getX() + 0.5,
                SPAWN_POS.getY(),
                SPAWN_POS.getZ() + 0.5,
                false
            );
            ensureObjective();
            updatePlayerScore(player);
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
            );
        });
    }

    public static void onItemPickedUp(ServerPlayerEntity player, ItemStack stack) {
        if (stack.isEmpty()) return;

        Identifier id = Registries.ITEM.getId(stack.getItem());
        
        // Only count obtainable items
        if (!OBTAINABLE_ITEMS.contains(id)) return;

        boolean newForGlobal = GLOBAL_UNIQUE.add(id);
        PER_PLAYER.computeIfAbsent(player.getUuid(), k -> new HashSet<>()).add(id);

        updatePlayerScore(player);

        if (newForGlobal) {
            if (server == null) return;
            expandBorder(server);
            
            if (config.broadcastNewItems) {
                String itemName = stack.getName().getString();
                String playerName = config.showPlayerName ? player.getName().getString() : "Someone";
                broadcast(Text.literal("§6[WBE] §e" + playerName + 
                    " §ffound a new item: §a" + itemName + " §7(" + GLOBAL_UNIQUE.size() + "/" + OBTAINABLE_ITEMS.size() + ")"));
                broadcast(Text.literal("§7→ Border expanded to §f" + (int)server.getOverworld().getWorldBorder().getSize() + " §7blocks!"));
            }
            
            updateAllScores();
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
            Items.BUDDING_AMETHYST, Items.PETRIFIED_OAK_SLAB, Items.TIPPED_ARROW
        ));

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
        // Try to find natural tree spawn first
        BlockPos naturalSpawn = findNaturalTreeSpawn(world);
        if (naturalSpawn != null) {
            world.getServer().sendMessage(Text.literal("[WBE] Found natural tree at " + naturalSpawn.toShortString()));
            return naturalSpawn;
        }
        
        // No tree found nearby, spawn our own tree at (0, y, 0)
        world.getServer().sendMessage(Text.literal("[WBE] No natural tree found, generating tree at spawn..."));
        BlockPos spawnPos = generateTreeAtSpawn(world);
        world.getServer().sendMessage(Text.literal("[WBE] Tree generated at " + spawnPos.toShortString()));
        return spawnPos;
    }

    private static BlockPos findNaturalTreeSpawn(ServerWorld world) {
        int maxRadius = Math.min(config.spawnSearchRadius, 128); // Cap at 128 for performance
        int step = 8; // Check every 8 blocks
        
        // Check center first
        BlockPos center = checkForTreeNearby(world, 0, 0, 2);
        if (center != null) return center;
        
        // Check in expanding rings
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
        
        // Skip if in ocean
        if (isOceanBiome(world, center)) return null;
        
        // Check if there's a log within radius
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -1; dy <= 8; dy++) {
                    m.set(x + dx, y + dy, z + dz);
                    if (world.getBlockState(m).isIn(BlockTags.LOGS)) {
                        // Found a log! Return a safe spot next to it
                        return getSafeSpotNearTree(world, m.toImmutable());
                    }
                }
            }
        }
        
        return null;
    }

    private static BlockPos getSafeSpotNearTree(ServerWorld world, BlockPos treePos) {
        // Try to find a safe spot on the ground near the tree
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
        
        // Fallback to tree position
        return treePos;
    }

    private static BlockPos generateTreeAtSpawn(ServerWorld world) {
        // Find suitable spot at (0, y, 0)
        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, 0, 0);
        BlockPos basePos = new BlockPos(0, y, 0);
        
        // Make sure we have solid ground
        BlockState ground = world.getBlockState(basePos);
        if (ground.isAir() || !ground.getFluidState().isEmpty()) {
            // Place grass block
            world.setBlockState(basePos, Blocks.GRASS_BLOCK.getDefaultState());
        }
        
        // Generate a simple oak tree
        BlockPos treeBase = basePos.up();
        generateSimpleOakTree(world, treeBase);
        
        // Return spawn position next to tree
        return basePos;
    }

    private static void generateSimpleOakTree(ServerWorld world, BlockPos base) {
        Random random = new Random();
        int height = 4 + random.nextInt(3); // 4-6 blocks tall
        
        // Place trunk
        for (int i = 0; i < height; i++) {
            world.setBlockState(base.up(i), Blocks.OAK_LOG.getDefaultState());
        }
        
        // Place leaves (simple sphere shape)
        BlockPos leafBase = base.up(height - 2);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    // Skip corners and center column
                    if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                    if (dx == 0 && dz == 0 && dy <= 0) continue;
                    
                    BlockPos leafPos = leafBase.add(dx, dy, dz);
                    if (world.getBlockState(leafPos).isAir()) {
                        world.setBlockState(leafPos, Blocks.OAK_LEAVES.getDefaultState());
                    }
                }
            }
        }
        
        // Top leaf
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
}
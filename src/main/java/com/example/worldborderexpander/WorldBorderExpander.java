package com.example.worldborderexpander;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.number.NumberFormat;
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
import java.util.Set;
import net.minecraft.entity.Entity;

import java.util.*;

public class WorldBorderExpander implements ModInitializer {
    public static final String MOD_ID = "worldborderexpander";

    private static final double START_SIZE = 1.0; // старт 1x1
    private static final double INCREMENT  = 1.0; // +1 блок за уникальный предмет

    private static MinecraftServer server;
    private static ScoreboardObjective OBJECTIVE; // колонка в TAB

    private static final Set<Identifier> GLOBAL_UNIQUE = new HashSet<>();
    private static final Map<UUID, Set<Identifier>> PER_PLAYER = new HashMap<>();

    private static BlockPos SPAWN_POS = null;
    private static final Set<UUID> TELEPORTED_PLAYERS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private static boolean shouldTeleportFirstJoin(ServerPlayerEntity player) {
        return TELEPORTED_PLAYERS.add(player.getUuid());
    }

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            server = s;

            GLOBAL_UNIQUE.clear();
            PER_PLAYER.clear();

            // 1) найти «лесной» спавн
            ServerWorld overworld = s.getOverworld();
            BlockPos treeSpawn = pickSpawnNearTree(overworld);

            // 3) синхронизировать ЦЕНТР и РАЗМЕР бордера во всех измерениях
            initBordersForAll(s, START_SIZE, treeSpawn.getX() + 0.5, treeSpawn.getZ() + 0.5);

            SPAWN_POS = treeSpawn;

            // 4) таб-колонка
            ensureObjective();
            setTabObjective();
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
            updatePlayerScore(handler.getPlayer());
        });
    }

    // Вызывается миксином при фактическом поднятии предмета
    public static void onItemPickedUp(ServerPlayerEntity player, ItemStack stack) {
        if (stack.isEmpty()) return;

        Identifier id = Registries.ITEM.getId(stack.getItem());
        boolean newForGlobal = GLOBAL_UNIQUE.add(id);
        PER_PLAYER.computeIfAbsent(player.getUuid(), k -> new HashSet<>()).add(id);

        updatePlayerScore(player);

        if (newForGlobal) {
            if (server == null) return;
            expandBorder(server);
            broadcast(Text.literal("[WBE] New unique item: " + id + " → border expanded!"));
            updateAllScores();
        }
    }

    /* -------------------- Поиск «лесного» спавна -------------------- */

    private static BlockPos pickSpawnNearTree(ServerWorld world) {
        // радиусы колец от центра (0,0)
        int[] radii = {0, 64, 128, 192, 256, 384, 512, 640, 768, 896, 1024};
        int step = 16; // шаг сетки/кольца
        for (int r : radii) {
            if (r == 0) {
                BlockPos p = candidateAt(world, 0, 0);
                if (p != null && isGoodForestlike(world, p)) return p;
                continue;
            }
            // пробегаем только «кольцо» (чтобы не проверять всю площадь)
            for (int x = -r; x <= r; x += step) {
                for (int z = -r; z <= r; z += step) {
                    if (Math.abs(x) != r && Math.abs(z) != r) continue; // только края квадрата-кольца
                    BlockPos p = candidateAt(world, x, z);
                    if (p != null && isGoodForestlike(world, p)) return p;
                }
            }
        }
        // запасной вариант — просто (0,0) поверхность
        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, 0, 0);
        return new BlockPos(0, y, 0);
    }

    // берём верхнюю твёрдую поверхность, если она не в воде
    private static BlockPos candidateAt(ServerWorld world, int x, int z) {
        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = world.getBlockState(pos);
        if (!state.getFluidState().isEmpty()) return null; // вода — мимо
        return pos;
    }

    // хороший кандидат: НЕ океан и рядом есть бревно в пределах r=6 (по горизонтали) и h=8 (вверх)
    private static boolean isGoodForestlike(ServerWorld world, BlockPos pos) {
        if (isOceanBiome(world, pos)) return false;
        return hasLogsNearby(world, pos, 4, 8);
    }

    private static boolean isOceanBiome(ServerWorld world, BlockPos pos) {
        RegistryEntry<Biome> entry = world.getBiome(pos);
        return entry.isIn(BiomeTags.IS_OCEAN);
    }

    private static boolean hasLogsNearby(ServerWorld world, BlockPos pos, int radius, int up) {
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = 0; dy <= up; dy++) {
                    m.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (world.getBlockState(m).isIn(BlockTags.LOGS)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /* -------------------- Border in all dimensions -------------------- */

    private static void initBordersForAll(MinecraftServer s, double size, double centerX, double centerZ) {
        for (ServerWorld w : s.getWorlds()) {
            WorldBorder b = w.getWorldBorder();
            b.setCenter(centerX, centerZ);
            b.setSize(size);
        }
    }

    private static void expandBorder(MinecraftServer s) {
        double newSize = s.getOverworld().getWorldBorder().getSize() + INCREMENT;
        for (ServerWorld w : s.getWorlds()) {
            w.getWorldBorder().setSize(newSize);
        }
    }

    /* -------------------- TAB / Scoreboard -------------------- */

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
        server.getScoreboard().setObjectiveSlot(ScoreboardDisplaySlot.LIST, OBJECTIVE); // LIST = TAB
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

package com.example.worldborderexpander;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.border.WorldBorder;

import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.number.NumberFormat;

import java.util.*;

public class WorldBorderExpander implements ModInitializer {
    public static final String MOD_ID = "worldborderexpander";

    private static final Set<Identifier> GLOBAL_UNIQUE = new HashSet<>();
    private static final Map<UUID, Set<Identifier>> PER_PLAYER = new HashMap<>();

    private static final double START_SIZE = 1.0; // 1x1 start
    private static final double INCREMENT  = 1.0; // +1 block per new unique item

    private static MinecraftServer server;
    private static ScoreboardObjective OBJECTIVE; // shows in TAB

    @Override
    public void onInitialize() {
        // Setup world border + scoreboard when the server starts
        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            server = s;

            // world border
            ServerWorld overworld = s.getOverworld();
            WorldBorder border = overworld.getWorldBorder();
            border.setCenter(0.0, 0.0);
            border.setSize(START_SIZE);

            GLOBAL_UNIQUE.clear();
            PER_PLAYER.clear();

            ensureObjective();
            setTabObjective();
        });

        // Ensure TAB objective is visible & player score exists on join
        ServerPlayConnectionEvents.JOIN.register((handler, sender, srv) -> {
            ensureObjective();
            updatePlayerScore(handler.getPlayer());
        });
    }

    // Called by our mixin when an item is picked up
    public static void onItemPickedUp(ServerPlayerEntity player, ItemStack stack) {
        if (stack.isEmpty()) return;

        Identifier id = Registries.ITEM.getId(stack.getItem());
        boolean newForGlobal = GLOBAL_UNIQUE.add(id);
        PER_PLAYER.computeIfAbsent(player.getUuid(), k -> new HashSet<>()).add(id);

        // Always keep the player's own count fresh
        updatePlayerScore(player);

        if (newForGlobal) {
            if (server == null) return;
            expandBorder(server);
            broadcast(Text.literal("[WBE] New unique item: " + id + " → border expanded!"));
            updateAllScores();
        }
    }

    /* -------------------- Scoreboard helpers -------------------- */

    private static void ensureObjective() {
        if (server == null) return;
        Scoreboard sb = server.getScoreboard();

        // 1.21.10: method name is getNullableObjective
        OBJECTIVE = sb.getNullableObjective("wbe_unique");
        if (OBJECTIVE == null) {
            // 1.21.10: addObjective signature requires 6 args, incl. NumberFormat
            OBJECTIVE = sb.addObjective(
                "wbe_unique",
                ScoreboardCriterion.DUMMY,
                Text.literal("Unique Items"),
                ScoreboardCriterion.RenderType.INTEGER,
                false,
                null  // allowed; server will use the default integer format
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
        // 1.21.10: use the overload that accepts a ScoreHolder (the player)
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

    /* -------------------- World border helpers -------------------- */

    private static void expandBorder(MinecraftServer s) {
        ServerWorld overworld = s.getOverworld();
        WorldBorder border = overworld.getWorldBorder();
        border.setSize(border.getSize() + INCREMENT);
    }

    private static void broadcast(Text msg) {
        if (server == null) return;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.sendMessage(msg, false);
        }
    }
}

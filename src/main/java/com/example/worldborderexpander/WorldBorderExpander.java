package com.example.worldborderexpander;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.border.WorldBorder;

import java.util.*;

public class WorldBorderExpander implements ModInitializer {
    public static final String MOD_ID = "worldborderexpander";

    private static final Set<Identifier> GLOBAL_UNIQUE = new HashSet<>();
    private static final Map<UUID, Set<Identifier>> PER_PLAYER = new HashMap<>();

    private static final double START_SIZE = 1.0; // 1x1 start
    private static final double INCREMENT  = 1.0; // +1 block per new unique item

    private static MinecraftServer server;

    @Override
    public void onInitialize() {
        // Initialize world border when server starts
        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            server = s;
            ServerWorld overworld = s.getOverworld();
            WorldBorder border = overworld.getWorldBorder();
            border.setCenter(0.0, 0.0);   // avoid spawn-pos API differences
            border.setSize(START_SIZE);
            GLOBAL_UNIQUE.clear();
            PER_PLAYER.clear();
        });
    }

    // Called by our mixin when an item is actually picked up
    public static void onItemPickedUp(ServerPlayerEntity player, ItemStack stack) {
        if (stack.isEmpty()) return;

        Identifier id = Registries.ITEM.getId(stack.getItem());
        boolean newForGlobal = GLOBAL_UNIQUE.add(id);
        PER_PLAYER.computeIfAbsent(player.getUuid(), k -> new HashSet<>()).add(id);

        if (newForGlobal) {
            expandBorder(player.getServerWorld().getServer());
            broadcast(Text.literal("[WBE] New unique item: " + id + " → border expanded!"));
        }
        // TODO: update a scoreboard column here to show per-player counts on Tab
    }

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

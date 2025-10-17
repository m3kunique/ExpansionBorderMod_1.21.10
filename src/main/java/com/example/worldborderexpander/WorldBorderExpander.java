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

    private static final double START_SIZE = 1.0; // 1x1 to start
    private static final double INCREMENT  = 1.0; // +1 block per new unique item

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ServerWorld overworld = server.getOverworld();
            WorldBorder border = overworld.getWorldBorder();
            border.setCenter(overworld.getSpawnPos().getX(), overworld.getSpawnPos().getZ());
            border.setSize(START_SIZE);
        });
    }

    public static void onItemPickedUp(ServerPlayerEntity player, ItemStack stack) {
        if (player.getServer() == null || stack.isEmpty()) return;

        Identifier id = Registries.ITEM.getId(stack.getItem());
        boolean newForGlobal = GLOBAL_UNIQUE.add(id);
        PER_PLAYER.computeIfAbsent(player.getUuid(), k -> new HashSet<>()).add(id);

        if (newForGlobal) {
            expandBorder(player.getServer());
            broadcast(player.getServer(), Text.literal("[WBE] New unique item: " + id + " → border expanded!"));
        }
        // TODO: update tab/scoreboard display here if you want (numeric list column is easiest).
    }

    private static void expandBorder(MinecraftServer server) {
        ServerWorld overworld = server.getOverworld();
        WorldBorder border = overworld.getWorldBorder();
        border.setSize(border.getSize() + INCREMENT);
    }

    private static void broadcast(MinecraftServer server, Text msg) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.sendMessage(msg, false);
        }
    }
}

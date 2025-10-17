package com.example.worldborderexpander;

import net.minecraft.util.Identifier;

public final class WBEPackets {
    public static final Identifier SYNC_ALL_GLOBAL = Identifier.of(WorldBorderExpander.MOD_ID, "sync_all_global");
    public static final Identifier SYNC_ALL_PERSONAL = Identifier.of(WorldBorderExpander.MOD_ID, "sync_all_personal");
    public static final Identifier SYNC_ADD_GLOBAL = Identifier.of(WorldBorderExpander.MOD_ID, "sync_add_global");
    public static final Identifier SYNC_ADD_PERSONAL = Identifier.of(WorldBorderExpander.MOD_ID, "sync_add_personal");

    private WBEPackets() {}
}

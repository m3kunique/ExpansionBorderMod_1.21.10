package com.example.worldborderexpander.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class WBENetworking {
    private WBENetworking() {}

    public static void registerCommon() {
        // S2C payloads
        PayloadTypeRegistry.playS2C().register(WBEPayloads.SyncAllGlobal.ID, WBEPayloads.SyncAllGlobal.CODEC);
        PayloadTypeRegistry.playS2C().register(WBEPayloads.SyncAllPersonal.ID, WBEPayloads.SyncAllPersonal.CODEC);
        PayloadTypeRegistry.playS2C().register(WBEPayloads.AddGlobal.ID, WBEPayloads.AddGlobal.CODEC);
        PayloadTypeRegistry.playS2C().register(WBEPayloads.AddPersonal.ID, WBEPayloads.AddPersonal.CODEC);
    }
}

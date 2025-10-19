package com.example.worldborderexpander.net;

import com.example.worldborderexpander.WorldBorderExpander;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public final class WBEPayloads {
    private WBEPayloads() {}

    // === sync_all_global ===
    public record SyncAllGlobal(List<Identifier> ids) implements CustomPayload {
        public static final CustomPayload.Id<SyncAllGlobal> ID =
                new CustomPayload.Id<>(Identifier.of(WorldBorderExpander.MOD_ID, "sync_all_global"));

        public static final PacketCodec<PacketByteBuf, SyncAllGlobal> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.collection(ArrayList::new, Identifier.PACKET_CODEC),
                        SyncAllGlobal::ids,
                        SyncAllGlobal::new
                );

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // === sync_all_personal ===
    public record SyncAllPersonal(List<Identifier> ids) implements CustomPayload {
        public static final CustomPayload.Id<SyncAllPersonal> ID =
                new CustomPayload.Id<>(Identifier.of(WorldBorderExpander.MOD_ID, "sync_all_personal"));

        public static final PacketCodec<PacketByteBuf, SyncAllPersonal> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.collection(ArrayList::new, Identifier.PACKET_CODEC),
                        SyncAllPersonal::ids,
                        SyncAllPersonal::new
                );

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // === sync_add_global ===
    public record AddGlobal(Identifier id) implements CustomPayload {
        public static final CustomPayload.Id<AddGlobal> ID =
                new CustomPayload.Id<>(Identifier.of(WorldBorderExpander.MOD_ID, "sync_add_global"));

        public static final PacketCodec<PacketByteBuf, AddGlobal> CODEC =
                PacketCodec.tuple(Identifier.PACKET_CODEC, AddGlobal::id, AddGlobal::new);

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // === sync_add_personal ===
    public record AddPersonal(Identifier id) implements CustomPayload {
        public static final CustomPayload.Id<AddPersonal> ID =
                new CustomPayload.Id<>(Identifier.of(WorldBorderExpander.MOD_ID, "sync_add_personal"));

        public static final PacketCodec<PacketByteBuf, AddPersonal> CODEC =
                PacketCodec.tuple(Identifier.PACKET_CODEC, AddPersonal::id, AddPersonal::new);

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
}

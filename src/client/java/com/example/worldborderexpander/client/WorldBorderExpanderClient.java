package com.example.worldborderexpander.client;

import com.example.worldborderexpander.net.WBEPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.HashSet;
import java.util.Set;

public class WorldBorderExpanderClient implements ClientModInitializer {
    public static final Set<Identifier> GLOBAL_FOUND = new HashSet<>();
    public static final Set<Identifier> PERSONAL_FOUND = new HashSet<>();

    @Override public void onInitializeClient() {
        // payload receivers
        ClientPlayNetworking.registerGlobalReceiver(WBEPayloads.SyncAllGlobal.ID,
                (payload, context) -> context.client().execute(() -> {
                    GLOBAL_FOUND.clear();
                    GLOBAL_FOUND.addAll(payload.ids());
                }));

        ClientPlayNetworking.registerGlobalReceiver(WBEPayloads.SyncAllPersonal.ID,
                (payload, context) -> context.client().execute(() -> {
                    PERSONAL_FOUND.clear();
                    PERSONAL_FOUND.addAll(payload.ids());
                }));

        ClientPlayNetworking.registerGlobalReceiver(WBEPayloads.AddGlobal.ID,
                (payload, context) -> context.client().execute(() -> GLOBAL_FOUND.add(payload.id())));

        ClientPlayNetworking.registerGlobalReceiver(WBEPayloads.AddPersonal.ID,
                (payload, context) -> context.client().execute(() -> PERSONAL_FOUND.add(payload.id())));

        // tooltip
        ItemTooltipCallback.EVENT.register((ItemStack stack, Item.TooltipContext tooltipContext, TooltipType tooltipType, java.util.List<Text> lines) -> {
            Item item = stack.getItem();
            if (!(item instanceof BlockItem)) return;
            Identifier id = Registries.ITEM.getId(item);

            boolean foundGlobal = GLOBAL_FOUND.contains(id);
            boolean foundPersonal = PERSONAL_FOUND.contains(id);

            lines.add(Text.literal(foundGlobal ? "§aНайдено (мир)" : "§cНе найдено (мир)"));
            if (foundPersonal && !foundGlobal) lines.add(Text.literal("§eВы находили это"));
        });
    }
}

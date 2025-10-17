package com.example.worldborderexpander.mixin;

import com.example.worldborderexpander.WorldBorderExpander;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerInventory.class)
public abstract class PlayerInventoryMixin {
    @Shadow public PlayerEntity player;

    @Inject(method = "setStack(ILnet/minecraft/item/ItemStack;)V", at = @At("TAIL"))
    private void wbe_onSetStack(int slot, ItemStack stack, CallbackInfo ci) {
        if (stack == null || stack.isEmpty()) return;
        if (player instanceof ServerPlayerEntity sp) {
            WorldBorderExpander.onItemPickedUp(sp, stack); // или onItemObtained
        }
    }
}

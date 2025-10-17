package com.example.worldborderexpander.mixin;

import com.example.worldborderexpander.WorldBorderExpander;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerMixin {

    // В 1.21.10 у ScreenHandler нет getSlots(), есть public final DefaultedList<Slot> slots
    @Shadow @Final public DefaultedList<Slot> slots;

    @Unique private static final ThreadLocal<ItemStack> WBE_$pendingItem = new ThreadLocal<>();

    @Unique
    private boolean wbe$isPlayerRange(int start, int end) {
        int last = Math.min(end, this.slots.size());
        int first = Math.max(0, start);
        for (int i = first; i < last; i++) {
            if (this.slots.get(i).inventory instanceof PlayerInventory) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private ServerPlayerEntity wbe$findServerPlayerInRange(int start, int end) {
        int last = Math.min(end, this.slots.size());
        int first = Math.max(0, start);
        for (int i = first; i < last; i++) {
            var inv = this.slots.get(i).inventory;
            if (inv instanceof PlayerInventory pi) {
                PlayerEntity p = pi.player; // public поле
                if (p instanceof ServerPlayerEntity sp) return sp;
            }
        }
        return null;
    }

    // HEAD: сохраняем копию стека до вставки (иначе он может измениться)
    @Inject(method = "insertItem(Lnet/minecraft/item/ItemStack;IIZ)Z", at = @At("HEAD"))
    private void wbe_head(ItemStack stack, int startIndex, int endIndex, boolean fromLast,
                          CallbackInfoReturnable<Boolean> cir) {
        if (stack != null && !stack.isEmpty() && wbe$isPlayerRange(startIndex, endIndex)) {
            WBE_$pendingItem.set(stack.copy());
        } else {
            WBE_$pendingItem.remove();
        }
    }

    // RETURN: если вставка удалась и целевой диапазон — инвентарь игрока
    @Inject(method = "insertItem(Lnet/minecraft/item/ItemStack;IIZ)Z", at = @At("RETURN"))
    private void wbe_tail(ItemStack stack, int startIndex, int endIndex, boolean fromLast,
                          CallbackInfoReturnable<Boolean> cir) {
        try {
            if (!cir.getReturnValue()) return;
            if (!wbe$isPlayerRange(startIndex, endIndex)) return;

            ItemStack obtained = WBE_$pendingItem.get();
            if (obtained == null || obtained.isEmpty()) return;

            ServerPlayerEntity sp = wbe$findServerPlayerInRange(startIndex, endIndex);
            if (sp != null) {
                WorldBorderExpander.onItemPickedUp(sp, obtained); // или onItemObtained
            }
        } finally {
            WBE_$pendingItem.remove();
        }
    }
}

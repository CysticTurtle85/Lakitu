package net.cystic.lakitu.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.cystic.lakitu.CloudSpeedEffect;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The cloud-speed effects (Altitude, Rain Cloud) read e.g. "Altitude" over "2.4× cloud speed" in the inventory instead
 * of a level numeral over "∞". The same two lines make the tooltip when the effect list is compact.
 */
@Mixin(EffectsInInventory.class)
public abstract class EffectsInInventoryMixin {
    @Inject(method = "getEffectName", at = @At("HEAD"), cancellable = true)
    private void lakitu$altitudeName(MobEffectInstance effect, CallbackInfoReturnable<Component> cir) {
        if (CloudSpeedEffect.is(effect))
            cir.setReturnValue(effect.getEffect().value().getDisplayName());
    }

    @WrapOperation(method = "extractEffects", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/effect/MobEffectUtil;formatDuration(Lnet/minecraft/world/effect/MobEffectInstance;FF)Lnet/minecraft/network/chat/Component;"))
    private Component lakitu$altitudeSpeed(MobEffectInstance effect, float scale, float tickrate, Operation<Component> original) {
        return CloudSpeedEffect.is(effect) ? CloudSpeedEffect.speedText(effect) : original.call(effect, scale, tickrate);
    }
}

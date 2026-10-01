package net.cystic.lakitu.mixin;

import net.cystic.lakitu.CloudSpeedEffect;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//#if FORGE
import org.spongepowered.asm.mixin.injection.Redirect;
//#else
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
//#endif
//#if MC >= 1.21.2
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
//#else
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
//#endif

/**
 * The cloud-speed effects (Altitude, Rain Cloud) read e.g. "Altitude" over "2.4× cloud speed" in the inventory instead
 * of a level numeral over "∞". The same two lines make the tooltip when the effect list is compact. The effect list
 * moved from EffectRenderingInventoryScreen to EffectsInInventory in 1.21.2, and the method that writes the duration
 * changes name across versions (checked with javap on each): renderEffects/renderLabels (to 1.21.5),
 * renderLabels/renderTooltip (1.21.6-1.21.10), renderEffects (1.21.11), extractEffects (26.x).
 */
//#if MC >= 1.21.2
@Mixin(EffectsInInventory.class)
//#else
@Mixin(EffectRenderingInventoryScreen.class)
//#endif
public abstract class EffectsInInventoryMixin {
    @Inject(method = "getEffectName", at = @At("HEAD"), cancellable = true)
    private void lakitu$altitudeName(MobEffectInstance effect, CallbackInfoReturnable<Component> cir) {
        if (CloudSpeedEffect.is(effect))
            cir.setReturnValue(CloudSpeedEffect.name(effect));
    }

    //#if MC >= 26.1
    @WrapOperation(method = "extractEffects", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/effect/MobEffectUtil;formatDuration(Lnet/minecraft/world/effect/MobEffectInstance;FF)Lnet/minecraft/network/chat/Component;"))
    //#elif MC >= 1.21.11
    @WrapOperation(method = "renderEffects", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/effect/MobEffectUtil;formatDuration(Lnet/minecraft/world/effect/MobEffectInstance;FF)Lnet/minecraft/network/chat/Component;"))
    //#elif MC >= 1.21.6
    @WrapOperation(method = {"renderLabels", "renderTooltip"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/effect/MobEffectUtil;formatDuration(Lnet/minecraft/world/effect/MobEffectInstance;FF)Lnet/minecraft/network/chat/Component;"))
    //#elif MC >= 1.20.5
    @WrapOperation(method = {"renderEffects", "renderLabels"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/effect/MobEffectUtil;formatDuration(Lnet/minecraft/world/effect/MobEffectInstance;FF)Lnet/minecraft/network/chat/Component;"))
    //#endif
    //#if MC >= 1.20.5
    private Component lakitu$altitudeSpeed(MobEffectInstance effect, float scale, float tickrate, Operation<Component> original) {
        return CloudSpeedEffect.is(effect) ? CloudSpeedEffect.speedText(effect) : original.call(effect, scale, tickrate);
    }
    //#elif FORGE
    @Redirect(method = {"renderEffects", "renderLabels"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/effect/MobEffectUtil;formatDuration(Lnet/minecraft/world/effect/MobEffectInstance;F)Lnet/minecraft/network/chat/Component;"))
    private Component lakitu$altitudeSpeed(MobEffectInstance effect, float scale) {
        return CloudSpeedEffect.is(effect) ? CloudSpeedEffect.speedText(effect) : net.minecraft.world.effect.MobEffectUtil.formatDuration(effect, scale);
    }
    //#else
    @WrapOperation(method = {"renderEffects", "renderLabels"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/effect/MobEffectUtil;formatDuration(Lnet/minecraft/world/effect/MobEffectInstance;F)Lnet/minecraft/network/chat/Component;"))
    private Component lakitu$altitudeSpeed(MobEffectInstance effect, float scale, Operation<Component> original) {
        return CloudSpeedEffect.is(effect) ? CloudSpeedEffect.speedText(effect) : original.call(effect, scale);
    }
    //#endif
}

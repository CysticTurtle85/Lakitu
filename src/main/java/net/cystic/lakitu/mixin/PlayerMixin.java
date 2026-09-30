package net.cystic.lakitu.mixin;

import net.cystic.lakitu.entity.LakituCloudEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shift sinks the Lakitu Cloud instead of dismounting it. Vanilla dismounts in {@code Player.rideTick} (server side)
 * whenever {@code wantsToStopRiding()} is true, which is simply "Shift is held"; the cloud is left with the item instead.
 */
@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
    private void lakitu$stayOnCloud(CallbackInfoReturnable<Boolean> cir) {
        if (LakituCloudEntity.isRiding((Player) (Object) this))
            cir.setReturnValue(false);
    }
}

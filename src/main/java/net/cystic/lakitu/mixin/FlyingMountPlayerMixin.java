package net.cystic.lakitu.mixin;

import net.cystic.lakitu.flight.FlyingMount;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shift sinks a {@link FlyingMount} instead of dismounting. Vanilla dismounts in {@code Player.rideTick} (server side)
 * whenever {@code wantsToStopRiding()} is true, which is simply "Shift is held"; flying mounts are left another way
 * (Lakitu's cloud: use its item again). List it under "mixins" in the mod's mixin config.
 *
 * <p>Framework module file (flying-mount): improve it in the minecraft-multiloader-mods skill
 * (assets/modules/flying-mount) and sync it into every mod that has it.
 */
@Mixin(Player.class)
public abstract class FlyingMountPlayerMixin {
    @Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
    private void lakitu$shiftSinksFlyingMount(CallbackInfoReturnable<Boolean> cir) {
        if (((Player) (Object) this).getVehicle() instanceof FlyingMount mount && mount.shiftSinks())
            cir.setReturnValue(false);
    }
}

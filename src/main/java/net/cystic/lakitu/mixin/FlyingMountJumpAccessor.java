package net.cystic.lakitu.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Whether a rider is holding Jump (Space): FlyingMount climbs while they do. {@code LivingEntity.isJumping()} only
 * became public in 1.21.6; the field it reads has always been there. Listed under "mixins" in the mod's mixin config.
 *
 * <p>Framework module file (flying-mount): improve it in the minecraft-multiloader-mods skill
 * (assets/modules/flying-mount) and sync it into every mod that has it.
 */
@Mixin(LivingEntity.class)
public interface FlyingMountJumpAccessor {
    @Accessor("jumping")
    boolean lakitu$isJumping();
}

package net.cystic.lakitu;

import net.cystic.lakitu.entity.LakituCloudEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * "Altitude": shown to a player riding a Lakitu Cloud so they can see what their height does to its speed. The
 * inventory shows "Altitude" over "2.4× cloud speed" (EffectsInInventoryMixin; the amplifier holds the multiplier in
 * tenths). It does nothing itself: the cloud applies the speed. It removes itself from anyone not riding a cloud.
 */
public class AltitudeEffect extends MobEffect {
    public AltitudeEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x9ED8FF);
    }

    public static boolean is(MobEffectInstance effect) {
        return effect.getEffect().value() instanceof AltitudeEffect;
    }

    /** The line under the name: "2.4× cloud speed". */
    public static Component speedText(MobEffectInstance effect) {
        return Component.translatable("effect.lakitu.altitude.speed", AltitudeSpeed.format(effect.getAmplifier()));
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int tickCount, int amplification) {
        return true;
    }

    /** Returning false removes the effect: a crash or a mod can't leave it on someone who isn't riding. */
    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amplification) {
        return LakituCloudEntity.isRiding(mob);
    }
}

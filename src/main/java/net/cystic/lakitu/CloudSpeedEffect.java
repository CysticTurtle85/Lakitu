package net.cystic.lakitu;

import net.cystic.lakitu.entity.LakituCloudEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * Effects that show a cloud rider what changes their cloud's speed: "Altitude" (height) and "Rain Cloud" (rain or
 * water). The inventory shows the name over "2.4× cloud speed" (EffectsInInventoryMixin; the amplifier holds the
 * multiplier in tenths). They do nothing themselves: the cloud applies the speed. They remove themselves from anyone
 * not riding a cloud.
 */
public class CloudSpeedEffect extends MobEffect {
    public CloudSpeedEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    public static boolean is(MobEffectInstance effect) {
        return effect.getEffect().value() instanceof CloudSpeedEffect;
    }

    /** The line under the name: "2.4× cloud speed". */
    public static Component speedText(MobEffectInstance effect) {
        return Component.translatable("effect.lakitu.cloud_speed", AltitudeSpeed.format(effect.getAmplifier()));
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

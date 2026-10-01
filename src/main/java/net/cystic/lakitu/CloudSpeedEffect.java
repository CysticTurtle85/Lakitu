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
        //#if MC >= 1.20.5
        return effect.getEffect().value() instanceof CloudSpeedEffect;
        //#else
        return effect.getEffect() instanceof CloudSpeedEffect;
        //#endif
    }

    /** Just the effect's name, without a level numeral. */
    public static Component name(MobEffectInstance effect) {
        //#if MC >= 1.20.5
        return effect.getEffect().value().getDisplayName();
        //#else
        return effect.getEffect().getDisplayName();
        //#endif
    }

    /** The line under the name: "2.4× cloud speed". */
    public static Component speedText(MobEffectInstance effect) {
        return Component.translatable("effect.lakitu.cloud_speed", AltitudeSpeed.format(effect.getAmplifier()));
    }

    //#if MC >= 1.20.5
    @Override
    public boolean shouldApplyEffectTickThisTick(int tickCount, int amplification) {
        return true;
    }

    /** Returning false removes the effect: a crash or a mod can't leave it on someone who isn't riding. */
    @Override
    //#if MC >= 1.21.2
    public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amplification) {
    //#else
    public boolean applyEffectTick(LivingEntity mob, int amplification) {
    //#endif
        return LakituCloudEntity.isRiding(mob);
    }
    //#endif
}

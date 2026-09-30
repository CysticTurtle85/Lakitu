package net.cystic.lakitu;

import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * Bonus max health (player and cloud) and speed (cloud) while riding a Lakitu Cloud. In the Overworld (and any other
 * dimension) it grows linearly from nothing at {@code altitudeMinY} to the full bonus at {@code altitudeMaxY}; the
 * Nether and End ignore height and use a fixed share of the full bonus.
 *
 * <p>The health bonus is a transient attribute modifier, so it is never saved: a crash or disconnect can't leave it
 * on a player. Changing it keeps the health fraction, so climbing and descending heals nothing.
 */
public final class AltitudeBonus {
    private static final Identifier HEALTH_MODIFIER = Lakitu.id("altitude_health");

    private AltitudeBonus() {}

    /** Share of the full bonus at this position, 0 to 1, rounded to whole percents to avoid churn. */
    public static double share(Level level, double y) {
        LakituConfig config = LakituConfig.values;
        double share;
        if (level.dimension() == Level.NETHER)
            share = config.netherBonusShare;
        else if (level.dimension() == Level.END)
            share = config.endBonusShare;
        else
            share = (y - config.altitudeMinY) / Math.max(1.0, config.altitudeMaxY - config.altitudeMinY);
        return Math.round(Mth.clamp(share, 0.0, 1.0) * 100.0) / 100.0;
    }

    public static float speedMultiplier(double share) {
        return (float) (1.0 + LakituConfig.values.altitudeMaxSpeedBonus * share);
    }

    /** Sets the health bonus for this share, keeping the entity's health fraction. */
    public static void applyHealth(LivingEntity entity, double share) {
        AttributeInstance maxHealth = entity.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null || entity.isDeadOrDying())
            return;
        double amount = LakituConfig.values.altitudeMaxHealthBonus * share;
        AttributeModifier current = maxHealth.getModifier(HEALTH_MODIFIER);
        if (current == null ? amount == 0.0 : current.amount() == amount)
            return;
        float fraction = entity.getHealth() / entity.getMaxHealth();
        maxHealth.removeModifier(HEALTH_MODIFIER);
        if (amount != 0.0)
            maxHealth.addTransientModifier(new AttributeModifier(HEALTH_MODIFIER, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        entity.setHealth(fraction * entity.getMaxHealth());
    }

    /** Removes the health bonus; health above the normal max is clamped to it. */
    public static void removeHealth(LivingEntity entity) {
        AttributeInstance maxHealth = entity.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null || maxHealth.getModifier(HEALTH_MODIFIER) == null)
            return;
        maxHealth.removeModifier(HEALTH_MODIFIER);
        if (entity.getHealth() > entity.getMaxHealth())
            entity.setHealth(entity.getMaxHealth());
    }
}

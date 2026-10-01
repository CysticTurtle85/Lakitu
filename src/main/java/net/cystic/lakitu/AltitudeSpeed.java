package net.cystic.lakitu;

import java.util.Locale;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * How fast a Lakitu Cloud flies at a given height. Normal speed at the dimension's sea level, rising smoothly (in
 * step with height) to {@code altitudeSpeedAtBuildLimit} times normal at the build limit, and falling smoothly to
 * {@code altitudeSpeedAtBottom} times normal at the bottom of the world (bedrock). The Nether and the End ignore
 * height: always {@code altitudeSpeedInNether} (0.5×) and {@code altitudeSpeedInEnd} (3×). It scales horizontal and
 * vertical speed alike; the rider's Speed and Slowness, and rain, multiply on top.
 *
 * <p>The rider's client works the multiplier out every tick from the cloud's exact height (the cloud's movement is
 * simulated there), so it changes continuously. The server only uses it for the Altitude effect, which shows it.
 */
public final class AltitudeSpeed {
    /** Vanilla's sprint boost to movement speed; riders can't sprint, but it never counts. */
    private static final Identifier SPRINTING = Identifier.withDefaultNamespace("sprinting");

    private AltitudeSpeed() {}

    public static double multiplier(Level level, double y, double atBuildLimit, double atBottom, double inNether, double inEnd) {
        if (level.dimension() == Level.NETHER)
            return inNether;
        if (level.dimension() == Level.END)
            return inEnd;
        double seaLevel = level.getSeaLevel();
        if (y >= seaLevel) {
            double t = (y - seaLevel) / Math.max(1.0, level.getMaxY() + 1 - seaLevel);
            return Mth.lerp(Mth.clamp(t, 0.0, 1.0), 1.0, atBuildLimit);
        }
        double t = (seaLevel - y) / Math.max(1.0, seaLevel - level.getMinY());
        return Mth.lerp(Mth.clamp(t, 0.0, 1.0), 1.0, atBottom);
    }

    /**
     * The rider's own speed changes as a factor: Speed, Slowness and anything else that changes walking speed, the
     * same way they change walking (Speed II and Slowness I: 1.4 × 0.85).
     */
    public static double riderFactor(LivingEntity rider) {
        AttributeInstance speed = rider.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null || speed.getBaseValue() <= 0.0)
            return 1.0;
        double value = speed.getValue();
        AttributeModifier sprint = speed.getModifier(SPRINTING);
        if (sprint != null && sprint.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
            value /= 1.0 + sprint.amount();
        return Math.max(0.0, value / speed.getBaseValue());
    }

    /** A cloud-speed effect's amplifier for a multiplier, in tenths (2.4× is 24): that's the number it shows. */
    public static int amplifier(double multiplier) {
        return Mth.clamp((int) Math.round(multiplier * 10.0), 0, 255);
    }

    public static String format(int amplifier) {
        return String.format(Locale.ROOT, "%.1f", amplifier / 10.0);
    }
}

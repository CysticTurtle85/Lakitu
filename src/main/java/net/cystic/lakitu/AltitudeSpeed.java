package net.cystic.lakitu;

import java.util.Locale;
import net.minecraft.util.Mth;
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
    private AltitudeSpeed() {}

    public static double multiplier(Level level, double y, double atBuildLimit, double atBottom, double inNether, double inEnd) {
        if (level.dimension() == Level.NETHER)
            return inNether;
        if (level.dimension() == Level.END)
            return inEnd;
        double seaLevel = level.getSeaLevel();
        if (y >= seaLevel) {
            //#if MC >= 1.21.2
            double t = (y - seaLevel) / Math.max(1.0, level.getMaxY() + 1 - seaLevel);
            //#else
            double t = (y - seaLevel) / Math.max(1.0, level.getMaxBuildHeight() - seaLevel);
            //#endif
            return Mth.lerp(Mth.clamp(t, 0.0, 1.0), 1.0, atBuildLimit);
        }
        //#if MC >= 1.21.2
        double t = (seaLevel - y) / Math.max(1.0, seaLevel - level.getMinY());
        //#else
        double t = (seaLevel - y) / Math.max(1.0, seaLevel - level.getMinBuildHeight());
        //#endif
        return Mth.lerp(Mth.clamp(t, 0.0, 1.0), 1.0, atBottom);
    }

    /** A cloud-speed effect's amplifier for a multiplier, in tenths (2.4× is 24): that's the number it shows. */
    public static int amplifier(double multiplier) {
        return Mth.clamp((int) Math.round(multiplier * 10.0), 0, 255);
    }

    public static String format(int amplifier) {
        return String.format(Locale.ROOT, "%.1f", amplifier / 10.0);
    }
}

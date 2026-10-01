package net.cystic.lakitu;

import net.minecraft.resources.Identifier;

/**
 * Rain or water turns a Lakitu's cloud into a grey rain cloud: slower ({@code rainCloudSpeed}) instead of hurt. It
 * counts when rain falls on (or water touches) the cloud or whoever is on it: the rider, or the Lakitu.
 */
public final class RainCloud {
    /** It stays grey this long after the last drop, so the edge of a storm or a splash doesn't make it flicker. */
    public static final int DRYING_TICKS = 20;
    /** The Lakitu mob's flying-speed modifier while it's a rain cloud. */
    //#if MC >= 1.21
    public static final Identifier SLOWDOWN = Lakitu.id("rain_cloud");
    //#else
    public static final java.util.UUID SLOWDOWN = java.util.UUID.fromString("5b3a0b38-6c1e-4a52-9d7a-2f0c6b1e7a41");
    //#endif

    private RainCloud() {}

    /** Ticks left as a rain cloud, given last tick's count and whether anything is wet now. */
    public static int wetTicks(int before, boolean wetNow) {
        return wetNow ? DRYING_TICKS : Math.max(0, before - 1);
    }
}

package net.cystic.lakitu.flight;

/**
 * How a {@link FlyingMount} flies. Speeds and timings are synced from the server (they usually come from a config);
 * the look (turning, float, lean) is the mount type's own.
 *
 * <p>Framework module file (flying-mount): improve it in the minecraft-multiloader-mods skill
 * (assets/modules/flying-mount) and sync it into every mod that has it.
 *
 * @param horizontalSpeed     blocks per second at full input, before {@link FlyingMount#speedMultiplier}
 * @param verticalSpeed       blocks per second rising (Space) or sinking (Shift)
 * @param accelerationSeconds time to (nearly) reach full speed from rest; it eases in, so it never jerks
 * @param glideSeconds        time to glide (nearly) to a stop after letting go at full speed; slower, it settles
 *                            sooner (a quick tap barely drifts)
 * @param turnSeconds         time for the body to swing round to where the rider looks (on screen only)
 * @param bobBlocks           idle float: how far it rises and falls, rider included (0 = none)
 * @param bobSeconds          idle float: one full rise and fall
 * @param leanDegrees         how far it leans into turns and dips when speeding up (on screen only; 0 = never)
 * @param leanPivot           blocks above its feet that it (and its rider) lean around: about the middle of the model
 */
public record FlightSettings(float horizontalSpeed, float verticalSpeed, float accelerationSeconds, float glideSeconds,
                             float turnSeconds, float bobBlocks, float bobSeconds, float leanDegrees, float leanPivot) {
    /** A calm, floaty mount: Lakitu's cloud. */
    public static final FlightSettings DEFAULT = new FlightSettings(8.0F, 5.0F, 0.6F, 1.8F, 0.25F, 1.0F / 16.0F, 2.5F, 6.0F, 6.0F / 16.0F);

    public FlightSettings withSpeeds(float horizontal, float vertical) {
        return new FlightSettings(horizontal, vertical, this.accelerationSeconds, this.glideSeconds, this.turnSeconds,
                this.bobBlocks, this.bobSeconds, this.leanDegrees, this.leanPivot);
    }

    public FlightSettings withTimings(float acceleration, float glide) {
        return new FlightSettings(this.horizontalSpeed, this.verticalSpeed, acceleration, glide, this.turnSeconds,
                this.bobBlocks, this.bobSeconds, this.leanDegrees, this.leanPivot);
    }

    public FlightSettings withLook(float turnSeconds, float bobBlocks, float bobSeconds, float leanDegrees, float leanPivot) {
        return new FlightSettings(this.horizontalSpeed, this.verticalSpeed, this.accelerationSeconds, this.glideSeconds,
                turnSeconds, bobBlocks, bobSeconds, leanDegrees, leanPivot);
    }
}

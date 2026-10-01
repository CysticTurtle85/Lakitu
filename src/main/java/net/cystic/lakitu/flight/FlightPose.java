package net.cystic.lakitu.flight;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * How something flying looks on screen: its body swinging smoothly round to where it faces, a lean into slides and
 * turns, and a dip forward when speeding up (a lift when slowing down). Lean and dip grow with its speed, so a quick
 * nudge barely shows and only real flying leans fully. Every {@link FlyingMount} has one, its rider leans with it
 * (FlyingMountRiderMixin), and a flying mob can keep one too (Lakitu): tick it once per client tick and draw it with
 * {@code FlightLean}.
 *
 * <p>Framework module file (flying-mount): improve it in the minecraft-multiloader-mods skill
 * (assets/modules/flying-mount) and sync it into every mod that has it.
 */
public final class FlightPose {
    /** How quickly its sense of "normal" motion catches up (per tick); motion ahead of it reads as speeding up. */
    private static final double SETTLE = 0.25;
    /** How quickly lean and dip follow what the motion asks for (per tick). */
    private static final float FOLLOW = 0.2F;
    /** Speeding up by this share of top speed, beyond its recent motion, dips fully. */
    private static final double FULL_DIP = 0.24;

    private float yaw, yawO, lean, leanO, tilt, tiltO;
    private Vec3 settled = Vec3.ZERO;
    private boolean started;

    /**
     * One client tick.
     *
     * @param facing      where it faces now (degrees); the body swings round to it
     * @param motion      how far it moved this tick
     * @param topSpeed    blocks per tick at full speed: lean and dip are measured against it
     * @param turnSeconds time for the body to swing round (0: at once)
     * @param leanDegrees the most it leans (dips: 60% of that); 0 = never
     */
    public void tick(float facing, Vec3 motion, double topSpeed, float turnSeconds, float leanDegrees) {
        if (!this.started) {
            this.yaw = facing;
            this.started = true;
        }
        this.yawO = this.yaw;
        this.leanO = this.lean;
        this.tiltO = this.tilt;
        float turnRate = (float) (1.0 - Math.pow(0.05, 1.0 / Math.max(1.0, turnSeconds * 20.0)));
        float turnLeft = Mth.wrapDegrees(facing - this.yaw);
        this.yaw += turnLeft * turnRate;
        if (leanDegrees <= 0.0F) {
            this.lean = this.tilt = 0.0F;
            return;
        }
        double top = Math.max(0.01, topSpeed);
        double yawRad = this.yaw * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Math.sin(yawRad), 0.0, Math.cos(yawRad));
        Vec3 right = new Vec3(-Math.cos(yawRad), 0.0, -Math.sin(yawRad));
        double speed = Math.min(1.0, motion.horizontalDistance() / top);
        // Leans into slides, and into turns as far as it's moving: standing still, it just turns.
        double sideways = motion.dot(right) / top + turnLeft / 40.0 * speed;
        // Dips as it pulls ahead of its recent motion: short nudges and jerky pushes (a ghast's) barely show.
        this.settled = this.settled.add(motion.subtract(this.settled).scale(SETTLE));
        double speedingUp = motion.subtract(this.settled).dot(forward) / (top * FULL_DIP);
        float targetLean = (float) Mth.clamp(sideways, -1.0, 1.0) * leanDegrees;
        float targetTilt = (float) Mth.clamp(speedingUp, -1.0, 1.0) * leanDegrees * 0.6F;
        this.lean += (targetLean - this.lean) * FOLLOW;
        this.tilt += (targetTilt - this.tilt) * FOLLOW;
    }

    /** Body yaw on screen, swinging smoothly round to where it faces. */
    public float yaw(float partialTick) {
        return this.yawO + Mth.wrapDegrees(this.yaw - this.yawO) * partialTick;
    }

    /** Degrees it leans to its right (into a right turn or slide); negative leans left. */
    public float lean(float partialTick) {
        return Mth.lerp(partialTick, this.leanO, this.lean);
    }

    /** Degrees it dips forward (speeding up); negative lifts its front (slowing down). */
    public float tilt(float partialTick) {
        return Mth.lerp(partialTick, this.tiltO, this.tilt);
    }
}

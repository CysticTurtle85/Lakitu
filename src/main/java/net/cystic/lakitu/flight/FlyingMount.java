package net.cystic.lakitu.flight;

import net.cystic.lakitu.mixin.FlyingMountJumpAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * A mount a player flies: a cloud, a broomstick, a carpet. The rider steers it like a Happy Ghast: WASD moves it
 * relative to where they look (pitch ignored), Space rises and Shift sinks (Shift never dismounts:
 * FlyingMountPlayerMixin). Movement is simulated on the rider's client, eased per axis with a critically damped
 * spring, so it gathers speed smoothly and glides to a stop; the faster it was going, the longer the glide, so a tap
 * barely drifts ({@link FlightSettings}). On screen ({@code FlyingMountRenderer}) it floats up and down with its rider,
 * swings round smoothly to where the rider looks and leans a little into turns and speed ({@link FlightPose}), the
 * rider leaning with it (FlyingMountRiderMixin).
 *
 * <p>A mount type extends this, passes its {@link FlightSettings} (and on the server, its configured speeds through
 * {@link #setFlightSettings}), and can change its speed on the fly with {@link #speedMultiplier} (Lakitu's cloud: height
 * and rain). Lifecycle (summoning, health, despawning) is the mount's own.
 *
 * <p>Framework module file (flying-mount): improve it in the minecraft-multiloader-mods skill
 * (assets/modules/flying-mount) and sync it into every mod that has it ({@code scripts/new_mod.py sync <mod> --apply}).
 */
public abstract class FlyingMount extends Mob {
    private static final EntityDataAccessor<Float> DATA_HORIZONTAL_SPEED = SynchedEntityData.defineId(FlyingMount.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_VERTICAL_SPEED = SynchedEntityData.defineId(FlyingMount.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ACCELERATION = SynchedEntityData.defineId(FlyingMount.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_GLIDE = SynchedEntityData.defineId(FlyingMount.class, EntityDataSerializers.FLOAT);
    //#if MC >= 1.21.11
    private static final EntityDataAccessor<Vector3fc> DATA_LAUNCH = SynchedEntityData.defineId(FlyingMount.class, EntityDataSerializers.VECTOR3);
    //#else
    private static final EntityDataAccessor<Vector3f> DATA_LAUNCH = SynchedEntityData.defineId(FlyingMount.class, EntityDataSerializers.VECTOR3);
    //#endif
    private static final EntityDataAccessor<Integer> DATA_LAUNCH_TICKS = SynchedEntityData.defineId(FlyingMount.class, EntityDataSerializers.INT);
    /** Vanilla's sprint boost to movement speed; riders can't sprint, but it never counts. */
    //#if MC >= 1.21
    private static final Identifier SPRINTING = Identifier.withDefaultNamespace("sprinting");
    //#else
    private static final java.util.UUID SPRINTING = java.util.UUID.fromString("662A6B8D-DA3E-4C1C-8813-96EA6097278D");
    //#endif
    /** A critically damped spring is ~95% of the way after this many of its smoothing times. */
    private static final double SETTLE = 2.4;

    private final FlightSettings look;
    /** The springs' own rates of change, per axis (rider's client). */
    private double easeX, easeY, easeZ;
    /** How long (smoothing ticks) the glide after letting go lasts: set from the speed while the keys are held. */
    private double glideHorizontal, glideVertical;
    /** On screen (every client): smoothed body yaw, lean into turns, dip when speeding up. */
    private final FlightPose pose = new FlightPose();

    protected FlyingMount(EntityType<? extends FlyingMount> type, Level level, FlightSettings settings) {
        super(type, level);
        this.look = settings;
        this.setNoGravity(true);
        if (!level.isClientSide())
            this.setFlightSettings(settings);
        this.glideHorizontal = this.glideVertical = settings.glideSeconds() * 20.0 / SETTLE;
    }

    //#if MC >= 1.20.5
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
    //#else
    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        SynchedEntityData entityData = this.entityData;
    //#endif
        FlightSettings d = FlightSettings.DEFAULT;
        entityData.define(DATA_HORIZONTAL_SPEED, d.horizontalSpeed());
        entityData.define(DATA_VERTICAL_SPEED, d.verticalSpeed());
        entityData.define(DATA_ACCELERATION, d.accelerationSeconds());
        entityData.define(DATA_GLIDE, d.glideSeconds());
        entityData.define(DATA_LAUNCH, new Vector3f());
        entityData.define(DATA_LAUNCH_TICKS, 0);
    }

    /** Server: the speeds and timings riders get (synced to them); the look stays the mount type's own. */
    public void setFlightSettings(FlightSettings settings) {
        this.entityData.set(DATA_HORIZONTAL_SPEED, settings.horizontalSpeed());
        this.entityData.set(DATA_VERTICAL_SPEED, settings.verticalSpeed());
        this.entityData.set(DATA_ACCELERATION, settings.accelerationSeconds());
        this.entityData.set(DATA_GLIDE, settings.glideSeconds());
    }

    /** The settings in use: synced speeds and timings, this type's look. */
    public FlightSettings flightSettings() {
        return new FlightSettings(this.entityData.get(DATA_HORIZONTAL_SPEED), this.entityData.get(DATA_VERTICAL_SPEED),
                this.entityData.get(DATA_ACCELERATION), this.entityData.get(DATA_GLIDE), this.look.turnSeconds(),
                this.look.bobBlocks(), this.look.bobSeconds(), this.look.leanDegrees(), this.look.leanPivot());
    }

    /**
     * How much faster or slower than its settings it flies right now. Called every tick on the rider's client (where
     * it moves), so keep it to things that client knows. Default: the rider's Speed and Slowness.
     */
    protected double speedMultiplier(Player rider) {
        return riderSpeedFactor(rider);
    }

    /**
     * The rider's own speed changes as a factor: Speed, Slowness and anything else that changes walking speed, the
     * same way they change walking (Speed II and Slowness I: 1.4 × 0.85). Sprinting doesn't count.
     */
    public static double riderSpeedFactor(LivingEntity rider) {
        AttributeInstance speed = rider.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null || speed.getBaseValue() <= 0.0)
            return 1.0;
        double value = speed.getValue();
        AttributeModifier sprint = speed.getModifier(SPRINTING);
        //#if MC >= 1.21
        if (sprint != null && sprint.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
            value /= 1.0 + sprint.amount();
        //#else
        if (sprint != null && sprint.getOperation() == AttributeModifier.Operation.MULTIPLY_TOTAL)
            value /= 1.0 + sprint.getAmount();
        //#endif
        return Math.max(0.0, value / speed.getBaseValue());
    }

    /**
     * Server, before it's added to the world: a push off on summon that fades into normal control over its first
     * {@code fadeTicks} ticks (counted by its age, which the rider's client shares).
     */
    public void launch(Vec3 velocity, int fadeTicks) {
        this.entityData.set(DATA_LAUNCH, new Vector3f((float) velocity.x, (float) velocity.y, (float) velocity.z));
        this.entityData.set(DATA_LAUNCH_TICKS, fadeTicks);
        this.setDeltaMovement(velocity);
    }

    public static boolean isRiding(Entity entity) {
        return entity.getVehicle() instanceof FlyingMount;
    }

    /** Whether Shift sinks it instead of dismounting (FlyingMountPlayerMixin). */
    public boolean shiftSinks() {
        return true;
    }

    // --- Riding ---------------------------------------------------------------------------------

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return !this.isVehicle() && passenger instanceof Player;
    }

    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        return this.getFirstPassenger() instanceof Player player ? player : super.getControllingPassenger();
    }

    //#if MC >= 1.21.6
    @Override
    public boolean isFlyingVehicle() {
        return true;
    }
    //#endif

    @Override
    protected Vec3 getRiddenInput(Player controller, Vec3 selfInput) {
        float vertical = (((FlyingMountJumpAccessor) controller).lakitu$isJumping() ? 1.0F : 0.0F) - (controller.isShiftKeyDown() ? 1.0F : 0.0F);
        return new Vec3(controller.xxa, vertical, controller.zza);
    }

    @Override
    protected void tickRidden(Player controller, Vec3 riddenInput) {
        super.tickRidden(controller, riddenInput);
        // Steering follows where the rider looks; the body swings round to it smoothly on screen (visualYaw).
        this.setRot(controller.getYRot(), 0.0F);
        this.yRotO = this.yBodyRot = this.yHeadRot = this.getYRot();
    }

    @Override
    public void travel(Vec3 input) {
        if (!(this.getControllingPassenger() instanceof Player rider)) {
            this.glideToStop();
            return;
        }
        FlightSettings s = this.flightSettings();
        double multiplier = this.speedMultiplier(rider);
        double horizontal = s.horizontalSpeed() * multiplier / 20.0;
        double vertical = s.verticalSpeed() * multiplier / 20.0;
        double strafe = input.x;
        double forward = input.z;
        double length = Math.sqrt(strafe * strafe + forward * forward);
        if (length > 1.0) {
            strafe /= length;
            forward /= length;
        }
        float yaw = this.getYRot() * Mth.DEG_TO_RAD;
        double sin = Mth.sin(yaw);
        double cos = Mth.cos(yaw);
        double wantX = (strafe * cos - forward * sin) * horizontal;
        double wantZ = (forward * cos + strafe * sin) * horizontal;
        double wantY = input.y * vertical;

        // Eases in when pushing, glides out when letting go; each axis on its own spring. The glide lasts as long as
        // the speed it had when the keys were let go earns: the full glide from full speed, a short settle after a tap.
        double accel = s.accelerationSeconds() * 20.0 / SETTLE;
        boolean steering = length > 0.01;
        boolean climbing = Math.abs(input.y) > 0.01;
        Vec3 current = this.getDeltaMovement();
        if (steering)
            this.glideHorizontal = glideTicks(s, current.horizontalDistance() / Math.max(horizontal, 1.0E-4));
        if (climbing)
            this.glideVertical = glideTicks(s, Math.abs(current.y) / Math.max(vertical, 1.0E-4));
        double horizontalTime = steering ? accel : this.glideHorizontal;
        double verticalTime = climbing ? accel : this.glideVertical;
        double[] x = smoothDamp(current.x, wantX, this.easeX, horizontalTime);
        double[] y = smoothDamp(current.y, wantY, this.easeY, verticalTime);
        double[] z = smoothDamp(current.z, wantZ, this.easeZ, horizontalTime);
        this.easeX = x[1];
        this.easeY = y[1];
        this.easeZ = z[1];
        Vec3 velocity = new Vec3(x[0], y[0], z[0]);

        int launchTicks = this.entityData.get(DATA_LAUNCH_TICKS);
        if (this.tickCount < launchTicks) {
            // A launch blends from its push to normal control.
            float progress = this.tickCount / (float) launchTicks;
            Vector3fc launch = this.entityData.get(DATA_LAUNCH);
            velocity = new Vec3(launch.x(), launch.y(), launch.z()).scale(1.0F - progress).add(velocity.scale(progress));
        }
        this.setDeltaMovement(velocity);
        this.move(MoverType.SELF, velocity);
        // A wall stops the spring too, so it doesn't keep pushing into it.
        Vec3 after = this.getDeltaMovement();
        if (after.x == 0.0)
            this.easeX = 0.0;
        if (after.z == 0.0)
            this.easeZ = 0.0;
    }

    /** Without a rider it drifts to a stop where it is. */
    protected void glideToStop() {
        this.easeX = this.easeY = this.easeZ = 0.0;
        this.setDeltaMovement(this.getDeltaMovement().scale(0.9));
        this.move(MoverType.SELF, this.getDeltaMovement());
    }

    /**
     * Smoothing ticks of the glide after letting go at {@code speedShare} of full speed: the full glide from full speed,
     * falling off with the square of the speed, but never under half the acceleration time (it still settles smoothly).
     */
    private static double glideTicks(FlightSettings s, double speedShare) {
        double share = Mth.clamp(speedShare, 0.0, 1.0);
        return Math.max(s.accelerationSeconds() * 0.5, s.glideSeconds() * share * share) * 20.0 / SETTLE;
    }

    /**
     * One tick of a critically damped spring (as Unity's SmoothDamp) toward {@code target}: eases in and out without
     * overshooting. {@code smoothTicks} sets how slow; it's ~95% there after 2.4 × that. Returns {value, rate}. When the
     * target moves behind it (keys let go or reversed), it drops the push it was still building toward the old one:
     * otherwise a tap keeps speeding up after the key is up.
     */
    static double[] smoothDamp(double current, double target, double rate, double smoothTicks) {
        if (rate * (target - current) < 0.0)
            rate = 0.0;
        double omega = 2.0 / Math.max(smoothTicks, 0.05);
        double exp = 1.0 / (1.0 + omega + 0.48 * omega * omega + 0.235 * omega * omega * omega);
        double change = current - target;
        double temp = rate + omega * change;
        return new double[] {target + (change + temp) * exp, (rate - omega * temp) * exp};
    }

    // --- Float, turning and lean (on screen) -----------------------------------------------------

    /** The idle float in blocks at this age: up and back once per bob period, smoothly. */
    public float bob(float ageInTicks) {
        if (this.look.bobBlocks() <= 0.0F)
            return 0.0F;
        return (1.0F - Mth.cos(ageInTicks * Mth.TWO_PI / (this.look.bobSeconds() * 20.0F))) * 0.5F * this.look.bobBlocks();
    }

    //#if MC >= 1.20.5
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        // The rider floats with it.
        return super.getPassengerAttachmentPoint(passenger, dimensions, scale).add(0.0, this.bob(this.tickCount), 0.0);
    }
    //#else
    /** Before 1.20.5 there are no passenger attachments: where riders sit, above its feet (a player's own -0.35 is added). */
    protected double legacySeatOffset() {
        return this.getBbHeight() * 0.75;
    }

    @Override
    public double getPassengersRidingOffset() {
        // The rider floats with it.
        return this.legacySeatOffset() + this.bob(this.tickCount);
    }
    //#endif

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide())
            this.tickLook();
    }

    private void tickLook() {
        this.pose.tick(this.getYRot(), this.position().subtract(this.xo, this.yo, this.zo),
                this.entityData.get(DATA_HORIZONTAL_SPEED) / 20.0, this.look.turnSeconds(), this.look.leanDegrees());
    }

    /** Its yaw, lean and dip on screen (client); the rider leans with it. */
    public FlightPose pose() {
        return this.pose;
    }

    /** Blocks above its feet that it and its rider lean around. */
    public float leanPivot() {
        return this.look.leanPivot();
    }

    // --- No falling --------------------------------------------------------------------------

    @Override
    protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
    }

    @Override
    public boolean isPushable() {
        return false;
    }
}

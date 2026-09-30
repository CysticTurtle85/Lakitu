package net.cystic.lakitu.entity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.cystic.lakitu.AltitudeBonus;
import net.cystic.lakitu.Lakitu;
import net.cystic.lakitu.LakituConfig;
import net.cystic.lakitu.item.CloudData;
import net.cystic.lakitu.item.DismountSlowFall;
import net.cystic.lakitu.item.LakituCloudItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The rideable cloud summoned from a Lakitu Cloud item. It only exists while its summoner rides it: whenever it loses
 * its rider (dismissed, rider died, or the item left their inventory) it disappears and its health goes back onto the
 * item. Portals take cloud and rider through together (vanilla vehicle travel), and a rider who logs out takes it
 * with them in their player data (like a horse), so they log back in still riding instead of falling.
 *
 * <p>Movement is simulated by the rider's client, like a horse or the Happy Ghast. WASD moves horizontally relative
 * to where the rider looks (pitch is ignored), Space rises and Shift sinks; {@code PlayerMixin} stops Shift from
 * dismounting.
 */
public class LakituCloudEntity extends Mob implements GeoEntity {
    private static final EntityDataAccessor<Float> DATA_HORIZONTAL_SPEED = SynchedEntityData.defineId(LakituCloudEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_VERTICAL_SPEED = SynchedEntityData.defineId(LakituCloudEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SPEED_MULTIPLIER = SynchedEntityData.defineId(LakituCloudEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Vector3fc> DATA_LAUNCH = SynchedEntityData.defineId(LakituCloudEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> DATA_LAUNCH_TICKS = SynchedEntityData.defineId(LakituCloudEntity.class, EntityDataSerializers.INT);

    /** Share of the gap to the wanted velocity closed each tick: the floaty, drift-to-a-stop feel. */
    private static final double ACCELERATION = 0.25;
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");

    /** Clouds currently out in the world, by cloud id (server only). */
    private static final Map<UUID, LakituCloudEntity> ACTIVE = new ConcurrentHashMap<>();
    /** Deaths whose item couldn't be found at the time, applied when the item is next used (server only). */
    private static final Map<UUID, Long> PENDING_DEATHS = new ConcurrentHashMap<>();

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private @Nullable UUID cloudId;
    private @Nullable UUID lastRider;
    private float savedHealth = -1.0F;
    private int ticksWithoutRider;
    private boolean finished;

    public LakituCloudEntity(EntityType<? extends LakituCloudEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        if (!level.isClientSide()) {
            this.entityData.set(DATA_HORIZONTAL_SPEED, (float) LakituConfig.values.cloudHorizontalSpeed);
            this.entityData.set(DATA_VERTICAL_SPEED, (float) LakituConfig.values.cloudVerticalSpeed);
        }
    }

    // Not noSave(): vanilla refuses to let anyone ride an entity type that can't be saved.
    public static EntityType.Builder<LakituCloudEntity> builder() {
        return EntityType.Builder.of(LakituCloudEntity::new, MobCategory.MISC)
                // Hitbox from the model's cloud group (1 x 0.56 blocks); the rider sits where the Lakitu does in it.
                .sized(1.0F, 0.6F)
                .passengerAttachments(0.6F)
                .noSummon()
                .clientTrackingRange(10);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 40.0);
    }

    public static boolean isRiding(Entity entity) {
        return entity.getVehicle() instanceof LakituCloudEntity;
    }

    public static boolean isOut(UUID cloudId) {
        LakituCloudEntity cloud = ACTIVE.get(cloudId);
        return cloud != null && !cloud.isRemoved();
    }

    public static @Nullable Long takePendingDeath(UUID cloudId) {
        return PENDING_DEATHS.remove(cloudId);
    }

    /** Server: links a freshly created cloud to its item's state, before it's added to the level. */
    public void setUp(UUID cloudId, float healthFraction, @Nullable Vec3 launch) {
        LakituConfig config = LakituConfig.values;
        this.cloudId = cloudId;
        ACTIVE.put(cloudId, this);
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(config.cloudMaxHealth);
        this.setHealth(Math.max(1.0F, healthFraction * this.getMaxHealth()));
        this.savedHealth = this.getHealth() / this.getMaxHealth();
        if (launch != null) {
            this.entityData.set(DATA_LAUNCH, new Vector3f((float) launch.x, (float) launch.y, (float) launch.z));
            this.entityData.set(DATA_LAUNCH_TICKS, LakituConfig.ticks(config.testLaunchFadeSeconds));
            this.setDeltaMovement(launch);
        }
    }

    public boolean belongsTo(ItemStack stack) {
        return cloudId != null && LakituCloudItem.data(stack).cloudId().filter(cloudId::equals).isPresent();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_HORIZONTAL_SPEED, 8.0F);
        entityData.define(DATA_VERTICAL_SPEED, 5.0F);
        entityData.define(DATA_SPEED_MULTIPLIER, 1.0F);
        entityData.define(DATA_LAUNCH, new Vector3f());
        entityData.define(DATA_LAUNCH_TICKS, 0);
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

    @Override
    public boolean isFlyingVehicle() {
        return true;
    }

    @Override
    protected Vec3 getRiddenInput(Player controller, Vec3 selfInput) {
        float vertical = (controller.isJumping() ? 1.0F : 0.0F) - (controller.isShiftKeyDown() ? 1.0F : 0.0F);
        return new Vec3(controller.xxa, vertical, controller.zza);
    }

    @Override
    protected void tickRidden(Player controller, Vec3 riddenInput) {
        super.tickRidden(controller, riddenInput);
        // The cloud turns with the rider so WASD is relative to where they look.
        this.setRot(controller.getYRot(), 0.0F);
        this.yRotO = this.yBodyRot = this.yHeadRot = this.getYRot();
    }

    @Override
    public void travel(Vec3 input) {
        if (!(this.getControllingPassenger() instanceof Player)) {
            this.setDeltaMovement(this.getDeltaMovement().scale(0.8));
            this.move(MoverType.SELF, this.getDeltaMovement());
            return;
        }
        float multiplier = this.entityData.get(DATA_SPEED_MULTIPLIER);
        double horizontal = this.entityData.get(DATA_HORIZONTAL_SPEED) * multiplier / 20.0;
        double vertical = this.entityData.get(DATA_VERTICAL_SPEED) * multiplier / 20.0;
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
        Vec3 wanted = new Vec3((strafe * cos - forward * sin) * horizontal, input.y * vertical, (forward * cos + strafe * sin) * horizontal);

        Vec3 velocity;
        int launchTicks = this.entityData.get(DATA_LAUNCH_TICKS);
        if (this.tickCount < launchTicks) {
            // Test launch: blend from the launch velocity to normal control.
            float progress = this.tickCount / (float) launchTicks;
            Vector3fc launch = this.entityData.get(DATA_LAUNCH);
            velocity = new Vec3(launch.x(), launch.y(), launch.z()).scale(1.0F - progress).add(wanted.scale(progress));
        } else {
            velocity = this.getDeltaMovement().add(wanted.subtract(this.getDeltaMovement()).scale(ACCELERATION));
        }
        this.setDeltaMovement(velocity);
        this.move(MoverType.SELF, velocity);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        // Leave the rider where they sit instead of searching for the ground: the cloud is usually high up.
        return this.position();
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!this.level().isClientSide() && passenger instanceof LivingEntity living) {
            AltitudeBonus.removeHealth(living);
            AltitudeBonus.removeHealth(this);
            this.entityData.set(DATA_SPEED_MULTIPLIER, 1.0F);
        }
    }

    // --- Server upkeep ------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel level && this.isAlive() && !this.isRemoved())
            this.serverTick(level);
    }

    private void serverTick(ServerLevel level) {
        if (!(this.getControllingPassenger() instanceof ServerPlayer rider)) {
            if (++this.ticksWithoutRider > 1)
                this.dismiss(level, false);
            return;
        }
        this.ticksWithoutRider = 0;
        this.lastRider = rider.getUUID();

        ItemStack stack = this.findItem(rider);
        if (stack.isEmpty() || rider.isDeadOrDying()) {
            this.dismiss(level, false);
            return;
        }

        double share = AltitudeBonus.share(level, this.getY());
        AltitudeBonus.applyHealth(this, share);
        AltitudeBonus.applyHealth(rider, share);
        this.entityData.set(DATA_SPEED_MULTIPLIER, AltitudeBonus.speedMultiplier(share));

        LakituConfig config = LakituConfig.values;
        if (this.tickCount % LakituConfig.ticks(config.rainDamageIntervalSeconds) == 0 && isInRain(this))
            this.hurtServer(level, rainDamage(level), (float) config.rainDamage);

        // Replaces vanilla's "Press Shift to Dismount", which is wrong for the cloud.
        if (this.tickCount == 5)
            rider.sendOverlayMessage(Component.translatable("message.lakitu.cloud_controls"));

        // Keep the item up to date while riding: a disconnect saves the player before the cloud is removed.
        float fraction = this.getHealth() / this.getMaxHealth();
        if (this.isAlive() && Math.abs(fraction - this.savedHealth) > 0.001F)
            this.saveHealth(stack, level);
    }

    /** Server: sends the cloud back into its item. {@code deliberate} is a dismount with the item (Slow Falling). */
    public void dismiss(ServerLevel level, boolean deliberate) {
        if (this.finished)
            return;
        this.finished = true;
        Player rider = this.riderOrLastRider(level);
        if (rider != null) {
            ItemStack stack = this.findItem(rider);
            if (!stack.isEmpty())
                this.saveHealth(stack, level);
            if (deliberate && LakituConfig.values.slowFallingOnDismount && this.hasPassenger(rider))
                DismountSlowFall.grant(rider);
        }
        this.ejectPassengers();
        this.poof(level);
        this.playSound(SoundEvents.HARNESS_GOGGLES_UP, 1.0F, 1.0F);
        this.discard();
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!(this.level() instanceof ServerLevel level) || this.finished)
            return;
        this.finished = true;
        long now = level.getGameTime();
        int cooldown = LakituConfig.ticks(LakituConfig.values.cloudDeathCooldownSeconds);
        Player rider = this.riderOrLastRider(level);
        ItemStack stack = rider != null ? this.findItem(rider) : ItemStack.EMPTY;
        if (!stack.isEmpty()) {
            stack.set(Lakitu.cloudData.get(), LakituCloudItem.data(stack).died(now, now + cooldown));
            rider.getCooldowns().addCooldown(CloudData.cooldownGroup(this.cloudId), cooldown);
        } else if (this.cloudId != null) {
            PENDING_DEATHS.put(this.cloudId, now + cooldown);
        }
        // The rider falls from here with normal fall damage.
        this.ejectPassengers();
        this.poof(level);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.storeNullable("cloud_id", UUIDUtil.CODEC, this.cloudId);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.cloudId = input.read("cloud_id", UUIDUtil.CODEC).orElse(null);
        this.savedHealth = this.getHealth() / this.getMaxHealth();
        if (this.cloudId != null && !this.level().isClientSide())
            ACTIVE.put(this.cloudId, this);
    }

    @Override
    public void onRemoval(RemovalReason reason) {
        super.onRemoval(reason);
        if (this.cloudId != null && !this.level().isClientSide())
            ACTIVE.remove(this.cloudId, this);
    }

    private void saveHealth(ItemStack stack, ServerLevel level) {
        float fraction = this.getHealth() / this.getMaxHealth();
        stack.set(Lakitu.cloudData.get(), LakituCloudItem.data(stack).withHealth(fraction, level.getGameTime()));
        this.savedHealth = fraction;
    }

    private ItemStack findItem(Player player) {
        return this.cloudId == null ? ItemStack.EMPTY : LakituCloudItem.find(player, this.cloudId);
    }

    private @Nullable Player riderOrLastRider(ServerLevel level) {
        if (this.getControllingPassenger() instanceof Player player)
            return player;
        return this.lastRider == null ? null : level.getServer().getPlayerList().getPlayer(this.lastRider);
    }

    private void poof(ServerLevel level) {
        level.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 0.4, this.getZ(), 24, 0.6, 0.25, 0.6, 0.02);
    }

    // --- Hazards ------------------------------------------------------------------------------

    /** Rain only counts where the top of the entity is open to the sky. */
    static boolean isInRain(Entity entity) {
        return entity.level().isRainingAt(BlockPos.containing(entity.getX(), entity.getBoundingBox().maxY, entity.getZ()));
    }

    static DamageSource rainDamage(ServerLevel level) {
        return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(Lakitu.RAIN));
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        // The rider can't hurt their own cloud (e.g. by swinging at something below them).
        if (source.getEntity() != null && this.hasPassenger(source.getEntity()))
            return false;
        return super.hurtServer(level, source, damage);
    }

    @Override
    protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
    }

    // --- Misc mob behaviour -----------------------------------------------------------------------

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState blockState) {
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.NEUTRAL;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.GHASTLING_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.GHASTLING_DEATH;
    }

    // --- GeckoLib -----------------------------------------------------------------------------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<LakituCloudEntity>("float", test -> test.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }
}
